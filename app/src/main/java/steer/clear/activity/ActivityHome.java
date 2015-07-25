package steer.clear.activity;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLng;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.inject.Inject;

import retrofit.client.Response;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import steer.clear.Logger;
import steer.clear.MainApp;
import steer.clear.R;
import steer.clear.fragment.FragmentHailRide;
import steer.clear.fragment.FragmentLogin;
import steer.clear.fragment.FragmentMap;
import steer.clear.fragment.ListenerForFragments;
import steer.clear.pojo.RideObject;
import steer.clear.retrofit.Client;
import steer.clear.util.Datastore;
import steer.clear.util.Utils;

/**
 * "HomeScreen" activity of the SteerClear app.
 * Instantiates MapFragments and handles anything having to do with Http.
 * @author Miles Peele
 *
 */
public class ActivityHome extends AppCompatActivity
	implements ListenerForFragments, OnConnectionFailedListener, ConnectionCallbacks {

	// Stores user's current location
	private static LatLng currentLatLng;

	// After user clicks "Next" in FragmentMap, stores their chosen LatLngs in these variables
	private static LatLng pickupLatLng;
	private static CharSequence pickupLocationName;
	private static LatLng dropoffLatLng;
	private static CharSequence dropoffLocationName; // says it's unused but it is used in makeHttpPostRequest()
	
	// Static strings used as tags for Fragments
    private static final String LOGIN = "authenticate";
	private final static String PICKUP = "pickup";
	private final static String DROPOFF = "dropoff";
	private final static String POST = "post";

	// Request code to use when launching the resolution activity
	private static final int REQUEST_RESOLVE_ERROR = 1001;

	@Inject Client helper;
    @Inject Datastore store;
	public GoogleApiClient mGoogleApiClient;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);

		((MainApp) getApplicationContext()).getApplicationComponent().inject(this);

		mGoogleApiClient = new GoogleApiClient.Builder(this)
				.enableAutoManage(this, 0, this)
				.addApi(Places.GEO_DATA_API)
				.addApi(Places.PLACE_DETECTION_API)
				.addConnectionCallbacks(this)
				.addApi(LocationServices.API)
				.build();
	}

    @Override
    public void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    public void onPause() {
        super.onPause();
        mGoogleApiClient.disconnect();
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
	}

	@Override
	public void onBackPressed() {
	    int count = getFragmentManager().getBackStackEntryCount();
	    if (count == 0) {
            moveTaskToBack(true);
	    } else {
	        getFragmentManager().popBackStack();
		}
	}

    private void addFragmentLogin() {
        FragmentManager manager = getFragmentManager();
        FragmentLogin login = (FragmentLogin) manager.findFragmentByTag(LOGIN);
        if (login != null) {
            manager.beginTransaction().show(login).commit();
        } else {
            manager.beginTransaction()
                    .add(R.id.activity_home_fragment_frame, FragmentLogin.newInstance(), LOGIN)
                    .commit();
        }
    }

    public void onRegisterResponse(Response response, String username, String password) {
        switch (response.getStatus()) {
            case 200:
            case 302:
                helper.login(username, password)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::onLoginResponse, this::onRxError);
                break;
            default:
                Logger.log("UNHANDLED RESPONSE CODE: " + response.getStatus());
        }
    }

    public void onLoginResponse(Response response) {
        switch (response.getStatus()) {
            case 200:
            case 302:
                showMapStuff();
                break;
            default:
                Logger.log("UNHANDLED LOGIN EXCEPTION " + response.getStatus());
        }
    }

    public void onRideResponseReceived(RideObject response) {
        RideObject.RideInfo info = response.getRideInfo();
        String pickupTime = info.getPickupTime();
        int cancelId = info.getId();
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("est"));
            Date eta = dateFormat.parse(pickupTime);

            Calendar calendar = new GregorianCalendar();
            calendar.setTime(eta);
            int pickupHour = calendar.get(Calendar.HOUR);
            int pickupMinute = calendar.get(Calendar.MINUTE);

            Intent etaActivity = new Intent(this, ActivityEta.class);
            etaActivity.putExtra("PICKUP_HOUR", pickupHour);
            etaActivity.putExtra("PICKUP_MINUTE", pickupMinute);
            etaActivity.putExtra("CANCEL_ID", cancelId);
            startActivity(etaActivity);

            finish();
        } catch (ParseException p) {
            p.printStackTrace();
        }
    }

    public void onRxError(Throwable throwable) {
        Logger.log("RXERROR " + throwable.getLocalizedMessage());
        throwable.printStackTrace();
    }

    private void showMapStuff() {
//        FragmentTransaction ft = getFragmentManager().beginTransaction();
//        FragmentLogin login = (FragmentLogin) getFragmentManager().findFragmentByTag(LOGIN);
//        if (login != null) ft.remove(login).commit();

        FragmentMap fragment = FragmentMap.newInstance(PICKUP, currentLatLng, false);
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.activity_home_fragment_frame, fragment, PICKUP);
        fragmentTransaction.commit();
    }

    @Override
    public void authenticate(String username, String password) {
        helper.register(username, password)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Response -> onRegisterResponse(Response, username, password), this::onRxError);
    }

	/**
	 * Convenience "get" method that fragments can call to get the googleApiClient.
	 * Because of the apiClient's automanage feature, we don't (shouldn't) have to worry about this causing problems.
	 */
	@Override
	public GoogleApiClient getGoogleApiClient() {
		return mGoogleApiClient;
	}

	@Override
	public void onConnectionFailed(ConnectionResult result) {
		if (result.hasResolution()) {
			try {
				result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
			} catch (IntentSender.SendIntentException e) {
				mGoogleApiClient.connect();
			}
		} else {
			GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), this, 1);
		}
	}

	@Override
	public void changePickup() {
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		FragmentMap fragment = FragmentMap.newInstance(PICKUP, currentLatLng, true);
		ft.addToBackStack(PICKUP);
		ft.add(R.id.activity_home_fragment_frame, fragment, PICKUP);
		ft.commit();
	}

	@Override
	public void changeDropoff() {
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		FragmentMap fragment = FragmentMap.newInstance(DROPOFF, currentLatLng, true);
		ft.addToBackStack(DROPOFF);
		ft.add(R.id.activity_home_fragment_frame, fragment, DROPOFF);
		ft.commit();
	}

	@Override
	public void setChosenLocation(String fragmentTag, LatLng latlng, CharSequence name) {
		if (fragmentTag == PICKUP) {
			ActivityHome.pickupLatLng = latlng;
			ActivityHome.pickupLocationName = name;

			FragmentTransaction ft = getFragmentManager().beginTransaction();
			Fragment fragment = FragmentMap.newInstance(DROPOFF, currentLatLng, false);
			ft.remove(getFragmentManager().findFragmentByTag(PICKUP));
			ft.addToBackStack(DROPOFF);
			ft.add(R.id.activity_home_fragment_frame, fragment, DROPOFF);
			ft.commit();
		} else {
			if (pickupLocationName.equals(name)) {
				Toast.makeText(this, getResources().getString(R.string.toast_pickup_dropoff_same),
						Toast.LENGTH_SHORT).show();
				return;
			}

			ActivityHome.dropoffLatLng = latlng;
			ActivityHome.dropoffLocationName = name;

			FragmentTransaction ft = getFragmentManager().beginTransaction();
			FragmentHailRide fragment = FragmentHailRide.newInstance(pickupLocationName, dropoffLocationName);
			ft.remove(getFragmentManager().findFragmentByTag(DROPOFF));
			ft.addToBackStack(POST);
			ft.replace(R.id.activity_home_fragment_frame, fragment, POST);
			ft.commit();
		}
	}

    @Override
    public void makeHttpPostRequest(int numPassengers) {
        helper.addRide(numPassengers, pickupLatLng.latitude, pickupLatLng.longitude,
                dropoffLatLng.latitude, dropoffLatLng.longitude)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onRideResponseReceived, this::onRxError);
    }

	@Override
	public void onChosenLocationChanged(String fragmentTag, LatLng latlng, CharSequence name) {
		if (name.equals(pickupLocationName) || name.equals(dropoffLocationName)) {
			Toast.makeText(this, getResources().getString(R.string.toast_pickup_dropoff_same),
					Toast.LENGTH_SHORT).show();
			return;
		}

		FragmentTransaction ft = getFragmentManager().beginTransaction();
		if (fragmentTag == PICKUP) {
			ActivityHome.pickupLatLng = latlng;
			ActivityHome.pickupLocationName = name;
		} else {
			ActivityHome.dropoffLatLng = latlng;
			ActivityHome.dropoffLocationName = name;
		}
		getFragmentManager().popBackStack();
		FragmentHailRide prevHailRide =
				(FragmentHailRide) getFragmentManager().findFragmentByTag(POST);
		prevHailRide.onLocationChanged(fragmentTag, name);
		ft.show(prevHailRide).commit();
	}

	@Override
	public void onConnected(Bundle connectionHint) {
        if (getFragmentManager().findFragmentByTag(PICKUP) == null) {
            Location currentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if (currentLocation != null) {
                currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
				addFragmentLogin();
            } else {
				currentLocation = Utils.getLocation(this);
                if (currentLocation != null) {
                    currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
					addFragmentLogin();
                } else {
                    showSettingsAlert();
                }
            }
        }
	}

    @Override
	public void onConnectionSuspended(int cause) {

	}
	
	/**
     * Method to show settings alert dialog if GPS could not be found
     * On pressing Settings button will launch Settings Options
     * */
    public void showSettingsAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(getResources().getString(R.string.dialog_no_gps_title));
        alertDialog.setMessage(getResources().getString(R.string.dialog_no_gps_body));
        alertDialog.setPositiveButton(getResources().getString(R.string.dialog_no_gps_pos_button_text),
                (dialog, which) -> {
                    dialog.dismiss();
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addCategory(Intent.CATEGORY_HOME);
                    startActivity(intent);
                });
  
        alertDialog.setNegativeButton(getResources().getString(R.string.dialog_no_gps_neg_button_text),
                (dialog, which) -> {
                    dialog.cancel();
                    finish();
                });

        alertDialog.show();
    }
}