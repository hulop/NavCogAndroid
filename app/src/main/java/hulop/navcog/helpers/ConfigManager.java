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
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Map;

public class ConfigManager {

    public static String[] filenamesWithSuffix(Context context, final String suffix) {
        File dir = context.getExternalFilesDir(null);
        if (dir != null) {
            return dir.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(suffix);
                }
            });
        }
        return new String[0];
    }

    public static boolean saveConfig(Context context, Map<String, Object> config, String
            name, boolean force) {
        File dir = context.getExternalFilesDir(null);
        File configFile = new File(dir, name + ".plist");
        if (!force && configFile.exists()) {
            return false;
        }
        NSDictionary dict = (NSDictionary) NSDictionary.fromJavaObject(config);
        try {
            PropertyListParser.saveAsXML(dict, configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    public static boolean loadConfig(Context context, String name) {
        File dir = context.getExternalFilesDir(null);
        File configFile = new File(dir, name);
        try {
            NSDictionary dict = (NSDictionary) PropertyListParser.parse(configFile);
            if (dict == null) {
                return false;
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (dict.containsKey("one_time_preset")) {
                NSDictionary preset = (NSDictionary) dict.get("one_time_preset");
                dict.remove("one_time_preset");

                if (preset.containsKey("id")) {
                    String key = "one_time_preset_" + preset.get("id").toString();
                    preset.remove("id");

                    if (!prefs.getBoolean(key, false)) {
                        prefs.edit().putBoolean(key, true).apply();
                        NSObject value = preset.get(key);
                        ConfigManager.putObject(prefs, key, value);
                    }
                }
            }
            for (String key : dict.allKeys()) {
                ConfigManager.putObject(prefs, key, dict.get(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private static void putObject(SharedPreferences prefs, String key, NSObject value) {
        Log.d("ConfigManager", "putObject: " + key + "," + value.toString());
        if (value.getClass().equals(NSNumber.class)) {
            NSNumber num = (NSNumber) value;
            switch (num.type()) {
                case NSNumber.BOOLEAN:
                    prefs.edit().putBoolean(key, num.boolValue()).apply();
                    break;
                case NSNumber.INTEGER:
                    prefs.edit().putInt(key, num.intValue()).apply();
                    break;
                case NSNumber.REAL:
                    prefs.edit().putFloat(key, num.floatValue()).apply();
            }
        }
        if (value.getClass().equals(NSString.class)) {
            prefs.edit().putString(key, value.toString()).apply();
        }
    }
}