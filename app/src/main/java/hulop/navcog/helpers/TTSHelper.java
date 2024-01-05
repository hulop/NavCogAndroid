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
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import java.util.Locale;

public class TTSHelper {

    private final Context mContext;
    private TextToSpeech mTTS;
    private boolean mReady = false;
    public static TTSHelper instance;


    public TTSHelper(Context context) {
        this.mContext = context;
        instance = this;
    }

    public void start() {
        mTTS = new TextToSpeech(mContext, new OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    Locale locale = Locale.getDefault();
                    int available = mTTS.isLanguageAvailable(locale);
                    Log.d("TTSHelper", "TTS init success. locale=" + locale + ", available=" + available);
                    mReady = true;
                    mTTS.setSpeechRate(1.0f);
                } else {
                    Log.e("TTSHelper", "TTS init failed. status=" + status);
                }
            }
        });

    }

    public void stop() {
        mReady = false;
        if (mTTS != null) {
            mTTS.shutdown();
        }
    }

    public void speak(String text, boolean flush) {
        float speechSpeed = PreferenceManager.getDefaultSharedPreferences(mContext).getFloat("speech_speed", 0.55f) * 4;
        mTTS.setSpeechRate(speechSpeed);
        if (mReady) {
            int queueMode = flush ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            mTTS.speak(text, queueMode, null, null);
        }
    }

    public boolean isSpeaking() {
        return mReady && mTTS.isSpeaking();
    }

}
