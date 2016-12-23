package com.arjanvlek.cyngnotainfo.common.activity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.arjanvlek.cyngnotainfo.cm.fragment.CMUpdateInformationFragment;
import com.arjanvlek.cyngnotainfo.cm.fragment.UnknownCMVersionErrorFragment;
import com.arjanvlek.cyngnotainfo.cm.fragment.UnofficialCMVersionErrorFragment;
import com.arjanvlek.cyngnotainfo.common.internal.ActivityLauncher;
import com.arjanvlek.cyngnotainfo.common.internal.ApplicationData;
import com.arjanvlek.cyngnotainfo.common.internal.SystemVersionProperties;
import com.arjanvlek.cyngnotainfo.common.internal.asynctask.GetServerStatus;
import com.arjanvlek.cyngnotainfo.common.model.ServerParameters;
import com.arjanvlek.cyngnotainfo.common.notification.GcmRegistrationIntentService;
import com.arjanvlek.cyngnotainfo.R;
import com.arjanvlek.cyngnotainfo.common.internal.Callback;
import com.arjanvlek.cyngnotainfo.common.internal.NetworkConnectionManager;
import com.arjanvlek.cyngnotainfo.common.internal.SettingsManager;
import com.arjanvlek.cyngnotainfo.cos.fragment.COSUpdateInformationFragment;
import com.arjanvlek.cyngnotainfo.common.fragment.DeviceInformationFragment;
import com.arjanvlek.cyngnotainfo.common.view.MessageDialog;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import static android.content.Intent.ACTION_VIEW;
import static com.arjanvlek.cyngnotainfo.common.internal.ApplicationData.IS_COS_KEY;
import static com.arjanvlek.cyngnotainfo.common.internal.SettingsManager.PROPERTY_DEVICE;
import static com.arjanvlek.cyngnotainfo.common.internal.SettingsManager.PROPERTY_DEVICE_ID;
import static com.arjanvlek.cyngnotainfo.common.internal.SettingsManager.PROPERTY_SETUP_DONE;
import static com.arjanvlek.cyngnotainfo.common.internal.SettingsManager.PROPERTY_UPDATE_CHECKED_DATE;
import static com.arjanvlek.cyngnotainfo.common.internal.SettingsManager.PROPERTY_UPDATE_METHOD;
import static com.arjanvlek.cyngnotainfo.common.internal.SettingsManager.PROPERTY_UPDATE_METHOD_ID;


@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity implements ActionBar.TabListener {

    private ViewPager mViewPager;
    private SettingsManager settingsManager;
    private NetworkConnectionManager networkConnectionManager;
    private ActivityLauncher activityLauncher;
    private Callback callback;

    // Used for Google Play Services check
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    // Permissions constants
    public final static String DOWNLOAD_PERMISSION = "android.permission.WRITE_EXTERNAL_STORAGE";
    public final static int PERMISSION_REQUEST_CODE = 200;

    private String device = "";
    private long deviceId = 0L;
    private String updateMethod = "";
    private long updateMethodId = 0L;


    private ApplicationData applicationData;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.applicationData = (ApplicationData)getApplication();
        setContentView(R.layout.activity_main_activity);
        Context context = getApplicationContext();
        settingsManager = new SettingsManager(context);
        networkConnectionManager = new NetworkConnectionManager(context);


        //Fetch currently selected device and update method
        device = settingsManager.getPreference(PROPERTY_DEVICE);
        deviceId = settingsManager.getLongPreference(PROPERTY_DEVICE_ID);
        updateMethod = settingsManager.getPreference(PROPERTY_UPDATE_METHOD);
        updateMethodId = settingsManager.getLongPreference(PROPERTY_UPDATE_METHOD_ID);

        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        }
        setTitle(getString(R.string.app_name));

        SectionsPagerAdapter mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), this.applicationData);

        mViewPager = (ViewPager) findViewById(R.id.mainActivityPager);

        if(mViewPager != null) {
            mViewPager.setAdapter(mSectionsPagerAdapter);
            mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    if (actionBar != null) {
                        actionBar.setSelectedNavigationItem(position);
                    }
                }
            });
        }



        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Creates a tab with text corresponding to the page title defined by
            // the adapter.
            //noinspection ConstantConditions
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }

        this.activityLauncher = new ActivityLauncher(this);

    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if Google Play services are installed on the device
        if (checkPlayServices()) {
            // Check if a device and update method have been set
            if (settingsManager.checkIfDeviceIsSet(this.applicationData.SYSTEM_TYPE)) {
                //Check if app needs to register for push notifications (like after device type change etc.)
                if(device != null && updateMethod != null) {
                    if (!settingsManager.checkIfRegistrationIsValid(deviceId, updateMethodId) || settingsManager.checkIfRegistrationHasFailed() && networkConnectionManager.checkNetworkConnection()) {
                        registerInBackground();
                    }
                }
            }

            // Mark the welcome tutorial as finished if the user is not running COS, or is moving from older app version. The latter is checked by either having stored update information for offline viewing, or if the last update checked date is set (if user always had up to date system and never viewed update information before).
            if(this.applicationData.SYSTEM_TYPE != SystemVersionProperties.SystemType.COS || (!settingsManager.getBooleanPreference(PROPERTY_SETUP_DONE) && (settingsManager.checkIfCacheIsAvailable() || settingsManager.containsPreference(PROPERTY_UPDATE_CHECKED_DATE)))) {
                settingsManager.saveBooleanPreference(PROPERTY_SETUP_DONE, true);
            }

            // Show the welcome tutorial if the app needs to be set up.
            if(!settingsManager.getBooleanPreference(PROPERTY_SETUP_DONE)) {
                if(networkConnectionManager.checkNetworkConnection()) {
                    activityLauncher.Tutorial();
                } else {
                    showNetworkError();
                }
            }

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private void showNetworkError() {
        MessageDialog errorDialog = new MessageDialog()
                .setTitle(getString(R.string.error_app_requires_network_connection))
                .setMessage(getString(R.string.error_app_requires_network_connection_message))
                .setNegativeButtonText(getString(R.string.download_error_close))
                .setClosable(false);
        errorDialog.show(getSupportFragmentManager(), "NetworkError");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handles action bar item clicks.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            activityLauncher.Settings();
            return true;
        }
        if (id == R.id.action_about) {
            activityLauncher.About();
            return true;
        }

        if (id == R.id.action_help) {
            activityLauncher.Help();
            return true;
        }

        if (id == R.id.action_faq) {
            activityLauncher.FAQ();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Action when clicked on a tab.
     * @param tab Tab which is selected
     * @param fragmentTransaction Android Fragment Transaction, unused here.
     */
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

    public void onCmDownloadPageButtonClick(View view) {
        new GetServerStatus(this.applicationData, new Callback() {
            @Override
            public void onActionPerformed(Object... result) {
                Intent i = new Intent(ACTION_VIEW, Uri.parse(((ServerParameters)result[0]).getCmDownloadUrl()));
                startActivity(i);
            }
        }).execute();
    }

    public void onCmInstallationGuideButtonClick(View view) {
        new GetServerStatus(this.applicationData, new Callback() {
            @Override
            public void onActionPerformed(Object... result) {
                Intent i = new Intent(ACTION_VIEW, Uri.parse(((ServerParameters)result[0]).getCmInstallGuideUrl()));
                startActivity(i);
            }
        }).execute();
    }


    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        private final ApplicationData applicationData;

        public SectionsPagerAdapter(FragmentManager fm, ApplicationData applicationData) {
            super(fm);
            this.applicationData = applicationData;
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a FragmentBuilder (defined as a static inner class below).
            return FragmentBuilder.newInstance(position + 1, applicationData);
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            boolean MorHigher = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
            switch (position) {
                case 0:
                    return MorHigher ? getString(R.string.update_information_header_short) : getString(R.string.update_information_header);
                case 1:
                    return MorHigher ? getString(R.string.device_information_header_short) : getString(R.string.device_information_header);
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
        public static Fragment newInstance(int sectionNumber, ApplicationData applicationData) {
            if (sectionNumber == 1) {
                switch(applicationData.SYSTEM_TYPE) {
                    case COS:
                        return new COSUpdateInformationFragment();
                    case OFFICIAL_CM:
                        return new CMUpdateInformationFragment();
                    case UNOFFICIAL_CM:
                        return new UnofficialCMVersionErrorFragment();
                    case UNKNOWN:
                        return new UnknownCMVersionErrorFragment();
                    default:
                        return new UnknownCMVersionErrorFragment();
                }
            }
            if (sectionNumber == 2) {
                return new DeviceInformationFragment();
            }
            return null;
        }
    }


    /**
     * Checks if the Google Play Services are installed on the device.
     * @return Returns if the Google Play Services are installed.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                finish();
            }
            return false;
        }
        return true;
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * Stores the registration ID and app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground() {
        Intent intent = new Intent(this,GcmRegistrationIntentService.class);
        intent.putExtra(IS_COS_KEY, this.applicationData.SYSTEM_TYPE == SystemVersionProperties.SystemType.COS);
        startService(intent);
    }

    // New Android 6.0 permissions methods

    public void requestDownloadPermissions(Callback callback) {
        if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.M) {
            this.callback = callback;
            requestPermissions(new String[]{DOWNLOAD_PERMISSION}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int  permsRequestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (permsRequestCode) {
            case PERMISSION_REQUEST_CODE:
                try {
                    if(this.callback != null) {
                        this.callback.onActionPerformed(grantResults[0]);
                    }
                } catch (Exception ignored) {

                }
        }
    }

    public boolean hasDownloadPermissions() {
        //noinspection SimplifiableIfStatement
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return (checkSelfPermission(DOWNLOAD_PERMISSION) == PackageManager.PERMISSION_GRANTED);
        } else {
            return true;
        }
    }

    public ActivityLauncher getActivityLauncher() {
        return this.activityLauncher;
    }
}
