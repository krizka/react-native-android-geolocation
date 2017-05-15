package com.rnandroidgeolocation;

import android.app.Activity;
import android.app.PendingIntent;
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
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.concurrent.TimeUnit;

public class AndroidGeolocationModule extends ReactContextBaseJavaModule
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        ActivityEventListener {
    protected static final String TAG = "GeoLocation";
    protected GoogleApiClient mGoogleApiClient;
    protected Location mLastLocation;
    private LocationRequest mLocationRequest;

    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = TimeUnit.MINUTES.toMillis(30);
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2;
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private static final int REQUEST_CODE = 1671;

    private static final String ERROR_MSG_LOCATION = "";
    private static final String ERROR_MSG_LOCATION_SERVICE_DISABLED = "";
    private static final String ERROR_MSG_UNKOWN = "unknown.";

    private Callback mSuccessCallback, mErrorCallback;

    @Override
    public String getName() {
        return "AndroidGeolocation";
    }

    public AndroidGeolocationModule(ReactApplicationContext reactContext) {
        super(reactContext);
        buildGoogleApiClient();
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
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return mLocationRequest;
    }

    @ReactMethod
    public void getCurrentLocation(Callback success, Callback error) {
        this.mSuccessCallback = success;
        this.mErrorCallback = error;
        LocationSettingsRequest locationSettingRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(getLocationRequest())
                .build();
        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, locationSettingRequest);
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                final Status status = locationSettingsResult.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        PendingIntent in = status.getResolution();
                        try {
                            status.startResolutionForResult(getCurrentActivity(), REQUEST_CODE);
                        } catch (IntentSender.SendIntentException e) {
                            // TODO LOG
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        Log.i("TAG", "== SETTINGS_CHANGE_UNAVAILABLE " + status);
                        break;
                }
            }
        });
    }

    private void requestLocation() {
        WritableMap location = Arguments.createMap();
        WritableMap coords = Arguments.createMap();
        String errorMessage = "Location could not be retrieved";
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            // If a location is returned, will invoke success callback with the locatation using a Javascript object
            coords.putDouble("latitude", mLastLocation.getLatitude());
            coords.putDouble("longitude", mLastLocation.getLongitude());
            location.putMap("coords", coords);
            if (mSuccessCallback != null) {
                mSuccessCallback.invoke(location);
            }
        } else {
            // Else, the error callback is invoked with an error message
            if (mErrorCallback != null) {
                mErrorCallback.invoke(errorMessage);
            }
        }
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
        if (resultCode == Activity.RESULT_OK) {
            requestLocation();
        } else if (resultCode == Activity.RESULT_CANCELED) {
            mErrorCallback.invoke(ERROR_MSG_LOCATION_SERVICE_DISABLED);
        } else {
            mErrorCallback.invoke(ERROR_MSG_UNKOWN);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        // do nothing.
    }
}
