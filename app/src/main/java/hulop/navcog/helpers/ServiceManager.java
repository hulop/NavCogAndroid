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

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.UUID;

import hulop.jni.Localizer;
import hulop.navcog.R;
import hulop.navcog.localizers.BleLocalizer;
import hulop.navcog.localizers.OSLocalizer;

import static android.content.Context.VIBRATOR_SERVICE;

public class ServiceManager {
    private static final String UI_PAGE = "%s://%s/%smobile.jsp?noheader&noclose&id=%s";

    private final Activity mActivity;
    private final Handler mHandler = new Handler();
    private SharedPreferences mPrefs;
    private boolean mLoggingBLE, mShowDebugInfo;
    public static boolean sDevMode, sLoggingNavi;

    private IndoorLocationManager mIndoorManager;
    private BleLocalizer mBleLocalizer;
    private OSLocalizer mOsLocalizer;
    private SensorHelper mSensorHelper;
    private BrowserHelper mBrowserHelper;
    private BrowserHelper.BrowserListener mBrowserListener;
    private TTSHelper mTTSHelper;
    private LogHelper mLogHelper;
    private int mSensorDebug = 0;
    private String mDeviceID;
    private long lastOrientationSent;

    public ServiceManager(Activity activity) {
        this.mActivity = activity;
    }

    public void backgroundServices(boolean start) {
        if (start) {
            mPrefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
            if (mPrefs.contains("device_id")) {
                mDeviceID = mPrefs.getString("device_id", null);
            } else {
                mDeviceID = UUID.randomUUID().toString();
                mPrefs.edit().putString("device_id", mDeviceID).commit();
            }
            mPrefs.edit().putString("model", "Android " + Build.VERSION.RELEASE + " " + Build.MANUFACTURER + " " + Build.MODEL).commit();
            mPrefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
            setPrefFlags();
            if (mBrowserHelper == null) {
                mBrowserHelper = new BrowserHelper(mActivity);
                mBrowserHelper.setBrowserListener(mBrowserListener);
                mBrowserHelper.start();
                navigate(R.id.nav_maps);
            }
            if (mTTSHelper == null) {
                (mTTSHelper = new TTSHelper(mActivity)).start();
            }
            if (mLogHelper == null) {
                (mLogHelper = new LogHelper(mActivity)).start();
            }
        } else {
            mHandler.removeCallbacksAndMessages(null);
            stopForegroundServices();
            mPrefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
            if (mBrowserHelper != null) {
                mBrowserHelper.stop();
                mBrowserHelper = null;
            }
            if (mTTSHelper != null) {
                mTTSHelper.stop();
                mTTSHelper = null;
            }
            if (mLogHelper != null) {
                mLogHelper.stop();
                mLogHelper = null;
            }
        }
    }

    public void foregroundService(boolean start) {
        if (start) {
            mHandler.removeCallbacksAndMessages(null);
            if (mIndoorManager == null) {
                Localizer.LocalizeMode mode = mPrefs.getBoolean("debug_pdr", false) ? Localizer.LocalizeMode.WEAK_POSE_RANDOM_WALKER : Localizer.LocalizeMode.ONESHOT;
                mIndoorManager = new IndoorLocationManager(mActivity);
                mIndoorManager.start(mode, new Localizer.Listener() {
                    @Override
                    public void onUpdated(double x, double y, double z, double floor, double lat, double lng, double orientation, double velocity, double[] debug_info, double[] debug_latlng) {
//                System.out.println("x=" + x + ", y=" + y + ", z=" + z + ", floor=" + floor + ", orientation=" + orientation + ", velocity=" + velocity);
                        try {
                            JSONObject location = new JSONObject();
                            location.put("x", x);
                            location.put("y", y);
                            location.put("z", z);
                            location.put("floor", floor);
                            location.put("lat", lat);
                            location.put("lng", lng);
                            location.put("orientation", 999);
                            location.put("velocity", velocity);
                            if (mShowDebugInfo) {
                                if (debug_info != null) {
                                    location.put("debug_info", new JSONArray(debug_info));
                                }
                                if (debug_latlng != null) {
                                    location.put("debug_latlng", new JSONArray(debug_latlng));
                                }
                            } else {
                                location.put("debug_info", new JSONArray());
                                location.put("debug_latlng", new JSONArray());
                            }
                            mIndoorManager.setAnchor(location);
//                    System.out.println(location.toString());
                            BrowserHelper.instance.fire(String.format("onData('XYZ',%s)", location));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        long now = System.currentTimeMillis();
                        if (now < lastOrientationSent + 200) {
                            return;
                        }
                        lastOrientationSent = now;
                        try {
                            JSONObject sensor = new JSONObject();
                            sensor.put("type", "ORIENTATION");
                            sensor.put("z", mIndoorManager.convertOrientation(orientation));
                            BrowserHelper.instance.fire(String.format("onData('Sensor',[%s])", sensor));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
                String modelFileSrc = mPrefs.getString("model_path", null);
                if (modelFileSrc != null && !modelFileSrc.isEmpty()) {
                    File modelFile = new File(modelFileSrc);
                    Log.d("ServiceManager", modelFile.getPath());
                    mIndoorManager.setModel(modelFile);
                } else {
                    Log.d("ServiceManager", "model file not defined");
                }
            }
            if (mOsLocalizer == null) {
                (mOsLocalizer = new OSLocalizer(mActivity)).start(new OSLocalizer.OSListener() {
                    @Override
                    public void onSuccess(JSONArray array) {
                        if (array.length() > 0) {
                            try {
                                mSensorHelper.putLocation(array.getJSONObject(0));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
            if (mBleLocalizer == null) {
                (mBleLocalizer = new BleLocalizer(mActivity)).start(new BleLocalizer.BleListener() {
                    @Override
                    public void onSuccess(JSONArray array) {
                        if (mIndoorManager != null) {
                            mIndoorManager.setDebug(mShowDebugInfo);
                            long timestamp = System.currentTimeMillis();
                            mIndoorManager.onBeaconData(timestamp, array);
                        }
//                        BrowserHelper.instance.fire(String.format("onData('BLE',%s)", array));
                        if (sLoggingNavi && mLoggingBLE) {
                            StringBuilder sb = new StringBuilder();
                            sb.append(String.format("Beacon,%d", array.length()));
                            for (int i = 0; i < array.length(); i++) {
                                try {
                                    JSONObject obj = array.getJSONObject(i);
                                    sb.append(String.format(",%d,%d,%d", obj.getInt("major"), obj.getInt("minor"), obj.getInt("rssi")));
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                            mLogHelper.appendText(sb.toString());
                        }
                    }
                });
            }
            if (mSensorHelper == null) {
                (mSensorHelper = new SensorHelper(mActivity)).start(new SensorHelper.SensorListener() {
                    @Override
                    public void onSuccess(JSONArray array) {
                        if (mIndoorManager != null) {
                            mIndoorManager.onSensorData(array);
                        }
                        try {
                            String type = array.getJSONObject(0).getString("type");
                            if (!type.equals("ACCELEROMETER")) {
                                mSensorDebug = 0;
                            } else {
                                mSensorDebug++;
                            }
                            if (mSensorDebug <= 1) {
//                                BrowserHelper.instance.fire(String.format("onData('Sensor',%s)", array));
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        if (sLoggingNavi && mLoggingBLE) {
                            for (int i = 0; i < array.length(); i++) {
                                try {
                                    JSONObject obj = array.getJSONObject(i);
                                    switch (obj.getString("type")) {
                                        case "ACCELEROMETER":
                                            mLogHelper.appendText(String.format("Acc,%f,%f,%f,%d", obj.getDouble("x"), obj.getDouble("y"), obj.getDouble("z"), obj.getLong("timestamp")));
                                            break;
                                        case "MOTION":
                                            mLogHelper.appendText(String.format("Motion,%f,%f,%f,%d", obj.getDouble("x"), obj.getDouble("y"), obj.getDouble("z"), obj.getLong("timestamp")));
                                            break;
                                        case "ORIENTATION":
                                            mLogHelper.appendText(String.format("Orientation,%f,%f,%f,%d", obj.getDouble("x"), obj.getDouble("y"), obj.getDouble("z"), obj.getLong("timestamp")));
                                            break;
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                });
            }

        } else {
            final boolean navigating = sLoggingNavi;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopForegroundServices();
                    ((Vibrator) mActivity.getSystemService(VIBRATOR_SERVICE)).vibrate(navigating ? 500 : 100);
                }
            }, (navigating ? 900 : 300) * 1000);
        }
    }

    private void stopForegroundServices() {
        Log.d("ServiceManager", "stopForegroundServices");
        if (mIndoorManager != null) {
            mIndoorManager.stop();
            mIndoorManager = null;
        }
        if (mOsLocalizer != null) {
            mOsLocalizer.stop();
            mOsLocalizer = null;
        }
        if (mBleLocalizer != null) {
            mBleLocalizer.stop();
            mBleLocalizer = null;
        }
        if (mSensorHelper != null) {
            mSensorHelper.stop();
            mSensorHelper = null;
        }
    }

    public void navigate(int id) {
        String url = null;
        switch (id) {
            case R.id.nav_maps:
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
                if (prefs.getBoolean("developer_mode", false)) {
                    url = prefs.getString("server_url", null);
                } else {
                    CharSequence res = mActivity.getResources().getText(R.string.pref_default_server_url);
                    if (res != null) {
                        url = res.toString();
                    }
                }
                url += "?id=" + mDeviceID;
                url = String.format(UI_PAGE, "https", prefs.getString("selected_hokoukukan_server", ""), "", mDeviceID);
                break;
            case R.id.nav_debug:
                url = "file:///android_asset/debug.html";
                break;
        }
        if (url != null && mBrowserHelper != null) {
            Log.d("ServiceManager", "Loading " + url);
            mBrowserHelper.loadUrl(url);
        }
    }

    private void setPrefFlags() {
        if (sDevMode = mPrefs.getBoolean("developer_mode", false)) {
            mLoggingBLE = mPrefs.getBoolean("log_ble", false);
            mShowDebugInfo = mPrefs.getBoolean("debug_info", false);
        }
    }

    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.d("ServiceManager", "PreferenceChanged: " + key);
            setPrefFlags();
            if (mBrowserHelper != null) {
                mBrowserHelper.syncPreferences();
            }
        }
    };

    public void setBrowserListener(BrowserHelper.BrowserListener browserListener) {
        this.mBrowserListener = browserListener;
    }
}
