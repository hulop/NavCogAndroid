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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;

import java.io.File;
import java.util.ArrayList;

import hulop.navcog.helpers.ConfigManager;
import hulop.navcog.model.UserMode;

public class InitActivity extends AppCompatActivity {
//    private AlertDialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_init);
//        this.alertDialog = NavUtil.showModalWaiting(this, R.string.CheckServerConfig);
//        Handler handler = new Handler();
//        handler.postDelayed(() -> alertDialog.dismiss(), 3000);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        ArrayList<UserMode> userModeArrayList = new ArrayList<>();
        userModeArrayList.add(new UserMode("user_wheelchair", getString(R.string.user_wheelchair)));
        if (prefs.getBoolean("user_stroller_available", false)) {
            userModeArrayList.add(new UserMode("user_stroller", getString(R.string.user_stroller)));
        }
        userModeArrayList.add(new UserMode("user_general", getString(R.string.user_general)));

        ListView listView = findViewById(R.id.user_mode_listView);
        UserModeAdapter adapter = new UserModeAdapter(this, R.layout.user_mode_list_item, userModeArrayList);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            ListView listView1 = (ListView) parent;
            UserMode serverListItem = (UserMode) listView1.getItemAtPosition(position);
            Log.d("itemSelected", serverListItem.getName());

            String userModeKey = serverListItem.getKey();

            String mode = "general";
            if (userModeKey.equals("user_wheelchair")) {
                mode = "wheelchair";
            } else if (userModeKey.equals("user_stroller")) {
                mode = "stroller";
            }
            prefs.edit().putString("user_mode", userModeKey).apply();
            ConfigManager.loadConfig(this, "location/presets/" + mode + ".plist");

            File config_file = new File(getExternalFilesDir("location/presets"), mode + ".json");
            if (config_file.exists()) {
                prefs.edit().putString("config_path", config_file.getPath()).apply();
            }
            prefs.edit().putString("ui_mode", "UI_WHEELCHAIR").apply();

            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            startActivity(intent);
            MainActivity.restart_intent = intent;
            finish();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.init, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_back) {
            Intent intent = new Intent(this, ServerSelectionActivity.class);
            intent.putExtra("recreate", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
