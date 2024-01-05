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

package hulop.navcog.ui;

import android.animation.Animator;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Timer;
import java.util.TimerTask;

import hulop.navcog.R;

public class DialogViewHelper {
    public interface DialogViewListener {
        void tapped();
    }

    private static abstract class _ui_thread_callback implements Runnable {
        protected DialogViewHelper self = null;

        public _ui_thread_callback(DialogViewHelper helper) {
            this.self = helper;
        }
    }

    public static enum state {
        Unknown("unknown"),
        Inactive("inactive"),
        Speaking("speaking"),
        Listening("listening"),
        Recognized("recognized");

        private final String _id;

        state(String id) {
            this._id = id;
        }

        public String getId() {
            return this._id;
        }
    }

    private EnumMap<state, EnumMap<state, _ui_thread_callback>> _anim_map = null;

    private Context _ctx = null;
    private float _density;

    private AnimLayer background;
    private AnimLayer circle;

    private AnimLayer indicatorCenter; // speak indicator center / volume indicator
    private AnimLayer indicatorLeft;
    private AnimLayer indicatorRight;

    private int mainColor = AnimLayer.blue;
    private int subColor = AnimLayer.white;
    private int backgroundColor = AnimLayer.gray;

    private AnimLayer micback;
    private ImageView mic;
    private Drawable micimgw;
    private Drawable micimgr;
    private float power = 0.0f;
    private boolean recording;
    private float threthold = 70;
    private float scale = 1.4f;
    private float speed = 1.0f;

    private Timer timer_listening = null;
    private float outFilter = 1.0f;
    private float peakDuration = 0.1f;
    private float peakTimer = 0;

    private float bScale = 1.03f;
    private float bDuration = 2.5f;

    public TextView label;

    private float Frequency = 1.0f / 30.0f;
    private float MaxDB = 120;
    private int IconBackgroundSize = 139;
    private int IconCircleSize = 113;
    private int IconSize = 90;
    private int VolumeMinSize = 30;
    private int IconSmallSize = 23;
    private int SmallIconPadding = 33;
    private int LabelHeight = 40;
    private int micIconHeight = 64;

    private int ViewSize = 142;
    private ViewGroup helperView;

    private void _set_viewSize(int size) {
        this.ViewSize = size;
        this.IconBackgroundSize = size * 139 / 142;
        this.IconCircleSize = size * 113 / 142;
        this.IconSize = size * 90 / 142;
        this.VolumeMinSize = size * 90 / 142;
        this.IconSmallSize = size * 23 / 142;
        this.SmallIconPadding = size * 33 / 142;
        this.LabelHeight = size * 40 / 142;
        this.micIconHeight = size * 64 / 142;
    }

    private state viewState = state.Unknown;

    public state getState() {
        return this.viewState;
    }

    public DialogViewListener _listener;

    private synchronized void _init_anim_map() {
        if (null != this._anim_map) {
            return;
        }
        EnumMap<state, _ui_thread_callback> mp_unknown = new EnumMap<>(state.class);
        mp_unknown.put(state.Inactive, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.inactiveAnim();
            }
        });
        mp_unknown.put(state.Speaking, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.speakpopAnim();
            }
        });
        mp_unknown.put(state.Listening, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.listenpopAnim();
            }
        });
        mp_unknown.put(state.Recognized, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.recognizeAnim();
            }
        });
        EnumMap<state, _ui_thread_callback> mp_inactive = new EnumMap<>(state.class);
        mp_inactive.put(state.Recognized, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.recognizeAnim();
            }
        });
        mp_inactive.put(state.Speaking, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.speakpopAnim();
            }
        });
        mp_inactive.put(state.Listening, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.listenpopAnim();
            }
        });
        EnumMap<state, _ui_thread_callback> mp_speaking = new EnumMap<>(state.class);
        mp_speaking.put(state.Recognized, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.recognizeAnim();
            }
        });
        mp_speaking.put(state.Inactive, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.inactiveAnim();
            }
        });
        mp_speaking.put(state.Listening, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.listenpopAnim();
            }
        });
        EnumMap<state, _ui_thread_callback> mp_listening = new EnumMap<>(state.class);
        mp_listening.put(state.Recognized, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.recognizeAnim();
            }
        });
        mp_listening.put(state.Inactive, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.inactiveAnim();
            }
        });
        mp_listening.put(state.Speaking, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.speakpopAnim();
            }
        });
        EnumMap<state, _ui_thread_callback> mp_recognized = new EnumMap<>(state.class);
        mp_recognized.put(state.Listening, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.listenpopAnim();
            }
        });
        mp_recognized.put(state.Inactive, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.inactiveAnim();
            }
        });
        mp_recognized.put(state.Speaking, new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.shrinkAnim(new _ui_thread_callback(self) {
                    @Override
                    public void run() {
                        self.speakpopAnim();
                    }
                });
            }
        });
        this._anim_map = new EnumMap<>(state.class);
        this._anim_map.put(state.Unknown, mp_unknown);
        this._anim_map.put(state.Inactive, mp_inactive);
        this._anim_map.put(state.Speaking, mp_speaking);
        this._anim_map.put(state.Listening, mp_listening);
        this._anim_map.put(state.Recognized, mp_recognized);
    }

    private _ui_thread_callback _get_anim_callback(state from, state to) {
        if (null != this._anim_map) {
            EnumMap<state, _ui_thread_callback> mp = this._anim_map.get(from);
            if (null != mp) {
                return mp.get(to);
            }
        }
        return null;
    }

    private void _anim_to(state to) {
        _ui_thread_callback clbk = this._get_anim_callback(this.getState(), to);
        if (null != clbk) {
            (new Handler(Looper.getMainLooper())).post(clbk);
        }
    }

    public DialogViewHelper(Context ctx) {
        super();
        this._ctx = ctx;
        this._density = this._ctx.getResources().getDisplayMetrics().density;
        this._init_anim_map();
    }

    public int dp_to_px(int px) {
        return Math.round(px * this._density);
    }

    public void recognize() {
        this._anim_to(state.Recognized);
    }

    public void speak() {
        this._anim_to(state.Speaking);
    }

    public void listen() {
        this._anim_to(state.Listening);
    }

    public void inactive() {
        this._anim_to(state.Inactive);
    }

    private void _set_layout(View ret, int max, int x, int y) {
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) ret.getLayoutParams();
        if (null == lp) {
            lp = new RelativeLayout.LayoutParams(max, max);
        }
        int pw = this.helperView.getWidth();
        int ph = this.helperView.getHeight();
        lp.setMargins(x - max / 2, y - max / 2, pw - x - max / 2, ph - y - max / 2);
        ret.setLayoutParams(lp);
    }

    private AnimLayer _make(int size, int max, int x, int y) {
        AnimLayer ret = new AnimLayer(this._ctx, max);
        ret.size = size;
        this._set_layout(ret, max, x, y);
        /*ret.setLayoutParams(new RelativeLayout.LayoutParams(max, max));
        ret.setLayoutParams(new RelativeLayout.MarginLayoutParams(x, y));*/
        this.helperView.addView(ret);
        //setneeddisplay in swift
        return ret;
    }

    public void setup(ViewGroup vg, PointF position, boolean tapEnabled, int dpsize) {
        int size = this.dp_to_px(dpsize);
        if (size > 0) {
            this._set_viewSize(size);
        }

        RelativeLayout rl = new RelativeLayout(this._ctx);
        rl.setGravity(Gravity.CENTER);
        this.helperView = rl;
        ((RelativeLayout) vg).setGravity(Gravity.CENTER);
        vg.addView(this.helperView, -1, new ViewGroup.LayoutParams(this.ViewSize, this.ViewSize));
        if (tapEnabled) {
            this.helperView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (null != _listener) {
                        _listener.tapped();
                    }
                }
            });
        }
        int cx = this.ViewSize / 2;
        int cy = this.ViewSize / 2;

        this.background = this._make(this.IconBackgroundSize, this.IconBackgroundSize, cx, cy);
        this.background.color = backgroundColor;
        this.background.setZ(0.0f);
        this.circle = this._make(this.IconCircleSize, this.IconCircleSize, cx, cy);
        this.circle.color = subColor;
        this.circle.setZ(1.0f);

        this.indicatorCenter = this._make(this.IconSmallSize, this.IconCircleSize, cx, cy);//IconSmallSize2 * 2 or 3 does not match the max value. e.g. listenanim
        //this.indicatorCenter.color = subColor;
        this.indicatorCenter.setZ(2.0f);
        this.indicatorCenter.setAlpha(1.0f);
        this.indicatorLeft = this._make(this.IconSmallSize, this.IconCircleSize, cx - this.SmallIconPadding, cy);
        this.indicatorLeft.setZ(2.0f);
        this.indicatorLeft.setAlpha(1.0f);
        this.indicatorRight = this._make(this.IconSmallSize, this.IconCircleSize, cx + this.SmallIconPadding, cy);
        this.indicatorRight.setZ(2.0f);
        this.indicatorRight.setAlpha(1.0f);

        this.micback = this._make(this.IconSize, this.IconSize * 2, cx, cy);
        this.micback.setAlpha(0.0f);
        this.micback.setZ(3.0f);

        this.micimgr = this._ctx.getDrawable(R.drawable.mic_blue);
        this.micimgw = this._ctx.getDrawable(R.drawable.mic);
        this.mic = new ImageView(this._ctx);
        this.mic.setZ(4.0f);
        this._set_layout(this.mic, this.micIconHeight, cx, cy);
        this.mic.setImageDrawable(this.micimgw);
        this.mic.setImageAlpha(0);
        //anitaliasingmask...
        this.helperView.addView(this.mic);

        this.label = new TextView(this._ctx);
        RelativeLayout.LayoutParams tlps = new RelativeLayout.LayoutParams(this.dp_to_px(1000), this.LabelHeight);
        tlps.setMargins(0, this.ViewSize - this.LabelHeight, 0, 0);
        this.label.setLayoutParams(tlps);//original code setting 1000 for width here.
        this.label.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        //this.label.setHorizontallyScrolling(true);
        //set font and size...
        //probably hide overflow text.
        this.label.setAlpha(1.0f);
        this.label.setTextColor(this._ctx.getResources().getColor(android.R.color.primary_text_light, this._ctx.getTheme()));
        this.label.setTextSize(22.0f);
        //this.label.setMarqueeRepeatLimit(3);
        this.label.setText("");

        this.label.setZ(5.0f);
        this.helperView.addView(this.label);
        //set label layout constraints in helper view to be center of the bound.

    }

    public void setMaxPower(float p) {
        if (this.power < p) {
            this.power = this.power * (1 - this.outFilter) + p * this.outFilter;
            this.peakTimer = this.peakDuration;
        }
    }


    private static class _text_animator implements Runnable {
        private int _pos = 0;
        private int _max = 0;
        private String _txt = null;
        private TextView _tv = null;
        private Handler _handler = null;
        private long _interval = 0;

        public _text_animator(TextView tv, String txt, long interval) {
            super();
            this._txt = txt;
            this._max = this._txt.length();
            this._tv = tv;
            this._interval = interval;
            this.start();
        }

        private void start() {
            this.cancel();
            this._handler = new Handler(Looper.getMainLooper());
            this._handler.post(this);
        }

        @Override
        public void run() {
            this._pos++;
            this._tv.setText(this._txt.substring(0, this._pos));
            if (this._pos < this._max) {
                this._handler.postDelayed(this, this._interval);
            } else {
                this.cancel();
            }
        }

        public synchronized void cancel() {
            if (null != this._handler) {
                this._handler.removeCallbacks(this);
                this._handler = null;
            }
        }
    }

    private _text_animator _textanimator = null;
    private boolean showtext_animation = true;

    public synchronized void showText(String text) {
        int len = text.length();
        if (len > 0 && this.showtext_animation) {
            String curtext = this.label.getText().toString();
            if (!curtext.equals(text)) {
                if (null != this._textanimator) {
                    this._textanimator.cancel();
                }
                this._textanimator = new _text_animator(this.label, text, 100);
            }
        } else {
            this.label.setText(text);
        }
    }

    /*private static void delay(long delay, _ui_thread_callback callback){
        (new Handler()).postDelayed(callback, delay);
    }*/
    private ArrayList<Animator> _anims = new ArrayList<Animator>();

    public void _add_anim_and_start(Animator anim) {
        anim.start();
        this._anims.add(anim);
    }

    private void _cancel_timer_listening() {
        if (null != this.timer_listening) {
            this.timer_listening.cancel();
            this.timer_listening = null;
        }
    }

    public void reset() {
        //remove all animations
        for (Animator anim : this._anims) {
            anim.cancel();
        }
        this._anims = new ArrayList<Animator>();

        this._cancel_timer_listening();
        //for l:calayer in [background, circle, indicatorCenter, indicatorLeft, indicatorRight, micback, mic] l?.removeAllAnimations
    }

    private void recognizeAnim() {
        Log.d("DialogViewHelper", "recognizeAnim");
        this.reset();
        this.circle.color = mainColor;
        this.micback.color = subColor;
        this.circle.invalidate();
        this.micback.invalidate();
        this.mic.setImageDrawable(this.micimgr);
        this.indicatorCenter.setAlpha(0);
        this.indicatorLeft.setAlpha(0);
        this.indicatorRight.setAlpha(0);
        this.micback.setAlpha(1.0f);
        this.mic.setImageAlpha(255);
        this._add_anim_and_start(AnimLayer.delay(AnimLayer.blink(this.circle, 500, 2), 100));
        (new Handler(Looper.getMainLooper())).postDelayed(new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.viewState = state.Recognized;
            }
        }, 500);
    }

    private void listening() {
        final float p = Math.max((this.power - this.threthold) / (this.MaxDB - this.threthold), 0);
        this.peakTimer -= this.Frequency;
        if (this.peakTimer < 0) {
            this.power -= this.MaxDB * this.Frequency / this.speed;
            this.power = Math.max(this.power, 0);
        }
        (new Handler(Looper.getMainLooper())).post(new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.indicatorCenter.size = (Math.round(
                        Math.min((p * (self.scale - 1.0f) + 1.0f) * self.IconSize, self.IconCircleSize)
                ));
                self.indicatorCenter.invalidate();
            }
        });
    }

    private void listenAnim() {
        Log.d("DialogViewHelper", "listenAnim");
        this.reset();
        this.circle.color = subColor;
        this.micback.color = mainColor;
        this.mic.setImageDrawable(this.micimgw);
        this.mic.setImageAlpha(255);
        this.indicatorCenter.size = this.IconSize;
        this.indicatorCenter.invalidate();
        this.indicatorCenter.setAlpha(1.0f);
        this.indicatorLeft.setAlpha(0);
        this.indicatorLeft.setAlpha(0);
        this.micback.size = this.IconSize;
        this.micback.setAlpha(1.0f);
        this.circle.invalidate();
        this.micback.invalidate();
        Animator a2 = AnimLayer.pulse(this.micback, Math.round(this.bDuration * 1000), this.IconSize, this.bScale, 10000000);
        this._add_anim_and_start(a2);

        this.viewState = state.Listening;
        this._cancel_timer_listening();
        this.timer_listening = new Timer();
        final DialogViewHelper self = this;
        this.timer_listening.schedule(new TimerTask() {
            @Override
            public void run() {
                self.listening();
            }
        }, Math.round(this.Frequency * 1000), Math.round(this.Frequency * 1000));
        this.viewState = state.Listening;
    }

    private void listenpopAnim() {
        Log.d("DialogViewHelper", "listenpopAnim");
        this.reset();
        this.indicatorCenter.setAlpha(1.0f);
        this.indicatorLeft.setAlpha(0);
        this.indicatorRight.setAlpha(0);
        this.micback.setAlpha(0);
        this.mic.setImageAlpha(0);
        this.mic.setImageDrawable(this.micimgw);
        this.indicatorCenter.size = this.IconSize;
        this.indicatorCenter.invalidate();
        this.micback.size = this.IconSize;
        this.circle.color = subColor;
        this.micback.color = mainColor;
        this.circle.invalidate();
        this.micback.invalidate();

        this._add_anim_and_start(AnimLayer.scale(this.indicatorCenter, 100, 23 / this.IconSize, 1.0f, new LinearInterpolator()));
        this._add_anim_and_start(AnimLayer.delay(AnimLayer.dissolve(this.micback, 100, new LinearInterpolator()), 100));
        this._add_anim_and_start(AnimLayer.delay(AnimLayer.dissolve(this.mic, 100, new LinearInterpolator()), 100));
        (new Handler(Looper.getMainLooper())).postDelayed(new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.listenAnim();
            }
        }, 200);
    }

    private void shrinkAnim(_ui_thread_callback startparallel) {
        Log.d("DialogViewHelper", "shrinkAnim");
        if (this.mic.getImageAlpha() == 0 && null != startparallel) {
            (new Handler(Looper.getMainLooper())).post(startparallel);
        }
        this.reset();
        this.indicatorCenter.setAlpha(1.0f);
        this.indicatorLeft.setAlpha(0);
        this.indicatorRight.setAlpha(0);
        this.micback.setAlpha(1.0f);
        this.mic.setImageAlpha(255);
        this.mic.setImageDrawable(this.micimgw);
        this.circle.color = subColor;
        this.micback.color = mainColor;
        this.circle.invalidate();
        this.micback.invalidate();

        Interpolator i = new DecelerateInterpolator();
        this._add_anim_and_start(AnimLayer.dissolveOut(this.mic, 200, i));
        this._add_anim_and_start(AnimLayer.dissolveOut(this.micback, 200, i));
        this._add_anim_and_start(AnimLayer.scale(this.indicatorCenter, 200, this.IconSize, 0, i));
        this._add_anim_and_start(AnimLayer.scale(this.indicatorLeft, 200, this.IconSize, 0, i));
        this._add_anim_and_start(AnimLayer.scale(this.indicatorRight, 200, this.IconSize, 0, i));

        if (null != startparallel) {
            (new Handler(Looper.getMainLooper())).post(startparallel);
        }
    }

    private void speakAnim() {
        Log.d("DialogViewHelper", "speakAnim");
        this.reset();
        this.indicatorCenter.setAlpha(1.0f);
        this.indicatorLeft.setAlpha(1.0f);
        this.indicatorRight.setAlpha(1.0f);
        this.indicatorCenter.size = this.indicatorLeft.size = this.indicatorRight.size = this.IconSmallSize;
        this.indicatorCenter.invalidate();
        this.indicatorLeft.invalidate();
        this.indicatorRight.invalidate();
        this.circle.color = subColor;
        this.circle.invalidate();

        this._add_anim_and_start(AnimLayer.pulse(this.indicatorCenter, 250, this.IconSmallSize, 1.03f, 1000));
        this._add_anim_and_start(AnimLayer.pulse(this.indicatorLeft, 250, this.IconSmallSize, 1.03f, 1000));
        this._add_anim_and_start(AnimLayer.pulse(this.indicatorRight, 250, this.IconSmallSize, 1.03f, 1000));
        this.viewState = state.Speaking;
    }

    private void speakpopAnim() {
        Log.d("DialogViewHelper", "speakpopAnim");
        this.reset();
        this.indicatorCenter.setAlpha(1.0f);
        this.indicatorLeft.setAlpha(1.0f);
        this.indicatorRight.setAlpha(1.0f);
        this.indicatorCenter.size = this.indicatorLeft.size = this.indicatorRight.size = this.IconSmallSize;//1;
        this.indicatorCenter.invalidate();
        this.indicatorLeft.invalidate();
        this.indicatorRight.invalidate();
        this.micback.setAlpha(0);
        this.mic.setImageAlpha(0);
        this.mic.setImageResource(android.R.color.transparent);
        this.micback.invalidate();
        this.mic.invalidate();
        this.circle.color = subColor;
        this.circle.invalidate();

        Interpolator easeout = new DecelerateInterpolator();
        Interpolator linear = new LinearInterpolator();
        this._add_anim_and_start(AnimLayer.dissolve(this.indicatorCenter, 200, easeout));
        this._add_anim_and_start(AnimLayer.scale(this.indicatorCenter, 200, 1 / this.IconSmallSize, 1.0f, linear));
        this._add_anim_and_start(AnimLayer.dissolve(this.indicatorLeft, 200, easeout));
        this._add_anim_and_start(AnimLayer.scale(this.indicatorLeft, 200, 1 / this.IconSmallSize, 1.0f, linear));
        this._add_anim_and_start(AnimLayer.dissolve(this.indicatorRight, 200, easeout));
        this._add_anim_and_start(AnimLayer.scale(this.indicatorRight, 200, 1 / this.IconSmallSize, 1.0f, linear));

        (new Handler(Looper.getMainLooper())).postDelayed(new _ui_thread_callback(this) {
            @Override
            public void run() {
                self.speakAnim();
            }
        }, 210);
    }

    private void inactiveAnim() {
        Log.d("DialogViewHelper", "inactiveAnim");
        this.reset();
        this.indicatorCenter.setAlpha(0);
        this.indicatorLeft.setAlpha(0);
        this.indicatorRight.setAlpha(0);
        this.micback.setAlpha(0);
        this.mic.setImageDrawable(this.micimgr);
        this.indicatorCenter.size = 23;
        this.micback.size = IconSize;
        this.circle.color = subColor;
        this.micback.color = backgroundColor;
        this.circle.invalidate();
        this.micback.invalidate();
        this.mic.invalidate();

        Interpolator easeout = new DecelerateInterpolator();
        this._add_anim_and_start(AnimLayer.dissolve(this.micback, 200, easeout));
        this._add_anim_and_start(AnimLayer.dissolve(this.mic, 200, easeout));
        this.viewState = state.Inactive;
    }

    private void removeFromSuperview() {//probably better be written at the bottom of the class..
        if (null != this._textanimator) {
            this._textanimator.cancel();
        }
        if (null != this.label) {
            this.label.setText("");
            ((ViewGroup) this.label.getParent()).removeView(this.label);
            this.label = null;
        }
        if (null != helperView) {
            ((ViewGroup) this.helperView.getParent()).removeView(this.helperView);
        }
    }
}
