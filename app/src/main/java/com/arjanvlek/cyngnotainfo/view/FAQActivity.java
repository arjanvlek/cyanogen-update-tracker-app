package com.arjanvlek.cyngnotainfo.view;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.RelativeLayout;

import com.arjanvlek.cyngnotainfo.R;
import com.arjanvlek.cyngnotainfo.Support.NetworkConnectionManager;

import static com.arjanvlek.cyngnotainfo.ApplicationContext.APP_USER_AGENT;

public class FAQActivity extends AppCompatActivity {

    private NetworkConnectionManager networkConnectionManager;

    private static final String FAQ_SERVER_URL = "https://cyanogenupdatetracker.com/inappfaq";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_faq);
        networkConnectionManager = new NetworkConnectionManager(getApplicationContext());
    }

    @Override
    protected void onStart() {
        super.onStart();
        final SwipeRefreshLayout refreshLayout = (SwipeRefreshLayout) findViewById(R.id.faq_webpage_layout);
        if(refreshLayout != null) {
            refreshLayout.setColorSchemeResources(R.color.lightBlue, R.color.holo_orange_light, R.color.holo_red_light);
            refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    loadFaqPage();
                }
            });
        }
        loadFaqPage();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar.
        getMenuInflater().inflate(R.menu.menu_faq, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handles action bar item clicks.
        int id = item.getItemId();

        // Respond to the action bar's Up/Home button, exit the activity gracefully to prevent downloads getting stuck.
        if (id == android.R.id.home) {
            finish();
            return true;
        }

        // Refreshes the web page if the Refresh button is clicked.
        if (id == R.id.action_refresh) {
            loadFaqPage();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Gracefully quit the activity if the Back button is pressed to prevent downloads getting stuck.
     */
    @Override
    public void onBackPressed() {
        finish();
    }

    /**
     * Loads the FAQ page, or displays a No Network connection screen if there is no network connection.
     */
    @SuppressLint("SetJavaScriptEnabled") // JavaScript is required to toggle the FAQ Item boxes.
    private void loadFaqPage() {
        if(networkConnectionManager != null && networkConnectionManager.checkNetworkConnection()) {
            SwipeRefreshLayout refreshLayout = (SwipeRefreshLayout) findViewById(R.id.faq_webpage_layout);

            if(refreshLayout != null && !refreshLayout.isRefreshing()) {
                refreshLayout.setRefreshing(true);
            }

            WebView FAQPageView = (WebView) findViewById(R.id.faqWebView);
            if(FAQPageView != null) {
                switchViews(true);

                FAQPageView.getSettings().setJavaScriptEnabled(true);
                FAQPageView.getSettings().setUserAgentString(APP_USER_AGENT);
                FAQPageView.clearCache(true);
                FAQPageView.loadUrl(FAQ_SERVER_URL);
            }

            if(refreshLayout != null && refreshLayout.isRefreshing()) {
                refreshLayout.setRefreshing(false);
            }
        } else {
            switchViews(false);
        }
    }

    /**
     * Handler for the Retry button on the "No network connection" page.
     * @param v View
     */
    public void onRetryButtonClick(View v) {
        loadFaqPage();
    }

    /**
     * Switches between the Web Browser view and the No Network connection screen based on the hasNetwork parameter.
     * @param hasNetwork Whether the device has a network connection or not.
     */
    private void switchViews (boolean hasNetwork) {
        RelativeLayout noNetworkLayout = (RelativeLayout) findViewById(R.id.faq_no_network_view);

        if(noNetworkLayout != null) {
            noNetworkLayout.setVisibility(hasNetwork ? View.GONE : View.VISIBLE);
        }

        SwipeRefreshLayout webPageLayout = (SwipeRefreshLayout) findViewById(R.id.faq_webpage_layout);
        if(webPageLayout != null) {
            webPageLayout.setVisibility(hasNetwork ? View.VISIBLE : View.GONE);
        }
    }
}
