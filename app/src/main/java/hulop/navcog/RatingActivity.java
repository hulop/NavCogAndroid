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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import hulop.navcog.model.HLPDataUtil;
import hulop.navcog.navigation.NavUtil;

public class RatingActivity extends AppCompatActivity {
    private static String API_ENQUETE = "%s://%s/%sapi/enquete";

    private String from, to, device_id;
    private double start, end;

    private RatingBar overallStars;
    private RatingBar q1Stars;
    private RatingBar q2Stars;
    private RatingBar q3Stars;
    private RatingBar q4Stars;

    private RatingBar aq1Stars;
    private RatingBar aq2Stars;
    private RatingBar aq3Stars;

    private EditText freeComment;

    private boolean submitting;

    private WaitingDialogFragment dialogFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rating);

        Intent intent = getIntent();
        start = intent.getDoubleExtra("start", -1.0);
        end = intent.getDoubleExtra("end", -1.0);
        from = intent.getStringExtra("from");
        to = intent.getStringExtra("to");
        device_id = PreferenceManager.getDefaultSharedPreferences(this).getString("device_id", null);

        overallStars = findViewById(R.id.ratingBar_overallStars);
        q1Stars = findViewById(R.id.ratingBar_q1Stars);
        q2Stars = findViewById(R.id.ratingBar_q2Stars);
        q3Stars = findViewById(R.id.ratingBar_q3Stars);
        q4Stars = findViewById(R.id.ratingBar_q4Stars);

        TextView aq1Text = findViewById(R.id.textView_aq1);
        TextView aq2Text = findViewById(R.id.textView_aq2);
        TextView aq3Text = findViewById(R.id.textView_aq3);

        aq1Stars = findViewById(R.id.ratingBar_aq1Stars);
        aq2Stars = findViewById(R.id.ratingBar_aq2Stars);
        aq3Stars = findViewById(R.id.ratingBar_aq3Stars);

        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(new Locale("en"));
        Resources resourcesEn = createConfigurationContext(configuration).getResources();

        int visibility = resourcesEn.getString(R.string.additional_q1_text).equals("") ? View.GONE : View.VISIBLE;
        aq1Text.setVisibility(visibility);
        aq1Stars.setVisibility(visibility);

        visibility = resourcesEn.getString(R.string.additional_q2_text).equals("") ? View.GONE : View.VISIBLE;
        aq2Text.setVisibility(visibility);
        aq2Stars.setVisibility(visibility);

        visibility = resourcesEn.getString(R.string.additional_q3_text).equals("") ? View.GONE : View.VISIBLE;
        aq3Text.setVisibility(visibility);
        aq3Stars.setVisibility(visibility);

        freeComment = findViewById(R.id.free_comment);

        submitting = false;

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void submitRating() {
        submitting = true;
        dialogFragment = NavUtil.showModalWaiting(this, R.string.THANKYOU_SENDING);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String userMode = prefs.getString("user_mode", "unknown");
        JSONObject answers = new JSONObject();
        Map<String, String> data = new HashMap<>();
        try {
            answers.put("from", from != null ? from : "unknown");
            answers.put("to", to != null ? to : "unknown");
            answers.put("start", (long) start);
            answers.put("end", (long) end);
            answers.put("device_id", device_id != null ? device_id : "unknown");
            answers.put("type", "application");
            answers.put("total", (int) overallStars.getRating());
            answers.put("q1", (int) q1Stars.getRating());
            answers.put("q2", (int) q2Stars.getRating());
            answers.put("q3", (int) q3Stars.getRating());
            answers.put("q4", (int) q4Stars.getRating());

            if (aq1Stars.getVisibility() == View.VISIBLE) {
                answers.put("q5", (int) aq1Stars.getRating());
            }
            if (aq2Stars.getVisibility() == View.VISIBLE) {
                answers.put("q6", (int) aq2Stars.getRating());
            }
            if (aq3Stars.getVisibility() == View.VISIBLE) {
                answers.put("q7", (int) aq3Stars.getRating());
            }

            answers.put("comment", freeComment.getText().toString());
            answers.put("user_mode", userMode);

            data.put("answers", answers.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String server = prefs.getString("selected_hokoukukan_server", "");
        String context = prefs.getString("hokoukukan_server_context", "");
        String https = prefs.getBoolean("https_connection", true) ? "https" : "http";


        String url = String.format(API_ENQUETE, https, server, context);
        Handler handler = new Handler();
        HLPDataUtil.postRequest(url, data, jsonObject -> {
            RatingActivity.completeRating(this);
            handler.postDelayed(() -> {
                dialogFragment.dismiss();
                runOnUiThread(this::onBackPressed);
            }, 2000);
        });
    }

    public static boolean shouldAskRating(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String identifier = prefs.getString("server_id", "temp");
        boolean ask_enquete = prefs.getBoolean("ask_enquete", false);
        boolean isFirstTime = !prefs.getBoolean(identifier + "_enquete_answered", false);

        int count = prefs.getInt(identifier + "_enquete_ask_count", 0);
        if (!isFirstTime) {
            count++;
            prefs.edit().putInt(identifier + "_enquete_ask_count", count).apply();
        } else {
            prefs.edit().putInt(identifier + "_enquete_ask_count", 0).apply();
        }
        return ask_enquete && (isFirstTime || count % 5 == 0);
    }

    public static void completeRating(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String identifier = prefs.getString("server_id", "temp");
        prefs.edit().putBoolean(identifier + "_enquete_answered", true).apply();
        int count = prefs.getInt(identifier + "_enquete_ask_count", 0);
        if (count >= 5) {
            prefs.edit().putInt(identifier + "_enquete_ask_count", 0).apply();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.rating, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_submit:
                submitRating();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
