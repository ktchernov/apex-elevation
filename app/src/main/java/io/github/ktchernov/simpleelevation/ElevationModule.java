package io.github.ktchernov.simpleelevation;

import android.support.annotation.NonNull;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.github.ktchernov.simpleelevation.api.GoogleElevationApi;
import io.github.ktchernov.simpleelevation.api.GoogleElevationApi.ElevationResult;
import io.github.ktchernov.simpleelevation.api.MockElevationApi;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.mock.BehaviorDelegate;
import retrofit2.mock.MockRetrofit;

@Module class ElevationModule {
	private static final String GOOGLE_MAPS_API_BASE_URL = "https://maps.googleapis.com/maps/api/";
	private static final ElevationResult MOCK_ELEVATION_RESULTS =
			ElevationResult.successResult(4729.2);
	private static final boolean USE_MOCK_ELEVATION = false;

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

	@NonNull static Retrofit buildRetrofit(OkHttpClient okHttpClient) {
		return new Retrofit.Builder()
				.baseUrl(GOOGLE_MAPS_API_BASE_URL)
				.addCallAdapterFactory(RxJavaCallAdapterFactory.create())
				.addConverterFactory(MoshiConverterFactory.create())
				.client(okHttpClient)
				.build();
	}

	@Singleton @Provides GoogleElevationApi googleElevationApi(Retrofit retrofit) {
		if (USE_MOCK_ELEVATION) {
			MockRetrofit mockRetrofit = new MockRetrofit.Builder(retrofit).build();
			BehaviorDelegate<GoogleElevationApi> behaviorDelegate =
					mockRetrofit.create(GoogleElevationApi.class);

			MockElevationApi mockElevationApi = new MockElevationApi(behaviorDelegate);
			mockElevationApi.setElevation(MOCK_ELEVATION_RESULTS);
			return mockElevationApi;
		} else {
			return retrofit.create(GoogleElevationApi.class);
		}
	}

	@Singleton @Provides @Named("ApiKey") String apiKey() {
		return this.apiKey;
	}
}
