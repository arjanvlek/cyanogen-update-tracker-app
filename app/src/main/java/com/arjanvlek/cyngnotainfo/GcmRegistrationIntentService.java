package com.arjanvlek.cyngnotainfo;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.widget.Toast;

import com.arjanvlek.cyngnotainfo.Support.NetworkConnectionManager;
import com.arjanvlek.cyngnotainfo.Support.ServerConnector;
import com.arjanvlek.cyngnotainfo.Support.SettingsManager;
import com.arjanvlek.cyngnotainfo.view.MainActivity;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import static com.arjanvlek.cyngnotainfo.ApplicationContext.APP_USER_AGENT;
import static com.arjanvlek.cyngnotainfo.ApplicationContext.LOCALE_DUTCH;
import static com.arjanvlek.cyngnotainfo.ApplicationContext.PACKAGE_REPLACED_KEY;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_DEVICE;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_DEVICE_ID;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_UPDATE_DATA_LINK;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_UPDATE_METHOD;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_UPDATE_METHOD_ID;

public class GcmRegistrationIntentService extends IntentService {

    private static final String TAG = "RegIntentService";
    private String registrationToken = "";
    private long deviceId;
    private long updateMethodId;

    //Settings properties
    public static final String PROPERTY_GCM_REGISTRATION_TOKEN = "registration_id";
    public static final String PROPERTY_GCM_DEVICE_ID = "gcm_device_type"; // Cannot be changed due to older app version
    public static final String PROPERTY_GCM_UPDATE_METHOD_ID = "gcm_update_type"; // Cannot be changed due to older app version
    public static final String PROPERTY_APP_VERSION = "appVersion";
    public static final String PROPERTY_REGISTRATION_ERROR = "registration_error";

    //JSON Properties
    private static final String JSON_PROPERTY_REGISTRATION_TOKEN = "registration_token";
    private static final String JSON_PROPERTY_DEVICE_ID = "device_id";
    private static final String JSON_PROPERTY_UPDATE_METHOD_ID = "update_method_id";
    private static final String JSON_PROPERTY_OLD_REGISTRATION_TOKEN = "old_registration_token";
    private static final String JSON_PROPERTY_APP_VERSION = "app_version";
    private static final String SUCCESS_JSON_TAG = "success";

    //Legacy strings used for migration
    private static final String LEGACY_DEVICE_ONEPLUS_ONE = "OnePlus One";
    private static final String LEGACY_DEVICE_YU_YUREKA = "Yu Yureka";
    private static final String LEGACY_DEVICE_OPPO_N1_CM_EDITION = "Oppo N1 CyanogenMod Edition";
    private static final String LEGACY_DEVICE_YU_YUPHORIA = "Yu Yuphoria";

    private static final String LEGACY_UPDATE_METHOD_INCREMENTAL_KEY = "incremental_update";
    private static final String LEGACY_UPDATE_METHOD_INCREMENTAL_VALUE_EN = "Incremental update";
    private static final String LEGACY_UPDATE_METHOD_INCREMENTAL_VALUE_NL = "Incrementele update";
    private static final String LEGACY_UPDATE_METHOD_FULL_KEY = "full_update";
    private static final String LEGACY_UPDATE_METHOD_FULL_VALUE_EN = "Full update";
    private static final String LEGACY_UPDATE_METHOD_FULL_VALUE_NL = "Volledige update";

    public GcmRegistrationIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        if(checkIfMigrationIsNeeded()) {
            migrateApp();
        }
        deviceId = settingsManager.getLongPreference(PROPERTY_DEVICE_ID);
        updateMethodId = settingsManager.getLongPreference(PROPERTY_UPDATE_METHOD_ID);

        try {
            // In the (unlikely) event that multiple refresh operations occur simultaneously,
            // ensure that they are processed sequentially.
            synchronized (TAG) {
                // Initially this call goes out to the network to retrieve the token, subsequent calls
                // are local.
                InstanceID instanceID = InstanceID.getInstance(this);
                registrationToken = instanceID.getToken(getString(R.string.gcm_defaultSenderId), GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);

                sendRegistrationTokenToServer(registrationToken);
            }
        } catch (Exception e) {
            // If an exception happens while fetching the new token or updating our registration data
            // on a third-party server, this ensures that we'll attempt the update at a later time.
            setRegistrationFailed(true);
        }
        //Release the wake lock received when the app has been upgraded.
        if(intent.getExtras() != null) {
            try {
                if (intent.getExtras().getBoolean(PACKAGE_REPLACED_KEY, false)) {
                    GcmPackageReplacedReceiver.completeWakefulIntent(intent);
                }
            } catch (Exception ignored) {

            }
        }
    }

    private boolean checkIfMigrationIsNeeded() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());

        // Remove update data link if present. This is from the past where Cyanogen provided the update data. Update data is now directly fetched from the API.
        if(settingsManager.containsPreference(PROPERTY_UPDATE_DATA_LINK)) {
            settingsManager.removePreference(PROPERTY_UPDATE_DATA_LINK);
        }
        return settingsManager.containsPreference(PROPERTY_DEVICE) && settingsManager.containsPreference(PROPERTY_UPDATE_METHOD) && (!settingsManager.containsPreference(PROPERTY_DEVICE_ID) || !settingsManager.containsPreference(PROPERTY_UPDATE_METHOD_ID));
    }


    /**
     * Helper method to migrate older versions to the new backend (with IDs);
     */
    private void migrateApp() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        String device = settingsManager.getPreference(PROPERTY_DEVICE);
        String updateMethod = settingsManager.getPreference(PROPERTY_UPDATE_METHOD);

        switch (device) {
            case LEGACY_DEVICE_ONEPLUS_ONE:
                settingsManager.saveLongPreference(PROPERTY_DEVICE_ID, 1L);
                break;
            case LEGACY_DEVICE_YU_YUREKA:
                settingsManager.saveLongPreference(PROPERTY_DEVICE_ID, 2L);
                break;
            case LEGACY_DEVICE_OPPO_N1_CM_EDITION:
                settingsManager.saveLongPreference(PROPERTY_DEVICE_ID, 3L);
                break;
            case LEGACY_DEVICE_YU_YUPHORIA:
                settingsManager.saveLongPreference(PROPERTY_DEVICE_ID, 5L); // The Yuphoria has ID 5 in the database due to a reservation for the Alcatel OneTouch Hero 2+
                break;
        }

        if(updateMethod.equals(LEGACY_UPDATE_METHOD_FULL_KEY)) {
            settingsManager.saveLongPreference(PROPERTY_UPDATE_METHOD_ID, 2L);
            switch(Locale.getDefault().getDisplayLanguage()) {
                case LOCALE_DUTCH:
                    settingsManager.savePreference(PROPERTY_UPDATE_METHOD, LEGACY_UPDATE_METHOD_FULL_VALUE_NL);
                    break;
                default:
                    settingsManager.savePreference(PROPERTY_UPDATE_METHOD, LEGACY_UPDATE_METHOD_FULL_VALUE_EN);

            }
        }
        else if(updateMethod.equals(LEGACY_UPDATE_METHOD_INCREMENTAL_KEY)) {
            settingsManager.saveLongPreference(PROPERTY_UPDATE_METHOD_ID, 1L);
            switch(Locale.getDefault().getDisplayLanguage()) {
                case LOCALE_DUTCH:
                    settingsManager.savePreference(PROPERTY_UPDATE_METHOD, LEGACY_UPDATE_METHOD_INCREMENTAL_VALUE_NL);
                    break;
                default:
                    settingsManager.savePreference(PROPERTY_UPDATE_METHOD, LEGACY_UPDATE_METHOD_INCREMENTAL_VALUE_EN);

            }
        }
    }

    /**
     * Persist registration to third-party servers.
     * <p/>
     * This code connects to <a href="cyanogenupdatetracker.com">cyanogenupdatetracker.com</a> to store the device registration.
     *
     * @param token The new token.
     */
    private void sendRegistrationTokenToServer(String token) {

        new RegisterTokenToBackend().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, token, getCurrentRegistrationToken());
    }

    /**
     * Registers a GCM Registration Token to the app's server.
     */
    private class RegisterTokenToBackend extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... strings) {
            String registrationId = strings[0];
            String oldRegistrationId = strings[1];
            HttpURLConnection urlConnection = null;
            InputStream in;
            String result = null;
            try {

                ServerConnector serverConnector = new ServerConnector();

                JSONObject jsonRequest = new JSONObject();
                jsonRequest.put(JSON_PROPERTY_REGISTRATION_TOKEN, registrationId);
                jsonRequest.put(JSON_PROPERTY_DEVICE_ID, deviceId);
                jsonRequest.put(JSON_PROPERTY_UPDATE_METHOD_ID, updateMethodId);
                jsonRequest.put(JSON_PROPERTY_OLD_REGISTRATION_TOKEN, oldRegistrationId);
                jsonRequest.put(JSON_PROPERTY_APP_VERSION, BuildConfig.VERSION_NAME);

                URL url = serverConnector.getDeviceRegistrationURL();
                assert url != null;

                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("User-Agent", APP_USER_AGENT);
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setConnectTimeout(10000);
                urlConnection.setReadTimeout(10000);
                urlConnection.connect();

                OutputStream out = urlConnection.getOutputStream();
                byte[] outputBytes = jsonRequest.toString().getBytes();
                out.write(outputBytes);
                out.close();
                in = new BufferedInputStream(urlConnection.getInputStream());
                result = inputStreamToString(in);
            } catch (Exception e) {
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(String response) {
            JSONObject result;
            try {
                result = new JSONObject(response);
                System.out.println(result.toString());
                if (result.getString(SUCCESS_JSON_TAG) != null) {
                    setRegistrationFailed(false);
                    storeRegistrationToken(registrationToken);
                } else {
                    setRegistrationFailed(true);
                }
            } catch (Exception e) {
                setRegistrationFailed(true);
            }
        }
    }

    /**
     * Sets a flag that this device needs to be re-registered for push notifications at a later stage.
     * @param failed Returns if GCM Registration has failed.
     */
    private void setRegistrationFailed(boolean failed) {
        SharedPreferences preferences = getGCMPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PROPERTY_REGISTRATION_ERROR, failed);
        if (failed) {
            try {
                NetworkConnectionManager networkConnectionManager = new NetworkConnectionManager(getApplicationContext());
                if(networkConnectionManager.checkNetworkConnection()) { // (Only show this error if there is a network connection, else it is useless.
                    Toast.makeText(this, getString(R.string.error_push_failure), Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                try {
                    Toast.makeText(this, getString(R.string.error_push_failure), Toast.LENGTH_LONG).show();
                } catch(Exception ignored) {

                }
            }
        }
        editor.apply();
    }

    /**
     * Returns the current device registration token, used for push notifications.
     * @return GCM Registration Token.
     */
    private String getCurrentRegistrationToken() {
        return getGCMPreferences().getString(PROPERTY_GCM_REGISTRATION_TOKEN, "");
    }

    /**
     * Returns the special section of settings that contains the notification settings.
     * @return GCM Shared Preferences.
     */
    private SharedPreferences getGCMPreferences() {
        return getSharedPreferences(MainActivity.class.getSimpleName(),
                Context.MODE_PRIVATE);
    }


    /**
     * Converts an InputStream (e.g. from HttpUrlConnection) to a normal String.
     * @param in InputStream that will be converted
     * @return String with the same text as in the InputStream.
     */
    private String inputStreamToString(InputStream in) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder out = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.toString();
    }



    /**
     * Stores the Registration Token and app's versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param registrationToken Registration Token
     */
    private void storeRegistrationToken(String registrationToken) {
        final SharedPreferences prefs = getGCMPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_GCM_REGISTRATION_TOKEN, registrationToken);
        editor.putLong(PROPERTY_GCM_DEVICE_ID, deviceId);
        editor.putLong(PROPERTY_GCM_UPDATE_METHOD_ID, updateMethodId);
        editor.putInt(PROPERTY_APP_VERSION, BuildConfig.VERSION_CODE);
        editor.apply();
    }
}
