package org.mercurialftc.mercurialftc.util.hardware;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;

public class Encoder {
	private final DcMotor motor;
	private Direction direction;
	private double previousTime;
	private int previousPosition;
	public final VelocityDataPacket[] medians;
	private int medianIndex;
	private VelocityDataPacket output;
	
	public Encoder(DcMotor motor) {
		this.motor = motor;
		this.direction = Direction.FORWARD;
		
		motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
		
		previousTime = System.nanoTime() / 1e9;
		previousPosition = motor.getCurrentPosition();;
		
		medians = new VelocityDataPacket[5];
		for (int i = 0; i < medians.length; i++) {
			medians[i] = new VelocityDataPacket(0, 1);
		}
		medianIndex = 0;
	}
	
	public void reset() {
		output = new VelocityDataPacket(0, 1);
		motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
		motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
		previousPosition = motor.getCurrentPosition();
		previousTime = System.nanoTime() / 1e9;
	}
	
	public Direction getDirection() {
		return direction;
	}
	/**
	 * Allows you to set the direction of the counts and velocity without modifying the motor's direction state
	 * @param direction either reverse or forward depending on if encoder counts should be negated
	 */
	public void setDirection(Direction direction) {
		this.direction = direction;
	}
	
	public enum Direction {
		FORWARD((byte) 1),
		REVERSE((byte) -1);
		
		private final byte multiplier;
		Direction(byte multiplier) {
			this.multiplier = multiplier;
		}
		
		private byte getMultiplier() {
			return multiplier;
		}
	}
	
	private int getMultiplier() {
		return getDirection().getMultiplier() * (motor.getDirection() == DcMotorSimple.Direction.FORWARD ? 1 : -1);
	}
	
	/**
	 * Gets the position from the underlying motor and adjusts for the set direction.
	 * @return encoder position
	 */
	public int getCurrentPosition() {
		return motor.getCurrentPosition() * getMultiplier();
	}
	
	/**
	 * <p>uses insertion sort</p>
	 * sorts medians into a new array (to preserve the new data coming in) and then finds the median
	 * @return the median of medians
	 */
	private VelocityDataPacket internalGetVelocityFromMedians() {
		VelocityDataPacket[] sortedMedians = medians;
		int n = 5; //length of medians
		for (int i = 1; i < n; i++) {
			VelocityDataPacket key = sortedMedians[i];
			int j = i - 1;
			while(j >= 0 && sortedMedians[j].getVelocity() > key.getVelocity()) {
				sortedMedians[j + 1] = sortedMedians[j];
				j--;
			}
			sortedMedians[j + 1] = key;
		}
		
		return sortedMedians[2];
	}
	
	/**
	 * needs {@link #updateVelocity()} to be called exactly once per loop, before this function is ever called, to be accurate
	 * @return the current value of the output, calculated by {@link #updateVelocity()}
	 */
	public VelocityDataPacket getVelocityDataPacket() {
		return output;
	}
	
	/** TODO: change this potentially, look into kalman filters
	 * to be tested simpler method of obtaining velocity than what rr has
	 * <p>needs to be called once per loop to be accurate</p>
	 * <p>if using this in a {@link com.mercurialftc.mercurialftc.scheduler.subsystems.SubsystemInterface} or subclass, chuck it in the periodic loop, and never think about it again</p>
	 * @return measured velocity
	 */
	public void updateVelocity() {
		int currentPosition = getCurrentPosition();
		double currentTime = System.nanoTime() / 1e9;
		double dt = currentTime - previousTime;
		
		VelocityDataPacket velocity = new VelocityDataPacket((currentPosition - previousPosition), dt);
		
		previousTime = currentTime;
		previousPosition = currentPosition;

//		medians[medianIndex] = velocity;
//		medianIndex = (medianIndex + 1) % 5;
//
//		output = internalGetVelocityFromMedians();
		output = velocity;
	}
	
	public static class VelocityDataPacket {
		private final double deltaTime;
		private final int deltaPosition;
		
		public double getDeltaTime() {
			return deltaTime;
		}
		
		public int getDeltaPosition() {
			return deltaPosition;
		}
		
		private VelocityDataPacket(int deltaPosition, double deltaTime) {
			this.deltaPosition = deltaPosition;
			this.deltaTime = deltaTime;
		}
		
		public double getVelocity() {
			return deltaPosition / deltaTime;
		}
	}
}
