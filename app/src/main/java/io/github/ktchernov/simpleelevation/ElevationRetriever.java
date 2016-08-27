package io.github.ktchernov.simpleelevation;

import android.location.Location;

import javax.inject.Inject;
import javax.inject.Named;

import io.github.ktchernov.simpleelevation.GoogleElevationApi.Locations;
import io.github.ktchernov.simpleelevation.api.ThreadModel;
import rx.Observable;
import timber.log.Timber;

class ElevationRetriever {
	private final GoogleElevationApi elevationApi;
	private final String apiKey;
	private final ThreadModel threadModel;

	@Inject ElevationRetriever(GoogleElevationApi elevationApi, @Named("ApiKey") String apiKey,
							   ThreadModel threadModel) {
		this.elevationApi = elevationApi;
		this.apiKey = apiKey;
		this.threadModel = threadModel;
	}

	Observable<Elevation> elevationObservable(Location location) {

		Elevation gpsElevation = Elevation.fromGps(location.getAltitude());

		return elevationApi.getElevation(Locations.from(location), apiKey)
				.map(GoogleElevationApi.ElevationResult::getElevation)
				.doOnError(throwable -> Timber.e(throwable, "Error fetching elevation"))
				.map(elevation -> elevation.elevation == null ? gpsElevation : elevation)
				.onErrorReturn(throwable -> gpsElevation)
				.compose(threadModel.transformer());
	}

}
