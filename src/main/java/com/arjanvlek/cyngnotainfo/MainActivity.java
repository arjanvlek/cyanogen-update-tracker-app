package com.arjanvlek.cyngnotainfo;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.arjanvlek.cyngnotainfo.views.SettingsActivity;
import com.arjanvlek.cyngnotainfo.views.AboutActivity;
import com.arjanvlek.cyngnotainfo.views.DeviceInformationFragment;
import com.arjanvlek.cyngnotainfo.views.TutorialActivity;
import com.arjanvlek.cyngnotainfo.views.UpdateInformationFragment;
import com.arjanvlek.cyngnotainfo.views.UpdateInstallationInstructionsActivity;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONObject;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity implements ActionBar.TabListener, GoogleApiClient.OnConnectionFailedListener {

    private ViewPager mViewPager;
    private AdView updateInformationAdView;
    private AdView deviceInformationAdView;

    // Used for Google Play Services check
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    // Used to register for Push Notifications
    public static final String PROPERTY_GCM_REG_ID = "registration_id";
    public static final String PROPERTY_GCM_DEVICE_TYPE = "gcm_device_type";
    public static final String PROPERTY_GCM_UPDATE_TYPE = "gcm_update_type";
    public static final String PROPERTY_APP_VERSION = "appVersion";
    public static final String PROPERTY_DEVICE_TYPE = "device_type";
    public static final String PROPERTY_UPDATE_TYPE = "update_type";
    public static final String PROPERTY_REGISTRATION_ERROR = "registration_error";
    public static final String PROPERTY_UPDATE_LINK = "update_link";

    private static final String JSON_PROPERTY_DEVICE_REGISTRATION_ID = "device_id";
    private static final String JSON_PROPERTY_DEVICE_TYPE = "tracking_device_type";
    private static final String JSON_PROPERTY_UPDATE_TYPE = "tracking_update_type";
    private static final String JSON_PROPERTY_OLD_DEVICE_ID = "old_device_id";

    public static final String FULL_UPDATE = "full_update";
    public static final String INCREMENTAL_UPDATE = "incremental_update";

    public static String SENDER_ID = "** Add your Google Cloud Messaging API key here **";
    public static String SERVER_URL = "** Add the base URL of your API / backend here **register-device.php";
    private GoogleCloudMessaging cloudMessaging;
    private Context context;
    private String registrationId;
    private String deviceType = "";
    private String updateType = "";
    private String updateLink = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_activity);
        context = getApplicationContext();

        deviceType = getPreference(PROPERTY_DEVICE_TYPE, getApplicationContext());
        updateType = getPreference(PROPERTY_UPDATE_TYPE, getApplicationContext());
        updateLink = getPreference(PROPERTY_UPDATE_LINK, getApplicationContext());


        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        }
        setTitle(getString(R.string.app_name));

        SectionsPagerAdapter mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (actionBar != null) {
                    actionBar.setSelectedNavigationItem(position);
                }
            }
        });

        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            //noinspection ConstantConditions
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        if (checkPlayServices()) {
            deviceInformationAdView = (AdView) findViewById(R.id.device_information_banner_field);
            updateInformationAdView = (AdView) findViewById(R.id.update_information_banner_field);
            cloudMessaging = GoogleCloudMessaging.getInstance(context);
            registrationId = getRegistrationId(context);
            if (checkIfDeviceIsSet()) {
                checkIfRegistrationHasFailed();
                if (!checkIfRegistrationIsValid(context)) {
                    registerInBackground(registrationId);
                }
            }
            if (deviceType == null || updateType == null || updateLink == null) {
                Tutorial();
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }


    private void Settings() {
        Intent i = new Intent(this, SettingsActivity.class);
        startActivity(i);
    }

    private void Tutorial() {
        Intent i = new Intent(this, TutorialActivity.class);
        startActivity(i);
    }

    private void About() {
        Intent i = new Intent(this, AboutActivity.class);
        startActivity(i);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Settings();
            return true;
        }
        if (id == R.id.action_about) {
            About();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a FragmentBuilder (defined as a static inner class below).
            return FragmentBuilder.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();
            switch (position) {
                case 0:
                    return getString(R.string.title_section1).toUpperCase(l);
                case 1:
                    return getString(R.string.title_section2).toUpperCase(l);
            }
            return null;
        }
    }

    /**
     * An inner class that constructs the fragments used in this application.
     */
    public static class FragmentBuilder {

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static Fragment newInstance(int sectionNumber) {
            if (sectionNumber == 1) {
                return new UpdateInformationFragment();
            }
            if (sectionNumber == 2) {
                return new DeviceInformationFragment();
            }
            return null;
        }

    }

    public void showUpdateInstructions(View v) {
        Intent i = new Intent(this, UpdateInstallationInstructionsActivity.class);
        startActivity(i);
    }


    /**
     * Called when returning to the activity
     */
    @Override
    public void onResume() {
        super.onResume();
        checkPlayServices();
    }


    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                System.out.println("This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }


    private boolean checkIfRegistrationIsValid(Context context) {
        final SharedPreferences prefs = getGCMPreferences();
        String registrationId = prefs.getString(PROPERTY_GCM_REG_ID, "");
        String registeredDeviceType = prefs.getString(PROPERTY_GCM_DEVICE_TYPE, "");
        String registeredUpdateType = prefs.getString(PROPERTY_GCM_UPDATE_TYPE, "");

        if (registrationId != null && registrationId.isEmpty()) {
            return false;
        }

        if (!deviceType.equals(registeredDeviceType)) {
            return false;
        }
        if (!updateType.equals(registeredUpdateType)) {
            return false;
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing registration ID is not guaranteed to work with
        // the new app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        return registeredVersion == currentVersion;
    }

    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences();
        String registrationId = prefs.getString(PROPERTY_GCM_REG_ID, "");
        if (registrationId != null && registrationId.isEmpty()) {
            return "";
        }
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            return "";
        }
        return registrationId;
    }

    private SharedPreferences getGCMPreferences() {
        return getSharedPreferences(MainActivity.class.getSimpleName(),
                Context.MODE_PRIVATE);
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
            return 0;
        }
    }


    /**
     * Registers the application with GCM servers asynchronously.
     * <p/>
     * Stores the registration ID and app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground(String registrationId) {
        new cloudRegisterTask().execute(registrationId, null, null);
    }

    private boolean checkIfDeviceIsSet() {
        return checkPreference(MainActivity.PROPERTY_DEVICE_TYPE, getApplicationContext()) && checkPreference((MainActivity.PROPERTY_UPDATE_TYPE), getApplicationContext()) && checkPreference((MainActivity.PROPERTY_UPDATE_LINK), getApplicationContext());
    }

    private class cloudRegisterTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... strings) {
            String oldRegistrationId = strings[0];
            try {
                if (cloudMessaging == null) {
                    cloudMessaging = GoogleCloudMessaging.getInstance(context);
                }
                registrationId = cloudMessaging.register(SENDER_ID);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    new RegisterIdToBackend().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, registrationId, oldRegistrationId);
                } else {
                    new RegisterIdToBackend().execute(registrationId, oldRegistrationId);
                }

                storeRegistrationId(context, registrationId);
            } catch (IOException ex) {
                setRegistrationFailed(true);
            }
            return null;
        }
    }

    private class RegisterIdToBackend extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... strings) {
            String registrationId = strings[0];
            String oldRegistrationId = strings[1];
            HttpURLConnection urlConnection = null;
            InputStream in;
            String result = null;
            try {

                JSONObject jsonResponse = new JSONObject();
                jsonResponse.put(JSON_PROPERTY_DEVICE_REGISTRATION_ID, registrationId);
                jsonResponse.put(JSON_PROPERTY_DEVICE_TYPE, deviceType);
                jsonResponse.put(JSON_PROPERTY_UPDATE_TYPE, updateType);
                jsonResponse.put(JSON_PROPERTY_OLD_DEVICE_ID, oldRegistrationId);
                URL url = new URL(SERVER_URL);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setDoOutput(true);
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setConnectTimeout(2000);
                urlConnection.setReadTimeout(5000);
                urlConnection.connect();
                OutputStream out = urlConnection.getOutputStream();
                byte[] outputBytes = jsonResponse.toString().getBytes();
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
                if (result.getString("success") != null) {
                    setRegistrationFailed(false);
                } else {
                    setRegistrationFailed(true);
                }
            } catch (Exception e) {
                setRegistrationFailed(true);
            }
        }
    }

    private void setRegistrationFailed(boolean failed) {
        SharedPreferences preferences = getGCMPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PROPERTY_REGISTRATION_ERROR, failed);
        if (failed) {
            Toast.makeText(this, getString(R.string.push_failure), Toast.LENGTH_LONG).show();
        }
        editor.apply();
    }

    private void checkIfRegistrationHasFailed() {
        SharedPreferences preferences = getGCMPreferences();
        if (preferences.getBoolean(PROPERTY_REGISTRATION_ERROR, false)) {
            registerInBackground(registrationId);
        }
    }

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
     * Stores the registration ID and app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId   registration ID
     */
    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences();
        int appVersion = getAppVersion(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_GCM_REG_ID, regId);
        editor.putString(PROPERTY_GCM_DEVICE_TYPE, deviceType);
        editor.putString(PROPERTY_GCM_UPDATE_TYPE, updateType);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.apply();
    }


    public static void savePreference(String key, String value, Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.apply();
    }

    public static boolean checkPreference(String key, Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.contains(key);
    }

    public static String getPreference(String key, Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString(key, null);
    }
}
