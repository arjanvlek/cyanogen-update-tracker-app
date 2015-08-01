package com.arjanvlek.cyngnotainfo.views;

import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.arjanvlek.cyngnotainfo.Model.Device;
import com.arjanvlek.cyngnotainfo.Model.UpdateDataLink;
import com.arjanvlek.cyngnotainfo.Model.UpdateMethod;
import com.arjanvlek.cyngnotainfo.R;
import com.arjanvlek.cyngnotainfo.Support.SettingsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    }

    private class DeviceDataFetcher extends AsyncTask<Void, Integer, List<Device>> {

        @Override
        public List<Device> doInBackground(Void... voids) {
            return getDevices();
        }

        @Override
        public void onPostExecute(List<Device> devices) {
            fillDeviceSettings(devices);

        }
    }

    private void fillDeviceSettings(final List<Device> devices) {
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
                    if(updateMethod.getUpdateMethod().equals(updateMethodName) || updateMethod.getUpdateMethodNl().equals(updateMethodName)) {
                        updateMethodId = updateMethod.getId();
                    }
                }
                    settingsManager.saveLongPreference(SettingsManager.PROPERTY_UPDATE_METHOD_ID, updateMethodId);
                    settingsManager.savePreference(SettingsManager.PROPERTY_UPDATE_METHOD, updateMethodName);
                //Set update link
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    new UpdateDataLinkSetter().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, settingsManager.getLongPreference(SettingsManager.PROPERTY_DEVICE_ID), updateMethodId);
                } else {
                    new UpdateDataLinkSetter().execute(settingsManager.getLongPreference(SettingsManager.PROPERTY_DEVICE_ID), updateMethodId);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    private class UpdateDataLinkSetter extends AsyncTask<Long, Integer, UpdateDataLink> {
        @Override
        public UpdateDataLink doInBackground(Long... deviceAndUpdateData) {
            Long deviceId = deviceAndUpdateData[0];
            Long updateMethodId = deviceAndUpdateData[1];
            return getServerConnector().getUpdateDataLink(deviceId, updateMethodId);
        }

        @Override
        public void onPostExecute(UpdateDataLink updateDataLink) {
            settingsManager.savePreference(SettingsManager.PROPERTY_UPDATE_DATA_LINK, updateDataLink.getUpdateDataUrl());
            try {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            } catch (Exception ignored) {

            }
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
