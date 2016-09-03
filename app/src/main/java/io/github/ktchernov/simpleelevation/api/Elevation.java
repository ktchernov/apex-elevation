package io.github.ktchernov.simpleelevation.api;

public class Elevation {
	public final Double elevation;
	public final boolean fromGps;
	public final boolean highAccuracy;

	public static Elevation fromApi(Double elevation, boolean highAccuracy) {
		return new Elevation(elevation, false, highAccuracy);
	}

	public static Elevation fromGps(double elevation, boolean highAccuracy) {
		return new Elevation(elevation, true, highAccuracy);
	}

	Elevation(Double elevation, boolean fromGps, boolean highAccuracy) {
		this.elevation = elevation;
		this.fromGps = fromGps;
		this.highAccuracy = highAccuracy;
	}

	public Double elevation() {
		return elevation;
	}

	@Override public boolean equals(Object o) {
		Elevation elevationOther = (Elevation) o;

		if (elevationOther == null) {
			return false;
		}

		return fromGps == elevationOther.fromGps &&
				highAccuracy == elevationOther.highAccuracy &&
				(elevation != null ? elevation.equals(elevationOther.elevation) :
						elevationOther.elevation == null);

	}

	@Override public int hashCode() {
		int result = elevation != null ? elevation.hashCode() : 0;
		result = 31 * result + (fromGps ? 1 : 0);
		result = 31 * result + (highAccuracy ? 1 : 0);
		return result;
	}

	@Override public String toString() {
		return "Elevation{" +
				"elevation=" + elevation +
				", fromGps=" + fromGps +
				'}';
	}
}
