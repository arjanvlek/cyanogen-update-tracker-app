package com.arjanvlek.cyngnotainfo.Support;

import android.content.Context;
import android.net.NetworkInfo;

public class NetworkConnectionManager {

    private Context context;

    public NetworkConnectionManager(Context context) {
        this.context = context;
    }

    /**
     * Checks if the device has an active network connection
     *
     * @return Returns if the device has an active network connection
     */
    public boolean checkNetworkConnection() {
        android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }
}
