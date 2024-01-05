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
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.UUID;

import hulop.navcog.model.Server;
import hulop.navcog.model.ServerList;
import hulop.navcog.navigation.NavUtil;

public class ServerSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!getIntent().getBooleanExtra("recreate", false)) {
            getIntent().putExtra("recreate", true);
            if (!isTaskRoot()) {
                finish();
                return;
            } else if (MainActivity.use_count > 0) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
                return;
            }
        }
        setContentView(R.layout.activity_server_selection);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.contains("device_id")) {
            String deviceID = UUID.randomUUID().toString();
            prefs.edit().putString("device_id", deviceID).apply();
        }

        File filesDir = getExternalFilesDir(null);
        File cacheDir = getExternalCacheDir();

        File serverlistJson = new File(filesDir, "serverlist.json");
        if (serverlistJson.exists()) {
            // do not download, only load serverlist.json
            askTOS(isOK -> {
                if (isOK) {
                    new LoadServerListTask(this, filesDir).execute((String) null);
                }
            });
            return;
        }

        String tempServerlistUrl = getString(R.string.ServerlistURL);

        File serverlistText = new File(filesDir, "serverlist.txt");
        InputStream serverlistTextInAssets = getFileFromAssets("serverlist.txt");
        if (serverlistText.exists()) {
            tempServerlistUrl = readFirstLine(serverlistText);
        } else if (serverlistTextInAssets != null) {
            tempServerlistUrl = readFirstLine(serverlistTextInAssets);
        }
        final String serverlistUrl = tempServerlistUrl;
        // download serverlist.json to cache dir and load serverlist.json
        askTOS(isOK -> {
            if (isOK) {
                new LoadServerListTask(this, cacheDir).execute(serverlistUrl);
            }
        });
    }

    private InputStream getFileFromAssets(String fileName) {
        InputStream is = null;
        try {
            is = getAssets().open(fileName);
        } catch (IOException e) {
            // serverlist.txt not found in Assets
            // return null
        }
        return is;
    }

    private String readFirstLine(File file) {
        String result = null;
        try {
            result = readFirstLine(new FileInputStream(file));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private String readFirstLine(InputStream is) {
        String result = null;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String url = br.readLine();
            if (url != null) {
                result = url;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private interface AskTOSListener {
        void callback(boolean isOK);
    }

    private void askTOS(AskTOSListener listener) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String termsOfServiceURL = getString(R.string.TermsOfServiceURL);
        String privacyPolicyURL = getString(R.string.PrivacyPolicyURL);

        if (!prefs.getBoolean("checked_tos", false) &&
                !termsOfServiceURL.isEmpty() && !privacyPolicyURL.isEmpty()) {

            LinearLayout layout = new LinearLayout(this);
            layout.setPadding(50, 50, 50, 0);
            TextView messageView = new TextView(this);
            messageView.setMovementMethod(TOSLinkMovementMethod.getInstance());
            messageView.setClickable(true);
            messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            layout.addView(messageView);

            String message = getString(R.string.AskTOSAlertMessage, termsOfServiceURL, privacyPolicyURL);
            messageView.setText(Html.fromHtml(message));
            String title = getString(R.string.AskTOSAlertTitle, getString(R.string.AppName));

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(layout)
                    .setTitle(title)
                    .setPositiveButton(R.string.I_Agree, (dialog, which) -> {
                        prefs.edit().putBoolean("checked_tos", true).apply();
                        listener.callback(true);
                    })
                    .show();
        } else {
            listener.callback(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.server_selection, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            recreate();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

class LoadServerListTask extends AsyncTask<String, Integer, String> {
    private AppCompatActivity activity;
    private File saveDir;
    private SharedPreferences prefs;
    private WaitingDialogFragment dialogFragment;

    LoadServerListTask(AppCompatActivity activity, File saveDir) {
        this.activity = activity;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        this.dialogFragment = NavUtil.showModalWaiting(activity, R.string.CheckServerList);
        this.saveDir = saveDir;
    }

    @Override
    protected String doInBackground(String... strings) {
        File file = new File(this.saveDir, "serverlist.json");

        if (strings[0] == null) {
            return null;
        }
        URL url = null;
        try {
            url = new URL(strings[0]);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        int count;
        InputStream input = null;
        OutputStream output = null;
        try {
            URLConnection connection = url.openConnection();
            connection.connect();
            int contentLength = connection.getContentLength();

            input = new BufferedInputStream(url.openStream(), 1048576);

            output = new FileOutputStream(file);

            byte[] data = new byte[1048576];

            long total = 0;

            while ((count = input.read(data)) != -1) {
                total += count;
                publishProgress((int) ((total * 100) / contentLength));
                if (isCancelled()) {
                    break;
                }
                Log.d("download",
                        "total: " + total + "/" + contentLength + ", " +
                                (int) ((total * 100) / contentLength));
                output.write(data, 0, count);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (output != null) {
                try {
                    output.flush();
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private void checkSettings(List<Server.Setting> settings, int serverIndex, SettingCallback callback) {
        Server.Setting setting = settings.get(serverIndex);
        String url = "https://" + setting.getHostname() + "/config/" + setting.getConfigFileName();
        Utils.checkHttpStatus(url, status -> {
            if (status == 200) {
                callback.callback(setting);
            } else {
                if (serverIndex + 1 < settings.size()) {
                    checkSettings(settings, serverIndex + 1, callback);
                }
            }
        });
    }

    private interface SettingCallback {
        void callback(Server.Setting setting);
    }

    @Override
    protected void onPostExecute(String s) {
        dialogFragment.dismiss();
        final ServerList serverList = ServerList.loadFromJSON(new File(this.saveDir, "serverlist.json"));

        ListView listView = activity.findViewById(R.id.server_listView);
        ServerListAdapter adapter = new ServerListAdapter(activity.getApplication(), R.layout.server_list_item, serverList.getList());
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            parent.setEnabled(false);
            ListView listView1 = (ListView) parent;
            Server serverListItem = (Server) listView1.getItemAtPosition(position);
            Log.d("itemSelected", serverListItem.getName());

            String serverId = serverListItem.getServerId();
            String hostname = serverListItem.getHostname();
            String configFileName = serverListItem.getConfigFileName();
            if (configFileName == null || configFileName.isEmpty()) {
                configFileName = "server_config.json";
            }
            boolean noCheckAgreement = serverListItem.isNoCheckAgreement();
            List<Server.Setting> settings = serverListItem.getSettings();

            prefs.edit()
                    .putString("server_id", serverId)
                    .putBoolean("no_checkagreement", noCheckAgreement)
                    .putString("selected_hokoukukan_server", hostname)
                    .putString("server_config_file_name", configFileName)
                    .apply();

            if (settings != null && settings.size() > 0) {
                checkSettings(settings, 0, (setting) -> {
                    prefs.edit()
                            .putString("selected_hokoukukan_server", setting.getHostname())
                            .putString("server_config_file_name", setting.getConfigFileName())
                            .apply();
                    String url = "https://" + setting.getHostname() + "/config/" + setting.getConfigFileName();
                    Log.d("LoadServerListTask", url);
                    new LoadServerConfigTask(activity).execute(url);
                });
            } else {
                String url = "https://" + hostname + "/config/" + configFileName;
                Log.d("LoadServerListTask", url);
                new LoadServerConfigTask(activity).execute(url);
            }
        });
    }

}

class LoadServerConfigTask extends AsyncTask<String, Integer, String> {
    private AppCompatActivity activity;
    private SharedPreferences prefs;
    private ProgressDialogFragment progressDialogFragment;

    LoadServerConfigTask(AppCompatActivity activity) {
        this.activity = activity;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        this.progressDialogFragment = ProgressDialogFragment.newInstance();
        this.progressDialogFragment.setMessage(activity.getString(R.string.DownloadingData));
        this.progressDialogFragment.setCancelable(false);
    }


    private void download(String urlString, File file) {
        download(urlString, file, -1L, true);
    }

    private void download(String urlString, JSONObject jsonObject, File targetFile) {
        download(urlString, jsonObject, targetFile, true);
    }

    private void download(String urlString, JSONObject jsonObject, File targetFile, boolean skipDownLoad) {
        String src = jsonObject.optString("src", null);
        long size = jsonObject.optLong("size", -1L);
        download(urlString + "/" + src, targetFile, size, skipDownLoad);
    }

    private void download(String urlString, File file, long fileSize, boolean skipDownLoad) {
        if (urlString == null) {
            return;
        }

        if (file.exists() && fileSize != -1L && skipDownLoad) {
            // skip downloading if the file is already downloaded
            if (file.length() == fileSize) {
                return;
            }
        }

        URL url = null;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        Log.d("download", urlString + ", " + file);
        int count;
        InputStream input = null;
        OutputStream output = null;
        try {
            URLConnection connection = url.openConnection();
            connection.connect();

            input = new BufferedInputStream(url.openStream(), 1048576);

            output = new FileOutputStream(file);

            byte[] data = new byte[1048576];

            long total = 0;

            while ((count = input.read(data)) != -1) {
                total += count;
                publishProgress((int) ((total * 100) / fileSize));
                if (isCancelled()) {
                    break;
                }
                Log.d("download",
                        "total: " + total + "/" + fileSize + ", " +
                                (int) ((total * 100) / fileSize));
                output.write(data, 0, count);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (output != null) {
                try {
                    output.flush();
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected String doInBackground(String... strings) {
        File configFile = new File(activity.getExternalFilesDir("config"), "server_config.json");

        WaitingDialogFragment waitingDialogFragment = NavUtil.showModalWaiting(activity, R.string.CheckServerConfig);
        download(strings[0], configFile);

        try {
            JSONObject jsonObject = Utils.readJSON(configFile);

            boolean ask_enquete = jsonObject.optBoolean("ask_enquete", false);
            boolean use_http = jsonObject.optBoolean("use_http", false);
            String conv_server = jsonObject.optString("conv_server", null);
            String conv_api_key = jsonObject.optString("conv_api_key", null);

            prefs.edit().putBoolean("ask_enquete", ask_enquete).apply();
            prefs.edit().putBoolean("https_connection", !use_http).apply();
            prefs.edit().putString("conv_server", conv_server).apply();
            prefs.edit().putString("conv_api_key", conv_api_key).apply();

            String https = use_http ? "http" : "https";
            String urlString = https + "://" + prefs.getString("selected_hokoukukan_server", "");

            File locationDir = activity.getExternalFilesDir("location");
            File presetsSaveDir = activity.getExternalFilesDir("location/presets");

            JSONObject preset_for_wheelchair = jsonObject.getJSONObject("preset_for_wheelchair");
            download(urlString, preset_for_wheelchair, new File(presetsSaveDir, "wheelchair.plist"));

            if (jsonObject.has("preset_for_stroller")) {
                prefs.edit().putBoolean("user_stroller_available", true).apply();
                JSONObject preset_for_stroller = jsonObject.getJSONObject("preset_for_stroller");
                download(urlString, preset_for_stroller, new File(presetsSaveDir, "stroller.plist"));
            }
            JSONObject preset_for_sighted = jsonObject.getJSONObject("preset_for_sighted");
            download(urlString, preset_for_sighted, new File(presetsSaveDir, "general.plist"));

            if (jsonObject.has("json_preset_for_wheelchair")) {
                JSONObject json = jsonObject.getJSONObject("json_preset_for_wheelchair");
                download(urlString, json, new File(presetsSaveDir, "wheelchair.json"));
            }
            if (jsonObject.has("json_preset_for_stroller")) {
                JSONObject json = jsonObject.getJSONObject("json_preset_for_stroller");
                download(urlString, json, new File(presetsSaveDir, "stroller.json"));
            }
            if (jsonObject.has("json_preset_for_sighted")) {
                JSONObject json = jsonObject.getJSONObject("json_preset_for_sighted");
                download(urlString, json, new File(presetsSaveDir, "general.json"));
            }

            prefs.edit().remove("post_survey").apply();
            if (jsonObject.has("post_survey")) {
                String jsonUrl = jsonObject.optString("post_survey", null);
                File post_surveyFile = new File(activity.getExternalFilesDir("config"), "post_survey.json");

                try {
                    if (post_surveyFile.exists()) {
                        post_surveyFile.delete();
                    }
                    download(urlString + "/" + jsonUrl, post_surveyFile);
                    prefs.edit()
                            .putString("post_survey", urlString + "/" + jsonUrl).apply();

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }


            waitingDialogFragment.dismiss();

            progressDialogFragment.show(activity.getSupportFragmentManager(), "progress_dialog");

            JSONArray map_files = jsonObject.optJSONArray("map_files");
            for (int i = 0; i < map_files.length(); i++) {
                JSONObject map_json = (JSONObject) map_files.get(i);
                String fileName = map_json.optString("src", "mapdata.json");
                fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
                File model = new File(locationDir, fileName);
                prefs.edit().putString("model_path", model.getPath()).apply();
                download(urlString, map_json, model, true);
            }

            progressDialogFragment.dismiss();
        } catch (Exception e) {
            if (waitingDialogFragment.isVisible()) {
                waitingDialogFragment.dismiss();
            }
            if (progressDialogFragment.isVisible()) {
                progressDialogFragment.dismiss();
            }
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        int value = values[0];
        if (progressDialogFragment != null && value >= 0) {
            progressDialogFragment.setProgress(value);
        }
    }

    @Override
    protected void onPostExecute(String s) {
        new CheckAgreementTask(activity, json -> {
            boolean agreed = json.optBoolean("agreed", false);
            String path = json.optString("path", "/init.jsp");

            prefs.edit().putString("agreement_init_path", path).apply();

            Intent intent;
            if (agreed) {
                intent = new Intent(activity, InitActivity.class);
            } else {
                intent = new Intent(activity, AgreementActivity.class);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            activity.startActivity(intent);
            activity.finish();
        }).execute();
    }
}

class TOSLinkMovementMethod extends LinkMovementMethod {

    private static LinkMovementMethod sInstance;

    public static MovementMethod getInstance() {
        if (sInstance == null)
            sInstance = new TOSLinkMovementMethod();

        return sInstance;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getAction();

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();

            x += widget.getScrollX();
            y += widget.getScrollY();

            Layout layout = widget.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            ClickableSpan[] links = buffer.getSpans(off, off, ClickableSpan.class);

            if (links.length != 0) {
                if (action == MotionEvent.ACTION_UP) {
                    if (links[0] instanceof URLSpan) {
                        String url = ((URLSpan) links[0]).getURL();
                        String title;
                        Context context = widget.getContext();
                        if (url.equals(context.getString(R.string.TermsOfServiceURL))) {
                            title = context.getString(R.string.Terms_of_Service);
                        } else {
                            title = context.getString(R.string.Privacy_Policy);
                        }
                        Intent intent = new Intent(context, WebViewActivity.class);
                        intent.setData(Uri.parse("other"));
                        intent.putExtra(WebViewActivity.EXTRA_WEBVIEW_TITLE, title);
                        intent.putExtra(WebViewActivity.EXTRA_WEBVIEW_URL, url);
                        context.startActivity(intent);
                    }
                } else if (action == MotionEvent.ACTION_DOWN) {
                    Selection.setSelection(buffer,
                            buffer.getSpanStart(links[0]),
                            buffer.getSpanEnd(links[0]));
                }
                return true;
            } else {
                Selection.removeSelection(buffer);
            }
        }

        return super.onTouchEvent(widget, buffer, event);
    }

}