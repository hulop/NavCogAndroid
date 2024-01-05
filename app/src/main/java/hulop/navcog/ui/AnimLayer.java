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
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.RelativeLayout;

import java.util.ArrayList;

public class AnimLayer extends View {
    public int size = 0;
    public static int blue = Color.argb(255, 50, 92, 128);
    public static int gray = Color.argb(255, 221, 222, 223);
    public static int white = Color.argb(255, 244, 244, 236);
    public static int black = Color.argb(255, 0, 0, 0);
    public static int red = Color.argb(255, 255, 0, 0);
    public static int transparent = Color.argb(0, 255, 255, 255);

    public int color = blue;
    private ArrayList<Long> frames = new ArrayList<>();

    public AnimLayer(Context context, int max) {
        super(context);
        this.setBoundSize(max);
    }

    public void setBoundSize(int max) {
        this.setLayoutParams(new RelativeLayout.LayoutParams(max, max));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = this.getWidth();
        //int h = this.getHeight();width == height within this component
        //int fs = Math.min(w,h);
        RectF rect = new RectF((w - size) / 2, (w - size) / 2, (w + size) / 2, (w + size) / 2);//new RectF((fs - size)/2, (fs - size) / 2, size, size);
        Paint paint = new Paint();
        paint.setColor(this.color);
        canvas.drawArc(rect, 0, 360, true, paint);

        /*
        Long epoch = (new Date()).getTime();
        this.frames.add(epoch);
        Long lastsec = epoch - 1000;
        while (this.frames.get(0) < lastsec) {
            this.frames.remove(0);
        }*///what does this block intend?
    }

    public static Animator pulse(AnimLayer view, long duration, int size, float scale, int count) {
        view.size = size;
        view.invalidate();
        ObjectAnimator ret = ObjectAnimator.ofPropertyValuesHolder(view, PropertyValuesHolder.ofFloat("scaleX", 1.0f, scale),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, scale));
        ret.setDuration(duration / 2);
        ret.setInterpolator(new FastOutSlowInInterpolator());
        ret.setRepeatMode(ObjectAnimator.REVERSE);
        if (1 < count) {
            ret.setRepeatCount(count * 2 - 1);//1 in swift means 0 in android//1);
        }
        return ret;
    }

    /* this method is not called from anywhere in the original code..
    public static Animator pop(View view, float scale, float x, float y, Interpolator type){
        //TODO*
    }
    */

    public static Animator scale(View view, long duration, float current, float scale, Interpolator type) {
        ObjectAnimator ret = ObjectAnimator.ofPropertyValuesHolder(view, PropertyValuesHolder.ofFloat("scaleX", scale),
                PropertyValuesHolder.ofFloat("scaleY", scale));
        view.setScaleX(current);
        view.setScaleY(current);
        ret.setDuration(duration);
        ret.setInterpolator(type);
        ret.setRepeatCount(0);//1 in swift means 0 in android//1);
        return ret;
    }

    public static Animator dissolve(View view, long duration, Interpolator type) {
        view.setAlpha(0);
        ObjectAnimator ret = ObjectAnimator.ofFloat(view, "alpha", 1.0f);
        ret.setDuration(duration);
        ret.setRepeatCount(0);//1 in swift means 0 in android//1);
        ret.setInterpolator(type);
        return ret;
    }

    public static Animator dissolveOut(View view, long duration, Interpolator type) {
        view.setAlpha(1.0f);
        ObjectAnimator ret = ObjectAnimator.ofFloat(view, "alpha", 0.0f);
        ret.setDuration(duration);
        ret.setRepeatCount(0);//1 in swift means 0 in android//1);
        ret.setInterpolator(type);
        return ret;
    }

    public static Animator blink(View view, long duration, int rep) {
        view.setAlpha(1.0f);
        ObjectAnimator ret = ObjectAnimator.ofFloat(view, "alpha", 0.0f);
        ret.setDuration(Float.valueOf(duration / 2.0f / rep).longValue());
        ret.setRepeatMode(ObjectAnimator.REVERSE);
        if (1 < rep) {
            ret.setRepeatCount(rep * 2 - 1);//1 in swift means 0 in android//1);
        }
        return ret;
    }

    public static Animator delay(Animator a, long millisec) {
        a.setStartDelay(millisec);
        return a;
    }
}

