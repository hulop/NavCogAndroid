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

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.RelativeLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import hulop.navcog.helpers.ScreenFilter;
import hulop.navcog.ui.Comment;
import hulop.navcog.ui.DialogViewHelper;
import hulop.navcog.ui.SpeechBubbleAdapter;

/**
 * Created by kosugiakihiro on 2017/10/24.
 */

public class DialogActivity extends AppCompatActivity {
    private Drawable _convicon = null;
    private String _name_bot = null;
    private ConversationEx _conversation = null;

    private static final HashMap<String, Integer> _map_icon = new HashMap<String, Integer>() {
        {
            put("conversation", R.drawable.conversation);
        }
    };

    ListView _view_comment = null;
    RelativeLayout _dlg_helper = null;

    private DialogViewHelper _helper = null;

    private SpeechBubbleAdapter _adapter = null;

    private com.ibm.watson.developer_cloud.conversation.v1.model.Context _conv_context = null;

    private static Intent _recognizer_intent = null;

    private SpeechRecognizer _recognizer = null;
    private boolean _listening = false;

    private synchronized SpeechRecognizer _get_recognizer() {
        if (null == this._recognizer) {
            Context context = this.getApplicationContext();
            this._recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        }
        return this._recognizer;
    }

    private void requestRecordAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String requiredPermission = Manifest.permission.RECORD_AUDIO;

            if (PackageManager.PERMISSION_DENIED == this.checkCallingOrSelfPermission(requiredPermission)) {
                requestPermissions(new String[]{requiredPermission}, 101);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                _conv_send_message("");
            }
        }
    }

    private TextToSpeech _tts = null;
    private String _tts_uttenance_id = this.getClass().getCanonicalName();//just an example

    private static interface _tts_instance_listener {
        public void _on_tts_instance(TextToSpeech tts);//null for error for instance.
    }

    private synchronized void _get_tts(final _tts_instance_listener listener) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final float speechSpeed = prefs.getFloat("speech_speed", 0.55f) * 4;

        if (null == this._tts) {
            this._tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (TextToSpeech.SUCCESS != status) {
                        _tts = null;
                    } else {
                        if(_tts != null) {
                            _tts.setSpeechRate(speechSpeed);
                        }
                    }
                    //TODO* #1 note _tts could be the reference of prior call (2nd line of this method) that is issued right after exit this clause.
                    listener._on_tts_instance(_tts);
                }
            });
            /*resolution for #1 above would be like this but cannot be compiled syntax error.
            final TextToSpeech tts = new TextToSpeech(this, new TextToSpeech.OnInitListener(){
                @Override
                public void onInit(int status) {
                    if(TextToSpeech.SUCCESS == status){
                        _tts = tts;
                        listener._on_tts_instance(_tts);
                    }else{
                        listener._on_tts_instance(null);
                    }
                }
            });*/

        } else {
            listener._on_tts_instance(this._tts);
        }
    }

    private ConversationEx _get_conversation() {
        ConversationEx conv = this._conversation;
        if (conv == null) {
            Log.d("conv", "conv is null");
            conv = new ConversationEx();
            this._conversation = conv;
        }
        return conv;
    }

    private void _request_navigation() {
        String toID = null;
        try {
            JSONObject context = new JSONObject(this._conv_context.toString());
            JSONObject dest_info = context.optJSONObject("dest_info");
            if (dest_info != null) {
                toID = dest_info.optString("nodes", null);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        boolean isNavi = this._conv_context.get("navi") ==  Boolean.TRUE;
        boolean isFinish = this._conv_context.get("finish") ==  Boolean.TRUE;
        Boolean useStair = null;
        Boolean useEscalator = null;
        Boolean useElevetor = null;

        Log.d("_request_navigation", "toID, " + toID);
        Log.d("_request_navigation", "navi, " + isNavi);

        if (isFinish || toID != null && !toID.isEmpty() && isNavi) {
            Intent intent = new Intent(this, MainActivity.class);
//            intent.putExtra("toID", toID);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            if (isNavi) {
                MainActivity.navigate_to = toID;

                Object tmpObj = this._conv_context.get("use_stair");
                if(tmpObj != null){
                    useStair = tmpObj == Boolean.TRUE;
                }
                tmpObj = this._conv_context.get("use_elevator");
                if(tmpObj != null){
                    useElevetor = tmpObj == Boolean.TRUE;
                }
                tmpObj = this._conv_context.get("use_escalator");
                if(tmpObj != null){
                    useEscalator = tmpObj == Boolean.TRUE;
                }
                MainActivity.useStair = useStair;
                MainActivity.useElevetor = useElevetor;
                MainActivity.useEscalator = useEscalator;
            }
            finish();
        } else {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    _helper._listener.tapped();
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void _conv_send_message(final String txt) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (this._conv_context == null) {
            this._conv_context = new com.ibm.watson.developer_cloud.conversation.v1.model.Context();
        } else {
            this._conv_context.put("no_welcome", true);
        }
        if (MainActivity.current_lat != null) this._conv_context.put("latitude", MainActivity.current_lat);
        if (MainActivity.current_lng != null) this._conv_context.put("longitude", MainActivity.current_lng);
        if (MainActivity.current_floor != null) this._conv_context.put("floor", MainActivity.current_floor);
        if (MainActivity.current_building != null) this._conv_context.put("building", MainActivity.current_building);
        this._conv_context.put("user_mode", prefs.getString("user_mode", "user_general"));

        final String conv_server = prefs.getString("conv_server", null);
        final String conv_api_key = prefs.getString("conv_api_key", null);
        final String client_id = prefs.getString("device_id", null);
        final com.ibm.watson.developer_cloud.conversation.v1.model.Context conv_context = this._conv_context;
        final ConversationEx conversation = _get_conversation();
        Log.d("conv", "" + conversation);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (conversation != null && conv_server != null && conv_api_key != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    MessageResponse response = conversation.message(
                            txt,
                            conv_server,
                            conv_api_key,
                            client_id,
                            conv_context
                    );
                    if (response != null) {
                        DialogActivity.this._conv_context = response.getContext();
                        String txt = Utils._join(response.getOutput().getText(), "\n");
                        String pron = (String)response.getContext().get("output_pron");
                        _new_conv_message(pron, txt);
                    } else {
                        if(Utils.isNetworkConnected(DialogActivity.this)) {
                            _new_conv_message(null, getString(R.string.serverConnectionError));
                        } else {
                            _new_conv_message(null, getString(R.string.checkNetworkConnection));
                        }
                        _listening = false;
                        _helper.inactive();
                    }
            }}).start();
        }
    }
    private void _new_conv_message(@Nullable String pron, String txt) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (this._name_bot == null) {
            String name_bot = (String) this._conv_context.get("agent_name");
            if (name_bot != null) {
                this._name_bot = name_bot;
            } else {
                this._name_bot = getString(R.string.AppName);
            }
        }
        Handler hl = new Handler(Looper.getMainLooper());
        this._get_tts(new _tts_instance_listener() {
            @Override
            public void _on_tts_instance(TextToSpeech tts) {
                if (null != tts) {
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                            //_helper.speak();
                        }

                        @Override
                        public void onDone(String utteranceId) {
//                            if (_tts_uttenance_id.equals(utteranceId)) {
//                                _helper.listen();
//                            }
                            _request_navigation();
                        }

                        @Override
                        public void onError(String utteranceId) {
                            if (_tts_uttenance_id.equals(utteranceId)) {
                                _helper.inactive();
                            }
                        }
                    });
                    _helper.speak();
                    float speechSpeed = prefs.getFloat("speech_speed", 0.55f) * 4;
                    tts.setSpeechRate(speechSpeed);
                    tts.speak(pron != null ? pron : txt, TextToSpeech.QUEUE_FLUSH, null, _tts_uttenance_id);
                }
            }
        });
        final DialogActivity activity = this;
        hl.post(new Runnable() {
            @Override
            public void run() {
                _adapter.addItem(new Comment(txt, Comment.bubbleType.fromLeft, activity._convicon, activity._name_bot));
            }
        });
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialog);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if(prefs.getBoolean("useScreenFilter", false)) {
            if(ScreenFilter.instance != null) {

                ScreenFilter screen_filter = ScreenFilter.instance;
                View filter_view = ScreenFilter.setScreenFilter(this);
                screen_filter.setListener(new ScreenFilter.Listener() {
                    @Override
                    public void onCheckedRestrictedArea(boolean restricted) {
                        if(restricted) {
                            onBackPressed();
                        }
                    }

                    @Override
                    public void onCheckedPreventWalkingArea(boolean prevented) {
                        Handler hl = new Handler(Looper.getMainLooper());
                        hl.post(new Runnable() {
                            @Override
                            public void run() {
                                ScreenFilter.setText(filter_view);
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

        requestRecordAudioPermission();

        String str_icon = prefs.getString("icon_bot", null);

        _get_conversation();
        if (str_icon != null) {
            Integer id_icon = _map_icon.get(str_icon);
            this._convicon = null != id_icon ? getResources().getDrawable(id_icon, this.getTheme()) : null;
        }

        this._view_comment = findViewById(R.id.list_comment);
        this._dlg_helper = findViewById(R.id.dlg_helper);

        Context context = this.getApplicationContext();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true);
        _recognizer_intent = intent;

        Comment[] comments = {
        };
        this._adapter = new SpeechBubbleAdapter(context, comments);
        final DialogActivity self = this;
        _view_comment.setAdapter(this._adapter);

        this._helper = new DialogViewHelper(context);

        //position = CGPoint(x: self.view.bounds.width/2, y: self.view.bounds.height - 120)
        this._helper.setup(this._dlg_helper, null, true, 100);
        this._helper._listener = new DialogViewHelper.DialogViewListener() {
            @Override
            public void tapped() {
                if (_listening) {
                    return;
                }
                if (_tts != null) {
                    _tts.stop();
                }
                SpeechRecognizer recognizer = _get_recognizer();
                //recognizer.stopListening();//to be revised.
                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        _listening = true;
//                        _helper.showText("");
                        _helper.listen();
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                        Log.d("onBeginningOfSpeech", "start");
                    }

                    @Override
                    public void onRmsChanged(float rmsdB) {
                        _helper.setMaxPower(rmsdB * 9.0f);
                    }

                    @Override
                    public void onBufferReceived(byte[] buffer) {

                    }

                    @Override
                    public void onEndOfSpeech() {
                        Log.d("onEndOfSpeech", "end");
                    }

                    @Override
                    public void onError(int error) {
                        Log.d("onError", "code: " + error);
                        if (SpeechRecognizer.ERROR_NO_MATCH == error) {
                            //hesitation, no result. probably try again.
                        }
                        _helper.inactive();
                        _listening = false;
                    }

                    @Override
                    public void onResults(Bundle results) {
                        Log.d("onResults", "results");
                        String txt = Utils._get_confident_candidate(results);
                        if (null != txt) {
//                            _helper.showText(txt);
                            self._adapter.addItem(new Comment(txt, Comment.bubbleType.fromRight, null, null));
                            _conv_send_message(txt);
                        }
                        _helper.recognize();
                        _listening = false;
                    }

                    @Override
                    public void onPartialResults(Bundle results) {
//                        String txt = Utils._get_confident_candidate(results);
//                        if (null != txt) {
//                            _helper.showText(txt);
//                        }
                    }

                    @Override
                    public void onEvent(int eventType, Bundle params) {

                    }
                });
                recognizer.startListening(_recognizer_intent);
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        if (_conv_context != null) {
            return;
        }
        this._conv_send_message("");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (_tts != null) {
            _tts.stop();
        }
        if (_recognizer != null) {
            _recognizer.cancel();
            _helper.inactive();
            _listening = false;
        }
    }

    @Override
    public void onDestroy() {
        this._view_comment = null;
        this._dlg_helper = null;
        if (_tts != null) {
            _tts.stop();
            _tts.shutdown();
            _tts = null;
        }
        if (_recognizer != null) {
            _recognizer.cancel();
            _recognizer.destroy();
            _recognizer = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
