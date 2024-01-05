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

package hulop.navcog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class AgreementActivity extends AppCompatActivity {
    public static final String TAG = "AgreementActivity";
    private WebView mWebView;
    private AgreementActivity self = null;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agreement);
        self = this;

        this.mWebView = findViewById(R.id.agreement_webView);
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        // settings.setAppCacheEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);

//        mWebView.clearCache(true);
        mWebView.setWebViewClient(new MyWebViewClient());
        mWebView.setWebChromeClient(new MyWebChromeClient());

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(self);

        String agreementPath = prefs.getString("agreement_init_path", "/init.jsp");
        String server_host = prefs.getString("selected_hokoukukan_server", "");
        String device_id = prefs.getString("device_id", "");
        String urlString = String.format("https://%s%s?id=%s", server_host, agreementPath, device_id);

        Log.d(TAG, "onCreate: " + urlString);
        mWebView.loadUrl(urlString);

        TextView textView = findViewById(R.id.textView);
        Button retryButton = findViewById(R.id.button_webViewRetry);
        retryButton.setOnClickListener(v -> retry());
        mWebView.setVisibility(View.INVISIBLE);
        textView.setVisibility(View.INVISIBLE);
        retryButton.setVisibility(View.INVISIBLE);
        if (Utils.isNetworkConnected(this)) {
            Utils.checkHttpStatus(mWebView.getUrl(), status -> {
                Log.d("CheckHttpsStatusTask", "status: " + status);
                if (status == 200) {
                    mWebView.setVisibility(View.VISIBLE);
                    textView.setVisibility(View.INVISIBLE);
                    retryButton.setVisibility(View.INVISIBLE);
                } else {
                    mWebView.setVisibility(View.INVISIBLE);
                    textView.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                }
            });
        } else {
            mWebView.setVisibility(View.INVISIBLE);
            textView.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.  onPause();
    }

    private void retry() {
        if (Utils.isNetworkConnected(this)) {
            recreate();
        }
    }

    private class MyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            // TODO: arrow only our content
            String urlString = request.getUrl().toString();
            Log.d(TAG, "shouldOverrideUrlLoading: " + urlString);

            if (urlString.contains("/finish_agreement.jsp")) { // check if finish page is tried to be loaded
                new CheckAgreementTask(self, new CheckAgreementTask.Callback() {
                    @Override
                    public void onPostExecute(JSONObject json) {
                        boolean agreed = json.optBoolean("agreed", false);

                        Intent intent;
                        if (agreed) {
                            intent = new Intent(self, InitActivity.class);
                        } else {
                            intent = new Intent(self, ServerSelectionActivity.class);
                            intent.putExtra("recreate", true);
                        }
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
                        self.startActivity(intent);
                        self.finish();
                    }
                }).execute();
                // ignore
                return true;
            }
            return false;
        }
    }

    private class MyWebChromeClient extends WebChromeClient {

    }

}

class CheckAgreementTask extends AsyncTask<String, Void, Void> {
    public static final String TAG = "CheckAgreementTask";
    private Callback callback;
    private File tempFile;

    private String agreementUrl;

    public CheckAgreementTask(@NonNull Context context, @NonNull Callback callback) {
        this.callback = callback;
        this.tempFile = new File(context.getExternalCacheDir(), "agreement");

        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String https = mPrefs.getBoolean("https_connection", true) ? "https" : "http";
        String server_host = mPrefs.getString("selected_hokoukukan_server", "");
        String device_id = mPrefs.getString("device_id", "");
        String appName = context.getPackageName();
        agreementUrl = String.format("%s://%s/api/check_agreement?id=%s&appname=%s", https, server_host, device_id, appName);
        Log.d(TAG, "onCreate: " + agreementUrl);
    }

    @Override
    protected Void doInBackground(String... strings) {
        if (agreementUrl == null) {
            return null;
        }
        Log.d(TAG, "doInBackground: " + agreementUrl);
        URL url = null;
        try {
            url = new URL(agreementUrl);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        int count;
        InputStream input = null;
        OutputStream output = null;
        try {
            URLConnection connection = url.openConnection();
            connection.connect();

            input = new BufferedInputStream(url.openStream(), 1048576);
            output = new FileOutputStream(tempFile);

            byte[] data = new byte[1048576];
            while ((count = input.read(data)) != -1) {
                if (isCancelled()) {
                    break;
                }
                output.write(data, 0, count);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (output != null) {
                try {
                    output.flush();
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        try {
            JSONObject json = Utils.readJSON(this.tempFile);
            callback.onPostExecute(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface Callback {
        void onPostExecute(JSONObject json);
    }
}