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

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import hulop.navcog.helpers.BrowserHelper;
import hulop.navcog.helpers.IndoorLocationManager;
import hulop.navcog.helpers.ScreenFilter;
import hulop.navcog.helpers.ServiceManager;
import hulop.navcog.helpers.TTSHelper;

public class MainActivity extends AppCompatActivity {

    public static String navigate_to = null;
    public static Boolean useStair = null;
    public static Boolean useEscalator = null;
    public static Boolean useElevetor = null;

    public static Intent restart_intent = null;
    public static Double current_lat, current_lng, current_floor;
    public static String current_building;
    public static int use_count = 0;
    private final int REQUEST_PERMISSION_1 = 10;
    private final int REQUEST_PERMISSION_2 = 100;
    private final ServiceManager mServiceManager;
    private FloatingActionButton fab;
    private ViewState state;

    private boolean mCancelVisible = false;
    private boolean mDoneVisible = false;
    private boolean mBackVisible = false;
    private boolean mStopVisible = false;
    private boolean mSettingVisible = false;
    private boolean mSearchVisible = false;
    private boolean mResetLocationVisible = false;

    private boolean mUseScreenFilter = false;
    private boolean mRestrictedArea = false;
    private boolean mPreventWalking = false;

    private boolean mDialogAvailable = false;

    public static final String[] PERMISSIONS = new String[]{
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };


    public MainActivity() {
        this.mServiceManager = new ServiceManager(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        restart_intent = null;
        Log.d("MainActivity", "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        state = ViewState.Loading;
        WebView webView = findViewById(R.id.webView);
        fab = findViewById(R.id.fab);
        fab.setContentDescription(getString(R.string.fab_start_conversation));
        TextView textView = findViewById(R.id.textView);
        Button retryButton = findViewById(R.id.button_webViewRetry);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        if(pref.getString("conv_server", null) != null) {
            mDialogAvailable = true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean flag = false;
            for (String perm : PERMISSIONS) {
                flag = flag || (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED);
            }
            if (flag) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSION_2);
            } else {
                startUI();
            }
        } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSION_1);
        }else {
            startUI();
        }


//        boolean checked = pref.getBoolean("useScreenFilter", false);
//        if(!checked) {
//            mUseScreenFilter = true;
//        }
        mUseScreenFilter = true;
        pref.edit().putBoolean("useScreenFilter", mUseScreenFilter).apply();

        boolean checked = pref.getBoolean("checked_tts", false);

        if (!checked) {
            TextToSpeech tts = new TextToSpeech(this, status -> {
            });
            if (tts.getDefaultEngine() == null || !tts.getDefaultEngine().equals("com.google.android.tts")) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.NoTTSEngineAlertMessage)
                        .setTitle(R.string.NoTTSEngineAlertTitle)
                        .setPositiveButton(R.string.I_Understand, (dialog, which) -> {
                        })
                        .show();
            }
            tts.shutdown();
            pref.edit().putBoolean("checked_tts", true).apply();
        }
        checked = pref.getBoolean("checked_altimeter", false);
        if (!checked) {
            SensorManager manager = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor pressureSensor = manager.getDefaultSensor(Sensor.TYPE_PRESSURE);
            if (pressureSensor == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.NoAltimeterAlertMessage)
                        .setTitle(R.string.NoAltimeterAlertTitle)
                        .setPositiveButton(R.string.I_Understand, (dialog, which) -> {
                        });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            }
            pref.edit().putBoolean("checked_altimeter", true).apply();
        }

        final MainActivity self = this;
        this.mServiceManager.setBrowserListener(
                new BrowserHelper.BrowserListener() {
                    @Override
                    public void onLocationChanged(double lat, double lng, double floor, boolean sync) {
                        Log.d("BrowserListener", String.format("onLocationChanged,%f,%f,%f,%b", lat, lng, floor, sync));
                        current_lat = lat;
                        current_lng = lng;
                        current_floor = floor;
                    }

                    @Override
                    public void onBuildingChanged(String building) {
                        Log.d("BrowserListener", "onBuildingChanged," + building);
                        current_building = building;
                    }

                    @Override
                    public void onUIPageChanged(String page, boolean inNavigation) {
                        Log.d("BrowserListener", String.format("onUIPageChanged,%s,%b", page, inNavigation));
                        if (page.equals("control")) {
                            state = ViewState.Search;
                        } else if (page.equals("settings")) {
                            state = ViewState.SearchSetting;
                        } else if (page.equals("confirm")) {
                            state = ViewState.RouteConfirm;
                        } else if (page.startsWith("map-page")) {
                            if (inNavigation) {
                                runOnUiThread(() ->
                                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
                                state = ViewState.Navigation;
                            } else {
                                runOnUiThread(() ->
                                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
                                state = ViewState.Map;
                            }
                        } else if (page.startsWith("ui-id-")) {
                            state = ViewState.SearchDetail;
                        } else if (page.equals("confirm_floor")) {
                            state = ViewState.RouteCheck;
                        } else {
                            Log.d("BrowserListener", "unmanaged page: " + page);
                        }
                        Log.d("BrowserListener", "state: " + state.name());
                        updateUI();
                    }

                    @Override
                    public void onCheckedRestrictedArea(boolean restricted) {
                        if(!mUseScreenFilter) return;
                        mRestrictedArea = restricted;
                        if(restricted) {
                            TTSHelper.instance.speak(getString(R.string.navigationTerminated) + getString(R.string.alertRestrictedArea), true);
                            if (navigate_to != null) {
                                state = ViewState.Map;
                            }
                        }
                        updateUI();
                    }

                    @Override
                    public void onCheckedPreventWalkingArea(boolean prevented) {
                        mPreventWalking = prevented;
                        updateUI();
                    }

                    @Override
                    public void onNavigationFinished(double start, double end, String from, String to) {
                        Log.d("BrowserListener", String.format("onNavigationFinished,%f,%f,%s,%s", start, end, from, to));

                        ArrayList<String> tmpA = Utils.getStringArrayFromPref(self,"completed_post_survey_urls");
                        String targetUrl = pref.getString("post_survey",null);
                        if(targetUrl != null && !tmpA.contains(targetUrl)) {
                            File postSurveyFile = new File(self.getExternalFilesDir("config"), "post_survey.json");
                            String html = PostSurveyDialogFragment.getDialogMessage(postSurveyFile);
                            if (html != null && html.length()>0) {
                                PostSurveyDialogFragment postSurveyDialog = new PostSurveyDialogFragment();
                                postSurveyDialog.setDialogMessage(html);
                                postSurveyDialog.show(self.getFragmentManager(), "postSurveyDialog");
                                tmpA.add(targetUrl);
                                Utils.setStringArrayToPref(self,"completed_post_survey_urls",tmpA);
                            }
                        }

                        if (RatingActivity.shouldAskRating(self)) {
                            Intent intent = new Intent(MainActivity.this, RatingActivity.class);
                            intent.putExtra("start", start);
                            intent.putExtra("end", end);
                            intent.putExtra("from", from);
                            intent.putExtra("to", to);
                            startActivity(intent);
                        }
                    }

                    @Override
                    public void shouldOpenUrl(final String url) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(self);
                        builder.setMessage(R.string.Open_with_Safari_Message)
                                .setTitle(R.string.Open_with_Safari);
                        builder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                self.startActivity(browserIntent);
                            }
                        });
                        builder.setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // do nothing
                            }
                        });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                }
        );
        mServiceManager.backgroundServices(true);

        retryButton.setOnClickListener(v -> self.retry());
        webView.setVisibility(View.INVISIBLE);
        //fab.setVisibility(View.INVISIBLE);
        fab.hide();
        textView.setVisibility(View.INVISIBLE);
        retryButton.setVisibility(View.INVISIBLE);
        if (Utils.isNetworkConnected(this)) {
            Utils.checkHttpStatus(webView.getUrl(), status -> {
                Log.d("CheckHttpsStatusTask", "status: " + status);
                if (status == 200) {
                    webView.setVisibility(View.VISIBLE);
                    if(mDialogAvailable) {
                        //fab.setVisibility(View.VISIBLE);
                        fab.show();
                    }
                    textView.setVisibility(View.INVISIBLE);
                    retryButton.setVisibility(View.INVISIBLE);
                } else {
                    webView.setVisibility(View.INVISIBLE);
                    //fab.setVisibility(View.INVISIBLE);
                    fab.hide();
                    textView.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                }
            });
        } else {
            webView.setVisibility(View.INVISIBLE);
            //fab.setVisibility(View.INVISIBLE);
            fab.hide();
            textView.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
        }

//        String toID = getIntent().getStringExtra("toID");
//        if (toID != null && !toID.isEmpty()) {
//            Log.d("toID", toID);
//            requestStartNavigation(toID, true, true, true);
//        }
        use_count++;
    }

    private void startUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if(mDialogAvailable) {
            fab.setOnClickListener(view -> startActivity(new Intent(this, DialogActivity.class)));
        }
    }

    private void updateUI() {
        mCancelVisible = false;
        mDoneVisible = false;
        mBackVisible = false;
        mStopVisible = false;
        mSettingVisible = false;
        mSearchVisible = false;
        mResetLocationVisible = false;

        switch (this.state) {
            case Map:
                mSettingVisible = true;
                mSearchVisible = true;
                mResetLocationVisible = true;
                break;
            case Search:
                mCancelVisible = true;
                mSettingVisible = true;
                break;
            case SearchDetail:
                mCancelVisible = true;
                mBackVisible = true;
                break;
            case SearchSetting:
                mSearchVisible = true;
                break;
            case RouteConfirm:
                mCancelVisible = true;
                break;
            case Navigation:
                mStopVisible = true;
                mResetLocationVisible = true;
                break;
            case Transition:
                // hide all
                break;
            case RouteCheck:
                mDoneVisible = true;
                break;
            case Loading:
                mSettingVisible = true;
                break;
            default:

        }

        invalidateOptionsMenu();

        if(mDialogAvailable) {
            final boolean micVisible = (this.state == ViewState.Map) && !mRestrictedArea && !mPreventWalking;

            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (micVisible) {
                        //fab.setVisibility(View.VISIBLE);
                        fab.show();
                    } else {
                        //fab.setVisibility(View.INVISIBLE);
                        fab.hide();
                    }
                }
            });
        }
    }

    private void retry() {
        if (Utils.isNetworkConnected(this)) {
            recreate();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_2) {
            boolean flag = true;

            for(int result: grantResults){
                if(result != PackageManager.PERMISSION_GRANTED){
                    flag = false;
                }
            }
            if (flag){
                startUI();
            }else if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) == false) {
                Toast.makeText(this, getString(R.string.LocationPermission), Toast.LENGTH_LONG).show();
            }
        }else if(requestCode == REQUEST_PERMISSION_1){
            if( grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startUI();
            }else{
                Toast.makeText(this, getString(R.string.LocationPermission), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d("MainActivity", "onDestroy");
        use_count--;
        mServiceManager.backgroundServices(false);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        Log.d("MainActivity", "onResume");
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "ACCESS_FINE_LOCATION is not permitted");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED){
            Log.d("MainActivity", "BLUETOOTH_CONNECTION is not permitted");
            return;
        }

            mServiceManager.foregroundService(true);
        if (navigate_to != null) {
            requestStartNavigation(navigate_to, useElevetor, useStair, useEscalator);
            navigate_to = null;
        }
    }

    @Override
    protected void onPause() {
        Log.d("MainActivity", "onPause");
        mServiceManager.foregroundService(false);
        super.onPause();
    }

    @Override
    public void onStart() {
        Log.d("MainActivity", "onStart");
        super.onStart();
        if (restart_intent != null) {
            finish();
            startActivity(restart_intent);
        }
    }

    @Override
    protected void onStop() {
        Log.d("MainActivity", "onStop");
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (!BrowserHelper.instance.goBack()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem cancel = menu.findItem(R.id.action_cancel);
        cancel.setVisible(mCancelVisible);
        MenuItem done = menu.findItem(R.id.action_done);
        done.setVisible(mDoneVisible);
        MenuItem back = menu.findItem(R.id.action_back);
        back.setVisible(mBackVisible);
        MenuItem stop = menu.findItem(R.id.action_stop);
        stop.setVisible(mStopVisible);
        MenuItem setting = menu.findItem(R.id.action_setting);
        setting.setVisible(mSettingVisible);
        MenuItem resetLocation = menu.findItem(R.id.action_resetlocation);
        resetLocation.setVisible(mResetLocationVisible);
        MenuItem search = menu.findItem(R.id.action_search);
        search.setVisible(mSearchVisible);

        if(mUseScreenFilter && ScreenFilter.instance != null) {
            ScreenFilter.disableOptionMenus(menu);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_cancel) {
            BrowserHelper.instance.triggerWebviewControl(BrowserHelper.Control.None);
            return true;
        }
        if (id == R.id.action_done) {
            BrowserHelper.instance.triggerWebviewControl(BrowserHelper.Control.Done);
            return true;
        }
        if (id == R.id.action_back) {
            if (state == ViewState.SearchDetail) {
                BrowserHelper.instance.triggerWebviewControl(BrowserHelper.Control.BackToControl);
            }
            return true;
        }
        if (id == R.id.action_stop) {
            BrowserHelper.instance.triggerWebviewControl(BrowserHelper.Control.None);
            return true;
        }
        if (id == R.id.action_setting) {
            if (state == ViewState.Map) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            } else if (state == ViewState.Search) {
                BrowserHelper.instance.triggerWebviewControl(BrowserHelper.Control.RouteSearchOption);
            }
            return true;
        }
        if (id == R.id.action_search) {
            BrowserHelper.instance.triggerWebviewControl(BrowserHelper.Control.RouteSearch);
            return true;
        }
        if (id == R.id.action_resetlocation) {
            if(IndoorLocationManager.instance != null) {
                IndoorLocationManager.instance.overwriteLocationUnknown();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void requestStartNavigation(String toID, Boolean use_elevator, Boolean use_stair, Boolean use_escalator) {
        if (toID == null) {
            return;
        }

        String elv = "";
        String stairs = "";
        String esc = "";

        if(use_elevator!=null) {
            elv = use_elevator ? "&elv=9" : "&elv=1";
        }
        if(use_stair!=null) {
            stairs = use_stair ? "&stairs=9" : "&stairs=1";
        }
        if(use_escalator!=null) {
            esc = use_escalator ? "&esc=9" : "&esc=1";
        }

        String hash = String.format(Locale.ENGLISH, "navigate=%s&dummy=%d%s%s%s", toID,
                new Date().getTime(), elv, stairs, esc);
        BrowserHelper.instance.setLocationHash(hash);
    }

    public enum ViewState {
        Map,
        Search,
        SearchDetail,
        SearchSetting,
        RouteConfirm,
        Navigation,
        Transition,
        RouteCheck,
        Loading,
    }
}
