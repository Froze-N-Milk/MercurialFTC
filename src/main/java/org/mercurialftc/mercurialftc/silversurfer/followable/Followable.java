package org.mercurialftc.mercurialftc.silversurfer.followable;

import org.mercurialftc.mercurialftc.silversurfer.followable.markers.Marker;
import org.mercurialftc.mercurialftc.silversurfer.geometry.Pose2D;
import org.mercurialftc.mercurialftc.silversurfer.geometry.Vector2D;

public abstract class Followable {
	private Output[] outputs;
	private Marker[] markers;
	
	protected final void setOutputs(Output[] outputs) {
		this.outputs = outputs;
	}
	public final Output[] getOutputs() {
		return outputs;
	}
	
	protected final void setMarkers(Marker[] markers) {
		this.markers = markers;
	}
	public final Marker[] getMarkers() {
		return markers;
	}
	
	public static class Output {
		public Vector2D getTranslationVector() {
			return translationVector;
		}
		
		public double getCallbackTime() {
			return callbackTime + accumulatedTime;
		}
		
		public double getAngularVelocity() {
			return angularVelocity;
		}


		public Pose2D getDestination() {
			return destination;
		}
		
		private final Vector2D translationVector; // controls x and y
		private final double callbackTime;
		private double accumulatedTime;
		private final Pose2D destination;
		
		public void setAccumulatedTime(double accumulatedTime) {
			this.accumulatedTime = accumulatedTime;
		}
		
		private final double angularVelocity; // controls heading
		public Output(Vector2D translationVector, double angularVelocity, double callbackTime, Pose2D destination) {
			this.translationVector = translationVector;
			this.angularVelocity = angularVelocity;
			this.callbackTime = callbackTime;
			this.accumulatedTime = 0;
			this.destination = destination;
		}
	}
}
