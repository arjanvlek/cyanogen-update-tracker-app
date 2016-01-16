package com.arjanvlek.cyngnotainfo.views;

import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.widget.SwitchCompat;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.arjanvlek.cyngnotainfo.Model.Device;
import com.arjanvlek.cyngnotainfo.Model.UpdateMethod;
import com.arjanvlek.cyngnotainfo.R;
import com.arjanvlek.cyngnotainfo.Support.SettingsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_RECEIVE_NEW_DEVICE_NOTIFICATIONS;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_RECEIVE_SYSTEM_UPDATE_NOTIFICATIONS;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_RECEIVE_WARNING_NOTIFICATIONS;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_SHOW_APP_UPDATE_MESSAGES;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_SHOW_IF_SYSTEM_IS_UP_TO_DATE;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_SHOW_NEWS_MESSAGES;

public class SettingsActivity extends AbstractActivity {
    private ProgressBar progressBar;
    private SettingsManager settingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        settingsManager = new SettingsManager(getApplicationContext());
        progressBar = (ProgressBar) findViewById(R.id.settingsProgressBar);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            try {
                progressBar.setVisibility(View.VISIBLE);
            } catch (Exception ignored) {

            }
            new DeviceDataFetcher().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            try {
                progressBar.setVisibility(View.VISIBLE);
            } catch (Exception ignored) {

            }
            new DeviceDataFetcher().execute();
        }
        initSwitches();
    }

    private void initSwitches() {
        SwitchCompat appUpdatesSwitch = (SwitchCompat) findViewById(R.id.settingsAppUpdatesSwitch);
        appUpdatesSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settingsManager.saveBooleanPreference(PROPERTY_SHOW_APP_UPDATE_MESSAGES, isChecked);
            }
        });
        appUpdatesSwitch.setChecked(settingsManager.showAppUpdateMessages());

        SwitchCompat appMessagesSwitch = (SwitchCompat) findViewById(R.id.settingsAppMessagesSwitch);
        appMessagesSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settingsManager.saveBooleanPreference(PROPERTY_SHOW_NEWS_MESSAGES, isChecked);
            }
        });
        appMessagesSwitch.setChecked(settingsManager.showNewsMessages());

        SwitchCompat importantPushNotificationsSwitch = (SwitchCompat) findViewById(R.id.settingsImportantPushNotificationsSwitch);
        importantPushNotificationsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settingsManager.saveBooleanPreference(PROPERTY_RECEIVE_WARNING_NOTIFICATIONS, isChecked);
            }
        });
        importantPushNotificationsSwitch.setChecked(settingsManager.receiveWarningNotifications());

        SwitchCompat newVersionPushNotificationsSwitch = (SwitchCompat) findViewById(R.id.settingsNewVersionPushNotificationsSwitch);
        newVersionPushNotificationsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settingsManager.saveBooleanPreference(PROPERTY_RECEIVE_SYSTEM_UPDATE_NOTIFICATIONS, isChecked);
            }
        });
        newVersionPushNotificationsSwitch.setChecked(settingsManager.receiveSystemUpdateNotifications());

        SwitchCompat newDevicePushNotificationsSwitch = (SwitchCompat) findViewById(R.id.settingsNewDevicePushNotificationsSwitch);
        newDevicePushNotificationsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settingsManager.saveBooleanPreference(PROPERTY_RECEIVE_NEW_DEVICE_NOTIFICATIONS, isChecked);
            }
        });
        newDevicePushNotificationsSwitch.setChecked(settingsManager.receiveNewDeviceNotifications());

        SwitchCompat systemIsUpToDateSwitch = (SwitchCompat) findViewById(R.id.settingsSystemIsUpToDateSwitch);
        systemIsUpToDateSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settingsManager.saveBooleanPreference(PROPERTY_SHOW_IF_SYSTEM_IS_UP_TO_DATE, isChecked);
            }
        });
        systemIsUpToDateSwitch.setChecked(settingsManager.showIfSystemIsUpToDate());
    }

    private class DeviceDataFetcher extends AsyncTask<Void, Integer, List<Device>> {

        @Override
        public List<Device> doInBackground(Void... voids) {
            return getAppApplicationContext().getServerConnector().getDevices();
        }

        @Override
        public void onPostExecute(List<Device> devices) {
            fillDeviceSettings(devices);

        }
    }

    private void fillDeviceSettings(final List<Device> devices) {
        if (devices != null && !devices.isEmpty()) {
            Spinner spinner = (Spinner) findViewById(R.id.settingsDeviceSpinner);
            List<String> deviceNames = new ArrayList<>();

            for (Device device : devices) {
                deviceNames.add(device.getDeviceName());
            }

            // Set the spinner to the previously selected device.
            Integer position = null;
            String currentDeviceName = settingsManager.getPreference(SettingsManager.PROPERTY_DEVICE);
            if (currentDeviceName != null) {
                for (int i = 0; i < deviceNames.size(); i++) {
                    if (deviceNames.get(i).equals(currentDeviceName)) {
                        position = i;
                    }
                }
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            if (position != null) {
                spinner.setSelection(position);
            }
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    String deviceName = (String) adapterView.getItemAtPosition(i);
                    Long deviceId = 0L;
                    for (Device device : devices) {
                        if (device.getDeviceName().equalsIgnoreCase(deviceName)) {
                            deviceId = device.getId();
                        }
                    }
                    settingsManager.savePreference(SettingsManager.PROPERTY_DEVICE, deviceName);
                    settingsManager.saveLongPreference(SettingsManager.PROPERTY_DEVICE_ID, deviceId);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        try {
                            progressBar.setVisibility(View.VISIBLE);
                        } catch (Exception ignored) {
                        }

                        new UpdateDataFetcher().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, deviceId);
                    } else {
                        try {
                            progressBar.setVisibility(View.VISIBLE);
                        } catch (Exception ignored) {

                        }
                        new UpdateDataFetcher().execute(deviceId);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                }
            });
        } else {
            hideDeviceAndUpdateMethodSettings();
            progressBar.setVisibility(View.GONE);

        }
    }

    private void hideDeviceAndUpdateMethodSettings() {
        findViewById(R.id.settingsDeviceSpinner).setVisibility(View.GONE);
        findViewById(R.id.settingsDeviceView).setVisibility(View.GONE);
        findViewById(R.id.settingsUpdateMethodSpinner).setVisibility(View.GONE);
        findViewById(R.id.settingsUpdateMethodView).setVisibility(View.GONE);
        findViewById(R.id.settingsDescriptionView).setVisibility(View.GONE);
        findViewById(R.id.settingsUpperDivisor).setVisibility(View.GONE);
    }

    private class UpdateDataFetcher extends AsyncTask<Long, Integer, List<UpdateMethod>> {

        @Override
        public List<UpdateMethod> doInBackground(Long... deviceIds) {
            long deviceId = deviceIds[0];
            return getServerConnector().getUpdateMethods(deviceId);
        }

        @Override
        public void onPostExecute(List<UpdateMethod> updateMethods) {
            fillUpdateSettings(updateMethods);

        }
    }

    private void fillUpdateSettings(final List<UpdateMethod> updateMethods) {
        if(updateMethods != null && !updateMethods.isEmpty()) {
            Spinner spinner = (Spinner) findViewById(R.id.settingsUpdateMethodSpinner);
            String currentUpdateMethod = settingsManager.getPreference(SettingsManager.PROPERTY_UPDATE_METHOD);
            Integer position = null;
            if (currentUpdateMethod != null) {
                for (int i = 0; i < updateMethods.size(); i++) {
                    if (updateMethods.get(i).getUpdateMethod().equals(currentUpdateMethod) || updateMethods.get(i).getUpdateMethodNl().equalsIgnoreCase(currentUpdateMethod)) {
                        position = i;
                    }
                }
            }
            List<String> updateMethodNames = new ArrayList<>();
            String language = Locale.getDefault().getDisplayLanguage();
            switch (language) {
                case "Nederlands":
                    for (UpdateMethod updateMethod : updateMethods) {
                        updateMethodNames.add(updateMethod.getUpdateMethodNl());
                    }
                    break;
                default:
                    for (UpdateMethod updateMethod : updateMethods) {
                        updateMethodNames.add(updateMethod.getUpdateMethod());
                    }
                    break;
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, updateMethodNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            if (position != null) {
                spinner.setSelection(position);
            }

            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    long updateMethodId = 0L;
                    String updateMethodName = (String) adapterView.getItemAtPosition(i);
                    try {
                        progressBar.setVisibility(View.VISIBLE);
                    } catch (Exception ignored) {
                    }
                    //Set update method in preferences.
                    for (UpdateMethod updateMethod : updateMethods) {
                        if (updateMethod.getUpdateMethod().equals(updateMethodName) || updateMethod.getUpdateMethodNl().equals(updateMethodName)) {
                            updateMethodId = updateMethod.getId();
                        }
                    }
                    settingsManager.saveLongPreference(SettingsManager.PROPERTY_UPDATE_METHOD_ID, updateMethodId);
                    settingsManager.savePreference(SettingsManager.PROPERTY_UPDATE_METHOD, updateMethodName);
                    try {
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }
                    } catch (Exception ignored) {

                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            });
        } else {
            hideDeviceAndUpdateMethodSettings();
            progressBar.setVisibility(View.GONE);
        }
    }

    private void showSettingsWarning() {
        Toast.makeText(this, getString(R.string.settings_entered_incorrectly), Toast.LENGTH_LONG).show();
    }


    @Override
    public void onBackPressed() {
        if (settingsManager.checkIfSettingsAreValid() && !progressBar.isShown()) {
            NavUtils.navigateUpFromSameTask(this);
        } else {
            showSettingsWarning();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                if (settingsManager.checkIfSettingsAreValid() && !progressBar.isShown()) {
                    NavUtils.navigateUpFromSameTask(this);
                    return true;
                } else {
                    showSettingsWarning();
                    return true;
                }
        }
        return super.onOptionsItemSelected(item);
    }

}
