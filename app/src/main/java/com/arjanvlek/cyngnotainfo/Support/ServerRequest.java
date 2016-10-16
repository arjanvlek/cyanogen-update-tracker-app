package com.arjanvlek.cyngnotainfo.Support;

import com.arjanvlek.cyngnotainfo.BuildConfig;

import java.net.MalformedURLException;
import java.net.URL;

public enum ServerRequest {

    DEVICES {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(getBaseUrl() + "devices");
        }
    },
    INSTALL_GUIDE {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(getBaseUrl() + "installGuide/" + params[0] + "/" + params[1]);
        }
    },
    INSTALL_GUIDE_IMAGE {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(params[0]);
        }
    },
    UPDATE_METHODS {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(getBaseUrl() + "updateMethods/" + params[0]);
        }
    },
    UPDATE_DATA {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(getBaseUrl() + "updateData/" + params[0] + "/" + params[1] + "/" + params[2]);
        }
    },
    MOST_RECENT_UPDATE_DATA {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(getBaseUrl() + "mostRecentUpdateData/" + params[0] + "/" + params[1]);
        }
    },
    SERVER_STATUS {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(getBaseUrl() + "serverStatus");
        }
    },
    SERVER_MESSAGES {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(getBaseUrl() + "serverMessages/" + params[0] + "/" + params[1]);
        }
    };

    abstract URL getURL(String...params) throws MalformedURLException;

    private static String getBaseUrl() {
        return BuildConfig.USE_TEST_SERVER ? ServerConnector.TEST_SERVER_URL : ServerConnector.SERVER_URL;
    }
}
