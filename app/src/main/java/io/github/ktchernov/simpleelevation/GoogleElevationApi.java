package io.github.ktchernov.simpleelevation;

import android.location.Location;

import com.squareup.moshi.Json;

import java.util.List;
import java.util.Locale;

import retrofit2.http.GET;
import retrofit2.http.Query;
import rx.Observable;

interface GoogleElevationApi {

	@GET("elevation/json") Observable<ElevationResult> getElevation(
			@Query("locations") Locations locations,
			@Query("key") String apiKey);

	class Locations {
		private final String locationsString;

		private Locations(String locationsString) {
			this.locationsString = locationsString;
		}

		@Override public String toString() {
			return locationsString;
		}

		public static Locations from(Location location) {
			return new Locations(String
					.format(Locale.US, "%f,%f", location.getLatitude(), location.getLongitude()));
		}
	}

	class ElevationResult {
		static final String STATUS_OK = "OK";

		@Json(name = "status") private String status;
		@Json(name = "results") private List<Result> results;

		ElevationResult(String status, List<Result> results) {
			this.status = status;
			this.results = results;
		}

		public Elevation getElevation() {
			if (!STATUS_OK.equals(status) || results == null || results.size() == 0) {
				return Elevation.fromApi(null);
			}

			return Elevation.fromApi(results.get(0).elevation);
		}

		static class Result {
			@Json(name = "elevation") double elevation;

			Result(double elevation) {
				this.elevation = elevation;
			}
		}
	}
}
