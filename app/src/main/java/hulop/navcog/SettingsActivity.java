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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;

import hulop.navcog.helpers.IndoorLocationManager;
import hulop.navcog.helpers.ScreenFilter;
import hulop.navcog.helpers.TTSHelper;

public class SettingsActivity extends AppCompatActivity {
    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new GeneralPreferenceFragment())
                .commit();
        setupActionBar();

        SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        if(prefs.getBoolean("useScreenFilter", false)) {
            if(ScreenFilter.instance != null) {
                ScreenFilter screenFilter = ScreenFilter.instance;
                View filterView = ScreenFilter.setScreenFilter(this);
                screenFilter.setListener(new ScreenFilter.Listener() {
                    @Override
                    public void onCheckedRestrictedArea(boolean restricted) {
                        if (restricted) {
                            onBackPressed();
                        }
                    }

                    @Override
                    public void onCheckedPreventWalkingArea(boolean prevented) {
                        if (filterView == null) return;
                        Handler hl = new Handler(Looper.getMainLooper());
                        hl.post(new Runnable() {
                            @Override
                            public void run() {
                                ScreenFilter.setText(filterView);
                                ActionBar actionBar = getSupportActionBar();
                                if (actionBar != null) {
                                    actionBar.setDisplayHomeAsUpEnabled(!prevented);
                                }
                                invalidateOptionsMenu();
                            }
                        });
                    }
                });
            }
        }
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
//        if (getSupportFragmentManager().getBackStackEntryCount() <= 1) {
//            super.onBackPressed();
//        } else {
//            getSupportFragmentManager().popBackStack();
//        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class GeneralPreferenceFragment extends PreferenceFragmentCompat {
        private SharedPreferences mPrefs;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.pref_general);
            setHasOptionsMenu(true);
            mPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.

            Preference openHelp = findPreference("open_help");
            openHelp.setOnPreferenceClickListener(preference -> {
                Context context = getContext();
                Intent intent = new Intent(context, WebViewActivity.class);
                intent.setData(Uri.parse("help"));
                startActivity(intent);
                return true;
            });

            Preference openRequirements = findPreference("open_requirements");
            openRequirements.setOnPreferenceClickListener(preference -> {
                Context context = getContext();
                Intent intent = new Intent(context, WebViewActivity.class);
                intent.setData(Uri.parse("usage"));
                startActivity(intent);
                return true;
            });

            Preference speechSpeedUi = findPreference("speech_speed_ui");
            Preference.OnPreferenceChangeListener speechSpeedPreferenceChangeListener = (preference, newValue) -> {
                int value = (int) newValue;
                float speechRate = value / 100.0f;
                PreferenceManager.getDefaultSharedPreferences(preference.getContext())
                        .edit().putFloat("speech_speed", speechRate).apply();
                TTSHelper.instance.speak(String.format(getString(R.string.SPEECH_SPEED_CHECK), value), true);
                return true;
            };
            speechSpeedUi.setOnPreferenceChangeListener(speechSpeedPreferenceChangeListener);

            Preference sendEmail = findPreference("send_feedback");
            sendEmail.setOnPreferenceClickListener(preference -> {
                String address = getResources().getString(R.string.MailAddress);
                String subject = getString(R.string.feedbackSubject, getString(R.string.AppName), mPrefs.getString("device_id", ""));
                Uri uri = Uri.parse("mailto:");

                Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{address});
                intent.putExtra(Intent.EXTRA_SUBJECT, subject);
                intent.putExtra(Intent.EXTRA_TEXT, getResources().getString(R.string.feedbackBody));
                startActivity(intent);
                return true;
            });

            Preference backToModeSelection = findPreference("back_to_mode_selection");
            backToModeSelection.setOnPreferenceClickListener(preference -> {
                Activity self = getActivity();
                if (self == null) return true;
                Intent intent = new Intent(self, InitActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                self.startActivity(intent);
                self.finish();
                return true;
            });

            Preference resetLocation = findPreference("reset_location");
            resetLocation.setOnPreferenceClickListener(preference -> {
                Activity self = getActivity();
                if (self == null) return true;
                if(IndoorLocationManager.instance != null) {
                    IndoorLocationManager.instance.overwriteLocationUnknown();
                }
                Intent intent = new Intent(self, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                self.startActivity(intent);
                self.finish();
                return true;
            });

//            Preference prefDeveloper = findPreference("pref_developer");
//            final FragmentActivity activity = getActivity();
//            prefDeveloper.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
//                @Override
//                public boolean onPreferenceClick(Preference preference) {
//                    activity.getSupportFragmentManager().beginTransaction()
//                            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
//                            .replace(android.R.id.content, new DeveloperPreferenceFragment())
//                            .addToBackStack(null)
//                            .commit();
//                    return false;
//                }
//            });
        }
    }

//    public static class DeveloperPreferenceFragment extends PreferenceFragmentCompat {
//
//        @Override
//        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
//            addPreferencesFromResource(R.xml.pref_developer);
//            setHasOptionsMenu(true);
//
//            bindPreferenceSummaryToValue(findPreference("selected_hokoukukan_server"));
//            bindPreferenceSummaryToValue(findPreference("debug_bias"));
//            bindPreferenceSummaryToValue(findPreference("debug_bias_width"));
//        }
//    }
}
