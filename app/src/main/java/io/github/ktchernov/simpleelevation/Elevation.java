package io.github.ktchernov.simpleelevation;

public class Elevation {
	public final Double elevation;
	public final boolean fromGps;

	public static Elevation fromApi(Double elevation) {
		return new Elevation(elevation, false);
	}

	public static Elevation fromGps(double elevation) {
		return new Elevation(elevation, true);
	}

	private Elevation(Double elevation, boolean fromGps) {
		this.elevation = elevation;
		this.fromGps = fromGps;
	}

	public Double elevation() {
		return elevation;
	}
}
