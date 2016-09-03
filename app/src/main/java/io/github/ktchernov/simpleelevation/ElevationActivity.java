package io.github.ktchernov.simpleelevation;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.SupportErrorDialogFragment;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.github.ktchernov.simpleelevation.api.Elevation;
import pl.charmas.android.reactivelocation.ReactiveLocationProvider;
import pl.charmas.android.reactivelocation.observables.GoogleAPIConnectionException;
import rx.Observable;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import timber.log.Timber;

public class ElevationActivity extends AppCompatActivity {

	private static final int LOCATION_REQUEST_INTERVAL = 5000;
	private static final int FASTEST_LOCATION_INTERVAL = 1000;

	static {
		if (BuildConfig.DEBUG) {
			Timber.plant(new Timber.DebugTree());
		}
	}

	private static final int REQUEST_CODE_GRANT_LOCATION_PERMISSION = 100;
	private static final int REQUEST_RESOLVE_ERROR = 101;
	private static final int REQUEST_CHECK_SETTINGS = 102;

	private final NumberFormat numberFormat = new DecimalFormat("###,###");
	private ReactiveLocationProvider reactiveLocationProvider;
	private Subscription elevationFetchSubscription = Subscriptions.unsubscribed();
	private UnitLocale unitLocale;
	private boolean locationIsStale;
	private boolean hasElevation;

	@Inject ElevationRetriever elevationRetriever;

	@BindView(R.id.content_layout) ViewGroup contentLayout;
	@BindView(R.id.approximate_warning) View approximateWarning;
	@BindView(R.id.approximate_info) View approximateInfo;
	@BindView(R.id.elevation_text_view) TextView elevationTextView;
	@BindView(R.id.elevation_unit_text_view) TextView elevationUnitTextView;
	@BindView(R.id.gps_progress_bar) ProgressBar gpsProgressBar;
	private Snackbar errorSnackbar;

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_elevation);

		ButterKnife.bind(this);

		reactiveLocationProvider = new ReactiveLocationProvider(getApplicationContext());

		ElevationComponent elevationComponent = DaggerElevationComponent.builder()
				.elevationModule(new ElevationModule(getString(R.string.googleElevationApiKey)))
				.build();

		elevationComponent.inject(this);
	}

	@Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
													 @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == REQUEST_CODE_GRANT_LOCATION_PERMISSION) {
			if (grantResults.length > 0
					&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				startFetchLocation();
			} else {
				createErrorSnackbar(R.string.location_permission_required_snackbar)
						.setAction(R.string.settings_link, this::onSnackbarSettingsClick)
						.show();
			}

		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_CHECK_SETTINGS && resultCode == RESULT_OK) {
			errorSnackbar.dismiss();
			startFetchLocation();
		}
	}

	@Override protected void onStart() {
		super.onStart();

		unitLocale = UnitLocale.getDefault();
		if (unitLocale == UnitLocale.IMPERIAL) {
			elevationUnitTextView.setText(R.string.unit_feet);
		} else {
			elevationUnitTextView.setText(R.string.unit_metres);
		}

		if (requestLocationPermission()) {
			startFetchLocation();
		}
	}

	@Override protected void onStop() {
		super.onStop();

		elevationFetchSubscription.unsubscribe();
	}

	@OnClick(R.id.approximate_info) void onApproximateInfo() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.no_internet_title)
				.setMessage(R.string.no_internet_message)
				.setNegativeButton(R.string.dismiss,
						((dialogInterface, buttonIndex) -> dialogInterface.dismiss()))
				.show();
	}

	private void startFetchLocation() {
		Timber.v("startFetchLocation");

		LocationRequest request = LocationRequest.create()
				.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
				.setFastestInterval(FASTEST_LOCATION_INTERVAL)
				.setInterval(LOCATION_REQUEST_INTERVAL);

		LocationSettingsRequest locationSettingsRequest = new LocationSettingsRequest.Builder()
				.addLocationRequest(request)
				.setAlwaysShow(true)
				.build();

		reactiveLocationProvider.checkLocationSettings(locationSettingsRequest)
				.flatMap((locationSettingsResult) ->
						fetchLocationIfSettingsEnabled(locationSettingsResult, request))
				.doOnSubscribe(this::showLoadingIfNeeded)
				.doOnUnsubscribe(this::hideLoading)
				.subscribe(this::onElevation, this::onElevationError);
	}

	private Observable<Elevation> fetchLocationObservable(LocationRequest request) {
		return reactiveLocationProvider
				.getUpdatedLocation(request)
				.doOnNext(location -> locationIsStale = false)
				.timeout(LOCATION_REQUEST_INTERVAL * 4, TimeUnit.SECONDS, getLastKnown())
				.switchIfEmpty(onNoLocationAtAll())
				.doOnCompleted(() -> contentLayout
						.postDelayed(this::startFetchLocation, LOCATION_REQUEST_INTERVAL))
				.onBackpressureLatest()
				.concatMap(location -> elevationRetriever.elevationObservable(location));
	}

	private Observable<Location> onNoLocationAtAll() {
		return Observable.defer(() -> {
			locationIsStale = true;
			onElevation(null);
			return Observable.empty();
		});
	}

	private Observable<Location> getLastKnown() {
		return reactiveLocationProvider.getLastKnownLocation()
				.doOnSubscribe(() -> {
					locationIsStale = true;
					Timber.v("Attempting last known location");
				});
	}

	private boolean requestLocationPermission() {
		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED) {

			if (ActivityCompat.shouldShowRequestPermissionRationale(this,
					Manifest.permission.ACCESS_FINE_LOCATION)) {
				new AlertDialog.Builder(this)
						.setTitle(R.string.location_permission_rationale_title)
						.setMessage(R.string.location_permission_rationale_message)
						.setPositiveButton(R.string.go_to_settings, this::openSettings)
						.setNegativeButton(R.string.dismiss,
								(dialogInterface, button) -> dialogInterface.dismiss())
						.show();
			} else {
				ActivityCompat.requestPermissions(this,
						new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
						REQUEST_CODE_GRANT_LOCATION_PERMISSION);
			}

			return false;
		}

		return true;
	}

	private void openSettings(DialogInterface dialogInterface, int buttonIndex) {
		Intent intent = new Intent();
		intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
		Uri uri = Uri.fromParts("package", getPackageName(), null);
		intent.setData(uri);
		startActivity(intent);
	}

	private Observable<Elevation> fetchLocationIfSettingsEnabled(
			LocationSettingsResult locationSettingsResult, LocationRequest request) {
		Status status = locationSettingsResult.getStatus();
		switch (status.getStatusCode()) {
			case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
				try {
					status.startResolutionForResult(this, REQUEST_CHECK_SETTINGS);
					return Observable.error(new LocationSettingsPendingException());
				} catch (IntentSender.SendIntentException ex) {
					Timber.e(ex, "Error opening settings activity.");
				}
				return Observable.error(new LocationNotAvaialableException());
			case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
				return Observable.error(new LocationNotAvaialableException());
			case LocationSettingsStatusCodes.SUCCESS:
			default:
				return fetchLocationObservable(request);
		}
	}

	private void showLoadingIfNeeded() {
		if (!hasElevation) {
			gpsProgressBar.setVisibility(View.VISIBLE);
		}
	}

	private void hideLoading() {
		gpsProgressBar.setVisibility(View.GONE);
	}

	private void onElevation(Elevation elevation) {
		hideLoading();

		hasElevation = (elevation != null);
		if (!hasElevation) {
			showBlankElevation();
			elevationFetchSubscription.unsubscribe();
			return;
		}

		Double elevationValue = elevation.elevation;
		String altitudeString = elevationValue == null ?
				getString(R.string.no_signal_elevation_placeholder) :
				numberFormat.format(unitLocale.convertMetres(elevationValue));

		if (elevation.fromGps) {
			showLocationIsInaccurate();
			showNoNetworkInfo();
		} else {
			hideLocationIsInaccurate();
			hideNoNetworkInfo();
		}

		if (locationIsStale) {
			showLocationIsInaccurate();
		} else if (!elevation.fromGps) {
			hideLocationIsInaccurate();
		}

		elevationTextView.setText(altitudeString);
	}

	private void showBlankElevation() {
		hideLocationIsInaccurate();
		elevationTextView.setText(R.string.no_signal_elevation_placeholder);
	}

	private void showNoNetworkInfo() {
		approximateInfo.setVisibility(View.VISIBLE);
	}

	private void hideNoNetworkInfo() {
		approximateInfo.setVisibility(View.GONE);
	}

	private void onElevationError(Throwable throwable) {
		hasElevation = false;
		showBlankElevation();

		if (throwable instanceof GoogleAPIConnectionException) {
			if (handleGoogleApiConnectionException((GoogleAPIConnectionException) throwable)) {
				return;
			}
		} else if (throwable instanceof LocationNotAvaialableException) {
			showErrorSnackbar(R.string.location_settings_disabled_snackbar);
			showLoadingIfNeeded();
			return;
		} else if (throwable instanceof LocationSettingsPendingException) {
			showErrorSnackbar(R.string.location_settings_pending_snackbar);
			showLoadingIfNeeded();
			return;
		}

		new AlertDialog.Builder(this)
				.setMessage(R.string.error_could_not_fetch_location)
				.setNegativeButton(R.string.dismiss, ((dialogInterface, buttonIndex) ->
						dialogInterface.dismiss()))
				.show();
	}

	private boolean handleGoogleApiConnectionException(GoogleAPIConnectionException throwable) {
		try {
			ConnectionResult connectionResult = throwable.getConnectionResult();

			if (connectionResult.hasResolution()) {
				connectionResult
						.startResolutionForResult(
								this,
								ConnectionResult.RESOLUTION_REQUIRED);
				return true;
			} else {
				SupportErrorDialogFragment errorDialogFragment =
						new SupportErrorDialogFragment();
				Bundle args = new Bundle();
				args.putInt("dialog_error", connectionResult.getErrorCode());
				errorDialogFragment.setArguments(args);
				errorDialogFragment.show(getSupportFragmentManager(), "errordialog");

				GoogleApiAvailability.getInstance().getErrorDialog(
						this, connectionResult.getErrorCode(), REQUEST_RESOLVE_ERROR).show();

				return true;
			}
		} catch (IntentSender.SendIntentException e) {
			Timber.e(e, "Could not send intent");
		}
		return false;
	}

	private void onSnackbarSettingsClick(View view) {
		Intent intent = new Intent();
		intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
		Uri uri = Uri.fromParts("package", getPackageName(), null);
		intent.setData(uri);
		startActivity(intent);
	}

	private void showLocationIsInaccurate() {
		approximateWarning.setVisibility(View.VISIBLE);
	}

	private void hideLocationIsInaccurate() {
		approximateWarning.setVisibility(View.INVISIBLE);
	}

	private void showErrorSnackbar(@StringRes int resId) {
		if (errorSnackbar != null) {
			errorSnackbar.dismiss();
		}
		errorSnackbar = createErrorSnackbar(resId);
		errorSnackbar.show();
	}

	private Snackbar createErrorSnackbar(@StringRes int stringId) {
		return Snackbar.make(contentLayout, stringId, Snackbar.LENGTH_INDEFINITE);
	}

	private static class LocationNotAvaialableException extends RuntimeException {
	}

	private static class LocationSettingsPendingException extends RuntimeException {
	}
}
