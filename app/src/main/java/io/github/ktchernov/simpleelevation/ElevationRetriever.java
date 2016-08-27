package io.github.ktchernov.simpleelevation;

import android.location.Location;

import io.github.ktchernov.simpleelevation.GoogleElevationApi.Locations;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

class ElevationRetriever {

	private final GoogleElevationApi elevationApi;
	private final String apiKey;

	ElevationRetriever(String apiKey) {
		this.apiKey = apiKey;

		HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
		logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

		OkHttpClient client = new OkHttpClient.Builder()
				.addInterceptor(logging)
				.build();

		elevationApi = new Retrofit.Builder()
				.baseUrl("https://maps.googleapis.com/maps/api/")
				.addCallAdapterFactory(RxJavaCallAdapterFactory.create())
				.addConverterFactory(MoshiConverterFactory.create())
				.client(client)
				.build()
				.create(GoogleElevationApi.class);
	}

	Observable<Elevation> elevationObservable(Location location) {
		return elevationApi.getElevation(Locations.from(location), apiKey)
				.map(GoogleElevationApi.ElevationResult::getElevation)
				.doOnError(throwable -> Timber.e(throwable, "Error fetching elevation"))
				.onErrorResumeNext(Observable.just(Elevation.fromGps(location.getAltitude())))
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread());

	}

}
