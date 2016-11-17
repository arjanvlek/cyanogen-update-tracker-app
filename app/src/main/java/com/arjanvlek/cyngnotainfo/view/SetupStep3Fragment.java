package com.arjanvlek.cyngnotainfo.view;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;

import com.arjanvlek.cyngnotainfo.ApplicationContext;
import com.arjanvlek.cyngnotainfo.Model.Device;
import com.arjanvlek.cyngnotainfo.Model.SystemVersionProperties;
import com.arjanvlek.cyngnotainfo.R;
import com.arjanvlek.cyngnotainfo.Support.CustomDropdown;
import com.arjanvlek.cyngnotainfo.Support.SettingsManager;

import java.util.List;

public class SetupStep3Fragment extends AbstractFragment {

    private View rootView;
    private SettingsManager settingsManager;
    private ProgressBar progressBar;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_setup_3, container, false);
        settingsManager = new SettingsManager(getActivity().getApplicationContext());
        progressBar = (ProgressBar) rootView.findViewById(R.id.settingsDeviceProgressBar);
        return rootView;
    }

    public void fetchDevices() {
        new GetDevices().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private class GetDevices extends AsyncTask<Void, Void, List<Device>> {

        @Override
        protected void onPreExecute() {
            try {
                progressBar.setVisibility(View.VISIBLE);
            } catch (Exception ignored) {

            }
        }

        @Override
        protected List<Device> doInBackground(Void... params) {
            return getApplicationContext().getDevices();
        }

        @Override
        protected void onPostExecute(List<Device> devices) {
            fillDeviceSettings(devices);
        }
    }

    private void fillDeviceSettings(final List<Device> devices) {
        Spinner spinner = (Spinner) rootView.findViewById(R.id.settingsDeviceSpinner);

        int selectedIndex = -1;
        SystemVersionProperties systemVersionProperties = ((ApplicationContext)getActivity().getApplication()).getSystemVersionProperties();

        for(int i=0; i<devices.size(); i++) {
            if(devices.get(i).getModelNumber() != null && devices.get(i).getModelNumber().equals(systemVersionProperties.getCyanogenDeviceCodeName())) {
                selectedIndex = i;
            }
        }

        final int selection = selectedIndex;

        if (settingsManager.containsPreference(SettingsManager.PROPERTY_DEVICE_ID)) {
            for(Device device : devices) {
                if(device.getId() == settingsManager.getLongPreference(SettingsManager.PROPERTY_DEVICE_ID) ){
                    selectedIndex = devices.indexOf(device);
                }
            }
        }

        ArrayAdapter<Device> adapter = new ArrayAdapter<Device>(getActivity(), android.R.layout.simple_spinner_item, devices) {

            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                return CustomDropdown.initCustomDeviceDropdown(position, convertView, parent, android.R.layout.simple_spinner_item, devices, selection, this.getContext());
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                return CustomDropdown.initCustomDeviceDropdown(position, convertView, parent, android.R.layout.simple_spinner_dropdown_item, devices, selection, this.getContext());
            }


    };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if(selectedIndex != -1) {
            spinner.setSelection(selectedIndex);
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Device device = (Device)adapterView.getItemAtPosition(i);
                settingsManager.savePreference(SettingsManager.PROPERTY_DEVICE, device.getDeviceName());
                settingsManager.saveLongPreference(SettingsManager.PROPERTY_DEVICE_ID, device.getId());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }

        });

        try {
            progressBar.setVisibility(View.GONE);
        } catch (Exception ignored) {

        }
    }
}
