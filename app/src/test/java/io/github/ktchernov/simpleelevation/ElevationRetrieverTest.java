package io.github.ktchernov.simpleelevation;

import android.location.Location;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import io.github.ktchernov.simpleelevation.GoogleElevationApi.ElevationResult;
import io.github.ktchernov.simpleelevation.api.ThreadModel;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.http.Query;
import retrofit2.mock.BehaviorDelegate;
import retrofit2.mock.MockRetrofit;
import retrofit2.mock.NetworkBehavior;
import rx.Observable;
import rx.observers.TestSubscriber;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class ElevationRetrieverTest {

	private static final String TEST_API_KEY = "apiKey";
	private static final double TEST_ELEVATION = 123.11;

	private MockElevationApi mockElevationApi;
	private ElevationRetriever elevationRetriever;
	private NetworkBehavior networkBehavior;
	@Mock Location mockLocation;

	@Before public void setUp() {
		networkBehavior = NetworkBehavior.create();
		networkBehavior.setDelay(0, TimeUnit.SECONDS);
		Retrofit retrofit =
				ElevationModule.buildRetrofit(new OkHttpClient.Builder().build());
		MockRetrofit mockRetrofit = new MockRetrofit.Builder(retrofit)
				.networkBehavior(networkBehavior)
				.build();

		BehaviorDelegate<GoogleElevationApi> mockGoogleElevationDelegate =
				mockRetrofit.create(GoogleElevationApi.class);

		mockElevationApi = new MockElevationApi(mockGoogleElevationDelegate);

		ThreadModel immediateThreadModel = mock(ThreadModel.class);
		Observable.Transformer transformer = observable -> observable;
		doReturn(transformer).when(immediateThreadModel).transformer();

		elevationRetriever =
				new ElevationRetriever(mockElevationApi, TEST_API_KEY, immediateThreadModel);

		doReturn(12.).when(mockLocation).getLongitude();
		doReturn(41.).when(mockLocation).getLatitude();
	}

	@Test public void getElevation_withApiResult_usesApiResult() {
		mockElevationApi.setElevation(new ElevationResult(
				ElevationResult.STATUS_OK,
				Collections.singletonList(new ElevationResult.Result(TEST_ELEVATION))));
		Observable<Elevation> elevationObservable =
				elevationRetriever.elevationObservable(mockLocation);

		TestSubscriber<Elevation> testSubscriber = new TestSubscriber<>();
		elevationObservable.subscribe(testSubscriber);

		testSubscriber.assertValue(Elevation.fromApi(TEST_ELEVATION));
	}

	@Test public void getElevation_noApiResult_usesOriginalResult() {
		networkBehavior.setFailurePercent(100);
		doReturn(TEST_ELEVATION).when(mockLocation).getAltitude();
		Observable<Elevation> elevationObservable =
				elevationRetriever.elevationObservable(mockLocation);

		TestSubscriber<Elevation> testSubscriber = new TestSubscriber<>();
		elevationObservable.subscribe(testSubscriber);

		testSubscriber.assertValue(Elevation.fromGps(TEST_ELEVATION));
	}

	@Test public void getElevation_errorApiResult_usesOriginalResult() {
		mockElevationApi.setElevation(new ElevationResult(
				"not ok",
				Collections.singletonList(new ElevationResult.Result(-1))));
		doReturn(TEST_ELEVATION).when(mockLocation).getAltitude();
		Observable<Elevation> elevationObservable =
				elevationRetriever.elevationObservable(mockLocation);

		TestSubscriber<Elevation> testSubscriber = new TestSubscriber<>();
		elevationObservable.subscribe(testSubscriber);

		testSubscriber.assertValue(Elevation.fromGps(TEST_ELEVATION));
	}

	private static class MockElevationApi implements GoogleElevationApi {
		private final BehaviorDelegate<GoogleElevationApi> delegate;
		private ElevationResult elevationResult;

		MockElevationApi(BehaviorDelegate<GoogleElevationApi> delegate) {
			this.delegate = delegate;
		}

		void setElevation(ElevationResult elevationResult) {
			this.elevationResult = elevationResult;
		}

		@Override public Observable<ElevationResult> getElevation(
				@Query("locations") Locations locations,
				@Query("key") String apiKey) {
			return delegate.returningResponse(elevationResult).getElevation(locations, apiKey);
		}
	}
}