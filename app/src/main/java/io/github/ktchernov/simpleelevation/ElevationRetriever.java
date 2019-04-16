package io.github.ktchernov.simpleelevation;

import android.location.Location;

import javax.inject.Inject;
import javax.inject.Named;

import io.github.ktchernov.simpleelevation.api.Elevation;
import io.github.ktchernov.simpleelevation.api.GoogleElevationApi;
import io.github.ktchernov.simpleelevation.api.GoogleElevationApi.Locations;
import io.github.ktchernov.simpleelevation.api.ThreadModel;
import rx.Observable;
import timber.log.Timber;

class ElevationRetriever {
	// accuracy above this threshold (in metres) will be considered low accuracy
	private static final double CACHE_LOCATION_PREFERRED_ACCURACY = 30.0;

	// if the new cached location is within this many meters, do not do a new API call
	private static final double CACHE_LOCATION_MIN_DISTANCE = CACHE_LOCATION_PREFERRED_ACCURACY / 2;

	private final GoogleElevationApi elevationApi;
	private final String apiKey;
	private final ThreadModel threadModel;
	private Location currentBestLocation;
	private Elevation lastElevation;

	@Inject ElevationRetriever(GoogleElevationApi elevationApi, @Named("ApiKey") String apiKey,
							   ThreadModel threadModel) {
		this.elevationApi = elevationApi;
		this.apiKey = apiKey;
		this.threadModel = threadModel;
	}

	Observable<Elevation> elevationObservable(Location location) {
//		if (!isBetterLocation(location)) {
//			Timber.v("Cached location used");
//			return Observable.just(lastElevation);
//		}

		boolean highAccuracy = location.getAccuracy() < CACHE_LOCATION_PREFERRED_ACCURACY;

		Elevation gpsElevation = Elevation.fromGps(location.getAltitude(), highAccuracy);

		return elevationApi.getElevation(Locations.from(location), apiKey)
				.map((elevationResult) -> elevationResult.getElevation(highAccuracy))
				.doOnNext(elevation -> cacheElevation(location, elevation))
				.doOnError(throwable -> Timber.e(throwable, "Error fetching elevation"))
				.map(elevation -> elevation.elevation == null ? gpsElevation : elevation)
				.onErrorReturn(throwable -> gpsElevation)
				.compose(threadModel.transformer());
	}


	// Based on: https://developer.android.com/guide/topics/location/strategies.html
	// But with more focus on the distance delta rather than the time.
	private boolean isBetterLocation(Location location) {
		if (currentBestLocation == null) {
			// A new location is always better than no location
			return true;
		}

		boolean hasMoved = currentBestLocation.distanceTo(location) > CACHE_LOCATION_MIN_DISTANCE;
		if (!hasMoved) {
			return false;
		}

		// Check whether the new location fix is more or less accurate
		int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
		boolean isLessAccurate = accuracyDelta > 0;
		boolean isMoreAccurate = accuracyDelta < 0;
		boolean isSignificantlyLessAccurate = accuracyDelta > CACHE_LOCATION_PREFERRED_ACCURACY;

		// Check if the old and new location are from the same provider
		boolean isFromSameProvider = isSameProvider(location.getProvider(),
				currentBestLocation.getProvider());

		// Check whether the new location fix is newer or older
		long timeDelta = location.getTime() - currentBestLocation.getTime();
		boolean isNewer = timeDelta > 0;

		// Determine location quality using a combination of timeliness and accuracy
		if (isMoreAccurate) {
			return true;
		} else if (isNewer && !isLessAccurate) {
			return true;
		} else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
			return true;
		}
		return false;
	}

	/** Checks whether two providers are the same */
	private static boolean isSameProvider(String provider1, String provider2) {
		if (provider1 == null) {
			return provider2 == null;
		}
		return provider1.equals(provider2);
	}

	private void cacheElevation(Location location, Elevation elevation) {
		this.currentBestLocation = location;
		this.lastElevation = elevation;
	}

}
