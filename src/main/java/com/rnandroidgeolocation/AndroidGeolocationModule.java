package com.rnandroidgeolocation;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

public class AndroidGeolocationModule extends ReactContextBaseJavaModule
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        ActivityEventListener {
    protected static final String TAG = "GeoLocation";
    protected GoogleApiClient mGoogleApiClient;
    protected Location mLastLocation;
    private LocationRequest mLocationRequest;

    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = TimeUnit.SECONDS.toMillis(2);
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2;
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private static final int REQUEST_CODE = 1644;

    private static final Integer ERROR_LOCATION_CANNOT_GET = 1;
    private static final Integer ERROR_LOCATION_SERVICE_WAS_DISABLED = 2;
    private static final Integer ERROR_UNKNOWN = 99;

    private Callback mSuccessCallback, mErrorCallback;

    @Override
    public String getName() {
        return "AndroidGeolocation";
    }

    public AndroidGeolocationModule(ReactApplicationContext reactContext) {
        super(reactContext);
        buildGoogleApiClient();
        reactContext.addActivityEventListener(this);
    }

    @Nullable
    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("ERROR_LOCATION_CANNOT_GET", ERROR_LOCATION_CANNOT_GET);
        constants.put("ERROR_LOCATION_SERVICE_DISABLED", ERROR_LOCATION_SERVICE_WAS_DISABLED);
        constants.put("ERROR_UNKNOWN", ERROR_UNKNOWN);
        return constants;
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(getReactApplicationContext())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }


    protected LocationRequest getLocationRequest() {
        if (mLocationRequest != null) {
            return mLocationRequest;
        }
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return mLocationRequest;
    }

    @ReactMethod
    public void setCallbacks(Callback success, final Callback error) {
        this.mSuccessCallback = success;
        this.mErrorCallback = error;
    }

    @ReactMethod
    public void watchPosition(ReadableMap options) {
        final LocationSettingsRequest locationSettingRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(getLocationRequest())
                .setAlwaysShow(true)
                .build();

        final PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, locationSettingRequest);
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                final Status status = locationSettingsResult.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        requestLocationUpdates();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            status.startResolutionForResult(getCurrentActivity(), REQUEST_CODE);
                        } catch (IntentSender.SendIntentException e) {
                            // ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                    default:
                        mErrorCallback.invoke(ERROR_UNKNOWN);
                }
            }
        });
    }

    @ReactMethod
    public void clearWatch() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    @ReactMethod
    public void getCurrentLocation(Callback success, final Callback error) {
        this.mSuccessCallback = success;
        this.mErrorCallback = error;
        final LocationSettingsRequest locationSettingRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(getLocationRequest())
                .setAlwaysShow(true)
                .build();
        final PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, locationSettingRequest);
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                final Status status = locationSettingsResult.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        requestLocation();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            status.startResolutionForResult(getCurrentActivity(), REQUEST_CODE);
                        } catch (IntentSender.SendIntentException e) {
                            // ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                    default:
                        error.invoke(ERROR_UNKNOWN);
                }
            }
        });
    }

    private void requestLocation() {
//        if (mLastLocation == null)
//            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            if (mSuccessCallback == null)
                return;

            WritableMap location = Arguments.createMap();
            WritableMap coords = Arguments.createMap();
            coords.putDouble("latitude", mLastLocation.getLatitude());
            coords.putDouble("longitude", mLastLocation.getLongitude());
            coords.putDouble("heading", mLastLocation.getBearing());
            location.putMap("coords", coords);
            mSuccessCallback.invoke(location);

            mSuccessCallback = null;
            mLastLocation = null;
        } else {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }

    private void requestLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
    }


    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to Google Play documentation for what errors can be logged
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // Attempts to reconnect if a disconnect occurs
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE) {
            return;
        }
        final LocationSettingsStates states = LocationSettingsStates.fromIntent(data);
        switch (resultCode) {
            case Activity.RESULT_OK:
                requestLocation();
                break;
            case Activity.RESULT_CANCELED:
                mErrorCallback.invoke(ERROR_LOCATION_SERVICE_WAS_DISABLED);
                break;
            default:
                mErrorCallback.invoke(ERROR_UNKNOWN);
                break;
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        // do nothing.
    }

    @Override
    public void onLocationChanged(Location currentLocation) {
        if (mSuccessCallback == null)
            return;

        WritableMap location = Arguments.createMap();
        WritableMap coords = Arguments.createMap();
        coords.putDouble("latitude", currentLocation.getLatitude());
        coords.putDouble("longitude", currentLocation.getLongitude());
        coords.putDouble("heading", currentLocation.getBearing());
        location.putMap("coords", coords);

        mSuccessCallback.invoke(location);
        mSuccessCallback = null;
    }
}
