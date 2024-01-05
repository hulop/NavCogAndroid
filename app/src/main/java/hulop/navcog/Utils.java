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

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {
    public static String _join(Collection<String> arr, String fill) {
        StringBuilder ret = null;
        for (String elm : arr) {
            if (null != ret) {
                ret.append(fill);
            } else {
                ret = new StringBuilder();
            }
            ret.append(elm);
        }
        return null != ret ? ret.toString() : "";
    }

    public static String _get_confident_candidate(Bundle results) {
        List<String> arr = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        float[] cfcs = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        int mxidx = 0;
        if (null != cfcs) {
            for (int i = 1; i < cfcs.length; i++) {
                if (cfcs[i] > cfcs[mxidx]) {
                    mxidx = i;
                }
            }
        }
        return null != arr ? arr.get(mxidx) : null;
    }

    @NonNull
    public static JSONObject readJSON(InputStream is) throws Exception {
        byte[] buff = new byte[is.available()];
        is.read(buff);
        is.close();
        return new JSONObject(new String(buff, StandardCharsets.UTF_8));
    }

    @NonNull
    public static JSONObject readJSON(File file) throws Exception {
        InputStream is = new FileInputStream(file);
        return Utils.readJSON(is);
    }

    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    public static List<String> userLanguageCandidates() {
        // Enumerate user language candidates by using first preferred language
        List<String> userLanguageCandidates = new ArrayList<>();
        String separator = "-";
        String userLanguage = Locale.getDefault().toLanguageTag(); // use first preferred language.
        if (userLanguage == null) {
            userLanguage = "en";
        }
        String[] userLangSplitted = userLanguage.split(separator);
        for (int i = 0; i < userLangSplitted.length - 1; i++) {
            int index = userLanguage.lastIndexOf(separator);
            userLanguage = userLanguage.substring(0, index);

            // for compatibility
            if (userLanguage.equals("zh-Hans")) {
                userLanguageCandidates.add("zh-CN");
            } else if (userLanguage.equals("zh-Hant")) {
                userLanguageCandidates.add("zh-TW");
            }
            userLanguageCandidates.add(userLanguage);
        }
        userLanguageCandidates.add("en");
        return userLanguageCandidates;
    }

    public static void checkHttpStatus(String url, CheckHttpStatusTask.Callback callback) {
        new CheckHttpStatusTask(url, callback).execute();
    }

    public static class CheckHttpStatusTask extends AsyncTask<String, Void, Void> {
        private Callback callback;
        private String targetUrl;
        private int httpStatusCode;

        CheckHttpStatusTask(@NonNull String url, @NonNull Callback callback) {
            this.callback = callback;
            targetUrl = url;
        }

        @Override
        protected Void doInBackground(String... strings) {
            if (targetUrl == null) {
                return null;
            }
            URL url;
            try {
                url = new URL(targetUrl);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return null;
            }
            try {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                httpStatusCode = connection.getResponseCode();
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            callback.onPostExecute(httpStatusCode);
        }

        public interface Callback {
            void onPostExecute(int status);
        }
    }

    private static final int ZIP_BUFFER = 1048576;
    private static final long ZIP_EXTRACTED_FILESIZE_LIMIT = 1024 * 1024 * 1024; //1GB
    private static final int ZIP_ENTRY_LIMIT = 1024;

    private static String validateFilename(String filename, String intendedDir)
            throws java.io.IOException {
        File f = new File(filename);
        String canonicalPath = f.getCanonicalPath();

        File iD = new File(intendedDir);
        String canonicalID = iD.getCanonicalPath();

        if (canonicalPath.startsWith(canonicalID)) {
            return canonicalPath;
        } else {
            throw new IllegalStateException("File is outside extraction target directory.");
        }
    }

    public static void unzip(File zipFile, File workingDir) throws java.io.IOException {
        ZipEntry entry;
        int entries = 0;
        long total = 0;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            while ((entry = zis.getNextEntry()) != null) {
                System.out.println("Extracting: " + entry);
                int count;
                byte data[] = new byte[ZIP_BUFFER];
                String name = validateFilename(entry.getName(), ".");
                try (BufferedOutputStream dest = new BufferedOutputStream(new FileOutputStream(new File(workingDir, name)), ZIP_BUFFER)) {
                    while (total + ZIP_BUFFER <= ZIP_EXTRACTED_FILESIZE_LIMIT && (count = zis.read(data, 0, ZIP_BUFFER)) != -1) {
                        dest.write(data, 0, count);
                        total += count;
                    }
                }
                zis.closeEntry();
                entries++;
                if (entries > ZIP_ENTRY_LIMIT) {
                    throw new IllegalStateException("Too many files to unzip.");
                }
                if (total + ZIP_BUFFER > ZIP_EXTRACTED_FILESIZE_LIMIT) {
                    throw new IllegalStateException("File being unzipped is too big.");
                }
            }
        }
    }

    public static void setStringArrayToPref(Context context, String key, ArrayList<String> values) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        JSONArray tmpA = new JSONArray();
        for (int i = 0; i < values.size(); i++) {
            tmpA.put(values.get(i));
        }
        if (!values.isEmpty()) {
            editor.putString(key, tmpA.toString());
        } else {
            editor.putString(key, null);
        }
        editor.apply();
    }

    public static ArrayList<String> getStringArrayFromPref(Context context, String key) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String json = prefs.getString(key, null);
        ArrayList<String> values = new ArrayList<String>();
        if (json != null) {
            try {
                JSONArray tmpA = new JSONArray(json);
                for (int i = 0; i < tmpA.length(); i++) {
                    values.add(tmpA.optString(i));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return values;
    }


}
