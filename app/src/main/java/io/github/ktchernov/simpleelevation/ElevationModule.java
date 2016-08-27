package io.github.ktchernov.simpleelevation;

import android.support.annotation.NonNull;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;

@Module class ElevationModule {
	private static final String GOOGLE_MAPS_API_BASE_URL = "https://maps.googleapis.com/maps/api/";

	private final String apiKey;

	public ElevationModule(String apiKey) {
		this.apiKey = apiKey;
	}

	@Singleton @Provides OkHttpClient okHttpClient() {
		HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
		logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

		return new OkHttpClient.Builder()
				.addInterceptor(logging)
				.build();
	}

	@Singleton @Provides Retrofit retrofit(OkHttpClient okHttpClient) {
		return buildRetrofit(okHttpClient);
	}

	@NonNull public static Retrofit buildRetrofit(OkHttpClient okHttpClient) {
		return new Retrofit.Builder()
				.baseUrl(GOOGLE_MAPS_API_BASE_URL)
				.addCallAdapterFactory(RxJavaCallAdapterFactory.create())
				.addConverterFactory(MoshiConverterFactory.create())
				.client(okHttpClient)
				.build();
	}

	@Singleton @Provides GoogleElevationApi googleElevationApi(Retrofit retrofit) {
		return retrofit.create(GoogleElevationApi.class);
	}

	@Singleton @Provides @Named("ApiKey") String apiKey() {
		return this.apiKey;
	}
}
