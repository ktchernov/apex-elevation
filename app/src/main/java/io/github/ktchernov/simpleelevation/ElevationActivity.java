package io.github.ktchernov.simpleelevation;

import com.google.android.gms.location.LocationRequest;

import android.Manifest;
import android.content.Intent;
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
import android.widget.TextView;

import pl.charmas.android.reactivelocation.ReactiveLocationProvider;

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

	private final NumberFormat numberFormat = new DecimalFormat("###,###");
	private ReactiveLocationProvider reactiveLocationProvider;
	private Subscription elevationFetchSubscription = Subscriptions.unsubscribed();
	private ElevationComponent elevationComponent;

	@Inject ElevationRetriever elevationRetriever;

	@BindView(R.id.elevation_text_view) TextView elevationTextView;

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_elevation);

		ButterKnife.bind(this);

		reactiveLocationProvider = new ReactiveLocationProvider(getApplicationContext());

		elevationComponent = DaggerElevationComponent.builder()
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
				.concatMap(location -> elevationRetriever.elevationObservable(location))
				.onBackpressureLatest()
				.subscribe(this::onElevation);
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

	private void onElevation(Elevation elevation) {
		Double elevationValue = elevation.elevation;
		String altitudeString = elevationValue == null ?
				getString(R.string.no_signal_elevation_placeholder) :
				numberFormat.format(elevationValue);

		if (elevation.fromGps) {
			altitudeString = "approx. " + altitudeString;
		}

		elevationTextView.setText(altitudeString);
	}

	private void onSnackbarSettingsClick(View view) {
		Intent intent = new Intent();
		intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
		Uri uri = Uri.fromParts("package", getPackageName(), null);
		intent.setData(uri);
		startActivity(intent);
	}
}
