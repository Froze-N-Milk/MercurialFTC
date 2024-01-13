package dev.frozenmilk.mercurial

import dev.frozenmilk.dairy.calcified.Calcified
import dev.frozenmilk.dairy.core.Feature
import dev.frozenmilk.dairy.core.OpModeWrapper
import dev.frozenmilk.dairy.core.dependencyresolution.dependencies.Dependency
import dev.frozenmilk.dairy.core.dependencyresolution.dependencyset.DependencySet
import dev.frozenmilk.mercurial.bindings.Binding
import dev.frozenmilk.mercurial.bindings.BoundGamepad
import dev.frozenmilk.mercurial.collections.emptyMutableWeakRefSet
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.subsystems.Subsystem
import dev.frozenmilk.util.cell.LateInitCell
import dev.frozenmilk.util.cell.LazyCell
import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMeta
import java.util.Collections
import java.util.WeakHashMap

/**
 * Mercurial's scheduler feature
 */
object Mercurial : Feature {
	//
	// Dependencies
	//

	private val calcifiedCell = LateInitCell<Feature>()

	/**
	 * the current cross-pollination behaviour of this system
	 *
	 * @see Mercurify.crossPollinate
	 */
	var crossPollinate = true
		private set

	override val dependencies: Set<Dependency<*, *>> = DependencySet(this)
			.includesExactlyOneOf(Attach::class.java)
			.bindOutputTo {
				crossPollinate = when (it) {
					is Attach -> {
						it.crossPollinate
					}

					else -> {
						true
					}
				}
			}
			.dependsOnOneOf(Calcified::class.java)
			.bindOutputTo(calcifiedCell)

	private val calcified: Calcified
		get() {
			return calcifiedCell.get() as Calcified
		}

	//
	// Fields
	//

	// External
	private val gamepad1Cell = LazyCell { BoundGamepad(Calcified.gamepad1) }
	@JvmStatic
	val gamepad1: BoundGamepad by gamepad1Cell
	private val gamepad2Cell = LazyCell { BoundGamepad(Calcified.gamepad2) }
	@JvmStatic
	val gamepad2: BoundGamepad by gamepad2Cell

	var runState: RunStates = RunStates.INIT_LOOP
		private set

	// Internal
	private val composedCommands = emptyMutableWeakRefSet<Command>()
	private val toSchedule = mutableListOf<Command>()
	private val toCancel = mutableListOf<Pair<Boolean, Command>>()
	private val subsystems = WeakHashMap<Subsystem, Boolean>()
	private val enabledSubsytems: Collection<Subsystem>
		get() {
			return subsystems.filter { it.value }.map { it.key }
		}
	private val requirementMap = WeakHashMap<Subsystem, Command>()
	private val defaultCommandMap = HashMap<Subsystem, Command?>()
	private val bindings = emptyMutableWeakRefSet<Binding>()

	private var previousOpMode = OpModeMeta.Flavor.SYSTEM
	//
	// External Functions
	//

	/**
	 * registers the subsystem, inits it if it wasn't already in the list
	 */
	@JvmStatic
	fun registerSubsystem(subsystem: Subsystem) {
		if(!subsystems.contains(subsystem)) {
			subsystem.init()
		}
		subsystems[subsystem] = true
	}

	@JvmStatic
	fun deregisterSubsystem(subsystem: Subsystem) {
		subsystems.remove(subsystem)
	}

	@JvmStatic
	fun setDefaultCommand(subsystem: Subsystem, command: Command?) {
		defaultCommandMap[subsystem] = command
	}

	@JvmStatic
	fun getDefaultCommand(subsystem: Subsystem): Command? {
		return defaultCommandMap[subsystem]
	}

	@JvmStatic
	fun isScheduled(command: Command): Boolean {
		return requirementMap.containsValue(command)
	}

	@JvmStatic
	fun scheduleCommand(command: Command) {
		toSchedule.add(command)
	}

	@JvmStatic
	fun cancelCommand(command: Command) {
		toCancel.add(true to command)
	}

	@JvmStatic
	fun registerComposedCommands(commands: Collection<Command>) {
		composedCommands.addAll(commands)
	}

	//
	// Internal Functions
	//

	internal fun registerBinding(binding: Binding) {
		bindings.add(binding)
	}

	private fun clearToCancel() {
		toCancel.forEach {(interrupted, command) ->
			if (!isScheduled(command)) return@forEach
			command.end(interrupted)
			for (requirement in command.requiredSubsystems) {
				requirementMap.remove(requirement, command)
			}
		}

		toCancel.clear()
	}

	private fun clearToSchedule(runState: RunStates) {
		toSchedule.forEach {command ->
			if (composedCommands.contains(command)) return@forEach
			if (!command.runStates.contains(runState)) return@forEach

			// if the subsystems required by the command are not required, register it
			if (Collections.disjoint(command.requiredSubsystems, requirementMap.keys)) {
				initialiseCommand(command, command.requiredSubsystems)
				return@forEach
			}
			else {
				// for each subsystem required, check the command currently requiring it, and make sure that they can all be overwritten
				for (subsystem in command.requiredSubsystems) {
					val requirer: Command? = requirementMap[subsystem]
					if (requirer != null && !requirer.interruptible) {
						return@forEach
					}
				}
			}

			// cancel all required commands
			command.requiredSubsystems.forEach {
				val requiringCommand = requirementMap[it]
				if(requiringCommand != null) { toCancel.add(true to requiringCommand) }
			}
		}

		toSchedule.clear()
	}

	private fun initialiseCommand(command: Command, commandRequirements: Set<Subsystem>) {
		for (requirement in commandRequirements) {
			requirementMap[requirement] = command
		}
		command.initialise()
	}

	private fun resolveSchedulerUpdate(runState: RunStates) {

		// checks to see if any commands are finished, if so, cancels them
		requirementMap.values.forEach {
			if (it.finished()) toCancel.add(false to it)
			else if (runState !in it.runStates) toCancel.add(true to it)
		}

		// cancel the commands
		clearToCancel()

		// schedule any default commands that can be scheduled
		enabledSubsytems.forEach {
			if (requirementMap[it] == null) {
				defaultCommandMap[it]?.schedule()
			}
		}

		// schedule new commands
		clearToSchedule(runState)

		// cancel the commands that got cancelled by the scheduling of new commands
		clearToCancel()
	}

	private fun pollPeriodics() {
		subsystems.filter { it.value }.forEach { it.key.periodic() }
	}

	private fun pollBindings() {
		bindings.forEach { if (it.activationCondition.get()) { it.toRun.schedule() } }
	}

	//
	// Hooks
	//
	override fun preUserInitHook(opMode: OpModeWrapper) {
		runState = RunStates.INIT_LOOP
	}

	override fun postUserInitHook(opMode: OpModeWrapper) {
		if(crossPollinate && previousOpMode != OpModeMeta.Flavor.AUTONOMOUS) {
			enabledSubsytems.forEach { it.refresh() }
		}
	}

	override fun preUserInitLoopHook(opMode: OpModeWrapper) {
		pollPeriodics()
		pollBindings()
	}

	override fun postUserInitLoopHook(opMode: OpModeWrapper) {
		resolveSchedulerUpdate(runState)
	}

	override fun preUserStartHook(opMode: OpModeWrapper) {
		runState = RunStates.LOOP
	}

	override fun postUserStartHook(opMode: OpModeWrapper) {
	}

	override fun preUserLoopHook(opMode: OpModeWrapper) {
		pollPeriodics()
		pollBindings()
	}

	override fun postUserLoopHook(opMode: OpModeWrapper) {
		resolveSchedulerUpdate(runState)
	}

	override fun preUserStopHook(opMode: OpModeWrapper) {
	}

	override fun postUserStopHook(opMode: OpModeWrapper) {
		bindings.clear()
		previousOpMode = opMode.opModeType
		// de-init all subsystems
		subsystems.replaceAll { _, _ -> false }
	}


	annotation class Attach(
			/**
			 * Controls when [Mercurial] runs reset on subsystems
			 *
			 * Set to false if you want to reset the subsystems by hand
			 *
			 * By default, resets subsystems at the start of an auto, and at the end of a teleop, allowing values to be carried over from an auto to a teleop
			 *
			 * @see dev.frozenmilk.dairy.calcified.Calcified.Attach.crossPollinate
			 */
			val crossPollinate: Boolean = true
	)
}
