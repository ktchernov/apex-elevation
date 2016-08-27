package io.github.ktchernov.simpleelevation.api;

import retrofit2.http.Query;
import retrofit2.mock.BehaviorDelegate;
import rx.Observable;

public class MockElevationApi implements GoogleElevationApi {
	private final BehaviorDelegate<GoogleElevationApi> delegate;
	private ElevationResult elevationResult;

	public MockElevationApi(BehaviorDelegate<GoogleElevationApi> delegate) {
		this.delegate = delegate;
	}

	public void setElevation(ElevationResult elevationResult) {
		this.elevationResult = elevationResult;
	}

	@Override public Observable<ElevationResult> getElevation(
			@Query("locations") Locations locations,
			@Query("key") String apiKey) {
		return delegate.returningResponse(elevationResult).getElevation(locations, apiKey);
	}
}
