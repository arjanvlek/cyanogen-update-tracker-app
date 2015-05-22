package com.arjanvlek.cyngnotainfo.views;

import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.arjanvlek.cyngnotainfo.Support.DateTimeFormatter;
import com.arjanvlek.cyngnotainfo.Model.CyanogenOTAUpdate;
import com.arjanvlek.cyngnotainfo.Model.DeviceType;
import com.arjanvlek.cyngnotainfo.R;
import com.arjanvlek.cyngnotainfo.Support.ServiceHandler;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.utils.ValueFormatter;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class UpdateInformationFragment extends Fragment implements Button.OnClickListener {

    private DeviceType deviceType;
    private String deviceName;
    private String updateType;
    private String localizedUpdateType;

    private CyanogenOTAUpdate cyanogenOTAUpdate;

    private RelativeLayout rootView;
    private AdView mAdView;

    private ProgressDialog progressDialog;
    private DateTime refreshedDate;
    private boolean isFetched;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences preferences = getActivity().getPreferences(Context.MODE_APPEND);
        String deviceName = preferences.getString("device-name", "not-set");
        updateType = preferences.getString("update-type", "not-set");

        if(deviceName != null) {
            detectDevice(deviceName);
        }
        if(updateType != null) {
            detectUpdateType(updateType);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        //Inflate the layout for this fragment
        rootView = (RelativeLayout)inflater.inflate(R.layout.fragment_updateinformation, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {

    }

    private boolean checkIfDeviceIsSet() {
        SharedPreferences preferences = getActivity().getPreferences(Context.MODE_APPEND);
        return preferences.contains("device-name") || preferences.contains("update-type");
    }

    @Override
    public void onStart() {
        super.onStart();
        if(!isFetched && checkIfDeviceIsSet()) {
            if(checkNetworkConnection()) {
                fetchUpdateInformation();
                showAds();
                refreshedDate = DateTime.now();
                isFetched = true;
            }
            else {
                hideAds();
                showNetworkError();
            }
        }

    }

    private void hideAds() {
        if(mAdView != null) {
            mAdView.destroy();
        }
    }

    private void showAds() {

        // Gets the ad view defined in layout/ad_fragment.xml with ad unit ID set in
        // values/strings.xml.
        mAdView = (AdView) rootView.findViewById(R.id.update_information_banner_field);

        // Create an ad request. Check logcat output for the hashed device ID to
        // get test ads on a physical device. e.g.
        // "Use AdRequest.Builder.addTestDevice("ABCDEF012345") to get test ads on this device."
        String adsTestId = "7CFCF353FBC40363065F03DFAC7D7EE4";
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice(adsTestId)
                .addKeyword("smartphone")
                .addKeyword("tablet")
                .addKeyword("news apps")
                .addKeyword("games")
                .build();

        // Start loading the ad in the background.
        mAdView.loadAd(adRequest);
    }
    private boolean checkNetworkConnection() {
        ConnectivityManager cm =
                (ConnectivityManager)getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
    }

    private void detectDevice(String deviceName) {
        if(deviceName.equals("bacon")) {
            deviceType = DeviceType.BACON;
            this.deviceName = "OnePlus One";
        }
        if(deviceName.equals("tomato")) {
            deviceType = DeviceType.TOMATO;
            this.deviceName = "YU Yureka";
        }
        if(deviceName.equals("n1")) {
            deviceType = DeviceType.N1;
            this.deviceName = "Oppo N1 CyanogenMod Edition";
        }
    }

    private void detectUpdateType(String updateTypeName) {
        if(updateTypeName.equals("stable")) {
            localizedUpdateType = getString(R.string.stable_update);
        }
        if(updateTypeName.equals("incremental")) {
            localizedUpdateType = getString(R.string.incremental_update);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if(refreshedDate != null && isFetched && checkIfDeviceIsSet()) {
            if (refreshedDate.plusMinutes(5).isBefore(DateTime.now())) {
                if(checkNetworkConnection()) {
                    fetchUpdateInformation();
                    refreshedDate = DateTime.now();
                }
                else {
                    showNetworkError();
                }
            }
        }
    }

    private void fetchUpdateInformation() {
        new GetUpdateInformation().execute();
    }

    private void displayUpdateInformation() {
        if(cyanogenOTAUpdate != null) {
            generateCircleDiagram();
            TextView buildNumberView = (TextView) rootView.findViewById(R.id.buildNumberLabel);
            buildNumberView.setText(cyanogenOTAUpdate.getName() + " " + getString(R.string.string_for) + " " + deviceName + ", " + localizedUpdateType + getString(R.string.dot));

            TextView downloadSizeView = (TextView) rootView.findViewById(R.id.downloadSizeLabel);
            downloadSizeView.setText((cyanogenOTAUpdate.getSize() / 1048576) + " " + getString(R.string.megabyte));

            TextView updatedDataView = (TextView) rootView.findViewById(R.id.lastUpdatedLabel);
            DateTimeFormatter dateTimeFormatter = new DateTimeFormatter(getActivity().getApplicationContext(),this);
            String dateUpdated = dateTimeFormatter.formatDateTime(cyanogenOTAUpdate.getDateUpdated());
            updatedDataView.setText(dateUpdated);


            if(cyanogenOTAUpdate.getDownloadUrl() != null) {
                Button downloadButton = (Button)rootView.findViewById(R.id.downloadButton);
                downloadButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        download(cyanogenOTAUpdate.getDownloadUrl(),cyanogenOTAUpdate.getFileName());
                    }
                });
                downloadButton.setEnabled(true);
            }

        }

    }

    private void download(String downloadUrl, String downloadName) {
        DownloadManager downloadManager =  (DownloadManager) getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(downloadUrl);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setDescription(getActivity().getString(R.string.downloader_description)).setTitle(getString(R.string.downloader_description));
        request.setDestinationInExternalFilesDir(getActivity(), Environment.DIRECTORY_DOWNLOADS, downloadName);
        request.setVisibleInDownloadsUi(true);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        downloadManager.enqueue(request);
        Toast.makeText(getActivity(),getString(R.string.downloading_in_background),Toast.LENGTH_LONG).show();
    }

    private void generateCircleDiagram() {
        PieChart pieChartView = (PieChart)rootView.findViewById(R.id.rolloutPercentageDiagram);
        List<Entry> chartData = new ArrayList<>();
        int rolloutPercentage = cyanogenOTAUpdate.getRolloutPercentage();
        chartData.add(0, new Entry(rolloutPercentage, 0));
        if(rolloutPercentage < 100) {
            chartData.add(1, new Entry(100 - rolloutPercentage, 1));
        }
        ArrayList<String> xVals = new ArrayList<>();
        xVals.add(0, getString(R.string.updated));
        if(rolloutPercentage < 100) {
            xVals.add(1, getString(R.string.not_updated));
        }
        PieDataSet pieDataSet = new PieDataSet(chartData, "");
        pieDataSet.setColors(new int[]{getResources().getColor(R.color.lightblue), getResources().getColor(android.R.color.darker_gray)});


        PieData pieData = new PieData(xVals, pieDataSet);
        pieData.setDrawValues(false);
        pieData.setValueTextSize(12);
        pieChartView.setDrawSliceText(false);
        pieChartView.setCenterText(rolloutPercentage + "%");
        pieChartView.setDescription("");
        Legend legend = pieChartView.getLegend();
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setFormSize(10);
        legend.setTextSize(12);
        pieChartView.setUsePercentValues(true);
        pieChartView.setData(pieData);
        pieChartView.setMinimumWidth(pieChartView.getWidth());
        pieChartView.getLegend().setPosition(Legend.LegendPosition.RIGHT_OF_CHART_CENTER);
        pieChartView.setBackgroundColor(getResources().getColor(R.color.chart_background));
        pieChartView.invalidate();

        Button descriptionButton = (Button)rootView.findViewById(R.id.updateDescriptionButton);
        descriptionButton.setOnClickListener(this);


    }

    @Override
    public void onClick(View view) {
        Intent i = new Intent(getActivity(), UpdateDetailsActivity.class);
        i.putExtra("update-description", cyanogenOTAUpdate.getDescription());
        startActivity(i);
    }


    /**
     * Async task class to get json by making HTTP call
     * */
    private class GetUpdateInformation extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage(getString(R.string.fetching_update));
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
        protected Void doInBackground(Void... arg0) {
            // Creating service handler class instance
            ServiceHandler sh = new ServiceHandler();
            String jsonStr = null;
            // Making a request to the right url and getting response
                if (deviceType == DeviceType.BACON && updateType.equals("incremental")) {
                    String baconIncrementalUri = "https://fota.cyngn.com/api/v1/update/get_latest?model=bacon&type=INCREMENTAL";
                    jsonStr = sh.makeServiceCall(baconIncrementalUri, ServiceHandler.GET);
                }
                if (deviceType == DeviceType.BACON && updateType.equals("stable")) {
                    String baconStableUrl = "https://fota.cyngn.com/api/v1/update/get_latest?model=bacon&type=STABLE";
                    jsonStr = sh.makeServiceCall(baconStableUrl, ServiceHandler.GET);
                }
                if (deviceType == DeviceType.TOMATO && updateType.equals("incremental")) {
                    String tomatoIncrementalUri = "https://fota.cyngn.com/api/v1/update/get_latest?model=tomato&type=INCREMENTAL";
                    jsonStr = sh.makeServiceCall(tomatoIncrementalUri, ServiceHandler.GET);
                }
                if (deviceType == DeviceType.TOMATO && updateType.equals("stable")) {
                    String tomatoStableUri = "https://fota.cyngn.com/api/v1/update/get_latest?model=tomato&type=STABLE";
                    jsonStr = sh.makeServiceCall(tomatoStableUri, ServiceHandler.GET);
                }
                if (deviceType == DeviceType.N1 && updateType.equals("incremental")) {
                    String n1IncrementalUri = "https://fota.cyngn.com/api/v1/update/get_latest?model=n1&type=INCREMENTAL";
                    jsonStr = sh.makeServiceCall(n1IncrementalUri, ServiceHandler.GET);
                }
                if (deviceType == DeviceType.N1 && updateType.equals("stable")) {
                    String n1StableUri = "https://fota.cyngn.com/api/v1/update/get_latest?model=n1&type=STABLE";
                    jsonStr = sh.makeServiceCall(n1StableUri, ServiceHandler.GET);
                }

            if (jsonStr != null) {
                try {
                    showAllInterfaceElements();
                    JSONObject c = new JSONObject(jsonStr);


                        cyanogenOTAUpdate = new CyanogenOTAUpdate();
                        cyanogenOTAUpdate.setDateUpdated(c.getString("date_updated"));
                        cyanogenOTAUpdate.setIncremental(c.getString("incremental"));
                        cyanogenOTAUpdate.setRequiredIncremental(c.getBoolean("required_incremental"));
                        cyanogenOTAUpdate.setSize(c.getInt("size"));
                        cyanogenOTAUpdate.setBuildNumber(c.getString("build_number"));
                        cyanogenOTAUpdate.setIncrementalParent(c.getString("incremental_parent"));
                        cyanogenOTAUpdate.setDownloadUrl(c.getString("download_url"));
                        cyanogenOTAUpdate.setFileName(c.getString("filename"));
                        cyanogenOTAUpdate.setSha1Sum(c.getString("sha1sum"));
                        cyanogenOTAUpdate.setType(c.getString("type"));
                        cyanogenOTAUpdate.setDescription(c.getString("description"));
                        cyanogenOTAUpdate.setDateCreatedUnix(c.getString("date_created_unix"));
                        cyanogenOTAUpdate.setRolloutPercentage(c.getInt("rollout_percentage"));
                        cyanogenOTAUpdate.setKey(c.getString("key"));
                        cyanogenOTAUpdate.setPath(c.getString("path"));
                        cyanogenOTAUpdate.setName(c.getString("name"));
                        cyanogenOTAUpdate.setMd5Sum(c.getString("md5sum"));
                        cyanogenOTAUpdate.setPublished(c.getBoolean("published"));
                        cyanogenOTAUpdate.setDateCreated(c.getString("date_created"));
                        cyanogenOTAUpdate.setModel(c.getString("model"));
                        cyanogenOTAUpdate.setApiLevel(c.getInt("api_level"));

                    } catch (JSONException e) {
                    e.printStackTrace();
                }

            } else {
                if(progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                showNetworkError();

            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            displayUpdateInformation();
            if(progressDialog.isShowing()) {
                progressDialog.dismiss();
            }


        }



    }
    private void hideAllInterfaceElements() {
        rootView.findViewById(R.id.headerLabel).setVisibility(View.GONE);
        rootView.findViewById(R.id.lastUpdatedLabel).setVisibility(View.GONE);
        rootView.findViewById(R.id.rolloutPercentageDiagram).setVisibility(View.GONE);
        TextView textView = (TextView)rootView.findViewById(R.id.downloadSizeLabel);
        textView.setText(getString(R.string.app_requires_network));
        rootView.findViewById(R.id.downloadButton).setVisibility(View.GONE);
        rootView.findViewById(R.id.updateDescriptionButton).setVisibility(View.GONE);
        rootView.findViewById(R.id.buildNumberLabel).setVisibility(View.GONE);
        rootView.findViewById(R.id.updateInstallationInstructionsButton).setVisibility(View.GONE);
    }

    private void showAllInterfaceElements() {
        rootView.findViewById(R.id.headerLabel).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.lastUpdatedLabel).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.rolloutPercentageDiagram).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.downloadSizeLabel).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.downloadButton).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.updateDescriptionButton).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.buildNumberLabel).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.updateInstallationInstructionsButton).setVisibility(View.VISIBLE);

    }

    private void showNetworkError() {
        hideAllInterfaceElements();
        DialogFragment networkErrorFragment = new NetworkErrorFragment();
        networkErrorFragment.show(getFragmentManager(), "NetworkError");


    }


}