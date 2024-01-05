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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BleLocalizer {

    //    private static final long KEEP_DURATION = 5 * 1000;
    private static final long SCAN_ON = 6 * 1000, SCAN_OFF = 0;
    private final Handler mHandler = new Handler();
    private final ArrayList<BleScanResult> mDeviceList = new ArrayList<>();
    private final BluetoothAdapter mBluetoothAdapter;
    private BleScanner mScanner;
    private BleListener mListener;

    public BleLocalizer(Context context) {
        this.mBluetoothAdapter = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }
    }

    public interface BleListener {
        void onSuccess(JSONArray array);
    }

    public void start(final BleListener listener) {
        Log.d("BleLocalizer", "start");
        mListener = listener;
        if (mBluetoothAdapter != null) {
//            if (!mBluetoothAdapter.isEnabled()) {
//                mHandler.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        start(listener);
//                    }
//                }, 1000);
//                Log.d("BleLocalizer", "Wait BLE enable...");
//                return;
//            }
            mScanner = new BleScanner();

            mHandler.post(new Runnable() {
                private boolean scanning = false;

                @Override
                public void run() {
                    if (mScanner != null) {
                        scanning = !scanning;
                        if (mBluetoothAdapter.isEnabled()) {
                            try {
                                if (scanning) {
                                    mScanner.start();
                                } else {
                                    mScanner.stop();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        mHandler.postDelayed(this, scanning ? SCAN_ON : SCAN_OFF);
                    }
                }
            });
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mScanner != null) {
                        try {
                            JSONArray array = getData();
                            if (array.length() > 0) {
                                mListener.onSuccess(array);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        mHandler.postDelayed(this, 1000);
                    }
                }
            });
        }
    }

    public void stop() {
        Log.d("BleLocalizer", "stop");
        if (mScanner != null) {
            if (mBluetoothAdapter.isEnabled()) {
                try {
                    mScanner.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            mScanner = null;
        }
    }

    public JSONArray getData() {
        JSONArray array = new JSONArray();
        synchronized (mDeviceList) {
//            clear(System.currentTimeMillis() - KEEP_DURATION);
            for (BleScanResult result : mDeviceList) {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("timestamp", result.getTimestamp());
                    obj.put("address", result.getDevice().getAddress());
                    obj.put("uuid", result.getUuid());
                    obj.put("major", result.getMajor());
                    obj.put("minor", result.getMinor());
                    obj.put("rssi", result.getRssi());
                    array.put(obj);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            mDeviceList.clear();
        }
        return array;
    }

//    private void clear(long expire) {
//        for (Iterator<BleScanResult> it = mDeviceList.iterator(); it.hasNext(); ) {
//            if (it.next().getTimestamp() < expire) {
//                it.remove();
//            }
//        }
//    }

    private void addScanResult(BluetoothDevice device, int rssi, byte[] scanRecord, long timestamp) {
        boolean isBeacon = (scanRecord.length > 30) && (scanRecord[5] == (byte) 0x4c) && (scanRecord[6] == (byte) 0x00) && (scanRecord[7] == (byte) 0x02) && (scanRecord[8] == (byte) 0x15);
        if (isBeacon) {
            synchronized (mDeviceList) {
                for (BleScanResult result : mDeviceList) {
                    if (result.mDevice.equals(device)) {
                        result.addRssi(rssi);
                        return;
                    }
                }
                mDeviceList.add(new BleScanResult(device, rssi, scanRecord, timestamp));
            }
        }
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private static String b2x(byte[] bytes, int start, int len) {
        char[] chars = new char[len * 2];
        for (int i = 0; i < len; i++) {
            int v = bytes[start + i] & 0xFF;
            chars[i * 2] = HEX[v >>> 4];
            chars[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(chars);
    }

    private class BleScanResult {
        private final BluetoothDevice mDevice;
        private final List<Integer> mRssiList = new ArrayList<>();
        private final byte[] mScanRecord;
        private final long mTimestamp;

        public BleScanResult(BluetoothDevice device, int rssi, byte[] scanRecord, long timestamp) {
            this.mDevice = device;
            this.mRssiList.add(rssi);
            this.mScanRecord = scanRecord;
            this.mTimestamp = timestamp;
        }

        public BluetoothDevice getDevice() {
            return mDevice;
        }

        public double getRssi() {
            double sum = 0;
            for(int rssi : mRssiList) {
                sum += rssi;
            }
            return sum / mRssiList.size();
        }

        public void addRssi(int rssi) {
            mRssiList.add(rssi);
        }

        public byte[] getScanRecord() {
            return mScanRecord;
        }

        public long getTimestamp() {
            return mTimestamp;
        }

        public int getMajor() {
            return ((mScanRecord[25] & 0xff) << 8) + (mScanRecord[26] & 0xff);
        }

        public int getMinor() {
            return ((mScanRecord[27] & 0xff) << 8) + (mScanRecord[28] & 0xff);
        }

        public String getUuid() {
            return b2x(mScanRecord, 9, 4) + "-" + b2x(mScanRecord, 13, 2) + "-" + b2x(mScanRecord, 15, 2) + "-" + b2x(mScanRecord, 17, 2) + "-" + b2x(mScanRecord, 19, 6);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof BleScanResult) {
                return getDevice().equals(((BleScanResult) o).getDevice());
            }
            return false;
        }
    }

    private class BleScanner extends ScanCallback {
        private final BluetoothLeScanner mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        private final ScanSettings mScanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        private final List<ScanFilter> mScanFilterList = new ArrayList<>();

        public BleScanner() {
       //     mScanFilterList.add(new ScanFilter.Builder().setManufacturerData(0x4c, new byte[]{0x02, 0x15}).build());
        }

        public void start() {
            mBluetoothLeScanner.startScan(mScanFilterList, mScanSettings, this);
        }

        public void stop() {
            mBluetoothLeScanner.stopScan(this);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results) {
                addResult(result);
            }
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            addResult(result);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e("BleLocalizer", "Scan failed with error: " + errorCode);
        }

        private void addResult(ScanResult result) {
            long timestamp = System.currentTimeMillis() - SystemClock.elapsedRealtime() + result.getTimestampNanos() / 1000000;
            addScanResult(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes(), timestamp);
        }
    }
}
