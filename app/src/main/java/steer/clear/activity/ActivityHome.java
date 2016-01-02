package steer.clear.activity;

import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.EventLog;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLng;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import retrofit.client.Response;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.observables.ConnectableObservable;
import rx.schedulers.Schedulers;
import steer.clear.MainApp;
import steer.clear.R;
import steer.clear.event.EventLogout;
import steer.clear.event.EventPlacesChosen;
import steer.clear.event.EventPostPlacesChosen;
import steer.clear.fragment.FragmentHailRide;
import steer.clear.fragment.FragmentMap;
import steer.clear.pojo.RideObject;
import steer.clear.retrofit.Client;
import steer.clear.util.ErrorUtils;
import steer.clear.util.LoadingDialog;
import steer.clear.util.Logg;

public class ActivityHome extends ActivityBase
	implements OnConnectionFailedListener, ConnectionCallbacks, LocationListener {

    private final static String MAP = "map";
    private final static String POST = "post";
    private static final int REQUEST_RESOLVE_ERROR = 1001;

    private LatLng userLatLng, pickupLatLng, dropoffLatLng;
    private LoadingDialog loadingDialog;
    private LocationRequest locationRequest;
	private GoogleApiClient mGoogleApiClient;
    private AlertDialog settings;

    public static Intent newIntent(Context context) {
        return new Intent(context, ActivityHome.class);
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);

        loadingDialog = new LoadingDialog(this, R.style.ProgressDialogTheme);

		mGoogleApiClient = new GoogleApiClient.Builder(this)
				.enableAutoManage(this, 0, this)
				.addApi(Places.GEO_DATA_API)
				.addApi(Places.PLACE_DETECTION_API)
				.addConnectionCallbacks(this)
				.addApi(LocationServices.API)
				.build();

        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_LOW_POWER)
                .setInterval(60 * 100000)        // 30 seconds, in milliseconds
                .setFastestInterval(10000); // 1 second, in milliseconds
	}

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumeLocationUpdates();
    }

    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_RESOLVE_ERROR) {
			if (resultCode == RESULT_OK) {
				if (!mGoogleApiClient.isConnecting() &&
						!mGoogleApiClient.isConnected()) {
					mGoogleApiClient.connect();
				}
			}
		}

        if (userLatLng != null && settings != null) {
            if (settings.isShowing()) {
                settings.hide();
            }
        }
	}

	@Override
	public void onBackPressed() {
        int count = getFragmentManager().getBackStackEntryCount();
	    if (count == 0) {
            super.onBackPressed();
	    } else {
	        getFragmentManager().popBackStack();
		}
	}

	@Override
	public void onConnected(Bundle connectionHint) {
        if (getFragmentManager().findFragmentByTag(MAP) == null) {
            Location currentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if (currentLocation != null) {
                userLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
				showMapStuff(userLatLng);
            } else {
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                        locationRequest, this);
                showSettingsAlert();
            }
        }
	}

    @Override
    public void onConnectionSuspended(int cause) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            Logg.log("CONNECTION FAILED WITH CODE: " + connectionResult.getErrorCode());
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
    }

	private void showMapStuff(LatLng userLatLng) {
        stopLocationUpdates();

		FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
		fragmentTransaction.replace(R.id.activity_home_fragment_frame,
                FragmentMap.newInstance(userLatLng), MAP);
		fragmentTransaction.commit();
	}

    private void stopLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    private void resumeLocationUpdates() {
        if (userLatLng == null) {
            locationRequest = LocationRequest.create()
                    .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setInterval(60 * 100000)        // 30 seconds, in milliseconds
                    .setFastestInterval(10000); // 1 second, in milliseconds
        }
    }

    public void showSettingsAlert() {
        if (settings == null) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            alertDialog.setTitle(getResources().getString(R.string.dialog_no_gps_title));
            alertDialog.setMessage(getResources().getString(R.string.dialog_no_gps_body));
            alertDialog.setPositiveButton(getResources().getString(R.string.dialog_no_gps_pos_button_text),
                    (dialog, which) -> {
                        dialog.dismiss();
                        Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    });

            alertDialog.setNegativeButton(getResources().getString(R.string.dialog_no_gps_neg_button_text),
                    (dialog, which) -> {
                        finish();
                    });
            settings = alertDialog.create();
            settings.show();
        }

        if (!settings.isShowing()) {
            settings.show();
        }
    }

    public GoogleApiClient getGoogleApiClient() {
        return mGoogleApiClient;
    }

    public void onEvent(EventPlacesChosen eventPlacesChosen) {
        pickupLatLng = eventPlacesChosen.pickupLatLng;
        dropoffLatLng = eventPlacesChosen.dropoffLatLng;

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        FragmentHailRide fragment = FragmentHailRide.newInstance(eventPlacesChosen.pickupName,
                eventPlacesChosen.dropoffName);
        ft.addToBackStack(MAP);
        ft.replace(R.id.activity_home_fragment_frame, fragment, POST).commit();
    }

    @SuppressWarnings("unused")
    public void onEvent(EventPostPlacesChosen eventPostPlacesChosen) {
        addSubscription(helper.addRide(eventPostPlacesChosen.numPassengers,
                pickupLatLng.latitude, pickupLatLng.longitude,
                dropoffLatLng.latitude, dropoffLatLng.longitude)
                .doOnError(this::handleError)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(createRideSubscriber()));
    }

    @SuppressWarnings("unused")
    public void onEvent(EventLogout eventLogout) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setTitle(getResources().getString(R.string.dialog_logout_title))
                .setMessage(getResources().getString(R.string.dialog_logout_body))
                .setPositiveButton(
                        getResources().getString(R.string.dialog_cancel_ride_pos_button_text),
                        (dialog, which) -> {
                            logout();
                        }).setNegativeButton(
                        getResources().getString(R.string.dialog_cancel_ride_neg_button_text),
                        (dialog, which) -> {
                            dialog.dismiss();
                        });

        alertDialog.show();
    }

    private void logout() {
        Subscriber<Response> logoutSubscriber = new Subscriber<Response>() {
            @Override
            public void onCompleted() {
                removeSubscription(this);
                loadingDialog.dismiss();
                startActivity(ActivityAuthenticate.newIntent(ActivityHome.this, true));
                finish();
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(Response response) {

            }

            @Override
            public void onStart() {
                super.onStart();
                loadingDialog.show();
            }
        };

        addSubscription(helper.logout()
                .subscribeOn(Schedulers.io())
                .retry()
                .onErrorResumeNext(Observable.<Response>empty())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(logoutSubscriber));
    }

    private Subscriber<RideObject> createRideSubscriber() {
        return new Subscriber<RideObject>() {

            int hour, minute, cancelId;

            @Override
            public void onCompleted() {
                removeSubscription(this);

                loadingDialog.dismiss();
                startActivity(ActivityEta.newIntent(ActivityHome.this,
                        String.format("%02d : %02d", hour, minute), cancelId));
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                finish();
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(RideObject rideObject) {
                RideObject.RideInfo info = rideObject.getRideInfo();
                String pickupTime = info.getPickupTime();
                cancelId = info.getId();
                try {
                    SimpleDateFormat dateFormat =
                            new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.US);
                    dateFormat.setTimeZone(TimeZone.getTimeZone("est"));

                    Calendar calendar = new GregorianCalendar();
                    calendar.setTime(dateFormat.parse(pickupTime));

                    hour = calendar.get(Calendar.HOUR);
                    minute = calendar.get(Calendar.MINUTE);
                } catch (ParseException p) {
                    p.printStackTrace();
                }
            }

            @Override
            public void onStart() {
                super.onStart();
                loadingDialog.show();
            }
        };
    }
}