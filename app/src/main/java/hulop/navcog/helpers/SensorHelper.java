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

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.util.Log;

import org.apache.commons.math3.complex.Quaternion;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.List;

public class SensorHelper {

    private final Context mContext;
    private final SensorManager mSensorManager;
    private final MySensorEventListener mySensorEventListener;
    private final OriAverage mOriAverage = new OriAverage();
    private final AccAverage mAccAverage = new AccAverage();
    private final GyroscopeAngles mGyroAngles = new GyroscopeAngles();
    private final AccQueue mAccQueue = new AccQueue();
    private SensorListener mListener;
    private float[] accValues, magValues, pressValues;
    private float[] initialPressValues;
    private GeomagneticField geomagneticField;

    public SensorHelper(Context context) {
        this.mContext = context;
        this.mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.mySensorEventListener = new MySensorEventListener();
        this.geomagneticField = new GeomagneticField(0, 0, 0, System.currentTimeMillis());

    }

    public interface SensorListener {
        void onSuccess(JSONArray array);
    }

    public void start(SensorListener listener) {
        Log.d("SensorHelper", "start");
        mListener = listener;
        for (Sensor sensor : mSensorManager.getSensorList(Sensor.TYPE_ALL)) {
            Log.d("SensorHelper", "type=" + sensor.getType() + ", name=" + sensor.getName());
        }
        for (int type : new int[]{Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE}) {
            List<Sensor> sensors = mSensorManager.getSensorList(type);
            if (sensors.size() > 0) {
                mSensorManager.registerListener(mySensorEventListener, sensors.get(0), SensorManager.SENSOR_DELAY_FASTEST);
            }
        }
        for (int type : new int[]{Sensor.TYPE_MAGNETIC_FIELD}) {
            List<Sensor> sensors = mSensorManager.getSensorList(type);
            if (sensors.size() > 0) {
                mSensorManager.registerListener(mySensorEventListener, sensors.get(0), SensorManager.SENSOR_DELAY_FASTEST);
            }
        }
        for (int type: new int[]{Sensor.TYPE_PRESSURE}) {
            List<Sensor> sensors = mSensorManager.getSensorList(type);
            if (sensors.size() > 0) {
                mSensorManager.registerListener(mySensorEventListener, sensors.get(0), SensorManager.SENSOR_DELAY_UI);
            }
        }

        //// Sensor.TYPE_GAME_ROTATION_VECTOR : Identical to TYPE_ROTATION_VECTOR except that it doesn't use the geomagnetic field.
        //// Sensor.TYPE_GAME_ROTATION_VECTOR is appropreate for our application because it is not affected by unstable magnetic heading.
        //// Removed the registration of Sensor.TYPE_GAME_ROTATION_VECTOR because reported rotation values are unreliable on some devices.
    }

    public void stop() {
        Log.d("SensorHelper", "stop");
        mSensorManager.unregisterListener(mySensorEventListener);
    }

    private JSONObject newResult(String type, float[] values, long timestamp) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("timestamp", timestamp);
        obj.put("type", type);
        obj.put("x", values[0]);
        obj.put("y", values[1]);
        obj.put("z", values[2]);
        return obj;
    }

    private void postResult(String type, float[] values, long timestamp) {
//        addScanResult(new SensorResult(type, values, timestamp));
        try {
            mListener.onSuccess(new JSONArray().put(newResult(type, values, timestamp)));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private class MySensorEventListener implements SensorEventListener {

        private final double G = SensorManager.STANDARD_GRAVITY;
        private float[] inR = new float[16];
        private float[] outR = new float[16];
        private float[] oriValues = new float[3];


        // Define heading accuracy levels
        private double mHeadingAccuracy = -1;
        private static final int HEADING_ACCURACY_UNRELIABLE = -1;
        private static final int HEADING_ACCURACY_LOW = 180;
        private static final int HEADING_ACCURACY_MEDIUM = 90;
        private static final int HEADING_ACCURACY_HIGH = 45;

        @Override
        public void onSensorChanged(SensorEvent event) {
            long timestamp = System.currentTimeMillis() - SystemClock.elapsedRealtime() + event.timestamp / 1000000;
            float values[];
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    values = mAccAverage.add(accValues = event.values.clone(), timestamp);
                    if (values != null) {
                        // Record raw accelerometer values
                        mAccQueue.add("ACCELEROMETER", new float[]{(float) (-accValues[0] / G), (float) (-accValues[1] / G), (float) (-accValues[2] / G)}, timestamp);
                    }
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    magValues = event.values.clone();
                    calcOrientation(timestamp);
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    values = mGyroAngles.add(event);
                    if (values != null) {
                        postResult("MOTION", values, timestamp);
                    }
                    break;
                case Sensor.TYPE_GAME_ROTATION_VECTOR:
                    // Currently, TYPE_GAME_ROTATION_VECTOR is not registered to the listener.
                    // obtain roll, pitch, yaw angles in device (x,y,z) coordinate at initialization time.
                    /*
                    float[] Rmat = new float[16];
                    float[] rRmat = new float[16];
                    float[] oVals = new float[3];
                    SensorManager.getRotationMatrixFromVector(Rmat, event.values);
                    SensorManager.remapCoordinateSystem(Rmat, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, rRmat); // remap coordinate to obtain yaw=0 at initial pose.
                    SensorManager.getOrientation(rRmat, oVals);
                    double roll = oVals[1];
                    double pitch = oVals[2];
                    double yaw = -oVals[0]; // reverse an azimuth-direction value to get a yaw value.
                    values = new float[]{(float) pitch, (float) roll, (float) yaw};
                    */
                    break;
                case Sensor.TYPE_PRESSURE:
                    // get Altitude
                    if (initialPressValues == null) {
                        initialPressValues = event.values.clone();
                        pressValues = event.values.clone();
                        return;
                    }
                    float currentAltitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]);
                    float initialAltitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, initialPressValues[0]);
                    float relativeAltitude =  currentAltitude - initialAltitude;
                    pressValues = event.values.clone();

                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("timestamp", timestamp);
                        obj.put("type", "ALTIMETER");
                        obj.put("relativeAltitude", relativeAltitude);
                        obj.put("pressure", event.values[0] / 10.0);
                        mListener.onSuccess(new JSONArray().put(obj));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    Log.e("", "Unknown sensor " + event.sensor);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Update heading accuracy
            if(sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                switch(accuracy) {
                    case SensorManager.SENSOR_STATUS_UNRELIABLE:
                        mHeadingAccuracy = HEADING_ACCURACY_UNRELIABLE;
                        break;
                    case SensorManager.SENSOR_STATUS_NO_CONTACT:
                        mHeadingAccuracy = HEADING_ACCURACY_UNRELIABLE;
                        break;
                    case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                        mHeadingAccuracy = HEADING_ACCURACY_LOW;
                        break;
                    case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                        mHeadingAccuracy = HEADING_ACCURACY_MEDIUM;
                        break;
                    case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                        mHeadingAccuracy = HEADING_ACCURACY_HIGH;
                        break;
                }
            }
        }

        private void calcOrientation(long timestamp) {
            if (accValues != null && magValues != null) {
                if (SensorManager.getRotationMatrix(inR, null, accValues, magValues) && SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_X, SensorManager.AXIS_Y, outR)) {
                    float values[] = mOriAverage.add(SensorManager.getOrientation(outR, oriValues), timestamp);
                    if (values != null) {
                        postResult("ORIENTATION", new float[]{values[1], values[2], -values[0]}, timestamp);
                        double magneticHeading = Math.toDegrees(values[0]);
                        if(magneticHeading<0.0){
                            magneticHeading += 360.0;
                        }

                        double headingAccuracy = mHeadingAccuracy;

                        // true heading
                        double trueHeading = magneticHeading;
                        if (geomagneticField != null) {
                            trueHeading += geomagneticField.getDeclination();
                            if(trueHeading<0.0){
                                trueHeading += 360.0;
                            }
                        }else {
                            // put -1 (inactive) if a declination value cannot be obtained.
                            // trueHeading = -1; // commented out this line because using magneticHeading instead of trueHeading would be better than completely ignoring the heading information.
                        }

                        JSONObject obj = new JSONObject();
                        try {
                            obj.put("timestamp", timestamp);
                            obj.put("type", "HEADING");
                            obj.put("magneticHeading", magneticHeading);
                            obj.put("trueHeading", trueHeading);
                            obj.put("headingAccuracy", headingAccuracy);
                            obj.put("x", magValues[0]);
                            obj.put("y", magValues[1]);
                            obj.put("z", magValues[2]);
                            mListener.onSuccess(new JSONArray().put(obj));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private class AccAverage {
        private final AveValues[] mValues = new AveValues[]{new AveValues(), new AveValues(), new AveValues()};
        private long mLastTimestamp;
        private int mCount;

        public float[] add(float[] values, long timestamp) {
            mValues[0].add(values[0]);
            mValues[1].add(values[1]);
            mValues[2].add(values[2]);
            mCount++;
            if (timestamp >= mLastTimestamp + 10) {
//                System.out.println((timestamp - mLastTimestamp) + "ms " + mCount + "/" + mValues[0].size());
                mLastTimestamp = timestamp;
                mCount = 0;
                return new float[]{mValues[0].average(), mValues[1].average(), mValues[2].average()};
            }
            return null;
        }

        private class AveValues {
            private float sum;
            private ArrayDeque<Float> queue = new ArrayDeque<>();

            public void clear() {
                sum = 0;
                queue.clear();
            }

            public int size() {
                return queue.size();
            }

            public void add(float value) {
                sum += value;
                queue.add(value);
                if (queue.size() > 3) {
                    sum -= queue.poll();
                }
            }

            public float average() {
                return sum / queue.size();
            }
        }
    }

    private class OriAverage {
        private final AveAngles[] mValues = new AveAngles[]{new AveAngles(), new AveAngles(), new AveAngles()};
        private long mLastTimestamp;
        private int mCount;

        public float[] add(float[] values, long timestamp) {
            mValues[0].add(values[0]);
            mValues[1].add(values[1]);
            mValues[2].add(values[2]);
            mCount++;
            if (timestamp >= mLastTimestamp + 500) {
//                System.out.println((timestamp - mLastTimestamp) + "ms " + mCount + "/" + mValues[0].size());
                mLastTimestamp = timestamp;
                mCount = 0;
                return new float[]{mValues[0].average(), mValues[1].average(), mValues[2].average()};
            }
            return null;
        }

        private class AveAngles {
            private float sumSin, sumCos;
            private ArrayDeque<Float> queue = new ArrayDeque<>();

            public void clear() {
                sumSin = sumCos = 0;
                queue.clear();
            }

            public int size() {
                return queue.size();
            }

            public void add(float rad) {
                sumSin += (float) Math.sin(rad);
                sumCos += (float) Math.cos(rad);
                queue.add(rad);
                if (queue.size() > 15) {
                    float old = queue.poll();
                    sumSin -= Math.sin(old);
                    sumCos -= Math.cos(old);
                }
            }

            public float average() {
                int size = queue.size();
                return (float) Math.atan2(sumSin / size, sumCos / size);
            }
        }
    }

    private class AccQueue {
        private JSONArray mQueue = new JSONArray();
        private long mLastTimestamp;

        public void add(String type, float[] values, long timestamp) {
            try {
                mQueue.put(newResult(type, values, timestamp));
                if (timestamp > mLastTimestamp + 100) {
                    mListener.onSuccess(mQueue);
                    mQueue = new JSONArray();
                    mLastTimestamp = timestamp;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    private class GyroscopeAngles {
        // Create a constant to convert nanoseconds to seconds.
        private static final double NS2S = 1.0f / 1000000000.0f;
        private static final double EPSILON = 0.000000001f;
        private long mLastTimestamp, mPrevTimestamp;
        private Quaternion qGyroscope;

        private final double G = SensorManager.STANDARD_GRAVITY;

        // parameters for tilt correction
        final double EPS_ACC = 0.1;
        private final double betaTiltCorrection = 0.02; // (0<=beta<=1.0) A small value slowly corrects tilt angle.

        public float[] add(SensorEvent event) {
            onSensorChanged(event);
            if (event.timestamp >= mLastTimestamp + 100 * 1000000) {
                mLastTimestamp = event.timestamp;
                return getOrientation();
            }
            return null;
        }

        public void onSensorChanged(SensorEvent event) {
            if (qGyroscope == null) {
                if (accValues == null || magValues == null) {
                    return;
                }
                qGyroscope = getRotationVectorFromAccelMag(accValues, magValues);
                Log.d("SensorHelper", "initial qGyroscope=" + qGyroscope);
            }

            // This timestamp's delta rotation to be multiplied by the current rotation
            // after computing it from the gyro sample data.
            if (mPrevTimestamp != 0 && qGyroscope != null) {
                // Axis of the rotation sample, not normalized yet.
                double axisX = event.values[0];
                double axisY = event.values[1];
                double axisZ = event.values[2];

                // Calculate the angular speed of the sample
                final double omegaMagnitude = Math.sqrt(Math.pow(axisX, 2) + Math.pow(axisY, 2) + Math.pow(axisZ, 2));

                // Normalize the rotation vector if it's big enough to get the axis
                // (that is, EPSILON should represent your maximum allowable margin of error)
                if (omegaMagnitude > EPSILON) {
                    axisX /= omegaMagnitude;
                    axisY /= omegaMagnitude;
                    axisZ /= omegaMagnitude;
                }

                // Integrate around this axis with the angular speed by the timestamp
                // in order to get a delta rotation from this sample over the timestamp
                // We will convert this axis-angle representation of the delta rotation
                // into a quaternion before turning it into the rotation matrix.
                final double dT = (event.timestamp - mPrevTimestamp) * NS2S;
                final double thetaOverTwo = omegaMagnitude * dT / 2.0f;
                final double sinThetaOverTwo = Math.sin(thetaOverTwo);
                // Create a new quaternion object base on the latest rotation
                // measurements...
                final Quaternion delta = new Quaternion(Math.cos(thetaOverTwo), sinThetaOverTwo * axisX, sinThetaOverTwo * axisY, sinThetaOverTwo * axisZ);

                // Since it is a unit quaternion, we can just multiply the old rotation
                // by the new rotation delta to integrate the rotation.
                qGyroscope = qGyroscope.multiply(delta);
//                Log.d("SensorHelper", "delta=" + delta + ", qGyroscope=" + qGyroscope);
                //System.out.println("delta=" + delta);
                //System.out.println("qGyroscope=" + qGyroscope);
            }

            // Apply tilt correction
            if(accValues != null){
                Quaternion tiltCorrection = getTiltCorrectionFromAcc(qGyroscope, accValues, betaTiltCorrection);
                if(tiltCorrection!=null){
                    qGyroscope = tiltCorrection.multiply(qGyroscope);
                }
            }

            mPrevTimestamp = event.timestamp;
        }

        public float[] getOrientation() {
            if (qGyroscope == null) {
                return null;
            }
            // Now we get a structure we can pass to get a rotation matrix, and
            // then an orientation vector from Android.
            float[] vector = new float[]{
                    (float) qGyroscope.getQ1(),
                    (float) qGyroscope.getQ2(),
                    (float) qGyroscope.getQ3(),
                    (float) qGyroscope.getScalarPart()};

            // We need a rotation matrix so we can get the orientation vector...
            // Getting Euler angles from a quaternion is not trivial, so this is the easiest
            // way, but perhaps not the fastest way of doing this.
            float[] rm = new float[9];
            SensorManager.getRotationMatrixFromVector(rm, vector);

            // Get the fused orientation
            float[] values = new float[3];
            SensorManager.getOrientation(rm, values);
            return new float[]{-values[1], values[2], -values[0]};
        }

        private Quaternion getTiltCorrectionFromAcc(Quaternion qGyro, float[] accValues, double beta){
            double ax = -accValues[0];
            double ay = -accValues[1];
            double az = -accValues[2];

            final double accMagnitude = Math.sqrt(Math.pow(ax, 2) + Math.pow(ay, 2) + Math.pow(az, 2));

            final double mu = Math.abs(accMagnitude - G);
            // Calculate a tilt correction quaternion only when
            if(mu < EPS_ACC){
                Quaternion qAccBody = new Quaternion(0.0, ax, ay, az);
                Quaternion qAccWorld = qGyro.multiply(qAccBody.multiply(qGyro.getInverse()));

                // Normalize vector components of qAccWorld
                double vx = qAccWorld.getQ1()/qAccWorld.getNorm();
                double vy = qAccWorld.getQ2()/qAccWorld.getNorm();
                double vz = qAccWorld.getQ3()/qAccWorld.getNorm();

                // n = outer_product(v, n_g) where n_g = [0,0,-1] is a gravity direction in the world coordinate.
                double nx = -vy;
                double ny = vx;
                double nz = 0.0;

                double cos_phi = -vz;
                double phi = Math.acos(cos_phi);

                // Apply beta coefficient to decrease an angle value
                phi = beta*phi;

                // Calculate a tile correction quaternion
                double sinPhiOverTwo = Math.sin(phi/2.0);
                double w = Math.cos(phi/2.0);
                double x = nx * sinPhiOverTwo;
                double y = ny * sinPhiOverTwo;
                double z = nz * sinPhiOverTwo;
                Quaternion qTiltCorr = new Quaternion(w, x, y, z);

                //System.out.println("TiltCorrection: phi="+phi + ",axis=("+nx+","+ny+","+nz+")" );
                return qTiltCorr;
            }

            return null;
        }

        private Quaternion getRotationVectorFromAccelMag(float[] accValues, float[] magValues){
            float[] rm = new float[9];
            if (!SensorManager.getRotationMatrix(rm, null, accValues, magValues)) {
                return null;
            }
            float[] values = new float[3];
            SensorManager.getOrientation(rm, values);
            // Assuming the angles are in radians.

            // getOrientation() values:
            // values[0]: azimuth, rotation around the Z axis.
            // values[1]: pitch, rotation around the X axis.
            // values[2]: roll, rotation around the Y axis.

            // Heading, Azimuth, Yaw
            double d1 = values[0] / 2;
            double c1 = Math.cos(d1);
            double s1 = Math.sin(d1);

            // Pitch, Attitude
            // The equation assumes the pitch is pointed in the opposite direction
            // of the orientation vector provided by Android, so we invert it.
            double d2 = -values[1] / 2;
            double c2 = Math.cos(d2);
            double s2 = Math.sin(d2);

            // Roll, Bank
            double d3 = values[2] / 2;
            double c3 = Math.cos(d3);
            double s3 = Math.sin(d3);

            double c1c2 = c1 * c2;
            double s1s2 = s1 * s2;

            double w = c1c2 * c3 - s1s2 * s3;
            double x = c1c2 * s3 + s1s2 * c3;
            double y = s1 * c2 * c3 + c1 * s2 * s3;
            double z = c1 * s2 * c3 - s1 * c2 * s3;

            // The quaternion in the equation does not share the same coordinate
            // system as the Android gyroscope quaternion we are using. We reorder
            // it here.

            // Android X (pitch) = Equation Z (pitch)
            // Android Y (roll) = Equation X (roll)
            // Android Z (azimuth) = Equation Y (azimuth)

            Quaternion q = new Quaternion(w, z, x, y);
            return q;
        }
    }

    public void putLocation(JSONObject obj) {
        Log.d("putLocation", obj.toString());
        try {
            double latitude = obj.getDouble("latitude");
            double longitude = obj.getDouble("longitude");
            double altitude = obj.optDouble("altitude", 0.0);
            long timestamp = obj.getLong("timestamp");
            geomagneticField = new GeomagneticField((float) latitude, (float) longitude, (float) altitude, timestamp);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }
}
