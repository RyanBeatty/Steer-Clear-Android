package steerclear.wm.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import android.provider.Settings;

/**
 * Created by mbpeele on 4/4/16.
 */
public class LocationService {

    public final static String GPS = LocationManager.GPS_PROVIDER;
    public final static String NETWORK = LocationManager.NETWORK_PROVIDER;
    public static LocationManager locationManager;

    public static LocationManager getLocationManager(Context context) {
        if (locationManager == null) {
            locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        }

        return locationManager;
    }

    @SuppressWarnings({"ResourceType"})
    public static Location getLastKnownLocation(Context context) {
        LocationManager locationManager = getLocationManager(context);
        boolean isGpsEnabled = locationManager.isProviderEnabled(GPS);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(NETWORK);

        if (isGpsEnabled) {
            return locationManager.getLastKnownLocation(GPS);
        } else if (isNetworkEnabled) {
            return locationManager.getLastKnownLocation(NETWORK);
        }

        return null;
    }

    @SuppressWarnings({"ResourceType"})
    public static void getLocationUpdates(Context context, LocationListener locationListener) {
        LocationManager locationManager = getLocationManager(context);

        boolean isGpsEnabled = locationManager.isProviderEnabled(GPS);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(NETWORK);

        if (isGpsEnabled) {
            locationManager.requestLocationUpdates(GPS, 0, 0, locationListener);
        } else if (isNetworkEnabled) {
            locationManager.requestLocationUpdates(NETWORK, 0, 0, locationListener);
        }
    }

    public static boolean isLocationEnabled(Context context) {
        int locationMode = 0;

        try {
            locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        return locationMode != Settings.Secure.LOCATION_MODE_OFF;
    }

    public static void getLocationAvailabilityUpdate(Context context, final AvailabilityCallback callback) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (LocationService.isLocationEnabled(context)) {
                    callback.onLocationEnabled(context, this, intent);
                }
            }
        };

        context.registerReceiver(receiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
    }

    public interface AvailabilityCallback {
        void onLocationEnabled(Context context, BroadcastReceiver receiver, Intent intent);
    }
}