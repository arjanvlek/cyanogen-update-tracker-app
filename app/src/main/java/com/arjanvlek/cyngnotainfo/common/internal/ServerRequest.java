package com.arjanvlek.cyngnotainfo.common.internal;

import java.net.MalformedURLException;
import java.net.URL;

import static com.arjanvlek.cyngnotainfo.BuildConfig.SERVER_BASE_URL;

enum ServerRequest {

    DEVICES {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(SERVER_BASE_URL + "devices");
        }
    },
    INSTALL_GUIDE {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(SERVER_BASE_URL + "installGuide/" + params[0] + "/" + params[1] + "/" + params[2]);
        }
    },
    UPDATE_METHODS {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(SERVER_BASE_URL + "updateMethods/" + params[0]);
        }
    },
    UPDATE_DATA {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(SERVER_BASE_URL + "updateData/" + params[0] + "/" + params[1] + "/" + params[2]);
        }
    },
    MOST_RECENT_UPDATE_DATA {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(SERVER_BASE_URL + "mostRecentUpdateData/" + params[0] + "/" + params[1]);
        }
    },
    SERVER_STATUS {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(SERVER_BASE_URL + "serverStatus");
        }
    },
    REGISTER_DEVICE {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(SERVER_BASE_URL + "registerDevice");
        }
    },
    SERVER_MESSAGES {
        @Override
        URL getURL(String... params) throws MalformedURLException {
            return new URL(SERVER_BASE_URL + "serverMessages/" + params[0] + "/" + params[1]);
        }
    };

    abstract URL getURL(String...params) throws MalformedURLException;
}
