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
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;

public class WebViewActivity extends AppCompatActivity {
    public static final String TAG = "WebViewActivity";
    public static final String EXTRA_WEBVIEW_TITLE = "hulop.navcog.EXTRA_WEBVIEW_TITLE";
    public static final String EXTRA_WEBVIEW_URL = "hulop.navcog.EXTRA_WEBVIEW_URL";

    private String title;
    private String url;
    private String defUrl;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        Intent intent = getIntent();
        String type = intent.getDataString();
        if (type != null) {
            switch (type) {
                case "help":
                case "instructions":
                case "usage":
                    prepareHelp(type);
                    break;
                default:
                    url = intent.getStringExtra(EXTRA_WEBVIEW_URL);
                    if (url == null) {
                        url = "";
                    }
                    title = intent.getStringExtra(EXTRA_WEBVIEW_TITLE);
                    if (title == null) {
                        title = "";
                    }
                    defUrl = getString(R.string.HelpURL)+"index.html";
            }
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(title);
        }
        WebView webView = findViewById(R.id.webView_webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBuiltInZoomControls(false);
        // settings.setAppCacheEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);

//        mWebView.clearCache(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        TextView textView = findViewById(R.id.textView);
        Button retryButton = findViewById(R.id.button_webViewRetry);
        retryButton.setOnClickListener(v -> retry());
        webView.setVisibility(View.INVISIBLE);
        textView.setVisibility(View.INVISIBLE);
        retryButton.setVisibility(View.INVISIBLE);
        if (Utils.isNetworkConnected(this)) {
            Utils.checkHttpStatus(url, status -> {
                if (status == 200) {
                    webView.loadUrl(url);
                } else {
                    webView.loadUrl(defUrl);
                }
                webView.setVisibility(View.VISIBLE);
                textView.setVisibility(View.INVISIBLE);
                retryButton.setVisibility(View.INVISIBLE);
            });
        } else {
            webView.setVisibility(View.INVISIBLE);
            textView.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
        }
    }

    private void prepareHelp(String type) {
        String lang = "-" + Locale.getDefault().getLanguage();
        if (lang.equals("-en")) {
            lang = "";
        }
        if (type != null) {
            switch (type) {
                case "help":
                    title = getString(R.string.Help);
                    break;
                case "instructions":
                    title = getString(R.string.Instructions);
                    break;
                case "usage":
                    title = getString(R.string.UsageRequirements);
                    break;
            }
        } else {
            title = getString(R.string.Help);
        }
        String base = getString(R.string.HelpURL);
        url = base + type + lang + ".html";
        defUrl = base + type + ".html";
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                super.onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void retry() {
        if (Utils.isNetworkConnected(this)) {
            recreate();
        }
    }
}
