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
		Elevation gpsElevation = Elevation.fromGps(location.getAltitude());

		if (!isBetterLocation(location)) {
			Timber.v("Cached location used");
			return Observable.just(lastElevation);
		}

		return elevationApi.getElevation(Locations.from(location), apiKey)
				.map(GoogleElevationApi.ElevationResult::getElevation)
				.doOnNext(elevation -> cacheElevation(location, elevation))
				.doOnError(throwable -> Timber.e(throwable, "Error fetching elevation"))
				.map(elevation -> elevation.elevation == null ? gpsElevation : elevation)
				.onErrorReturn(throwable -> gpsElevation)
				.compose(threadModel.transformer());
	}

	private static final int CACHE_LOCATION_TIME_OUT = 1000 * 10;

	// based on: https://developer.android.com/guide/topics/location/strategies.html
	private boolean isBetterLocation(Location location) {
		if (currentBestLocation == null) {
			// A new location is always better than no location
			return true;
		}

		// Check whether the new location fix is newer or older
		long timeDelta = location.getTime() - currentBestLocation.getTime();
		boolean isSignificantlyNewer = timeDelta > CACHE_LOCATION_TIME_OUT;
		boolean isSignificantlyOlder = timeDelta < -CACHE_LOCATION_TIME_OUT;
		boolean isNewer = timeDelta > 0;

		// If it's been more than two minutes since the current location, use the new location
		// because the user has likely moved
		if (isSignificantlyNewer) {
			return true;
			// If the new location is more than two minutes older, it must be worse
		} else if (isSignificantlyOlder) {
			return false;
		}

		boolean hasMoved = currentBestLocation.distanceTo(location) > 10.0;
		if (!hasMoved) {
			return false;
		}

		// Check whether the new location fix is more or less accurate
		int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
		boolean isLessAccurate = accuracyDelta > 0;
		boolean isMoreAccurate = accuracyDelta < 0;
		boolean isSignificantlyLessAccurate = accuracyDelta > 200;

		// Check if the old and new location are from the same provider
		boolean isFromSameProvider = isSameProvider(location.getProvider(),
				currentBestLocation.getProvider());

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
