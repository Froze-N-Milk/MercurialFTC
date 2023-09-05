package org.mercurialftc.mercurialftc.example.autointoteleop;

import org.mercurialftc.mercurialftc.example.DemoSubsystem;
import org.mercurialftc.mercurialftc.scheduler.OpModeEX;
import org.mercurialftc.mercurialftc.scheduler.Scheduler;

/**
 * see readme.md in this directory
 */
public class Auto extends OpModeEX {
	DemoSubsystem demoSubsystem;
	/**
	 * called before {@link #initEX()}, solely for initialising all subsystems, ensures that they are registered with the correct {@link Scheduler}, and that their init methods will be run
	 */
	@Override
	public void registerSubsystems() {
		demoSubsystem = new DemoSubsystem(this);
	}
	
	/**
	 * should contain your regular init code
	 */
	@Override
	public void initEX() {
		Scheduler.refreshScheduler = false; // we do NOT want to refresh the scheduler when we swap to teleop
	}
	
	/**
	 * registers triggers after the subsystem and regular init code,
	 * useful for organisation of your OpModeEX, but functionally no different to initialising them at the end of {@link #initEX()}
	 */
	@Override
	public void registerTriggers() {
		// typically no trigger code is required in your auto!
	}
	
	@Override
	public void init_loopEX() {
	
	}
	
	@Override
	public void startEX() {
		// reset everything!
		
		// run your not asynchronous auto code here!
	}
	
	@Override
	public void loopEX() {
		// run your asynchronous auto code here!
	}
	
	@Override
	public void stopEX() {
		// add each subsystem to the stored subsystems arraylist in the scheduler
		// we made sure not to wipe the scheduler, so we can get them back!
		getScheduler().storeSubsystem(demoSubsystem);
//		getScheduler().storeSubsystem(demoSubsystem2); (this doesn't work bc i dont have more than one subsystem, but for demonstration's sake)
	}
}
