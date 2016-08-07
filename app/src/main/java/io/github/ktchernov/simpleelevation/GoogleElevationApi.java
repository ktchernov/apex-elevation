package io.github.ktchernov.simpleelevation;

import android.location.Location;
import android.text.TextUtils;

import com.squareup.moshi.Json;

import java.util.List;
import java.util.Locale;

import retrofit2.http.GET;
import retrofit2.http.Query;
import rx.Observable;

public interface GoogleElevationApi {

	@GET("elevation/json") Observable<ElevationResult> getElevation(
			@Query("locations") Locations locations,
			@Query("key") String apiKey);

	class Locations {
		private final String locationsString;

		private Locations(String locationsString) {
			this.locationsString = locationsString;
		}

		public String toString() {
			return locationsString;
		}

		public static Locations from(Location location) {
			return new Locations(String
					.format(Locale.US, "%f,%f", location.getLatitude(), location.getLongitude()));
		}
	}

	class ElevationResult {

		@Json(name = "status") private String status;
		@Json(name = "results") private List<Result> results;

		private static class Result {
			@Json(name = "elevation") double elevation;
		}

		public double getElevation() {
			if (!TextUtils.equals(status, "OK") || results == null || results.size() == 0) {
				return 0.0;
			}

			return results.get(0).elevation;
		}
	}
}
