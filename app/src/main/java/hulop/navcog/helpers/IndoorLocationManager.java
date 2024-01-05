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
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import hulop.jni.Localizer;
import hulop.navcog.Utils;

import static android.content.Context.VIBRATOR_SERVICE;

public class IndoorLocationManager {

    private final Activity mActivity;
    private SharedPreferences mPrefs;
    private Localizer mLocalizer;
    private final File mFilesDir;
    private boolean mReady = false;
    private JSONObject mAnchor;
    private Timer mTimer;
    private static final long TIMER_INTERVAL = 30 * 1000;
    private JSONArray lastBeaconData;
    private long lastBeaconTime;
    private AtomicInteger mBeaconPool = new AtomicInteger(1), mAccPool = new AtomicInteger(100), mAttPool = new AtomicInteger(10);
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    public static IndoorLocationManager instance;
    private boolean playingBack = false;
    private JSONObject mBiasLocation = null;
    private int mBiasCount = 0;
    private double mBiasSum = 0;

    private final SensorManager mSensorManager; // used to get available sensor list

    public IndoorLocationManager(Activity activity) {
        instance = this;
        mActivity = activity;
        mFilesDir = activity.getExternalFilesDir(null);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(activity);

        mSensorManager = (SensorManager) mActivity.getSystemService(Context.SENSOR_SERVICE);
    }

    public void start(Localizer.LocalizeMode mode, Localizer.Listener listener) {
        Log.d("IndoorLocationManager", "start mode=" + mode);
        if (mPrefs.contains("config_path")) {
            try {
                JSONObject options = Utils.readJSON(new File(mPrefs.getString("config_path", null)));
                options = updateOptions(options);
                mLocalizer = new Localizer(options);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mLocalizer == null) {
            mLocalizer = new Localizer(mode);
        }
        mLocalizer.setListener(listener);
        mTimer = new Timer("IndoorLocationManager", true);
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                long time = System.currentTimeMillis() - TIMER_INTERVAL;
                if (lastBeaconTime > time) {
                    Log.d("IndoorLocationManager", new Date(lastBeaconTime).toString() + " beacon=" + lastBeaconData.toString());
                }
            }
        }, TIMER_INTERVAL, TIMER_INTERVAL);
        // TODO TEST
//        setModel();
//        debugPlayback();
    }

    private JSONObject updateOptions(JSONObject options) throws JSONException {
        // check available sensors
        boolean pressureAvailable = false;
        for (int type : new int[]{Sensor.TYPE_PRESSURE}) {
            List<Sensor> sensors = mSensorManager.getSensorList(type);
            if (sensors.size() > 0) {
                pressureAvailable = true;
            }
        }

        // read json
        JSONObject updatedOptions = new JSONObject(options.toString());

        // check if the model is in the pressure supported device list.
        // String phoneID = "Android_" + Build.VERSION.RELEASE + "_" + Build.MANUFACTURER + "_" + Build.MODEL;
        final String modelID = Build.MODEL;
        boolean inList = false;
        // create a model list and read the device info from the json
        Set<String> modelList = new HashSet<String>();
        try {
            JSONArray psModels = updatedOptions.getJSONObject("deviceInfo").getJSONArray("psModels");
            for (int i = 0; i < psModels.length(); i++) {
                String modelName = psModels.getString(i);
                modelList.add(modelName);
            }
        } catch (JSONException e) {
            Log.d("IndoorLocationManager", "Skipped to load additional deviceInfo (psModels).");
        }

        for (String s : modelList) {
            if (modelID.contains(s)) {
                inList = true;
                break;
            }
        }

        if (inList) {
            Log.d("IndoorLocationManager", "Pressure sensor validated model " + modelID);
        } else {
            Log.d("IndoorLocationManager", "Pressure sensor non-validated model " + modelID);
        }


        // update parameters in the json object to predefined values
        if (!pressureAvailable || !inList) {
            Log.d("IndoorLocationManager", "Pressure unavailable");
            JSONObject value0 = updatedOptions.getJSONObject("value0");
            value0.put("usesAltimeterForFloorTransCheck", false);
            updatedOptions.put("value0", value0);
        }

        // set rss bias if the device model is specified in deviceInfo
        try {
            JSONObject rssBiases = updatedOptions.getJSONObject("deviceInfo").getJSONObject("rssiBias");
            Iterator<String> keys = rssBiases.keys();
            while(keys.hasNext()) {
                String key = keys.next();
                if (modelID.contains(key)) {
                    JSONObject value0 = updatedOptions.getJSONObject("value0");
                    double meanRssiBiasOrig = value0.getDouble("meanRssiBias_");
                    double meanRssiBias = rssBiases.getDouble(key);
                    double minRssiBias = meanRssiBias - 0.1;
                    double maxRssiBias = meanRssiBias + 0.1;
                    value0.put("meanRssiBias_", meanRssiBias);
                    value0.put("minRssiBias_", minRssiBias);
                    value0.put("maxRssiBias_", maxRssiBias);
                    updatedOptions.put("value0", value0);
                    Log.d("IndoorLocationManager", "meanRssiBias was updated from " + meanRssiBiasOrig + " to " +  meanRssiBias + ".");
                }
            }
        } catch (JSONException e) {
            Log.d("IndoorLocationManager", "Skipped to load additional deviceInfo (rssBias).");
        }


        return updatedOptions;
    }

    public void setAnchor(JSONObject location) {
        try {
            location.put("anchor", new JSONObject().put("lat", mAnchor.getDouble("latitude")).put("lng", mAnchor.getDouble("longitude")));
            location.put("rotate", mAnchor.getDouble("rotate"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public double convertOrientation(double localOrientation) throws JSONException {
        double globalOrientation = localOrientation - mAnchor.getDouble("rotate") / 180 * Math.PI;
        double x = Math.cos(globalOrientation);
        double y = Math.sin(globalOrientation);
        return -Math.atan2(x, y);
    }

    public void stop() {
        mTimer.cancel();
        mLocalizer.dispose();
        instance = null;
    }

    public void overwriteLocationUnknown() {
    	if(mReady) {
    		mLocalizer.overwriteLocationUnknown();
    	}
    }

    public void setModel(final File model) {
        if (!model.exists()) {
            Log.d("IndoorLocationManager", "setModel: no model data");
            return;
        }
        File tempModel = model;
        if (model.getPath().endsWith(".zip")) {
            File tempDir = mActivity.getExternalCacheDir();
            try {
                Utils.unzip(model, tempDir);
                String fileName = model.getName();
                fileName = fileName.substring(0, fileName.lastIndexOf(".")) + ".json";
                tempModel = new File(tempDir, fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        final File newModel = tempModel;
        execute(() -> {
            long start = System.currentTimeMillis();
            mReady = false;
            try {
                mLocalizer.setModel(newModel.getPath(), newModel.getParent());
                JSONObject localization = Utils.readJSON(newModel);
                mAnchor = localization.getJSONObject("anchor");
                mReady = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            Log.d("IndoorLocationManager", "setModel: " + (System.currentTimeMillis() - start) + "ms");
            BrowserHelper.instance.invoke("$hulop.util.loading(false)");
        });
    }

    public void setDebug(boolean debug) {
        mLocalizer.setDebug(debug);
    }

    public void putBeacons(long timestamp, final JSONArray beacons) {
//        Log.d("IndoorLocationManager", "putBeacons");
        if (mReady && mBeaconPool.get() > 0) {
            mBeaconPool.decrementAndGet();
            execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        long start = System.currentTimeMillis();
                        mLocalizer.putBeacons(timestamp, beacons);
                        if (mBiasCount > 0 && mBiasLocation != null) {
                            mBiasCount--;
                            double bias = mLocalizer.estimateBias(mBiasLocation, beacons);
                            LogHelper.instance.appendText("estimatedRssiBias," + bias);
                            mBiasSum += bias;
                            if (mBiasCount == 0) {
                                double average = mBiasSum / 5;
                                LogHelper.instance.appendText("averageRssiBias," + average);
                                mPrefs.edit().putString("debug_bias", Double.toString(average)).apply();
                                Log.d("IndoorLocationManager", "averageRssiBias: " + average);
                                ((Vibrator) mActivity.getSystemService(VIBRATOR_SERVICE)).vibrate(100);
                            }
                        }
                        Log.d("IndoorLocationManager", "putBeacons: " + (System.currentTimeMillis() - start) + "ms (" + mBeaconPool);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        mBeaconPool.incrementAndGet();
                    }
                }
            });
        }
    }

    public void putAccelerations(final JSONArray array) {
//        if (true) return;
//        Log.d("IndoorLocationManager", "putAcceleration");
        if (mReady && mAccPool.get() > 0) {
            mAccPool.decrementAndGet();
            execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        long start = System.currentTimeMillis();
                        mLocalizer.putAccelerations(array);
                        long elapsed = System.currentTimeMillis() - start;
                        if (elapsed > 0) {
//                            Log.d("IndoorLocationManager", "putAccelerations: " + elapsed + "ms " + array.length() + " items (" + mAccPool);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        mAccPool.incrementAndGet();
                    }
                }
            });
        }
    }

    public void putAttitude(final JSONObject attitude) {
//        if (true) return;
//        Log.d("IndoorLocationManager", "putAttitude");
        if (mReady && mAttPool.get() > 0) {
            mAttPool.decrementAndGet();
            execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        long start = System.currentTimeMillis();
                        try {
                            mLocalizer.putAttitude(attitude);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        long elapsed = System.currentTimeMillis() - start;
                        if (elapsed > 0) {
                            Log.d("IndoorLocationManager", "putAttitude: " + elapsed + "ms (" + mAttPool);
                        }
                    } finally {
                        mAttPool.incrementAndGet();
                    }
                }
            });
        }
    }

    public void putAltimeter(final JSONObject altimeter) {
        if (mReady && mAttPool.get() > 0) {
            mAttPool.decrementAndGet();
            execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        long start = System.currentTimeMillis();
                        try {
                            mLocalizer.putAltimeter(altimeter);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        long elapsed = System.currentTimeMillis() - start;
                        if (elapsed > 0) {
                            Log.d("IndoorLocationManager", "putAltimeter: " + elapsed + "ms (" + mAttPool);
                        }
                    } finally {
                        mAttPool.incrementAndGet();
                    }
                }
            });
        }
    }

    public void putHeading(final JSONObject heading) {
        if (mReady && mAttPool.get() > 0) {
            mAttPool.decrementAndGet();
            execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        long start = System.currentTimeMillis();
                        try {
                            mLocalizer.putHeading(heading);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        long elapsed = System.currentTimeMillis() - start;
                        if (elapsed > 0) {
                            Log.d("IndoorLocationManager", "putHeading: " + elapsed + "ms (" + mAttPool);
                        }
                    } finally {
                        mAttPool.incrementAndGet();
                    }
                }
            });
        }
    }

    public void onBeaconData(long timestamp, JSONArray array) {
        if (playingBack) return;
//        if (true) return;
        putBeacons(timestamp, array);
        lastBeaconData = array;
        lastBeaconTime = timestamp;
    }

    public void onSensorData(JSONArray array) {
        if (playingBack || array.length() == 0) return;
//        if (true) return;
        try {
            JSONObject obj = array.getJSONObject(0);
            switch (obj.getString("type")) {
                case "ACCELEROMETER":
                    putAccelerations(array);
                    break;
                case "MOTION":
                    putAttitude(obj);
                    break;
                case "ALTIMETER":
                    putAltimeter(obj);
                    break;
                case "HEADING":
                    putHeading(obj);
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getRssiBias(JSONObject params) {
        Log.d("IndoorLocationManager", "getRssiBias params=" + params);
        mBiasLocation = params;
        mBiasCount = 5;
        mBiasSum = 0;
    }

    private void execute(final Runnable command) {
        executor.execute(command);
//        new Thread(command).start();
    }
}
