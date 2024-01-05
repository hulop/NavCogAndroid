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

package hulop.navcog.localizers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class OSLocalizer {

    private static final long KEEP_DURATION = 10 * 1000;
    private final Context mContext;
    private final LocationManager mLocationManager;
    private final List<OSLocationResult> mLocationList = new ArrayList<>();
    private final MyLocationListener mLocationListener;
    private OSListener mListener;

    public OSLocalizer(Context context) {
        this.mContext = context;
        this.mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.mLocationListener = new MyLocationListener();
    }

    public interface OSListener {
        void onSuccess(JSONArray array);
    }

    public void start(OSListener listener) {
        mListener = listener;
        mLocationListener.switchLocationProvider();
    }

    public void stop() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocationManager.removeUpdates(mLocationListener);
        }
    }

    public JSONArray getData() {
        JSONArray array = new JSONArray();
        synchronized (mLocationList) {
            clear(System.currentTimeMillis() - KEEP_DURATION);
            for (OSLocationResult result : mLocationList) {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("timestamp", result.getTimestamp());
                    Location location = result.getLocation();
                    obj.put("provider", location.getProvider());
                    obj.put("latitude", location.getLatitude());
                    obj.put("longitude", location.getLongitude());
                    if (location.hasAltitude()) {
                        obj.put("altitude", location.getAltitude());
                    }
                    if (location.hasAccuracy()) {
                        obj.put("accuracy", location.getAccuracy());
                    }
                    if (location.hasBearing()) {
                        obj.put("bearing", location.getBearing());
                    }
                    if (location.hasSpeed()) {
                        obj.put("speed", location.getSpeed());
                    }
                    obj.put("time", location.getTime());
                    array.put(obj);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return array;
    }


    private void clear(long expire) {
        for (Iterator<OSLocationResult> it = mLocationList.iterator(); it.hasNext(); ) {
            if (it.next().getTimestamp() < expire) {
                it.remove();
            }
        }
    }

    private void addScanResult(OSLocationResult result) {
        synchronized (mLocationList) {
            for (int i = 0; i < mLocationList.size(); i++) {
                if (mLocationList.get(i).equals(result)) {
                    mLocationList.set(i, result);
                    return;
                }
            }
            mLocationList.add(result);
        }
    }

    private class MyLocationListener implements LocationListener {

        public void switchLocationProvider() {
            System.out.println("Switch location provider");
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mLocationManager.removeUpdates(this);
                if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    Log.d("OSLocalizer", LocationManager.GPS_PROVIDER);
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
                }
                if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    Log.d("OSLocalizer", LocationManager.NETWORK_PROVIDER);
                    mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
                }
            }
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.d("OSLocalizer", "onStatusChanged: " + location.getProvider());
            addScanResult(new OSLocationResult(location));
            mListener.onSuccess(getData());
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.d("OSLocalizer", "onStatusChanged: " + provider);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.d("OSLocalizer", "onProviderEnabled: " + provider);
            switchLocationProvider();
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.d("OSLocalizer", "onProviderDisabled: " + provider);
            switchLocationProvider();
        }
    }

    private class OSLocationResult {
        private final Location mLocation;
        private final long mTimestamp;

        public OSLocationResult(Location location) {
            this.mLocation = location;
            this.mTimestamp = System.currentTimeMillis();
        }

        public Location getLocation() {
            return mLocation;
        }

        public long getTimestamp() {
            return mTimestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof OSLocationResult) {
                return getLocation().getProvider().equals(((OSLocationResult) o).getLocation().getProvider());
            }
            return false;
        }
    }
}
