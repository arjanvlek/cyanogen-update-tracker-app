package com.arjanvlek.cyngnotainfo.cm.fragment;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.arjanvlek.cyngnotainfo.BuildConfig;
import com.arjanvlek.cyngnotainfo.R;
import com.arjanvlek.cyngnotainfo.cm.model.CyanogenModUpdateData;
import com.arjanvlek.cyngnotainfo.common.activity.MainActivity;
import com.arjanvlek.cyngnotainfo.common.fragment.AbstractUpdateInformationFragment;
import com.arjanvlek.cyngnotainfo.common.internal.ApplicationData;
import com.arjanvlek.cyngnotainfo.common.internal.Callback;
import com.arjanvlek.cyngnotainfo.common.internal.DateTimeFormatter;
import com.arjanvlek.cyngnotainfo.common.internal.SystemVersionProperties;
import com.arjanvlek.cyngnotainfo.common.internal.UpdateDescriptionParser;
import com.arjanvlek.cyngnotainfo.common.internal.UpdateDownloadListener;
import com.arjanvlek.cyngnotainfo.common.internal.UpdateDownloader;
import com.arjanvlek.cyngnotainfo.common.internal.asynctask.GetServerStatus;
import com.arjanvlek.cyngnotainfo.common.model.DownloadProgressData;
import com.arjanvlek.cyngnotainfo.common.model.ServerParameters;
import com.arjanvlek.cyngnotainfo.common.model.UpdateData;
import com.arjanvlek.cyngnotainfo.common.view.MessageDialog;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static android.app.DownloadManager.ERROR_CANNOT_RESUME;
import static android.app.DownloadManager.ERROR_DEVICE_NOT_FOUND;
import static android.app.DownloadManager.ERROR_FILE_ALREADY_EXISTS;
import static android.app.DownloadManager.ERROR_FILE_ERROR;
import static android.app.DownloadManager.ERROR_HTTP_DATA_ERROR;
import static android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE;
import static android.app.DownloadManager.ERROR_TOO_MANY_REDIRECTS;
import static android.app.DownloadManager.ERROR_UNHANDLED_HTTP_CODE;
import static android.app.DownloadManager.PAUSED_QUEUED_FOR_WIFI;
import static android.app.DownloadManager.PAUSED_UNKNOWN;
import static android.app.DownloadManager.PAUSED_WAITING_FOR_NETWORK;
import static android.app.DownloadManager.PAUSED_WAITING_TO_RETRY;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.RelativeLayout.ABOVE;
import static android.widget.RelativeLayout.BELOW;
import static com.arjanvlek.cyngnotainfo.common.internal.ApplicationData.LOCALE_DUTCH;
import static com.arjanvlek.cyngnotainfo.common.internal.SettingsManager.PROPERTY_DEVICE;
import static com.arjanvlek.cyngnotainfo.common.internal.SettingsManager.PROPERTY_DOWNLOAD_ID;
import static com.arjanvlek.cyngnotainfo.common.internal.SettingsManager.PROPERTY_OFFLINE_FILE_NAME;
import static com.arjanvlek.cyngnotainfo.common.internal.SettingsManager.PROPERTY_OFFLINE_UPDATE_DESCRIPTION;
import static com.arjanvlek.cyngnotainfo.common.internal.SettingsManager.PROPERTY_OFFLINE_UPDATE_DOWNLOAD_SIZE;
import static com.arjanvlek.cyngnotainfo.common.internal.SettingsManager.PROPERTY_OFFLINE_UPDATE_INFORMATION_AVAILABLE;
import static com.arjanvlek.cyngnotainfo.common.internal.SettingsManager.PROPERTY_OFFLINE_UPDATE_NAME;
import static com.arjanvlek.cyngnotainfo.common.internal.SettingsManager.PROPERTY_UPDATE_CHECKED_DATE;
import static com.arjanvlek.cyngnotainfo.common.internal.UpdateDownloader.NOT_SET;
import static com.arjanvlek.cyngnotainfo.common.model.ServerParameters.Status.OK;
import static com.arjanvlek.cyngnotainfo.common.model.ServerParameters.Status.UNREACHABLE;

public class CMUpdateInformationFragment extends AbstractUpdateInformationFragment {




    private UpdateDownloader updateDownloader;

    private DateTime refreshedDate;
    private boolean isFetched;
    private String deviceName;

    public static final int NOTIFICATION_ID = 1;




    /*
      -------------- ANDROID ACTIVITY LIFECYCLE METHODS -------------------
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // SettingsManager is created in the parent class.
        if(settingsManager != null) {
            deviceName = settingsManager.getPreference(PROPERTY_DEVICE);
        }
        if(getActivity() != null) {
            context = getActivity().getApplicationContext();
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        this.rootView = (RelativeLayout) inflater.inflate(R.layout.fragment_cmupdateinformation, container, false);
        return this.rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        if(isAdded()) {
            initLayout();
            initData();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (adView != null) {
            adView.pause();
        }

    }

    @Override
    public void onResume() {
        super.onResume();

        if (adView != null) {
            adView.resume();
        }
        if (refreshedDate != null && isFetched && settingsManager.checkIfSettingsAreValid() && isAdded()) {
            if (refreshedDate.plusMinutes(5).isBefore(DateTime.now())) {
                if (networkConnectionManager.checkNetworkConnection()) {
                    getServerData();
                    refreshedDate = DateTime.now();
                } else if (settingsManager.checkIfCacheIsAvailable()) {
                    getServerData();
                    displayUpdateInformation(buildOfflineUpdateData(), false, false);
                    refreshedDate = DateTime.now();
                } else {
                    showNetworkError();
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (adView != null) {
            adView.destroy();
        }
    }

    /*
      -------------- INITIALIZATION / DATA FETCHING METHODS -------------------
     */

    /**
     * Initializes the layout. Sets refresh listeners for pull-down to refresh and applies the right colors for pull-down to refresh screens.
     */
    private void initLayout() {
        if (updateInformationRefreshLayout == null && rootView != null && isAdded()) {
            updateInformationRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.updateInformationRefreshLayout);
            systemIsUpToDateRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.updateInformationSystemIsUpToDateRefreshLayout);
            if(updateInformationRefreshLayout != null) {
                updateInformationRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        if (networkConnectionManager.checkNetworkConnection()) {
                            getServerData();
                        } else if (settingsManager.checkIfCacheIsAvailable()) {
                            getServerData();
                            displayUpdateInformation(buildOfflineUpdateData(), false, false);
                        } else {
                            showNetworkError();
                        }
                    }
                });

                // Todo this part is custom for CM!!!
                Button installUpdateButton = (Button) updateInformationRefreshLayout.findViewById(R.id.updateInstallButton);
                installUpdateButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            Process p = Runtime.getRuntime().exec("su");
                        } catch (IOException e) {
                            Toast.makeText(getContext(), "Root test failed", Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        }
                    }
                });

                updateInformationRefreshLayout.setColorSchemeResources(R.color.lightBlue, R.color.holo_orange_light, R.color.holo_red_light);
            }
            if(systemIsUpToDateRefreshLayout != null) {
                systemIsUpToDateRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        if (networkConnectionManager.checkNetworkConnection()) {
                            getServerData();
                        } else if (settingsManager.checkIfCacheIsAvailable()) {
                            getServerData();
                            displayUpdateInformation(buildOfflineUpdateData(), false, false);
                        } else {
                            showNetworkError();
                        }
                    }
                });
                systemIsUpToDateRefreshLayout.setColorSchemeResources(R.color.lightBlue, R.color.holo_orange_light, R.color.holo_red_light);
            }

        }
    }

    /**
     * Checks if there is a network connection. If that's the case, start the connections to the backend
     * If not, build an offline {@link CyanogenModUpdateData} and display it without trying to reach the server at all
     * If an offline {@link CyanogenModUpdateData} is not available, display a "No network connection error message".
     */
    private void initData() {
        if (!isFetched && settingsManager.checkIfSettingsAreValid()) {
            if (networkConnectionManager.checkNetworkConnection()) {
                getServerData();
                showAds();
                refreshedDate = DateTime.now();
                isFetched = true;
            } else if (settingsManager.checkIfCacheIsAvailable()) {
                getServerData();
                cyanogenModUpdateData = (CyanogenModUpdateData)buildOfflineUpdateData();
                displayUpdateInformation(cyanogenModUpdateData, false, false);
                initDownloadManager();
                hideAds();
                refreshedDate = DateTime.now();
                isFetched = true;
            } else {
                hideAds();
                showNetworkError();
            }
        }
    }

    /**
     * Fetches all server data. This includes update information, server messages and server status checks
     */
    private void getServerData() {
        this.inAppMessageBarData = new HashMap<>();
        new GetCMUpdateInformation().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        new GetServerStatus(getApplicationData(), new Callback() {
            @Override
            public void onActionPerformed(Object... result) {
                displayServerStatus((ServerParameters)result[0]);
            }
        }).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        if(settingsManager.showNewsMessages()) {
            new GetServerMessages().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        }
        checkNoConnectionBar();
    }

    /**
     * Displays the update information from a {@link CyanogenModUpdateData} with update information.
     * @param online Whether or not the device has an active network connection
     * @param displayInfoWhenUpToDate Flag set to show update information anyway, even if the system is up to date.
     */
    @Override
    public void displayUpdateInformation(final UpdateData updateData, final boolean online, boolean displayInfoWhenUpToDate) {
        // Abort if no update data is found or if the fragment is not attached to its activity to prevent crashes.
        if(!isAdded() || rootView == null) {
            return;
        }

        View loadingScreen = rootView.findViewById(R.id.updateInformationLoadingScreen);
        if(loadingScreen != null) {
            loadingScreen.setVisibility(GONE);
        }

        if(updateData == null) {
            return;
        }

        CyanogenModUpdateData cyanogenModUpdateData = (CyanogenModUpdateData)updateData;

        if(((cyanogenModUpdateData.isSystemUpToDate(null)) && !displayInfoWhenUpToDate) || !cyanogenModUpdateData.isUpdateInformationAvailable()) {
            displayUpdateInformationWhenUpToDate(cyanogenModUpdateData, online);
        } else {
            displayUpdateInformationWhenNotUpToDate(cyanogenModUpdateData, online, displayInfoWhenUpToDate);
        }

        if(online) {
            // Save update data for offline viewing
            settingsManager.savePreference(PROPERTY_OFFLINE_UPDATE_NAME, cyanogenModUpdateData.getVersionNumber());
            settingsManager.savePreference(PROPERTY_OFFLINE_UPDATE_DESCRIPTION, cyanogenModUpdateData.getDescription()); // TODO implement
            settingsManager.savePreference(PROPERTY_OFFLINE_FILE_NAME, cyanogenModUpdateData.getFilename());
            settingsManager.saveBooleanPreference(PROPERTY_OFFLINE_UPDATE_INFORMATION_AVAILABLE, cyanogenModUpdateData.isUpdateInformationAvailable());
            settingsManager.savePreference(PROPERTY_UPDATE_CHECKED_DATE, LocalDateTime.now().toString());
        }

        // Hide the refreshing icon if it is present.
        hideRefreshIcons();
    }

    private void displayUpdateInformationWhenUpToDate(final CyanogenModUpdateData cyanogenModUpdateData, boolean online) {
        // Show "System is up to date" view.
        rootView.findViewById(R.id.updateInformationRefreshLayout).setVisibility(GONE);
        rootView.findViewById(R.id.updateInformationSystemIsUpToDateRefreshLayout).setVisibility(VISIBLE);

        // Set the current CyanogenMod version if available.
        String cyanogenModVersion = ((ApplicationData)getActivity().getApplication()).CYANOGEN_VERSION;
        TextView versionNumberView = (TextView) rootView.findViewById(R.id.updateInformationSystemIsUpToDateVersionTextView);
        if(!cyanogenModVersion.equals(SystemVersionProperties.NOT_SET)) {
            versionNumberView.setVisibility(VISIBLE);
            versionNumberView.setText(String.format(getString(R.string.update_information_cyanogen_mod_version), cyanogenModVersion));
        } else {
            versionNumberView.setVisibility(GONE);
        }

        // Set "No Update Information Is Available" button if needed.
        Button updateInformationButton = (Button) rootView.findViewById(R.id.updateInformationSystemIsUpToDateStatisticsButton);
        if(!cyanogenModUpdateData.isUpdateInformationAvailable()) {
            updateInformationButton.setText(getString(R.string.update_information_no_update_data_available));
            updateInformationButton.setClickable(false);
        } else {
            updateInformationButton.setText(getString(R.string.update_information_view_update_information));
            updateInformationButton.setClickable(true);
            updateInformationButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    displayUpdateInformation(cyanogenModUpdateData, true, true);
                }
            });
        }

        // Save last time checked if online.
        if(online) {
            settingsManager.savePreference(PROPERTY_UPDATE_CHECKED_DATE, LocalDateTime.now().toString());
        }

        // Show last time checked.
        TextView dateCheckedView = (TextView) rootView.findViewById(R.id.updateInformationSystemIsUpToDateDateTextView);
        DateTimeFormatter dateTimeFormatter = new DateTimeFormatter(context, this);
        dateCheckedView.setText(String.format(getString(R.string.update_information_last_checked_on), dateTimeFormatter.formatDateTime(settingsManager.getPreference(PROPERTY_UPDATE_CHECKED_DATE))));

    }

    private void displayUpdateInformationWhenNotUpToDate(final CyanogenModUpdateData cyanogenModUpdateData, boolean online, boolean displayInfoWhenUpToDate) {
        // Show "System update available" view.
        rootView.findViewById(R.id.updateInformationRefreshLayout).setVisibility(VISIBLE);
        rootView.findViewById(R.id.updateInformationSystemIsUpToDateRefreshLayout).setVisibility(GONE);

        // Display available update version number.
        TextView buildNumberView = (TextView) rootView.findViewById(R.id.updateInformationBuildNumberView);
        if (cyanogenModUpdateData.getVersionNumber() != null && !cyanogenModUpdateData.getVersionNumber().equals("null")) {
            buildNumberView.setText(cyanogenModUpdateData.getVersionNumber());
        } else {
            buildNumberView.setText(String.format(getString(R.string.update_information_unknown_update_name), deviceName));
        }

        // Display download size. TODO check what to do with this.
        //TextView downloadSizeView = (TextView) rootView.findViewById(R.id.updateInformationDownloadSizeView);
        //downloadSizeView.setText(String.format(getString(R.string.download_size_megabyte), cyanogenModUpdateData.getSize()));

        // Display update description.
        String description = cyanogenModUpdateData.getDescription();
        TextView descriptionView = (TextView) rootView.findViewById(R.id.updateDescriptionView);
        descriptionView.setMovementMethod(LinkMovementMethod.getInstance());
        descriptionView.setText(description != null && !description.isEmpty() && !description.equals("null") ? UpdateDescriptionParser.parse(description) : getString(R.string.update_information_description_not_available));

        // Display update file name.
        TextView fileNameView = (TextView) rootView.findViewById(R.id.updateFileNameView);
        fileNameView.setText(String.format(getString(R.string.update_information_file_name), cyanogenModUpdateData.getFilename()));

        final Button downloadButton = (Button) rootView.findViewById(R.id.updateInformationDownloadButton);

        // Activate download button, or make it gray when the device is offline or if the update is not downloadable.
        if (online && cyanogenModUpdateData.getDownloadUrl() != null) {
            downloadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDownloadButtonClick(downloadButton);
                }
            });
            downloadButton.setEnabled(true);
            downloadButton.setTextColor(ContextCompat.getColor(context, R.color.lightBlue));
        } else {
            downloadButton.setEnabled(false);
            downloadButton.setTextColor(ContextCompat.getColor(context, R.color.dark_grey));
        }

        // Format top title based on system version installed.
        TextView headerLabel = (TextView) rootView.findViewById(R.id.headerLabel);
        Button updateInstallationButton = (Button) rootView.findViewById(R.id.updateInstallButton);
        View downloadSizeTable = rootView.findViewById(R.id.buttonTable);
        View downloadSizeImage = rootView.findViewById(R.id.downloadSizeImage);


        if(displayInfoWhenUpToDate) {
            headerLabel.setText(getString(R.string.update_information_installed_update));
            downloadButton.setVisibility(GONE);
            updateInstallationButton.setVisibility(GONE);
            fileNameView.setVisibility(GONE);
            downloadSizeTable.setVisibility(GONE);
            downloadSizeImage.setVisibility(GONE);
            //downloadSizeView.setVisibility(GONE);
        } else {
            if(cyanogenModUpdateData.isSystemUpToDate(null)) {
                headerLabel.setText(getString(R.string.update_information_installed_update));
            } else {
                headerLabel.setText(getString(R.string.update_information_latest_available_update));
            }
            downloadButton.setVisibility(VISIBLE);
            updateInstallationButton.setVisibility(VISIBLE);
            fileNameView.setVisibility(VISIBLE);
            downloadSizeTable.setVisibility(VISIBLE);
            downloadSizeImage.setVisibility(VISIBLE);
            //downloadSizeView.setVisibility(VISIBLE);
        }
    }

    /**
     * Builds a {@link CyanogenModUpdateData} class based on the data that was stored when the device was online.
     * @return CyanogenModUpdateData with data from the latest succesful fetch.
     */
    @Override
    protected UpdateData buildOfflineUpdateData() { // TODO implement
        CyanogenModUpdateData cyanogenModUpdateData = new CyanogenModUpdateData();

        //cyanogenModUpdateData.setResult(results);
        return cyanogenModUpdateData;
    }

    /*
      -------------- USER INTERFACE ELEMENT METHODS -------------------
     */

    private Button getDownloadButton() {
        return (Button) rootView.findViewById(R.id.updateInformationDownloadButton);
    }

    private ImageButton getDownloadCancelButton() {
        return (ImageButton) rootView.findViewById(R.id.updateInformationDownloadCancelButton);
    }


    private TextView getDownloadStatusText() {
        return (TextView) rootView.findViewById(R.id.updateInformationDownloadDetailsView);
    }


    private ProgressBar getDownloadProgressBar() {
        return (ProgressBar) rootView.findViewById(R.id.updateInformationDownloadProgressBar);
    }

    private void showDownloadProgressBar() {
        View downloadProgressBar = rootView.findViewById(R.id.downloadProgressTable);
        if(downloadProgressBar != null) {
            downloadProgressBar.setVisibility(VISIBLE);
        }
    }

    private void hideDownloadProgressBar() {
        rootView.findViewById(R.id.downloadProgressTable).setVisibility(GONE);

    }

    private void hideRefreshIcons() {
        if (updateInformationRefreshLayout != null) {
            if (updateInformationRefreshLayout.isRefreshing()) {
                updateInformationRefreshLayout.setRefreshing(false);
            }
        }
        if (systemIsUpToDateRefreshLayout != null) {
            if (systemIsUpToDateRefreshLayout.isRefreshing()) {
                systemIsUpToDateRefreshLayout.setRefreshing(false);
            }
        }
    }


    /*
      -------------- GOOGLE ADS METHODS -------------------
     */


    private void showAds() {
        if(rootView != null) {
            adView = (AdView) rootView.findViewById(R.id.updateInformationAdView);
        }
        if (adView != null) {
            AdRequest adRequest = new AdRequest.Builder()
                    .addTestDevice(ADS_TEST_DEVICE_ID_OWN_DEVICE)
                    .addTestDevice(ADS_TEST_DEVICE_ID_TEST_DEVICE)
                    .addTestDevice(ADS_TEST_DEVICE_ID_EMULATOR_1)
                    .addTestDevice(ADS_TEST_DEVICE_ID_EMULATOR_2)
                    .addTestDevice(ADS_TEST_DEVICE_ID_EMULATOR_3)
                    .build();

            adView.loadAd(adRequest);
        }
    }

    private void hideAds() {
        if (adView != null) {
            adView.destroy();
        }
    }

    /*
      -------------- UPDATE DOWNLOAD METHODS -------------------
     */

    /**
     * Creates a {@link UpdateDownloader} and applies an {@link UpdateDownloadListener} to it to allow displaying update download progress and error messages.
     */
    @Override
    protected void initDownloadManager() {
        if(isAdded() && updateDownloader == null) {
            updateDownloader = new UpdateDownloader(getActivity())
                    .setUpdateDownloadListener(new UpdateDownloadListener() {
                        @Override
                        public void onDownloadManagerInit() {
                            getDownloadCancelButton().setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    updateDownloader.cancelDownload();
                                }
                            });
                        }

                        @Override
                        public void onDownloadStarted(long downloadID) {
                            if(isAdded()) {
                                getDownloadButton().setText(getString(R.string.downloading));
                                getDownloadButton().setClickable(false);

                                showDownloadProgressBar();
                                getDownloadProgressBar().setIndeterminate(false);
                            }
                        }

                        @Override
                        public void onDownloadPending() {
                            if(isAdded()) {
                                showDownloadProgressBar();
                                getDownloadButton().setText(getString(R.string.downloading));
                                getDownloadButton().setClickable(false);
                                TextView downloadStatusText = getDownloadStatusText();
                                downloadStatusText.setText(getString(R.string.download_pending));
                            }
                        }

                        @Override
                        public void onDownloadProgressUpdate(DownloadProgressData downloadProgressData) {
                            if(isAdded()) {
                                showDownloadProgressBar();
                                getDownloadButton().setText(getString(R.string.downloading));
                                getDownloadButton().setClickable(false);
                                getDownloadProgressBar().setIndeterminate(false);
                                getDownloadProgressBar().setProgress(downloadProgressData.getProgress());

                                if(downloadProgressData.getDownloadSpeed() == NOT_SET || downloadProgressData.getTimeRemaining() == null) {
                                    getDownloadStatusText().setText(getString(R.string.download_progress_text_unknown_time_remaining, downloadProgressData.getProgress()));
                                } else {
                                    DownloadProgressData.TimeRemaining timeRemaining = downloadProgressData.getTimeRemaining();

                                    if(timeRemaining.getHoursRemaining() > 1) {
                                        getDownloadStatusText().setText(getString(R.string.download_progress_text_hours_remaining, downloadProgressData.getProgress(), timeRemaining.getHoursRemaining()));
                                    } else if(timeRemaining.getHoursRemaining() == 1) {
                                        getDownloadStatusText().setText(getString(R.string.download_progress_text_one_hour_remaining, downloadProgressData.getProgress()));
                                    } else if(timeRemaining.getHoursRemaining() == 0 && timeRemaining.getMinutesRemaining() > 1) {
                                        getDownloadStatusText().setText(getString(R.string.download_progress_text_minutes_remaining, downloadProgressData.getProgress(), timeRemaining.getMinutesRemaining()));
                                    } else if(timeRemaining.getHoursRemaining() == 0 && timeRemaining.getMinutesRemaining() == 1) {
                                        getDownloadStatusText().setText(getString(R.string.download_progress_text_one_minute_remaining, downloadProgressData.getProgress()));
                                    } else if(timeRemaining.getHoursRemaining() == 0 && timeRemaining.getMinutesRemaining() == 0 && timeRemaining.getSecondsRemaining() > 10) {
                                        getDownloadStatusText().setText(getString(R.string.download_progress_text_less_than_a_minute_remaining, downloadProgressData.getProgress()));
                                    } else if(timeRemaining.getHoursRemaining() == 0 && timeRemaining.getMinutesRemaining() == 0 && timeRemaining.getSecondsRemaining() <= 10) {
                                        getDownloadStatusText().setText(getString(R.string.download_progress_text_seconds_remaining, downloadProgressData.getProgress()));
                                    }
                                }
                            }
                        }

                        @Override
                        public void onDownloadPaused(int statusCode) {
                            if(isAdded()) {
                                showDownloadProgressBar();
                                getDownloadButton().setText(getString(R.string.downloading));
                                getDownloadButton().setClickable(false);

                                TextView downloadStatusText = getDownloadStatusText();
                                switch (statusCode) {
                                    case PAUSED_QUEUED_FOR_WIFI:
                                        downloadStatusText.setText(getString(R.string.download_waiting_for_wifi));
                                        break;
                                    case PAUSED_WAITING_FOR_NETWORK:
                                        downloadStatusText.setText(getString(R.string.download_waiting_for_network));
                                        break;
                                    case PAUSED_WAITING_TO_RETRY:
                                        downloadStatusText.setText(getString(R.string.download_will_retry_soon));
                                        break;
                                    case PAUSED_UNKNOWN:
                                        downloadStatusText.setText(getString(R.string.download_paused_unknown));
                                        break;
                                }
                            }
                        }

                        @Override
                        public void onDownloadComplete() {
                            if(isAdded()) {
                                Toast.makeText(getApplicationData(), getString(R.string.download_verifying_start), Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onDownloadCancelled() {
                            if(isAdded()) {
                                getDownloadButton().setClickable(true);
                                getDownloadButton().setText(getString(R.string.download));
                                hideDownloadProgressBar();
                            }
                        }

                        @Override
                        public void onDownloadError(int statusCode) {
                            // Treat any HTTP status code exception (lower than 1000) as a network error.
                            // Handle any other errors according to the error message.
                            if(isAdded()) {
                                if (statusCode < 1000) {
                                    showDownloadError(getString(R.string.download_error), getString(R.string.download_error_network), getString(R.string.download_error_close), getString(R.string.download_error_retry), true);
                                } else {
                                    switch (statusCode) {
                                        case ERROR_UNHANDLED_HTTP_CODE:
                                        case ERROR_HTTP_DATA_ERROR:
                                        case ERROR_TOO_MANY_REDIRECTS:
                                            showDownloadError(getString(R.string.download_error), getString(R.string.download_error_network), getString(R.string.download_error_close), getString(R.string.download_error_retry), true);
                                            break;
                                        case ERROR_FILE_ERROR:
                                            updateDownloader.makeDownloadDirectory();
                                            showDownloadError(getString(R.string.download_error), getString(R.string.download_error_directory), getString(R.string.download_error_close), null, true);
                                            break;
                                        case ERROR_INSUFFICIENT_SPACE:
                                            showDownloadError(getString(R.string.download_error), getString(R.string.download_error_storage), getString(R.string.download_error_close), getString(R.string.download_error_retry), true);
                                            break;
                                        case ERROR_DEVICE_NOT_FOUND:
                                            showDownloadError(getString(R.string.download_error), getString(R.string.download_error_sd_card), getString(R.string.download_error_close), getString(R.string.download_error_retry), true);
                                            break;
                                        case ERROR_CANNOT_RESUME:
                                            updateDownloader.cancelDownload();
                                            if (networkConnectionManager.checkNetworkConnection() && cyanogenModUpdateData != null && cyanogenModUpdateData.getDownloadUrl() != null) {
                                                updateDownloader.downloadUpdate(cyanogenOSUpdateData);
                                            }
                                            break;
                                        case ERROR_FILE_ALREADY_EXISTS:
                                            Toast.makeText(getApplicationData(), getString(R.string.download_already_downloaded), Toast.LENGTH_LONG).show();
                                            onUpdateDownloaded(true, false);
                                    }
                                }

                                // Make sure the failed download file gets deleted before the user tries to download it again.
                                updateDownloader.cancelDownload();
                                hideDownloadProgressBar();
                                getDownloadButton().setText(getString(R.string.download));
                                onUpdateDownloaded(false, true);
                            }
                        }

                        @Override
                        public void onVerifyStarted() {
                            if(isAdded()) {
                                showDownloadProgressBar();
                                getDownloadProgressBar().setIndeterminate(true);
                                showVerifyingNotification(false);
                                getDownloadButton().setText(getString(R.string.download_verifying));
                                getDownloadStatusText().setText(getString(R.string.download_progress_text_verifying));
                            }
                        }

                        @Override
                        public void onVerifyError() {
                            if(isAdded()) {
                                showDownloadError(getString(R.string.download_error), getString(R.string.download_error_corrupt), getString(R.string.download_error_close), getString(R.string.download_error_retry), true);
                                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath() + File.separator + cyanogenModUpdateData.getFilename());
                                try {
                                    //noinspection ResultOfMethodCallIgnored
                                    file.delete();
                                } catch (Exception ignored) {

                                }
                                showVerifyingNotification(true);
                            }
                        }

                        @Override
                        public void onVerifyComplete() {
                            if(isAdded()) {
                                hideDownloadProgressBar();
                                hideVerifyingNotification();
                                onUpdateDownloaded(true, false);
                                Toast.makeText(getApplicationData(), getString(R.string.download_complete), Toast.LENGTH_LONG).show();
                            }
                        }
                    });
            updateDownloader.checkDownloadProgress(cyanogenOSUpdateData);
        }
    }

    /**
     * Shows an {@link MessageDialog} with the occured download error.
     * @param title Title of the error message
     * @param message Contents of the error message
     * @param positiveButtonText Rightmost button text
     * @param negativeButtonText Leftmost button text
     * @param closable If the dialog may be closed, this is set to true. If not, this is set to false. In that case, the application will be killed on exiting the dialog.
     */
    private void showDownloadError(String title, String message, String positiveButtonText, String negativeButtonText, boolean closable) {
        MessageDialog errorDialog = new MessageDialog()
                .setTitle(title)
                .setMessage(message)
                .setPositiveButtonText(positiveButtonText)
                .setNegativeButtonText(negativeButtonText)
                .setClosable(closable)
                .setDialogListener(new MessageDialog.DialogListener() {
                    @Override
                    public void onDialogPositiveButtonClick(DialogFragment dialogFragment) {

                    }

                    @Override
                    public void onDialogNegativeButtonClick(DialogFragment dialogFragment) {
                        updateDownloader.cancelDownload();
                        updateDownloader.downloadUpdate(cyanogenOSUpdateData);
                    }
                });
        errorDialog.setTargetFragment(this, 0);
        FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
        transaction.add(errorDialog, "DownloadError");
        transaction.commitAllowingStateLoss();
    }

    /**
     * Common actions for changing download button parameters and deleting incomplete download files.
     * @param updateIsDownloaded Whether or not the update is successfully downloaded.
     * @param fileMayBeDeleted Whether or not the update file may be deleted.
     */
    private void onUpdateDownloaded(boolean updateIsDownloaded, boolean fileMayBeDeleted) {
        final Button downloadButton = getDownloadButton();

        if(updateIsDownloaded && isAdded()) {
            downloadButton.setEnabled(true);
            downloadButton.setTextColor(ContextCompat.getColor(context, R.color.lightBlue));
            downloadButton.setClickable(true);
            downloadButton.setText(getString(R.string.downloaded));
            downloadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onDownloadedButtonClick();
                }
            });
        } else {
            if (networkConnectionManager != null && networkConnectionManager.checkNetworkConnection() && cyanogenModUpdateData != null && cyanogenModUpdateData.getDownloadUrl() != null && isAdded()) {
                if(updateDownloader != null) {
                    updateDownloader.checkDownloadProgress(cyanogenOSUpdateData);
                } else {
                    initDownloadManager();
                }
                downloadButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onDownloadButtonClick(downloadButton);
                    }
                });
                downloadButton.setEnabled(true);
                downloadButton.setTextColor(ContextCompat.getColor(context, R.color.lightBlue));

                if(fileMayBeDeleted) {
                    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath() + File.separator + cyanogenModUpdateData.getFilename());
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            } else {
                if(isAdded()) {
                    downloadButton.setEnabled(false);
                    downloadButton.setTextColor(ContextCompat.getColor(context, R.color.dark_grey));
                }
            }
        }
    }

    /**
     * Download button click listener. Performs these actions when the button is clicked.
     * @param downloadButton Button to perform actions on.
     */
    private void onDownloadButtonClick(Button downloadButton) {
        MainActivity mainActivity = (MainActivity) getActivity();
        if(mainActivity != null) {
            if(mainActivity.hasDownloadPermissions()) {
                if(updateDownloader != null) {
                    updateDownloader.downloadUpdate(cyanogenOSUpdateData);
                    downloadButton.setText(getString(R.string.downloading));
                    downloadButton.setClickable(false);
                }
            } else {
                Callback callback = new Callback() {
                    @Override
                    public void onActionPerformed(Object... result) {
                        if((int)result[0] == PackageManager.PERMISSION_GRANTED && updateDownloader != null && cyanogenModUpdateData != null) {
                            updateDownloader.downloadUpdate(cyanogenOSUpdateData);
                        }
                    }
                };
                mainActivity.requestDownloadPermissions(callback);
            }
        }
    }

    /**
     * Allows an already downloaded update file to be deleted to save storage space.
     */
    private void onDownloadedButtonClick() {
        MessageDialog dialog = new MessageDialog()
                .setTitle(getString(R.string.delete_message_title))
                .setMessage(getString(R.string.delete_message_contents))
                .setClosable(true)
                .setPositiveButtonText(getString(R.string.download_error_close))
                .setNegativeButtonText(getString(R.string.delete_message_delete_button))
                .setDialogListener(new MessageDialog.DialogListener() {
                    @Override
                    public void onDialogPositiveButtonClick(DialogFragment dialogFragment) {

                    }

                    @Override
                    public void onDialogNegativeButtonClick(DialogFragment dialogFragment) {
                        if(cyanogenModUpdateData != null) {
                            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath() + File.separator + cyanogenModUpdateData.getFilename());
                            if(file.exists()) {
                                if(file.delete()) {
                                    getDownloadButton().setText(getString(R.string.download));
                                    checkIfUpdateIsAlreadyDownloaded(cyanogenModUpdateData);
                                }
                            }
                        }
                    }
                });
        dialog.setTargetFragment(this, 0);
        FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
        transaction.add(dialog, "DeleteDownload");
        transaction.commitAllowingStateLoss();
    }

    /**
     * Checks if an update file is already downloaded.
     * @param updateData Cyanogen Update data containing the file name of the update.
     */
    @Override
    public void checkIfUpdateIsAlreadyDownloaded(UpdateData updateData) {
        if(updateData != null) {
            CyanogenModUpdateData cyanogenModUpdateData = (CyanogenModUpdateData)updateData;
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath() + File.separator + cyanogenModUpdateData.getFilename());
            onUpdateDownloaded(file.exists() && !settingsManager.containsPreference(PROPERTY_DOWNLOAD_ID), false);
        }
    }


    /*
      -------------- UPDATE VERIFICATION METHODS -------------------
     */


    /**
     * Shows a notification that the downloaded update file is being verified on MD5 sums.
     * @param error If an error occurred during verification, display an error text in the notification.
     */
    private void showVerifyingNotification(boolean error) {
        NotificationCompat.Builder builder;
        try {
            builder = new NotificationCompat.Builder(getActivity())
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOngoing(true)
                    .setProgress(100, 50, true);

            if(error) {
                builder.setContentTitle(getString(R.string.download_verifying_error));
            } else {
                builder.setContentTitle(getString(R.string.download_verifying));
            }

            if (Build.VERSION.SDK_INT >= 21) {
                builder.setCategory(Notification.CATEGORY_PROGRESS);
            }
            NotificationManager manager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch(Exception e) {
            try {
                NotificationManager manager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
                manager.cancel(NOTIFICATION_ID);
            } catch(Exception e1) {
                try {
                    // If cancelling the notification fails fails (and yes, it happens!), then I assume either that the user's device has a (corrupt) custom firmware or that something is REALLY going wrong.
                    // We try it once more but now with the Application Context instead of the Activity.
                    NotificationManager manager = (NotificationManager) getApplicationData().getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.cancel(NOTIFICATION_ID);
                } catch (Exception ignored) {
                    // If the last attempt has also failed, well then there's no hope.
                    // We leave everything as is, but the user will likely be stuck with a verifying notification that stays until a reboot.
                }
            }
        }
    }

    /**
     * Hides the verifying notification. Used when verification has succeeded.
     */
    private void hideVerifyingNotification() {
        NotificationManager manager = (NotificationManager) getApplicationData().getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID);
    }
}