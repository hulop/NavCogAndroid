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

package hulop.jni;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;


public class Localizer {
    static {
        System.loadLibrary("bleloc-lib");
    }
    
    public static final double DEFAULT_BIAS_VALUE = 0.0;
    public static final double DEFAULT_BIAS_WIDTH = 2.0;
    
    public enum LocalizeMode{
        ONESHOT(0),
        RANDOM_WALK(1),
        RANDOM_WALK_ACC(2),
        RANDOM_WALK_ACC_ATT(3),
        WEAK_POSE_RANDOM_WALKER(4);
    	private final int type;
    	private LocalizeMode(final int type) {
    		this.type = type;
    	}
    	public int getInt() {
    		return this.type;
    	}
    	public boolean isTracking() {
    		return this != ONESHOT;
    	}
        public static LocalizeMode fromInt(int mode) {
            switch (mode) {
                case 0:
                    return ONESHOT;
                case 1:
                    return RANDOM_WALK;
                case 2:
                    return RANDOM_WALK_ACC;
                case 3:
                    return RANDOM_WALK_ACC_ATT;
                default:
                    return WEAK_POSE_RANDOM_WALKER;
            }
        }
    };

    public enum LocationStatus{
        UNKNOWN(0),
        LOCATING(1),
        STABLE(2),
        UNSTABLE(3),
        NIL(-1);
        private final int status;
        private LocationStatus(final int status) {
            this.status = status;
        }
        public static LocationStatus fromInt(int mode) {
            switch (mode) {
                case 0:
                    return UNKNOWN;
                case 1:
                    return LOCATING;
                case 2:
                    return STABLE;
                case 3:
                    return UNSTABLE;
                case -1:
                    return NIL;
                default:
                    return NIL;
            }
        }
    };
    
    class BiasSettings {
    	public BiasSettings(JSONArray settings) throws Exception {
    		if (settings == null) {
    			return;
    		}
    		for(int i = 0; i < settings.length(); i++) {
    			JSONObject setting = settings.getJSONObject(i);
    			this.settings.add(new BiasSetting(setting));
    		}
    	}    	
    	ArrayList<BiasSetting> settings = new ArrayList<BiasSetting>();
    	public BiasSetting findBiasSetting(String deviceType) {
    		for(BiasSetting setting: settings) {
    			if (setting.matches(deviceType)) {
    				return setting;
    			}
    		}
    		return null;
    	}
    }
    class BiasSetting {
    	public double value;
    	public final double width = DEFAULT_BIAS_WIDTH;
    	public Pattern pattern;
    	public BiasSetting(JSONObject setting) throws Exception {
			this.value = setting.getDouble("bias");
			String type = setting.getString("type");
    		this.pattern = Pattern.compile(type);
    	}
    	public boolean matches(String deviceType) {
    		Matcher m = this.pattern.matcher(deviceType);
    		return m.matches();
    	}
    }

    private final LocalizeMode mMode;
    private BiasSettings biasSettings;

    public interface Listener {
        void onUpdated(double x, double y, double z, double floor, double lat, double lng, double orientation, double velocity, double[] debug_info, double[] debug_latlng);
    }

    private long nativePtr;
    private double biasValue = 0;
    private Listener mListener;

    public Localizer(LocalizeMode mode) {
        this.mMode = mode;
        nativePtr = create(mode.getInt(), 3, 3);
    }

    public Localizer(LocalizeMode mode, JSONObject options) {
    	this.mMode = mode;
    	nativePtr = create(mode.getInt(), 3, 3, options.toString());
    }

    public Localizer(JSONObject options) {
        LocalizeMode mode;
        try {
            mode = LocalizeMode.fromInt(options.getJSONObject("value0").getInt("localizeMode"));
        } catch(Exception e) {
            e.printStackTrace();
            mode = Localizer.LocalizeMode.WEAK_POSE_RANDOM_WALKER;
        }
        this.mMode = mode;
        nativePtr = create(options.toString());
    }

    public void reset() {
	if (nativePtr != 0) {
	    reset(nativePtr);
	}
    }

    public void overwriteLocationUnknown() {
        if (nativePtr != 0) {
            overwrite_location_unknown(nativePtr);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        dispose();
        super.finalize();
    }

    public void dispose() {
        if (nativePtr != 0) {
            long ptr = nativePtr;
            nativePtr = 0;
            delete(ptr);
        }
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public double setModel(String dir, double biasValue, double biasWidth) {
        if (nativePtr != 0) {
            return set_model(nativePtr, dir, biasValue, biasWidth);
        }
        return 0;
    }

    public void setModel(String modelPath, String workingDir) {
    	//System.out.println("java:setModel ("+modelPath+", "+workingDir+")");
	
        if (nativePtr != 0) {
            set_model(nativePtr, modelPath, workingDir);
        }	
    }
    
    public void setBiasSettings(JSONArray settings) throws Exception {
   		biasSettings = new BiasSettings(settings);
    }

	public boolean setBiasWithModelType(String deviceType) {
		if (biasSettings == null || deviceType == null) {
			set_bias(nativePtr, biasValue = DEFAULT_BIAS_VALUE, DEFAULT_BIAS_WIDTH);
			return true;
		}
		BiasSetting setting = biasSettings.findBiasSetting(deviceType);
		if (setting == null) {
			setting = biasSettings.findBiasSetting("default");
			if (setting == null) {
				return false;
			}
		}
		set_bias(nativePtr, biasValue = setting.value, setting.width);
		return true;
	}

    public void setBias(double value, double width) {
    	if (nativePtr != 0) {
    		biasValue = value;
    		set_bias(nativePtr, value, width);
    	}
    }
    
    public double getBias() {
    	return biasValue;
    }

    public void setDebug(boolean debug) {
        if (nativePtr != 0) {
            set_debug(nativePtr, debug);
        }
    }

    public void putBeacons(long timestamp, JSONArray beacons) throws Exception {
        if (nativePtr != 0) {
            put_beacons(nativePtr, timestamp, getUUIDArray(beacons), getBeaconArray(beacons));
        }
    }

    public double estimateBias(JSONObject params, JSONArray beacons) throws Exception {
        if (nativePtr != 0) {
            return estimate_bias(nativePtr, params.getDouble("x"), params.getDouble("y"), params.getDouble("z"), params.getDouble("floor"), getBeaconArray(beacons));
        }
        return 0;
    }

    public void putAccelerations(JSONArray array) throws Exception {
        if (nativePtr != 0 && mMode.isTracking()) {
            try {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    put_acceleration(nativePtr, obj.getLong("timestamp"), obj.getDouble("x"), obj.getDouble("y"), obj.getDouble("z"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void putAttitude(JSONObject obj) throws Exception {
        if (nativePtr != 0 && mMode.isTracking()) {
            put_attitude(nativePtr, obj.getLong("timestamp"), obj.getDouble("x"), obj.getDouble("y"), obj.getDouble("z"));
        }
    }

    public void putAltimeter(JSONObject obj) throws Exception {
        if (nativePtr != 0 && mMode.isTracking()) {
            put_altimeter(nativePtr, obj.getLong("timestamp"), obj.getDouble("relativeAltitude"), obj.getDouble("pressure"));
        }
    }

    public void putHeading(JSONObject obj) throws Exception {
        if (nativePtr != 0) {
            put_heading(nativePtr, obj.getLong("timestamp"),
                    obj.getDouble("magneticHeading"), obj.getDouble("trueHeading"), obj.getDouble("headingAccuracy"),
                    obj.getDouble("x"), obj.getDouble("y"), obj.getDouble("z"));
        }
    }

    public void updated(double x, double y, double z, double floor, double lat, double lng, double orientation, double velocity,
                            double stdX, double stdY, double stdOrientation, int locationStatus,
                            double[] debug_info, double[] debug_latlng) {
        if (mListener != null) {
//            long now = System.currentTimeMillis();
//            if (now > mLastDebugInfo + 1000) {
//                mLastDebugInfo = now;
//            } else {
//                debug_info = null;
//            }
            LocationStatus locStatus = LocationStatus.fromInt(locationStatus);

            /*
            //TODO: use the location accuracy and the orientation accuracy to control the blue dot and arrow.
            boolean usesBlelocppAcc = true;
            double blelocppAccuracySigma = 3.0;
            double oriAccThreshold = 45;

            double locAcc = accuracyForDemo? 0.5:5.0;
            if(usesBlelocppAcc) {
                double sigma = blelocppAccuracySigma;
                locAcc = Math.max(locAcc, ((stdX + stdY)/2.0) * sigma);
            }

            if(locStatus == LocationStatus.UNKNOWN){
                lat = Double.NaN;
                lng = Double.NaN;
                orientation = Double.NaN;
            }
            if( oriAccThreshold < stdOrientation){
                orientation = Double.NaN;
            }
            */

            mListener.onUpdated(x, y, z, floor, lat, lng, mMode.isTracking() ? orientation : 999, velocity, debug_info, debug_latlng);
        }
    }

    public void logString(String string){
        //TODO: log string to a file.
    }


    private double[][] getBeaconArray(JSONArray beacons) throws Exception {
        double[][] array = new double[beacons.length()][];
        for (int i = 0; i < beacons.length(); i++) {
            JSONObject obj = beacons.getJSONObject(i);
            array[i] = new double[]{obj.getDouble("major"), obj.getDouble("minor"), obj.getDouble("rssi")};
        }
        return array;
    }

    private String[] getUUIDArray(JSONArray beacons) throws Exception {
        String[] array = new String[beacons.length()];
        for (int i = 0; i < beacons.length(); i++) {
            JSONObject obj = beacons.getJSONObject(i);
            array[i] = obj.getString("uuid");
        }
        return array;
    }

    public void print(String text) {
        System.out.println(text);
    }

    private native long create(String optionsJSON);

    private native long create(int mode, int smooth, int accuracy);

    private native long create(int mode, int smooth, int accuracy, String optionsJSON);

    private native void reset(long nativePtr);

    private native void overwrite_location_unknown(long nativePtr);

    private native void delete(long nativePtr);

    @Deprecated
    private native double set_model(long nativePtr, String dir, double biasValue, double biasWidth);

    private native void set_model(long nativePtr, String model, String workingDir);

    private native void set_bias(long nativePtr, double value, double width);
    
    private native void set_debug(long nativePtr, boolean debug);

    private native void put_beacons(long nativePtr, long timestamp, String[] uuids, double[][] beacons);
    
    private native double estimate_bias(long nativePtr, double x, double y, double z, double floor, double[][] beacons);
    
    private native void put_acceleration(long nativePtr, long timestamp, double ax, double ay, double az);

    private native void put_attitude(long nativePtr, long timestamp, double pitch, double roll, double yaw);

    private native void put_heading(long nativePtr, long timestamp, double magnetic_heading, double true_heading, double heading_accuracy,              
                                    double mx, double my, double mz);

    private native void put_local_heading(long nativePtr, long timestamp, double orientation, double orientation_deviation);

    private native void put_altimeter(long nativePtr, long timestamp, double relative_altitude, double pressure);
}
