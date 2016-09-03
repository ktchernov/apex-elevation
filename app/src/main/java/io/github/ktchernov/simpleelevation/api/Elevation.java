package io.github.ktchernov.simpleelevation.api;

public class Elevation {
	public final Double elevation;
	public final boolean fromGps;

	public static Elevation fromApi(Double elevation) {
		return new Elevation(elevation, false);
	}

	public static Elevation fromGps(double elevation) {
		return new Elevation(elevation, true);
	}

	Elevation(Double elevation, boolean fromGps) {
		this.elevation = elevation;
		this.fromGps = fromGps;
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
				(elevation != null ? elevation.equals(elevationOther.elevation) :
						elevationOther.elevation == null);

	}

	@Override public int hashCode() {
		int result = elevation != null ? elevation.hashCode() : 0;
		result = 31 * result + (fromGps ? 1 : 0);
		return result;
	}

	@Override public String toString() {
		return "Elevation{" +
				"elevation=" + elevation +
				", fromGps=" + fromGps +
				'}';
	}
}
