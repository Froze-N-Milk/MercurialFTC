package org.mercurialftc.mercurialftc.silversurfer.geometry;

/**
 * An absolute angle, in either radians or degrees
 */
public abstract class Angle {
	private double theta;
	
	public Angle(double theta) {
		this.theta = absolute(theta);
	}
	public final double getTheta() {
		return this.theta;
	}
	
	public abstract double getDegrees();
	public abstract double getRadians();
	
	protected abstract double absolute(double theta);
	
	public final Angle setTheta(double theta) {
		this.theta = absolute(theta);
		return this;
	}
	
	public abstract Angle setTheta(double x, double y);
	
	/**
	 * non-mutating
	 * @param theta2
	 * @return a new angle, with the desired operation applied
	 */
	public abstract Angle add(double theta2);
	/**
	 * non-mutating
	 * @param other
	 * @return a new angle, with the desired operation applied
	 */
	public abstract Angle add(Angle other);
	/**
	 * non-mutating
	 * @param theta2
	 * @return a new angle, with the desired operation applied
	 */
	public abstract Angle subtract(double theta2);
	/**
	 * non-mutating
	 * @param other
	 * @return a new angle, with the desired operation applied
	 */
	public abstract Angle subtract(Angle other);
	
	/**
	 * finds the shortest distance from this to other
	 * @param other
	 * @return
	 */
	public abstract double findShortestDistance(Angle other);
	public abstract AngleRadians toAngleRadians();
	public abstract AngleDegrees toAngleDegrees();
}
