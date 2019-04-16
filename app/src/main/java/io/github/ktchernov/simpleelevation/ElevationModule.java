package io.github.ktchernov.simpleelevation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.github.ktchernov.simpleelevation.api.GoogleElevationApi;
import io.github.ktchernov.simpleelevation.api.GoogleElevationApi.ElevationResult;
import io.github.ktchernov.simpleelevation.api.MockElevationApi;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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
	private final Context appContext;

	ElevationModule(String apiKey, Context appContext) {
		this.apiKey = apiKey;
		this.appContext = appContext;
	}

	@Singleton @Provides OkHttpClient okHttpClient() {
		OkHttpClient.Builder builder = new OkHttpClient.Builder();
		if (BuildConfig.DEBUG) {
			HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
			logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
			builder.addInterceptor(logging);
		}

		builder.addInterceptor(chain -> {
			Request request = chain.request();
			Headers.Builder headersBuilder = request.headers().newBuilder();
			String packageName = appContext.getPackageName();
			headersBuilder.add("X-Android-Package", packageName);
			String sig = getSignature();
			headersBuilder.add("X-Android-Cert", sig);


			Headers headers = headersBuilder.build();
			request = request.newBuilder().headers(headers).build();

			return chain.proceed(request);
		});

		return builder.build();
	}

	// Adapted from: https://gist.github.com/scottyab/b849701972d57cf9562e
	@SuppressLint("PackageManagerGetSignatures")
	private String getSignature() {
		try {
			PackageInfo packageInfo = appContext.getPackageManager().getPackageInfo(
					appContext.getPackageName(), PackageManager.GET_SIGNATURES);

			return getSHA1(packageInfo.signatures[0].toByteArray());
		} catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
			return "";
		}
	}

	private static String getSHA1(byte[] sig) throws NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance("SHA-1");
		digest.update(sig);
		byte[] hashtext = digest.digest();
		return bytesToHex(hashtext);
	}

	private static String bytesToHex(byte[] bytes) {
		final char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8',
				'9', 'A', 'B', 'C', 'D', 'E', 'F' };
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for (int j = 0; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
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
