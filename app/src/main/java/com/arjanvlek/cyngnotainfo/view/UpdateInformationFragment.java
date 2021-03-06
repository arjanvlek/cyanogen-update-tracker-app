package com.arjanvlek.cyngnotainfo.view;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
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

import com.arjanvlek.cyngnotainfo.ApplicationContext;
import com.arjanvlek.cyngnotainfo.BuildConfig;
import com.arjanvlek.cyngnotainfo.Model.CyanogenOTAUpdate;
import com.arjanvlek.cyngnotainfo.Model.DownloadProgressData;
import com.arjanvlek.cyngnotainfo.Model.ServerMessage;
import com.arjanvlek.cyngnotainfo.Model.ServerStatus;
import com.arjanvlek.cyngnotainfo.Model.SystemVersionProperties;
import com.arjanvlek.cyngnotainfo.R;
import com.arjanvlek.cyngnotainfo.Support.Callback;
import com.arjanvlek.cyngnotainfo.Support.DateTimeFormatter;
import com.arjanvlek.cyngnotainfo.Support.UpdateDescriptionParser;
import com.arjanvlek.cyngnotainfo.Support.UpdateDownloadListener;
import com.arjanvlek.cyngnotainfo.Support.UpdateDownloader;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import static android.widget.RelativeLayout.BELOW;
import static com.arjanvlek.cyngnotainfo.ApplicationContext.LOCALE_DUTCH;
import static com.arjanvlek.cyngnotainfo.ApplicationContext.NO_CYANOGEN_OS;
import static com.arjanvlek.cyngnotainfo.Model.ServerStatus.Status.OK;
import static com.arjanvlek.cyngnotainfo.Model.ServerStatus.Status.UNREACHABLE;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_DEVICE;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_DOWNLOAD_ID;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_OFFLINE_FILE_NAME;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_OFFLINE_UPDATE_DESCRIPTION;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_OFFLINE_UPDATE_DOWNLOAD_SIZE;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_OFFLINE_UPDATE_INFORMATION_AVAILABLE;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_OFFLINE_UPDATE_NAME;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_UPDATE_CHECKED_DATE;
import static com.arjanvlek.cyngnotainfo.Support.UpdateDownloader.NOT_SET;
import static com.arjanvlek.cyngnotainfo.view.InstallGuideActivity.INTENT_SHOW_DOWNLOAD_PAGE;

public class UpdateInformationFragment extends AbstractUpdateInformationFragment {


    private SwipeRefreshLayout updateInformationRefreshLayout;
    private SwipeRefreshLayout systemIsUpToDateRefreshLayout;
    private RelativeLayout rootView;

    private Context context;
    private UpdateDownloader updateDownloader;

    private DateTime refreshedDate;
    private boolean isFetched;
    private String deviceName;

    public static final int NOTIFICATION_ID = 1;
    public static final int DOWNLOAD_COMPLETE_NOTIFICATION_ID = 1000000000;

    // In app message bar collections and identifiers.
    private static final String KEY_APP_UPDATE_BARS = "app_update_bars";
    private static final String KEY_SERVER_ERROR_BARS = "server_error_bars";
    private static final String KEY_NO_NETWORK_CONNECTION_BARS = "no_network_connection_bars";
    private static final String KEY_SERVER_MESSAGE_BARS = "server_message_bars";
    private static final String KEY_HAS_DOWNLOAD_ERROR = "has_download_error";
    private static final String KEY_DOWNLOAD_ERROR_MESSAGE = "download_error_message";

    private Map<String, List<Object>> inAppMessageBarData = new HashMap<>();
    private List<ServerMessageBar> inAppMessageBars = new ArrayList<>();

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
            // If the activity is started with a download error (when clicked on a "download failed" notification), show it to the user.
            if(getActivity().getIntent() != null && getActivity().getIntent().getBooleanExtra(KEY_HAS_DOWNLOAD_ERROR, false)) {
                Intent i = getActivity().getIntent();
                showDownloadError(i.getStringExtra(KEY_DOWNLOAD_ERROR_MESSAGE));
            }
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        this.rootView = (RelativeLayout) inflater.inflate(R.layout.fragment_updateinformation, container, false);
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
    public void onResume() {
        super.onResume();

        if (refreshedDate != null && isFetched && settingsManager.checkIfSettingsAreValid() && isAdded()) {
            if (refreshedDate.plusMinutes(5).isBefore(DateTime.now())) {
                if (networkConnectionManager.checkNetworkConnection()) {
                    getServerData();
                    refreshedDate = DateTime.now();
                } else if (settingsManager.checkIfCacheIsAvailable()) {
                    getServerData();
                    displayUpdateInformation(buildOfflineCyanogenOTAUpdate(), false, false);
                    refreshedDate = DateTime.now();
                } else {
                    showNetworkError();
                }
            }
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
                            displayUpdateInformation(buildOfflineCyanogenOTAUpdate(), false, false);
                        } else {
                            showNetworkError();
                        }
                    }
                });
                Button installGuideButton = (Button) updateInformationRefreshLayout.findViewById(R.id.updateInstallationInstructionsButton);
                installGuideButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((MainActivity)getActivity()).getActivityLauncher().UpdateInstructions(checkIfUpdateIsDownloaded(cyanogenOTAUpdate));
                        hideDownloadCompleteNotification();
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
                            displayUpdateInformation(buildOfflineCyanogenOTAUpdate(), false, false);
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
     * If not, build an offline {@link CyanogenOTAUpdate} and display it without trying to reach the server at all
     * If an offline {@link CyanogenOTAUpdate} is not available, display a "No network connection error message".
     */
    private void initData() {
        if (!isFetched && settingsManager.checkIfSettingsAreValid()) {
            if (networkConnectionManager.checkNetworkConnection()) {
                getServerData();
                refreshedDate = DateTime.now();
                isFetched = true;
            } else if (settingsManager.checkIfCacheIsAvailable()) {
                getServerData();
                cyanogenOTAUpdate = buildOfflineCyanogenOTAUpdate();
                displayUpdateInformation(cyanogenOTAUpdate, false, false);
                initDownloadManager();
                refreshedDate = DateTime.now();
                isFetched = true;
            } else {
                showNetworkError();
            }
        }
    }

    /**
     * Fetches all server data. This includes update information, server messages and server status checks
     */
    private void getServerData() {
        this.inAppMessageBarData = new HashMap<>();
        new GetUpdateInformation().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        new GetServerStatus().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        if(settingsManager.showNewsMessages()) {
            new GetServerMessages().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        }
        checkNoConnectionBar();
    }


    /*
      -------------- METHODS FOR DISPLAYING DATA ON THE FRAGMENT -------------------
     */


    private void checkNoConnectionBar() {
        // Display the "No connection" bar depending on the network status of the device.
        List<Object> noConnectionBars = new ArrayList<>(1);

        if(!networkConnectionManager.checkNetworkConnection()) {
            noConnectionBars.add(new Object());
        }

        inAppMessageBarData.put(KEY_NO_NETWORK_CONNECTION_BARS, noConnectionBars);

        if(areAllServerMessageBarsLoaded()) {
            displayInAppMessageBars();
        }
    }
    public void displayServerMessages(List<ServerMessage> serverMessages) {
        List<Object> serverMessageBars = new ArrayList<>();

        if(serverMessages != null && settingsManager.showNewsMessages()) {
            for(ServerMessage serverMessage : serverMessages) {
                serverMessageBars.add(serverMessage);
            }
        }
        inAppMessageBarData.put(KEY_SERVER_MESSAGE_BARS, serverMessageBars);

        if(areAllServerMessageBarsLoaded()) {
            displayInAppMessageBars();
        }
    }

    /**
     * Displays the status of the server (warning, error, maintenance or invalid app version)
     * @param serverStatus Server status data from the backend
     */
    public void displayServerStatus(ServerStatus serverStatus) {

        List<Object> serverErrorBars = new ArrayList<>(1);
        List<Object> appUpdateBars = new ArrayList<>(1);

        if(serverStatus != null && isAdded() && serverStatus.getStatus() != OK) {
            serverErrorBars.add(serverStatus);
        }

        if(serverStatus != null && settingsManager.showAppUpdateMessages() && !checkIfAppIsUpToDate(serverStatus.getLatestAppVersion())) {
            appUpdateBars.add(serverStatus);
        }

        if(serverStatus == null) {
            ServerStatus status = new ServerStatus();
            status.setLatestAppVersion(BuildConfig.VERSION_NAME);
            status.setStatus(UNREACHABLE);
            serverErrorBars.add(status);
        }

        inAppMessageBarData.put(KEY_SERVER_ERROR_BARS, serverErrorBars);
        inAppMessageBarData.put(KEY_APP_UPDATE_BARS, appUpdateBars);

        if(areAllServerMessageBarsLoaded()) {
            displayInAppMessageBars();
        }

    }

    private int addMessageBar(ServerMessageBar view, int numberOfBars) {
        // Add the message to the update information screen if it is not null.
        if(this.rootView != null) {
            // Set the layout params based on the view count.
            // First view should go below the app update message bar (if visible)
            // Consecutive views should go below their parent / previous view.
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params.topMargin = numberOfBars * diPToPixels(20);
            view.setId((numberOfBars * 20000000 + 1));
            this.rootView.addView(view, params);
            numberOfBars = numberOfBars + 1;
            this.inAppMessageBars.add(view);
        }
        return numberOfBars;
    }

    private void deleteAllInAppMessageBars() {
        for(ServerMessageBar view : this.inAppMessageBars) {
            if(view != null && isAdded()) {
                this.rootView.removeView(view);
            }
        }
        this.inAppMessageBars = new ArrayList<>();
    }

    private void displayInAppMessageBars() {
        if(!isAdded()) {
            return;
        }
        deleteAllInAppMessageBars();
        int numberOfBars = 0;

        // Display the "No connection" bar if no connection is available.
        for(Object ignored : inAppMessageBarData.get(KEY_NO_NETWORK_CONNECTION_BARS)) {
            ServerMessageBar noConnectionError = new ServerMessageBar(getApplicationContext(), null);
            View noConnectionErrorBar = noConnectionError.getBackgroundBar();
            TextView noConnectionErrorTextView = noConnectionError.getTextView();

            noConnectionErrorBar.setBackgroundColor(ContextCompat.getColor(context, R.color.holo_red_light));
            noConnectionErrorTextView.setText(getString(R.string.error_no_internet_connection));
            numberOfBars = addMessageBar(noConnectionError, numberOfBars);
        }

        // Display server error bars / messages
        for(Object serverStatusObject : inAppMessageBarData.get(KEY_SERVER_ERROR_BARS)) {
            ServerStatus serverStatus = (ServerStatus)serverStatusObject;

            // Create a new server message view and get its contents
            ServerMessageBar serverStatusView = new ServerMessageBar(getApplicationContext(), null);
            View serverStatusWarningBar = serverStatusView.getBackgroundBar();
            TextView serverStatusWarningTextView = serverStatusView.getTextView();

            if (settingsManager.showNewsMessages()) {
                serverStatusWarningBar.setVisibility(VISIBLE);
                serverStatusWarningTextView.setVisibility(VISIBLE);
            }

            switch (serverStatus.getStatus()) {
                case WARNING:
                    if (settingsManager.showNewsMessages()) {
                        serverStatusWarningBar.setBackgroundColor(ContextCompat.getColor(context, R.color.holo_orange_light));
                        serverStatusWarningTextView.setText(getString(R.string.server_status_warning));
                        numberOfBars = addMessageBar(serverStatusView, numberOfBars);
                    }
                    break;
                case ERROR:
                    if (settingsManager.showNewsMessages()) {
                        serverStatusWarningBar.setBackgroundColor(ContextCompat.getColor(context, R.color.holo_red_light));
                        serverStatusWarningTextView.setText(getString(R.string.server_status_error));
                        numberOfBars = addMessageBar(serverStatusView, numberOfBars);
                    }
                    break;
                case MAINTENANCE:
                    showMaintenanceError();
                    break;
                case TAKEN_DOWN:
                    showAppNotValidError();
                    break;
                case UNREACHABLE:
                    serverStatusWarningBar.setBackgroundColor(ContextCompat.getColor(context, R.color.holo_red_light));
                    serverStatusWarningTextView.setText(getString(R.string.server_status_unreachable));
                    numberOfBars = addMessageBar(serverStatusView, numberOfBars);
                    break;
            }

        }

        // Display app update message if available
        for(Object serverStatusObject : inAppMessageBarData.get(KEY_APP_UPDATE_BARS)) {
            ServerStatus serverStatus = (ServerStatus)serverStatusObject;
            if (isAdded()) {
                // getActivity() is required here. Otherwise, clicking on the update message link will crash the application.
                ServerMessageBar appUpdateMessageView = new ServerMessageBar(getActivity(), null);
                View appUpdateMessageBar = appUpdateMessageView.getBackgroundBar();
                TextView appUpdateMessageTextView = appUpdateMessageView.getTextView();

                appUpdateMessageBar.setBackgroundColor(ContextCompat.getColor(context, R.color.holo_green_light));

                appUpdateMessageTextView.setText(Html.fromHtml(String.format(getString(R.string.new_app_version), serverStatus.getLatestAppVersion())));
                appUpdateMessageTextView.setMovementMethod(LinkMovementMethod.getInstance());
                numberOfBars = addMessageBar(appUpdateMessageView, numberOfBars);
            }
        }

        // Display server message bars / messages
        List<Object> serverMessageObjects = this.inAppMessageBarData.get(KEY_SERVER_MESSAGE_BARS);
        for (Object messageObject : serverMessageObjects) {
            ServerMessage message = (ServerMessage)messageObject;
            // Create a new server message view and get its contents
            ServerMessageBar serverMessageBar = new ServerMessageBar(getApplicationContext(), null);
            View serverMessageBackgroundBar = serverMessageBar.getBackgroundBar();
            TextView serverMessageTextView = serverMessageBar.getTextView();


            // Set the right locale text of the message in the view.
            String appLocale = Locale.getDefault().getDisplayLanguage();

            if (appLocale.equals(LOCALE_DUTCH)) {
                serverMessageTextView.setText(message.getMessageNl());
            } else {
                serverMessageTextView.setText(message.getMessage());
            }

            // Set the background color of the view according to the priority data from the backend.
            switch (message.getPriority()) {
                case LOW:
                    serverMessageBackgroundBar.setBackgroundColor(ContextCompat.getColor(context, R.color.holo_green_light));
                    break;
                case MEDIUM:
                    serverMessageBackgroundBar.setBackgroundColor(ContextCompat.getColor(context, R.color.holo_orange_light));
                    break;
                case HIGH:
                    serverMessageBackgroundBar.setBackgroundColor(ContextCompat.getColor(context, R.color.holo_red_light));
                    break;
            }

            // Set the message as moving text if it is marked as being marquee from the backend.
            if (message.isMarquee()) {
                serverMessageTextView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                serverMessageTextView.setHorizontallyScrolling(true);
                serverMessageTextView.setSingleLine(true);
                serverMessageTextView.setMarqueeRepeatLimit(-1); // -1 is forever
                serverMessageTextView.setFocusable(true);
                serverMessageTextView.setFocusableInTouchMode(true);
                serverMessageTextView.requestFocus();
                serverMessageTextView.setSelected(true);
            }

            numberOfBars = addMessageBar(serverMessageBar, numberOfBars);
        }

        // Set the margins of the app ui to be below the last added server message bar.
        if(inAppMessageBars.size() > 0 && isAdded()) {
            View lastServerMessageView = inAppMessageBars.get(inAppMessageBars.size() - 1);
            if (lastServerMessageView != null) {
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                params.addRule(BELOW, lastServerMessageView.getId());

                if(systemIsUpToDateRefreshLayout != null) {
                    systemIsUpToDateRefreshLayout.setLayoutParams(params);
                }
                if(updateInformationRefreshLayout != null) {
                    updateInformationRefreshLayout.setLayoutParams(params);
                }
            }
        }

    }

    /**
     * Displays the update information from a {@link CyanogenOTAUpdate} with update information.
     * @param cyanogenOTAUpdate Update information to display
     * @param online Whether or not the device has an active network connection
     * @param displayInfoWhenUpToDate Flag set to show update information anyway, even if the system is up to date.
     */
    @Override
    public void displayUpdateInformation(final CyanogenOTAUpdate cyanogenOTAUpdate, final boolean online, boolean displayInfoWhenUpToDate) {
        // Abort if no update data is found or if the fragment is not attached to its activity to prevent crashes.
        if(!isAdded() || rootView == null) {
            return;
        }

        View loadingScreen = rootView.findViewById(R.id.updateInformationLoadingScreen);
        if(loadingScreen != null) {
            loadingScreen.setVisibility(GONE);
        }

        if(cyanogenOTAUpdate == null) {
            return;
        }

        if(!cyanogenOTAUpdate.isSystemIsUpToDateCheck()) {
            cyanogenOTAUpdate.setSystemIsUpToDate(isSystemUpToDateStringCheck(cyanogenOTAUpdate));
        }

        if(((cyanogenOTAUpdate.isSystemIsUpToDate(settingsManager)) && !displayInfoWhenUpToDate) || !cyanogenOTAUpdate.isUpdateInformationAvailable()) {
            displayUpdateInformationWhenUpToDate(cyanogenOTAUpdate, online);
        } else {
            displayUpdateInformationWhenNotUpToDate(cyanogenOTAUpdate, online, displayInfoWhenUpToDate);
        }

        if(online) {
            // Save update data for offline viewing
            settingsManager.savePreference(PROPERTY_OFFLINE_UPDATE_NAME, cyanogenOTAUpdate.getName());
            settingsManager.saveIntPreference(PROPERTY_OFFLINE_UPDATE_DOWNLOAD_SIZE, cyanogenOTAUpdate.getSize());
            settingsManager.savePreference(PROPERTY_OFFLINE_UPDATE_DESCRIPTION, cyanogenOTAUpdate.getDescription());
            settingsManager.savePreference(PROPERTY_OFFLINE_FILE_NAME, cyanogenOTAUpdate.getFileName());
            settingsManager.saveBooleanPreference(PROPERTY_OFFLINE_UPDATE_INFORMATION_AVAILABLE, cyanogenOTAUpdate.isUpdateInformationAvailable());
            settingsManager.savePreference(PROPERTY_UPDATE_CHECKED_DATE, LocalDateTime.now().toString());
        }

        // Hide the refreshing icon if it is present.
        hideRefreshIcons();
    }

    private void displayUpdateInformationWhenUpToDate(final CyanogenOTAUpdate cyanogenOTAUpdate, boolean online) {
        // Show "System is up to date" view.
        rootView.findViewById(R.id.updateInformationRefreshLayout).setVisibility(GONE);
        rootView.findViewById(R.id.updateInformationSystemIsUpToDateRefreshLayout).setVisibility(VISIBLE);

        // Set the current Cyanogen OS version if available.
        String cyanogenOSVersion = ((ApplicationContext)getActivity().getApplication()).getSystemVersionProperties().getCyanogenOSVersion();
        TextView versionNumberView = (TextView) rootView.findViewById(R.id.updateInformationSystemIsUpToDateVersionTextView);
        if(!cyanogenOSVersion.equals(NO_CYANOGEN_OS)) {
            versionNumberView.setVisibility(VISIBLE);
            versionNumberView.setText(String.format(getString(R.string.update_information_cyanogen_os_version), cyanogenOSVersion));
        } else {
            versionNumberView.setVisibility(GONE);
        }

        // Set "No Update Information Is Available" button if needed.
        Button updateInformationButton = (Button) rootView.findViewById(R.id.updateInformationSystemIsUpToDateStatisticsButton);
        if(!cyanogenOTAUpdate.isUpdateInformationAvailable()) {
            updateInformationButton.setText(getString(R.string.update_information_no_update_data_available));
            updateInformationButton.setClickable(false);
        } else {
            updateInformationButton.setText(getString(R.string.update_information_view_update_information));
            updateInformationButton.setClickable(true);
            updateInformationButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    displayUpdateInformation(cyanogenOTAUpdate, true, true);
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

    private void displayUpdateInformationWhenNotUpToDate(final CyanogenOTAUpdate cyanogenOTAUpdate, boolean online, boolean displayInfoWhenUpToDate) {
        // Show "System update available" view.
        rootView.findViewById(R.id.updateInformationRefreshLayout).setVisibility(VISIBLE);
        rootView.findViewById(R.id.updateInformationSystemIsUpToDateRefreshLayout).setVisibility(GONE);

        // Display available update version number.
        TextView buildNumberView = (TextView) rootView.findViewById(R.id.updateInformationBuildNumberView);
        if (cyanogenOTAUpdate.getName() != null && !cyanogenOTAUpdate.getName().equals("null")) {
            buildNumberView.setText(cyanogenOTAUpdate.getName());
        } else {
            buildNumberView.setText(String.format(getString(R.string.update_information_unknown_update_name), deviceName));
        }

        // Display download size.
        TextView downloadSizeView = (TextView) rootView.findViewById(R.id.updateInformationDownloadSizeView);
        downloadSizeView.setText(String.format(getString(R.string.download_size_megabyte), cyanogenOTAUpdate.getSize()));

        // Display update description.
        String description = cyanogenOTAUpdate.getDescription();
        TextView descriptionView = (TextView) rootView.findViewById(R.id.updateDescriptionView);
        descriptionView.setMovementMethod(LinkMovementMethod.getInstance());
        descriptionView.setText(description != null && !description.isEmpty() && !description.equals("null") ? UpdateDescriptionParser.parse(description) : getString(R.string.update_information_description_not_available));

        // Display update file name.
        TextView fileNameView = (TextView) rootView.findViewById(R.id.updateFileNameView);
        fileNameView.setText(String.format(getString(R.string.update_information_file_name), cyanogenOTAUpdate.getFileName()));

        final Button downloadButton = (Button) rootView.findViewById(R.id.updateInformationDownloadButton);

        // Activate download button, or make it gray when the device is offline or if the update is not downloadable.
        if (online && cyanogenOTAUpdate.getDownloadUrl() != null) {
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
        Button updateInstallationGuideButton = (Button) rootView.findViewById(R.id.updateInstallationInstructionsButton);
        View downloadSizeTable = rootView.findViewById(R.id.buttonTable);
        View downloadSizeImage = rootView.findViewById(R.id.downloadSizeImage);


        if(displayInfoWhenUpToDate) {
            headerLabel.setText(getString(R.string.update_information_installed_update));
            downloadButton.setVisibility(GONE);
            updateInstallationGuideButton.setVisibility(GONE);
            fileNameView.setVisibility(GONE);
            downloadSizeTable.setVisibility(GONE);
            downloadSizeImage.setVisibility(GONE);
            downloadSizeView.setVisibility(GONE);
        } else {
            if(cyanogenOTAUpdate.isSystemIsUpToDateCheck()) {
                headerLabel.setText(getString(R.string.update_information_installed_update));
            } else {
                headerLabel.setText(getString(R.string.update_information_latest_available_update));
            }
            downloadButton.setVisibility(VISIBLE);
            updateInstallationGuideButton.setVisibility(VISIBLE);
            fileNameView.setVisibility(VISIBLE);
            downloadSizeTable.setVisibility(VISIBLE);
            downloadSizeImage.setVisibility(VISIBLE);
            downloadSizeView.setVisibility(VISIBLE);
        }
    }

    /**
     * Builds a {@link CyanogenOTAUpdate} class based on the data that was stored when the device was online.
     * @return CyanogenOTAUpdate with data from the latest succesful fetch.
     */
    @Override
    protected CyanogenOTAUpdate buildOfflineCyanogenOTAUpdate() {
        CyanogenOTAUpdate cyanogenOTAUpdate = new CyanogenOTAUpdate();
        cyanogenOTAUpdate.setName(settingsManager.getPreference(PROPERTY_OFFLINE_UPDATE_NAME));
        cyanogenOTAUpdate.setSize(settingsManager.getIntPreference(PROPERTY_OFFLINE_UPDATE_DOWNLOAD_SIZE));
        cyanogenOTAUpdate.setDescription(settingsManager.getPreference(PROPERTY_OFFLINE_UPDATE_DESCRIPTION));
        cyanogenOTAUpdate.setUpdateInformationAvailable(settingsManager.getBooleanPreference(PROPERTY_OFFLINE_UPDATE_INFORMATION_AVAILABLE));
        cyanogenOTAUpdate.setFileName(settingsManager.getPreference(PROPERTY_OFFLINE_FILE_NAME));
        return cyanogenOTAUpdate;
    }

    /**
     * Additional check if system is up to date by comparing version Strings.
     * This is needed to show the "System is up to date" message for full updates as incremental (parent) versions are not checked there.
     * @param cyanogenOTAUpdate CyanogenOTAUpdate that needs to be checked against the current version.
     * @return True if the system is up to date, false if not.
     */
    private boolean isSystemUpToDateStringCheck(CyanogenOTAUpdate cyanogenOTAUpdate) {
        if(settingsManager.showIfSystemIsUpToDate()) {
            // This grabs Cyanogen OS version from build.prop. As there is no direct SDK way to do this, it has to be done in this way.
            SystemVersionProperties systemVersionProperties = ((ApplicationContext)getActivity().getApplication()).getSystemVersionProperties();

            String cyanogenOSVersion = systemVersionProperties.getCyanogenOSVersion();
            String newCyanogenOSVersion = cyanogenOTAUpdate.getName();

            if(newCyanogenOSVersion == null || newCyanogenOSVersion.isEmpty()) {
                return false;
            }

            if (cyanogenOSVersion == null || cyanogenOSVersion.isEmpty() || cyanogenOSVersion.equals(NO_CYANOGEN_OS)) {
                return false;
            } else {
                if (newCyanogenOSVersion.equals(cyanogenOSVersion)) {
                    return true;
                } else {
                    // remove incremental version naming.
                    newCyanogenOSVersion = newCyanogenOSVersion.replace(" Incremental", "");
                    if (newCyanogenOSVersion.equals(cyanogenOSVersion)) {
                        return true;
                    } else {
                        newCyanogenOSVersion = newCyanogenOSVersion.replace("-", " ");
                        cyanogenOSVersion = cyanogenOSVersion.replace("-", " ");
                        return newCyanogenOSVersion.contains(cyanogenOSVersion);
                    }
                }
            }
        }
        else {
            return false; // Always show update info if user does not want to see if system is up to date.
        }
    }

    /*
      -------------- USER INTERFACE ELEMENT METHODS -------------------
     */

    private boolean areAllServerMessageBarsLoaded() {
        return inAppMessageBarData.containsKey(KEY_APP_UPDATE_BARS) && inAppMessageBarData.containsKey(KEY_NO_NETWORK_CONNECTION_BARS) && inAppMessageBarData.containsKey(KEY_SERVER_ERROR_BARS) && inAppMessageBarData.containsKey(KEY_SERVER_MESSAGE_BARS);
    }

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
                                Toast.makeText(getApplicationContext(), getString(R.string.download_verifying_start), Toast.LENGTH_LONG).show();
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
                                    showDownloadError(getString(R.string.download_error_network));
                                    showDownloadFailedNotification(getString(R.string.download_error_network), getString(R.string.download_notification_error_network));
                                } else {
                                    switch (statusCode) {
                                        case ERROR_UNHANDLED_HTTP_CODE:
                                        case ERROR_HTTP_DATA_ERROR:
                                        case ERROR_TOO_MANY_REDIRECTS:
                                            showDownloadError(getString(R.string.download_error_network));
                                            showDownloadFailedNotification(getString(R.string.download_error_network), getString(R.string.download_notification_error_network));
                                            break;
                                        case ERROR_FILE_ERROR:
                                            updateDownloader.makeDownloadDirectory();
                                            showDownloadError(getString(R.string.download_error_directory));
                                            showDownloadFailedNotification(getString(R.string.download_error_directory), getString(R.string.download_notification_error_storage_not_found));
                                            break;
                                        case ERROR_INSUFFICIENT_SPACE:
                                            showDownloadError(getString(R.string.download_error_storage));
                                            showDownloadFailedNotification(getString(R.string.download_error_storage), getString(R.string.download_notification_error_storage_full));
                                            break;
                                        case ERROR_DEVICE_NOT_FOUND:
                                            showDownloadError(getString(R.string.download_error_sd_card));
                                            showDownloadFailedNotification(getString(R.string.download_error_sd_card), getString(R.string.download_notification_error_sd_card_missing));
                                            break;
                                        case ERROR_CANNOT_RESUME:
                                            updateDownloader.cancelDownload();
                                            if (networkConnectionManager.checkNetworkConnection() && cyanogenOTAUpdate != null && cyanogenOTAUpdate.getDownloadUrl() != null) {
                                                updateDownloader.downloadUpdate(cyanogenOTAUpdate);
                                            }
                                            break;
                                        case ERROR_FILE_ALREADY_EXISTS:
                                            Toast.makeText(getApplicationContext(), getString(R.string.download_already_downloaded), Toast.LENGTH_LONG).show();
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
                                showDownloadError(getString(R.string.download_error_corrupt));
                                hideVerifyingNotification();
                                showDownloadFailedNotification(getString(R.string.download_error_corrupt), getString(R.string.download_notification_error_corrupt));
                                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath() + File.separator + cyanogenOTAUpdate.getFileName());
                                try {
                                    //noinspection ResultOfMethodCallIgnored
                                    file.delete();
                                    settingsManager.deletePreference(PROPERTY_DOWNLOAD_ID);
                                    onUpdateDownloaded(false, false);
                                } catch (Exception ignored) {

                                }
                            }
                        }

                        @Override
                        public void onVerifyComplete() {
                            if(isAdded()) {
                                hideDownloadProgressBar();
                                hideVerifyingNotification();
                                onUpdateDownloaded(true, false);
                                Toast.makeText(getApplicationContext(), getString(R.string.download_complete), Toast.LENGTH_LONG).show();
                                showDownloadCompleteNotification();
                            }
                        }
                    });
            updateDownloader.checkDownloadProgress(cyanogenOTAUpdate);
        }
    }

    /**
     * Shows an {@link MessageDialog} with the occured download error.
     * @param message Contents of the error message
     */
    private void showDownloadError(String message) {
        MessageDialog errorDialog = new MessageDialog()
                .setTitle(getString(R.string.download_error))
                .setMessage(message)
                .setPositiveButtonText(getString(R.string.download_error_close))
                .setNegativeButtonText(getString(R.string.download_error_retry))
                .setClosable(true)
                .setDialogListener(new MessageDialog.DialogListener() {
                    @Override
                    public void onDialogPositiveButtonClick(DialogFragment dialogFragment) {
                        hideDownloadCompleteNotification();
                    }

                    @Override
                    public void onDialogNegativeButtonClick(DialogFragment dialogFragment) {
                        hideDownloadCompleteNotification();
                        updateDownloader.cancelDownload();
                        updateDownloader.downloadUpdate(cyanogenOTAUpdate);
                    }
                });
        errorDialog.setTargetFragment(this, 0);
        FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
        transaction.add(errorDialog, "DownloadError");
        transaction.commitAllowingStateLoss();
    }

    private void showDownloadFailedNotification(String message, String notificationMessage) {
        // If the download failed notification is clicked, the error message must be shown in the app.
        Intent resultIntent = new Intent(getActivity(), MainActivity.class);
        resultIntent.putExtra(KEY_HAS_DOWNLOAD_ERROR, true);
        resultIntent.putExtra(KEY_DOWNLOAD_ERROR_MESSAGE, message);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getActivity());
        // Adds the back stack
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);

        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getActivity())
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(false)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationMessage))
                .setContentTitle(getString(R.string.download_failed))
                .setContentText(notificationMessage);

        if(Build.VERSION.SDK_INT >= 21) {
            builder.setCategory(Notification.CATEGORY_SYSTEM);
        }

        NotificationManager manager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(DOWNLOAD_COMPLETE_NOTIFICATION_ID, builder.build());
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
            if (networkConnectionManager != null && networkConnectionManager.checkNetworkConnection() && cyanogenOTAUpdate != null && cyanogenOTAUpdate.getDownloadUrl() != null && isAdded()) {
                if(updateDownloader != null) {
                    updateDownloader.checkDownloadProgress(cyanogenOTAUpdate);
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
                    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath() + File.separator + cyanogenOTAUpdate.getFileName());
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
                    updateDownloader.downloadUpdate(cyanogenOTAUpdate);
                    downloadButton.setText(getString(R.string.downloading));
                    downloadButton.setClickable(false);
                }
            } else {
                Callback callback = new Callback() {
                    @Override
                    public void onActionPerformed(Object... result) {
                        if((int)result[0] == PackageManager.PERMISSION_GRANTED && updateDownloader != null && cyanogenOTAUpdate != null) {
                            updateDownloader.downloadUpdate(cyanogenOTAUpdate);
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
                        if(cyanogenOTAUpdate != null) {
                            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath() + File.separator + cyanogenOTAUpdate.getFileName());
                            if(file.exists()) {
                                if(file.delete()) {
                                    getDownloadButton().setText(getString(R.string.download));
                                    checkIfUpdateIsAlreadyDownloaded(cyanogenOTAUpdate);
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
     * @param cyanogenOTAUpdate Cyanogen Update data containing the file name of the update.
     */
    @Override
    public void checkIfUpdateIsAlreadyDownloaded(CyanogenOTAUpdate cyanogenOTAUpdate) {
        if(cyanogenOTAUpdate != null) {
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath() + File.separator + cyanogenOTAUpdate.getFileName());
            onUpdateDownloaded(file.exists() && !settingsManager.containsPreference(PROPERTY_DOWNLOAD_ID), false);
        }
    }

    public boolean checkIfUpdateIsDownloaded(CyanogenOTAUpdate cyanogenOTAUpdate) {
        if(cyanogenOTAUpdate == null) return false;
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath() + File.separator + cyanogenOTAUpdate.getFileName());
        return (file.exists() && !settingsManager.containsPreference(PROPERTY_DOWNLOAD_ID));
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

            if (error) {
                builder.setContentTitle(getString(R.string.download_verifying_error));
            } else {
                builder.setContentTitle(getString(R.string.download_verifying));
            }

            if (Build.VERSION.SDK_INT >= 21) {
                builder.setCategory(Notification.CATEGORY_PROGRESS);
            }
            NotificationManager manager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            try {
                NotificationManager manager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
                manager.cancel(NOTIFICATION_ID);
            } catch (Exception e1) {
                try {
                    // If cancelling the notification fails fails (and yes, it happens!), then I assume either that the user's device has a (corrupt) custom firmware or that something is REALLY going wrong.
                    // We try it once more but now with the Application Context instead of the Activity.
                    NotificationManager manager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.cancel(NOTIFICATION_ID);
                } catch (Exception ignored) {
                    // If the last attempt has also failed, well then there's no hope.
                    // We leave everything as is, but the user will likely be stuck with a verifying notification that stays until a reboot.
                }
            }
        }
    }

    private void showDownloadCompleteNotification() {
        try {
            // If the download complete notification is clicked, hide the first page of the install guide.
            Intent resultIntent = new Intent(getActivity(), InstallGuideActivity.class);
            resultIntent.putExtra(INTENT_SHOW_DOWNLOAD_PAGE, false);

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(getActivity());
            // Adds the back stack
            stackBuilder.addParentStack(MainActivity.class);
            // Adds the Intent to the top of the stack
            stackBuilder.addNextIntent(resultIntent);
            // Gets a PendingIntent containing the entire back stack
            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getActivity())
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setOngoing(false)
                    .setContentIntent(resultPendingIntent)
                    .setAutoCancel(true)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.download_complete_notification)))
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.download_complete_notification));

            if (Build.VERSION.SDK_INT >= 21) {
                builder.setCategory(Notification.CATEGORY_SYSTEM);
            }

            NotificationManager manager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(DOWNLOAD_COMPLETE_NOTIFICATION_ID, builder.build());
        } catch(Exception ignored) {

        }
    }

    /**
     * Hides the verifying notification. Used when verification has succeeded.
     */
    private void hideVerifyingNotification() {
        NotificationManager manager = (NotificationManager)getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID);
    }

    /**
     * Hides the download complete notification. Used when the install guide is manually clicked from within the app.
     */
    private void hideDownloadCompleteNotification() {
        NotificationManager manager = (NotificationManager)getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(DOWNLOAD_COMPLETE_NOTIFICATION_ID);
    }

    /**
     * Converts DiP units to pixels
     */
    private int diPToPixels(int numberOfPixels) {
        if(getActivity() != null && getActivity().getResources() != null) {
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    numberOfPixels,
                    getActivity().getResources().getDisplayMetrics()
            );
        } else {
            return 0;
        }
    }
}