package com.arjanvlek.cyngnotainfo;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.app.DialogFragment;
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

import com.arjanvlek.cyngnotainfo.Model.DeviceTypeEntity;
import com.arjanvlek.cyngnotainfo.Model.UpdateTypeEntity;
import com.arjanvlek.cyngnotainfo.Settings.DeviceSettingsFragment;
import com.arjanvlek.cyngnotainfo.Settings.UpdateSettingsFragment;
import com.arjanvlek.cyngnotainfo.Support.ServerConnector;
import com.arjanvlek.cyngnotainfo.views.AboutActivity;
import com.arjanvlek.cyngnotainfo.views.DeviceInformationFragment;
import com.arjanvlek.cyngnotainfo.views.UpdateInformationFragment;
import com.arjanvlek.cyngnotainfo.views.UpdateInstallationInstructionsActivity;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements ActionBar.TabListener, GoogleApiClient.OnConnectionFailedListener {

    private ViewPager mViewPager;
    private AdView mAdView;

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
    public static final String INCREMENTAL_UPDATE = "Incremental update";

    private String SENDER_ID = "** Add your Google Cloud Messaging API key here **";
    private String SERVER_URL = "** Add the base URL of your API / backend here **register-device.php";
    private GoogleCloudMessaging cloudMessaging;
    private Context context;
    private String registrationId;
    private String deviceType = "";
    private String updateType = "";
    private String updateLink = "";
    private ProgressDialog progressDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_activity);
        context = getApplicationContext();

        SharedPreferences preferences = getPreferences(MODE_APPEND);
        deviceType = preferences.getString(PROPERTY_DEVICE_TYPE, "");
        updateType = preferences.getString(PROPERTY_UPDATE_TYPE, "");
        updateLink = preferences.getString(PROPERTY_UPDATE_LINK, "");



        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        }



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
            cloudMessaging = GoogleCloudMessaging.getInstance(context);
            registrationId = getRegistrationId(context);
            if (checkIfDeviceIsSet()) {
                checkIfRegistrationHasFailed();
                if (!checkIfRegistrationIsValid(context)) {
                    registerInBackground(registrationId);
                }
            }
            if (deviceType != null && updateType != null) {
                if (deviceType.isEmpty() || updateType.isEmpty() || updateLink.isEmpty()) {
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        new SettingsLauncher().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }
                    else {
                        new SettingsLauncher().execute();
                    }
                }
            }
        }
    }




    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    private class SettingsLauncher extends AsyncTask<Void,Integer,List<Object>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setMessage(getString(R.string.fetching_devices));
            progressDialog.setTitle(getString(R.string.loading));
            progressDialog.setIndeterminate(false);
            progressDialog.setCancelable(true);
            progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    cancel(true);
                }
            });
            progressDialog.show();


        }

        @Override
        public List<Object> doInBackground(Void... voids) {
            ServerConnector serverConnector = new ServerConnector();
            List<Object> objects = new ArrayList<>();
            objects.add(serverConnector.getDeviceTypeEntities());
            objects.add(serverConnector.getUpdateTypeEntities());
            return objects;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onPostExecute(List<Object> entities) {
            ArrayList<DeviceTypeEntity> deviceTypeEntities = (ArrayList<DeviceTypeEntity>)entities.get(0);
            ArrayList<UpdateTypeEntity> updateTypeEntities = (ArrayList<UpdateTypeEntity>)entities.get(1);
            ArrayList<String>deviceNames = new ArrayList<>();
            ArrayList<String>updateTypes = new ArrayList<>();

            for(DeviceTypeEntity deviceTypeEntity : deviceTypeEntities) {
                deviceNames.add(deviceTypeEntity.getDeviceType());
            }
            for(UpdateTypeEntity updateTypeEntity : updateTypeEntities) {
                updateTypes.add(updateTypeEntity.getUpdateType());
            }
            if(progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            askForDeviceSettings(deviceNames, updateTypes);

        }
    }

    private void askForDeviceSettings(ArrayList<String> deviceNames, ArrayList<String> updateTypes) {
        DialogFragment fragment = new DeviceSettingsFragment();
        Bundle args = new Bundle();
        args.putStringArrayList("device_names", deviceNames);
        args.putStringArrayList("update_types", updateTypes);
        fragment.setArguments(args);
        fragment.show(getSupportFragmentManager(), "deviceSettings");


    }

    private void About() {
        Intent i = new Intent(this,AboutActivity.class);
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
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                new SettingsLauncher().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
            else {
                new SettingsLauncher().execute();
            }
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
            if(sectionNumber == 1) {
                return new UpdateInformationFragment();
            }
            if(sectionNumber == 2) {
                return new DeviceInformationFragment();
            }
            return null;
        }

    }
    public void showUpdateInstructions(View v){
        Intent i = new Intent(this,UpdateInstallationInstructionsActivity.class);
        startActivity(i);
    }

    /** Called when leaving the activity */
    @Override
    public void onPause() {
        if (mAdView != null) {
            mAdView.pause();
        }
        super.onPause();
    }

    /** Called when returning to the activity */
    @Override
    public void onResume() {
        super.onResume();
        checkPlayServices();
        if (mAdView != null) {
            mAdView.resume();
        }
    }

    /** Called before the activity is destroyed */
    @Override
    public void onDestroy() {
        if (mAdView != null) {
            mAdView.destroy();
        }
        super.onDestroy();
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
            System.out.println("Registration not found.");
            return false;
        }

        if(!deviceType.equals(registeredDeviceType)){
            System.out.println("Device type has changed.");
            return false;
        }
        if(!updateType.equals(registeredUpdateType)) {
            System.out.println("Update type has changed.");
            return false;
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing registration ID is not guaranteed to work with
        // the new app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            System.out.println("App version changed.");
            return false;
        }
        return true;
    }

    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences();
        String registrationId = prefs.getString(PROPERTY_GCM_REG_ID, "");
        if(registrationId != null && registrationId.isEmpty()) {
            return "";
        }
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            System.out.println("App version changed.");
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
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }



    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration ID and app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground(String registrationId) {
        new cloudRegisterTask().execute(registrationId, null, null);
    }

    private boolean checkIfDeviceIsSet() {
        SharedPreferences preferences = getPreferences(Context.MODE_APPEND);
        return preferences.contains(MainActivity.PROPERTY_DEVICE_TYPE) && preferences.contains(MainActivity.PROPERTY_UPDATE_TYPE) && preferences.contains(MainActivity.PROPERTY_UPDATE_LINK);
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

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    new RegisterIdToBackend().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, registrationId, oldRegistrationId);
                }
                else {
                    new RegisterIdToBackend().execute(registrationId, oldRegistrationId);
                }

                storeRegistrationId(context, registrationId);
            } catch (IOException ex) {
                setRegistrationFailure();
            }
            return null;
        }
    }

    private class RegisterIdToBackend extends AsyncTask<String,Integer, String> {

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
                urlConnection = (HttpURLConnection)url.openConnection();
                urlConnection.setDoOutput(true);
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.connect();
                OutputStream out = urlConnection.getOutputStream();
                byte[] outputBytes = jsonResponse.toString().getBytes();
                out.write(outputBytes);
                out.close();
                in = new BufferedInputStream(urlConnection.getInputStream());
                result = inputStreamToString(in);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(String response) {
            JSONObject result = null;
            try {
                result = new JSONObject(response);
                System.out.println(result.toString());
                if(result.getString("success") != null) {
                    System.out.println("registration successful!");
                }
                else {
                    setRegistrationFailure();
                    System.out.println("registration error");
                }
            } catch (Exception e) {
                try {
                    setRegistrationFailure();
                    System.out.println("registration error: " + (result != null ? result.getString("error") : null));
                } catch (JSONException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }
    private void setRegistrationFailure() {
        SharedPreferences preferences = getGCMPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PROPERTY_REGISTRATION_ERROR, true);
        editor.apply();
    }
    private void checkIfRegistrationHasFailed() {
        SharedPreferences preferences = getGCMPreferences();
        if(preferences.getBoolean(PROPERTY_REGISTRATION_ERROR, false)) {
            registerInBackground(registrationId);
        }

        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PROPERTY_REGISTRATION_ERROR, false);
        editor.apply();
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
     * @param regId registration ID
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
}
