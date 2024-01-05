/*******************************************************************************
 * Copyright (c) 2016, 2024  IBM Corporation, Carnegie Mellon University and others
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *******************************************************************************/

package hulop.navcog.helpers;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

import hulop.navcog.BuildConfig;
import hulop.navcog.R;

import static android.content.Context.VIBRATOR_SERVICE;

public class BrowserHelper {
    private static final String JS_BRIDGE_NAME = "mobile_bridge";
    public static BrowserHelper instance;
    private final Activity mActivity;
    private final SharedPreferences mPrefs;
    private final WebView mWebView;
    private BrowserListener mListener = null;
    private String mCallback = null;

    private ScreenFilter screenFilter = null;

    @SuppressLint("SetJavaScriptEnabled")
    public BrowserHelper(Activity activity) {
        Log.d("BrowserHelper", "context dir=" + activity.getFilesDir().getPath());
        instance = this;
        mActivity = activity;
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        this.mWebView = activity.findViewById(R.id.webView);
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        //settings.setAppCacheEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setGeolocationEnabled(true);
        settings.setGeolocationDatabasePath(activity.getFilesDir().getPath());

        mWebView.clearCache(true);
        mWebView.setWebViewClient(new MyWebViewClient());
        mWebView.setWebChromeClient(new MyWebChromeClient());
        mWebView.addJavascriptInterface(new JSObject(), JS_BRIDGE_NAME);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        if(prefs.getBoolean("useScreenFilter", false)) {
            screenFilter = new ScreenFilter(activity);
        }
    }

    public void start() {
    }

    public void stop() {

    }

    public void loadUrl(String url) {
        mWebView.loadUrl(url);
    }


    public void setLocationHash(String hash) {
        mWebView.evaluateJavascript("location.hash=\"" + hash + "\"", value -> {
        });
    }

    public void setBrowserListener(BrowserListener browserListener) {
        this.mListener = browserListener;
    }

    public void clearCache() {
        mWebView.clearCache(true);
    }

    public boolean goBack() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        return false;
    }

    public void syncPreferences() {
        fire(String.format("onPreferences(%s)", new JSONObject(mPrefs.getAll())));
    }

    /*
     * javascript call-out interface
     */
    public void fire(String event) {
        if (mCallback != null) {
            invoke(String.format("if(%s){%s.%s;}", mCallback, mCallback, event));
        }
    }

    public void invoke(final String script) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mWebView.loadUrl(String.format("javascript:%s", script));
            }
        });
    }

    public enum Control {
        RouteSearchOption,
        RouteSearch,
        Done,
        EndNavigation,
        BackToControl,
        None,
    }

    public void triggerWebviewControl(Control control) {
        switch (control) {
            case RouteSearchOption:
                invoke("$('a[href=\"#settings\"]').click()");
                break;
            case RouteSearch:
                invoke("$('a[href=\"#control\"]').click()");
                break;
            case Done:
                invoke("$('div[role=banner]:visible a').click()");
                break;
            case EndNavigation:
                invoke("$('#end_navi').click()");
                break;
            case BackToControl:
                invoke("$('div[role=banner]:visible a').click()");
                break;
            default:
                invoke("$hulop.map.resetState()");
                break;
        }
    }

    /*
     * javascript call-in interfaces
     */
    private class JSObject {
        @JavascriptInterface
        public void setCallback(String value) {
            Log.d("BrowserHelper", "setCallback: " + value);
            mCallback = value;
            syncPreferences();
        }

        @JavascriptInterface
        public void speak(String text, boolean flush) {
            Log.d("BrowserHelper", "speak: " + text);
            TTSHelper.instance.speak(text, flush);
//            LogHelper.instance.appendText(String.format("speak,%s", text));
        }

        @JavascriptInterface
        public void isSpeaking(String callback) {
            fire(callback + "(" + TTSHelper.instance.isSpeaking() + ")");
        }

        @JavascriptInterface
        public void mapCenter(double lat, double lng, double floor, boolean sync) {
//            if (LogHelper.instance.loggingNavi) {
//                LogHelper.instance.appendText(String.format("mapCenter,%f,%f,%f,%b", lat, lng, floor, sync));
//            }
            if (mListener != null) {
                mListener.onLocationChanged(lat, lng, floor, sync);
            }
        }

        @JavascriptInterface
        public void logText(String text) {
            Log.d("BrowserHelper", "logText: " + text);
            LogHelper.instance.appendText(text);
            if (mListener != null) {
                if (text.startsWith("buildingChanged,")) {
                    try {
                        JSONObject json = new JSONObject(text.substring(text.indexOf(',') + 1));
                        String building = json.optString("building", null);
                        mListener.onBuildingChanged(building);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                if (text.startsWith("stateChanged,")) {
                    try {
                        JSONObject json = new JSONObject(text.substring(text.indexOf(',') + 1));
                        String page = json.getString("page");
                        boolean navi = json.optBoolean("navigation", false);
                        mListener.onUIPageChanged(page, navi);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                if (text.startsWith("enterRestrictedArea")) {
                    if(screenFilter != null) {
                        screenFilter.onCheckedRestrictedArea(true);
                    }
                    mListener.onCheckedRestrictedArea(true);
                    triggerWebviewControl(Control.None);
                }
                if (text.equals("exitRestrictedArea")) {
                    if(screenFilter != null) {
                        screenFilter.onCheckedRestrictedArea(false);
                    }
                    mListener.onCheckedRestrictedArea(false);
                }
                if (text.startsWith("startPreventWalking")) {
                    if(screenFilter != null) {
                        screenFilter.onCheckedPreventWalkingArea(true);
                    }
                    mListener.onCheckedPreventWalkingArea(true);
                }
                if (text.equals("endPreventWalking")) {
                    if(screenFilter != null) {
                        screenFilter.onCheckedPreventWalkingArea(false);
                    }
                    mListener.onCheckedPreventWalkingArea(false);
                }
                if (text.startsWith("navigationFinished,")) {
                    try {
                        JSONObject json = new JSONObject(text.substring(text.indexOf(',') + 1));
                        double start = json.getDouble("start");
                        double end = json.getDouble("end");
                        String from = json.getString("from");
                        String to = json.getString("to");
                        mListener.onNavigationFinished(start, end, from, to);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @JavascriptInterface
        public void vibrate() {
            Log.d("BrowserHelper", "vibrate");
            ((Vibrator) mActivity.getSystemService(VIBRATOR_SERVICE)).vibrate(400);
        }
    }


    /*
     * browser event handlers
     */
    private class MyWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Log.d("BrowserHelper", "onPageStarted: " + url);
            super.onPageStarted(view, url, favicon);
            mCallback = null;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Log.d("BrowserHelper", "onPageFinished: " + url);
            // initialURL = url;
            super.onPageFinished(view, url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            Log.d("BrowserHelper", "onReceivedError: " + request + ", error=" + error);
            super.onReceivedError(view, request, error);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.d("BrowserHelper", "shouldOverrideUrlLoading: " + url);
            if (mListener != null) {
                mListener.shouldOpenUrl(url);
            }
            return true;
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            Log.d("BrowserHelper", "onReceivedHttpError: " + errorResponse);
            super.onReceivedHttpError(view, request, errorResponse);
        }

        @Override
        public void onLoadResource(WebView view, String url) {
            Log.d("BrowserHelper", "onLoadResource: " + url);
            super.onLoadResource(view, url);
        }
    }

    private class MyWebChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
//            Log.d(consoleMessage.sourceId(), "Line " + consoleMessage.lineNumber() + " : "
//                    + consoleMessage.message());
            return true;
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            callback.invoke(origin, true, false);
        }
    }

    public interface BrowserListener {
        void onLocationChanged(double lat, double lng, double floor, boolean sync);

        void onBuildingChanged(String building);

        void onUIPageChanged(String page, boolean inNavigation);

        void onCheckedRestrictedArea(boolean restricted);

        void onCheckedPreventWalkingArea(boolean prevented);

        void onNavigationFinished(double start, double end, String from, String to);

        void shouldOpenUrl(String url);
    }
}
