package org.mercurialftc.mercurialftc.scheduler;

import org.firstinspires.ftc.robotcore.internal.system.AppUtil;
import org.jetbrains.annotations.NotNull;
import org.mercurialftc.mercurialftc.scheduler.commands.Command;
import org.mercurialftc.mercurialftc.scheduler.configoptions.ConfigOptionsManager;
import org.mercurialftc.mercurialftc.scheduler.subsystems.SubsystemInterface;
import org.mercurialftc.mercurialftc.scheduler.triggers.Trigger;

import java.io.*;
import java.util.*;

public class Scheduler {
	public static Scheduler scheduler;

	private static boolean schedulerRefreshEnabled, loggingEnabled;
	private static ConfigOptionsManager configOptionsManager;
	private final LinkedHashSet<SubsystemInterface> subsystems; // currently registered Subsystems
	private final LinkedHashSet<Trigger> triggers;
	private final Set<Command> composedCommands = Collections.newSetFromMap(new WeakHashMap<>());
	private final LinkedHashSet<Command> commands; // currently scheduled Commands
	private final ArrayList<Command> commandsToCancel; // commands to be cancelled this loop
	private final LinkedHashSet<Command> commandsToSchedule; // commands to be scheduled this loop;
	private final LinkedHashMap<SubsystemInterface, Command> requirements; // the mapping of required Subsystems to commands
	private final HashMap<String, SubsystemInterface> storedSubsystems;
	private OpModeEX.OpModeEXRunStates runState;

	private Scheduler() {
		this.subsystems = new LinkedHashSet<>();
		this.commands = new LinkedHashSet<>();
		this.commandsToCancel = new ArrayList<>();
		this.commandsToSchedule = new LinkedHashSet<>();
		this.requirements = new LinkedHashMap<>();
		this.triggers = new LinkedHashSet<>();
		this.storedSubsystems = new HashMap<>();
	}

	public static ConfigOptionsManager getConfigOptionsManager() {
		interpretConfigFiles();
		return configOptionsManager;
	}

	/**
	 * A safe method of accessing the scheduler singleton, if it has not been generated, the generation will be run.
	 *
	 * @return safe return of a non-null scheduler instance
	 */
	public static Scheduler getSchedulerInstance() {
		if (scheduler == null) {
			scheduler = new Scheduler();
		}
		return scheduler;
	}

	public static Scheduler freshInstance() {
		return scheduler = new Scheduler();
	}

	public static void interpretConfigFiles() {
		if (configOptionsManager != null) {
			schedulerRefreshEnabled = Boolean.TRUE.equals(configOptionsManager.getTomlParseResult().getBoolean(ConfigOptions.SCHEDULER_REFRESH_ENABLED.getOption()));
			loggingEnabled = Boolean.TRUE.equals(configOptionsManager.getTomlParseResult().getBoolean(ConfigOptions.LOGGING_ENABLED.getOption()));
			return;
		}

		File configOptionsFile = new File(AppUtil.FIRST_FOLDER, "mercurialftc/configOptions.toml");

		try {
			/*
			 * # this file is automatically generated and edited by mercurialftc's scheduler
			 * # you may add more settings here, and they will show up in the 'Edit Scheduler Config Options' OpMode that appears under the teleop list
			 * # removing either of these two properties will cause the scheduler to remake this file with the default settings
			 *
			 * schedulerRefreshEnabled = true
			 * loggingEnabled = false
			 *
			 */

			String defaultTomlString =
					"# this file is automatically generated and edited by mercurialftc's scheduler\n" +
							"# you may add more settings here and they will show up in the 'Edit Scheduler Config Options' OpMode that appears under the teleop list\n" +
							"# removing either of these two properties will cause the scheduler to remake this file with the default settings\n" +
							"\n" +
							"schedulerRefreshEnabled = true\n" +
							"loggingEnabled = true\n";

			configOptionsManager = new ConfigOptionsManager(configOptionsFile, defaultTomlString);
		} catch (IOException e) {
			throw new RuntimeException("Error creating/reading scheduler config:\n" + e);
		}
	}

	public static boolean isSchedulerRefreshEnabled() {
		interpretConfigFiles();
		return schedulerRefreshEnabled;
	}

	public static boolean isLoggingEnabled() {
		interpretConfigFiles();
		return loggingEnabled;
	}

	public LinkedHashSet<SubsystemInterface> getSubsystems() {
		return subsystems;
	}

	public LinkedHashSet<Trigger> getTriggers() {
		return triggers;
	}

	public LinkedHashSet<Command> getCommands() {
		return commands;
	}

	public void registerSubsystem(SubsystemInterface subsystem) {
		this.subsystems.add(subsystem);
	}

	public void pollSubsystemsPeriodic() {
		for (SubsystemInterface subsystem : subsystems) {
			subsystem.periodic();
		}
	}

	public void scheduleCommand(Command command) {
		commandsToSchedule.add(command);
	}

	private void cancelCommand(Command command, boolean interrupted) {
		if (command == null) return;
		if (!isScheduled(command)) return;
		command.end(interrupted);
		for (SubsystemInterface requirement : command.getRequiredSubsystems()) {
			requirements.remove(requirement, command);
		}
		commands.remove(command);
	}

	private void initialiseCommand(Command command) {
		if (command == null) return;
		if (isScheduled(command)) return;
		if (!command.getRunStates().contains(runState)) return;

		Set<SubsystemInterface> commandRequirements = command.getRequiredSubsystems();

		// if the subsystems required by the command are not required, register it
		if (Collections.disjoint(commandRequirements, requirements.keySet())) {
			initialiseCommand(command, commandRequirements);
			return;
		} else {
			// for each subsystem required, check the command currently requiring it, and make sure that they can all be overwritten
			for (SubsystemInterface subsystem : commandRequirements) {
				Command requirer = requirements.get(subsystem);
				if (requirer != null && !requirer.interruptable()) {
					return;
				}
			}
		}

		// cancel all required commands
		for (SubsystemInterface subsystem : commandRequirements) {
			Command requirer = requirements.get(subsystem);
			if (requirer != null) {
				commandsToCancel.add(requirer);
			}
		}

		initialiseCommand(command, commandRequirements);
	}

	private void initialiseCommand(Command command, @NotNull Set<SubsystemInterface> commandRequirements) {
		commands.add(command);
		for (SubsystemInterface requirement : commandRequirements) {
			requirements.put(requirement, command);
		}
		command.initialise();
	}

	public void pollCommands(OpModeEX.OpModeEXRunStates runState) {
		this.runState = runState;
		// checks to see if any commands are finished, if so, cancels them
		for (Command command : commands) {
			if (command.finished()) {
				cancelCommand(command, false);
			}
			// checks to see if we have exited the valid run states for this command, if so, cancels and interrupts the command.
			else if (!command.getRunStates().contains(runState)) {
				cancelCommand(command, true);
			}
		}


		// initialises all the commands that are due to be scheduled
		for (Command command : commandsToSchedule) {
			initialiseCommand(command);
		}

		// empties the queue
		commandsToSchedule.clear();

		// checks if any subsystems are not being used by any commands, if so, schedules the default command for that subsystem
		for (SubsystemInterface subsystem : subsystems) {
			if (!requirements.containsKey(subsystem)) {
				scheduleCommand(subsystem.getDefaultCommand());
			}
		}

		// initialises all the commands that are due to be scheduled
		for (Command command : commandsToSchedule) {
			initialiseCommand(command);
		}

		// empties the queue
		commandsToSchedule.clear();

		// cancels all cancel queued commands
		for (Command command : commandsToCancel) {
			cancelCommand(command, true);
		}
		// empties the queue
		commandsToCancel.clear();

		// runs the commands
		for (Command command : commands) {
			command.execute();
		}
	}

	public void registerTrigger(Trigger trigger) {
		triggers.add(trigger);
	}

	public void deregisterTrigger(Trigger trigger) {
		triggers.remove(trigger);
	}

	public void pollTriggers() {
		for (Trigger trigger : triggers) {
			trigger.poll();
		}
	}

	public void storeSubsystem(String name, SubsystemInterface subsystem) {
		storedSubsystems.put(name, subsystem);
	}

	public SubsystemInterface getStoredSubsystem(String name) {
		SubsystemInterface result = storedSubsystems.get(name);
		storedSubsystems.remove(name, result);
		return result;
	}

	/**
	 * Checks to see if a subsystem has a non-default command running
	 *
	 * @param subsystem the subsystem to check
	 * @return true if it isn't running its default command
	 */
	public boolean isBusy(SubsystemInterface subsystem) {
		return requirements.containsKey(subsystem) && !requirements.containsValue(subsystem.getDefaultCommand());
	}

	/**
	 * Register commands as composed. An exception will be thrown if these commands are scheduled
	 * directly or added to a composition.
	 *
	 * @param commands the commands to register
	 * @throws IllegalArgumentException if the given commands have already been composed.
	 */
	public void registerComposedCommands(Command... commands) {
		Set<Command> commandSet = new HashSet<>(Arrays.asList(commands));
		registerComposedCommands(commandSet);
	}

	/**
	 * Register commands as composed. An exception will be thrown if these commands are scheduled
	 * directly or added to a composition.
	 *
	 * @param commands the commands to register
	 * @throws IllegalArgumentException if the given commands have already been composed.
	 */
	public void registerComposedCommands(Set<Command> commands) {
		requireNotComposed(commands);
		composedCommands.addAll(commands);
	}

	/**
	 * Register commands as composed. An exception will be thrown if these commands are scheduled
	 * directly or added to a composition.
	 *
	 * @param commands the commands to register
	 * @throws IllegalArgumentException if the given commands have already been composed.
	 */
	public void registerComposedCommands(List<Command> commands) {
		Set<Command> commandSet = new HashSet<>(commands);
		requireNotComposed(commandSet);
		composedCommands.addAll(commandSet);
	}

	/**
	 * Checks to see if a command is scheduled
	 *
	 * @param command the command to check
	 * @return true if it is scheduled
	 */
	public boolean isScheduled(Command command) {
		return commands.contains(command);
	}

	/**
	 * Requires that the specified command hasn't been already added to a composition.
	 *
	 * @param command The command to check
	 * @throws IllegalArgumentException if the given commands have already been composed.
	 */
	public void requireNotComposed(Command command) {
		if (composedCommands.contains(command)) {
			throw new IllegalArgumentException(
					"Commands that have been composed may not be added to another composition or scheduled "
							+ "individually!");
		}
	}

	/**
	 * Requires that the specified commands not have been already added to a composition.
	 *
	 * @param commands The commands to check
	 * @throws IllegalArgumentException if the given commands have already been composed.
	 */
	public void requireNotComposed(Collection<Command> commands) {
		if (!Collections.disjoint(commands, getComposedCommands())) {
			throw new IllegalArgumentException(
					"Commands that have been composed may not be added to another composition or scheduled "
							+ "individually!");
		}
	}

	/**
	 * Check if the given command has been composed.
	 *
	 * @param command The command to check
	 * @return true if composed
	 */
	public boolean isComposed(Command command) {
		return getComposedCommands().contains(command);
	}

	public Set<Command> getComposedCommands() {
		return composedCommands;
	}

	public enum ConfigOptions {
		SCHEDULER_REFRESH_ENABLED("schedulerRefreshEnabled"),
		LOGGING_ENABLED("loggingEnabled");

		private final String option;

		ConfigOptions(String option) {
			this.option = option;
		}

		public String getOption() {
			return option;
		}
	}

}
