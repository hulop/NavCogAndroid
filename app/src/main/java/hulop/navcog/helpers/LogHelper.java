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
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import hulop.navcog.R;

public class LogHelper {
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final SimpleDateFormat DF_LOGFILE = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss'.log'");
    private static final SimpleDateFormat DF_TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private final Context mContext;
    private final File mLogDir;
    private File mLogFile;
    public static LogHelper instance;

    public LogHelper(Context context) {
        instance = this;
        this.mContext = context;
        this.mLogDir = mContext.getExternalFilesDir("log");
    }

    public void start() {
    }

    public void stop() {
        mLogFile = null;
    }

    public void appendText(String text) {
        if (ServiceManager.sDevMode) {
            if (mLogFile == null) {
                mLogFile = new File(mLogDir, DF_LOGFILE.format(new Date()));
                Log.d("LogHelper", "logfile=" + mLogFile.getPath());
            }
            doAppend(text);
        }
        if (text.equals("startNavigation")) {
            ServiceManager.sLoggingNavi = true;
        } else if (text.equals("endNavigation")) {
            ServiceManager.sLoggingNavi = false;
            stop();
        } else {
            if (text.startsWith("getRssiBias,")) {
                try {
                    if (IndoorLocationManager.instance != null) {
                        IndoorLocationManager.instance.getRssiBias(new JSONObject(text.substring(text.indexOf(",") + 1)));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doAppend(String text) {
        if (mLogFile == null) {
            return;
        }
        if (!mLogFile.getParentFile().exists()) {
            mLogFile.getParentFile().mkdirs();
        }
        String logText = DF_TIMESTAMP.format(new Date()) + " " + mContext.getString(R.string.AppName) + "[0000:0000] " + text + "\n";
//        Log.d("LogHelper", logText);
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(mLogFile, true);
            os.write(logText.getBytes(CHARSET));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
