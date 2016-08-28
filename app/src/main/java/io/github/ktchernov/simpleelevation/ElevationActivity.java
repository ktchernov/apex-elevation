package io.github.ktchernov.simpleelevation;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.SupportErrorDialogFragment;
import com.google.android.gms.location.LocationRequest;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import pl.charmas.android.reactivelocation.ReactiveLocationProvider;
import pl.charmas.android.reactivelocation.observables.GoogleAPIConnectionException;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.github.ktchernov.simpleelevation.api.Elevation;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import timber.log.Timber;

public class ElevationActivity extends AppCompatActivity {

	static {
		if (BuildConfig.DEBUG) {
			Timber.plant(new Timber.DebugTree());
		}
	}

	private static final int REQUEST_CODE_GRANT_LOCATION_PERMISSION = 100;
	private static final int REQUEST_RESOLVE_ERROR = 101;

	private final NumberFormat numberFormat = new DecimalFormat("###,###");
	private ReactiveLocationProvider reactiveLocationProvider;
	private Subscription elevationFetchSubscription = Subscriptions.unsubscribed();
	private UnitLocale unitLocale;

	@Inject ElevationRetriever elevationRetriever;

	@BindView(R.id.content_layout) ViewGroup contentLayout;
	@BindView(R.id.approximate_warning) View approximateWarning;
	@BindView(R.id.elevation_text_view) TextView elevationTextView;
	@BindView(R.id.elevation_unit_text_view) TextView elevationUnitTextView;
	private Snackbar noNetworkSnackbar;

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
				Snackbar.make(elevationTextView, R.string.location_permission_required_snackbar,
						Snackbar.LENGTH_LONG)
						.setAction(R.string.settings_link, this::onSnackbarSettingsClick)
						.show();
			}

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

	private void startFetchLocation() {
		LocationRequest request = LocationRequest.create() //standard GMS LocationRequest
				.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
				.setFastestInterval(500)
				.setInterval(1000);

		elevationFetchSubscription = reactiveLocationProvider
				.getUpdatedLocation(request)
				.onBackpressureLatest()
				.concatMap(location -> elevationRetriever.elevationObservable(location))
				.subscribe(this::onElevation, this::onElevationError);
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

	private void onElevation(Elevation elevation) {
		if (elevation == null) {
			elevationTextView.setText(R.string.no_signal_elevation_placeholder);
			elevationFetchSubscription.unsubscribe();
			return;
		}
		Double elevationValue = elevation.elevation;
		String altitudeString = elevationValue == null ?
				getString(R.string.no_signal_elevation_placeholder) :
				numberFormat.format(unitLocale.convertMetres(elevationValue));

		if (elevation.fromGps) {
			approximateWarning.setVisibility(View.VISIBLE);
			if (!noNetworkShowing()) {
				noNetworkSnackbar = Snackbar.make(contentLayout, R.string.no_internet,
						Snackbar.LENGTH_INDEFINITE);
				noNetworkSnackbar.show();
			}
		} else {
			approximateWarning.setVisibility(View.INVISIBLE);
			if (noNetworkShowing()) {
				noNetworkSnackbar.dismiss();
				noNetworkSnackbar = null;
			}
		}

		elevationTextView.setText(altitudeString);
	}

	public boolean noNetworkShowing() {
		return noNetworkSnackbar != null && noNetworkSnackbar.isShownOrQueued();
	}

	private void onElevationError(Throwable throwable) {
		if (throwable instanceof GoogleAPIConnectionException) {
			try {
				GoogleAPIConnectionException googleAPIConnectionException =
						(GoogleAPIConnectionException) throwable;

				ConnectionResult connectionResult =
						googleAPIConnectionException.getConnectionResult();

				if (connectionResult.hasResolution()) {
					connectionResult
							.startResolutionForResult(
									this,
									ConnectionResult.RESOLUTION_REQUIRED);
					return;
				} else {
					SupportErrorDialogFragment errorDialogFragment =
							new SupportErrorDialogFragment();
					Bundle args = new Bundle();
					args.putInt("dialog_error", connectionResult.getErrorCode());
					errorDialogFragment.setArguments(args);
					errorDialogFragment.show(getSupportFragmentManager(), "errordialog");

					GoogleApiAvailability.getInstance().getErrorDialog(
							this, connectionResult.getErrorCode(), REQUEST_RESOLVE_ERROR).show();

					return;
				}
			} catch (IntentSender.SendIntentException e) {
				Timber.e(e, "Could not send intent");
			}
		}

		new AlertDialog.Builder(this)
				.setMessage(R.string.error_could_not_fetch_location)
				.setNegativeButton(R.string.dismiss, ((dialogInterface, buttonIndex) ->
						dialogInterface.dismiss()))
				.show();
	}

	private void onSnackbarSettingsClick(View view) {
		Intent intent = new Intent();
		intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
		Uri uri = Uri.fromParts("package", getPackageName(), null);
		intent.setData(uri);
		startActivity(intent);
	}
}
