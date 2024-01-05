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

import android.app.Activity;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import hulop.navcog.R;

public class ScreenFilter {
    public static ScreenFilter instance;
    private ScreenFilter.Listener mListener;

    private static String terminatedText;
    private static String restrictedText;
    private static String preventedText;

    private static int colorDisabled;
    private static int colorEnabled;

    private static State state = State.NONE;

    public enum State {
        RESTRICTED_AREA,
        PREVENTED_WALKING,
        NONE
    }


    public ScreenFilter(Activity activity) {
        instance = this;
        terminatedText = activity.getString(R.string.navigationTerminated);
        restrictedText = activity.getString(R.string.alertRestrictedArea);
        preventedText = activity.getString(R.string.dontLookWhileWalking);
        colorDisabled = activity.getColor(R.color.colorOptionMenuDisabled);
        colorEnabled = activity.getColor(R.color.colorOptionMenuEnabled);
    }

    public void setListener(ScreenFilter.Listener listener) {
        mListener = listener;
    }

    public static View setScreenFilter(Activity activity) {
        View view = activity.getLayoutInflater().inflate(R.layout.screen_filter, null);
        activity.addContentView(view, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        view.setVisibility(View.INVISIBLE);
        view.bringToFront();
        return view;
    }

    public static void setText(View view) {
        TextView text = view.findViewById(R.id.screen_filter_text);
        switch (state) {
            case RESTRICTED_AREA:
                view.setVisibility(View.VISIBLE);
                view.bringToFront();
                text.setText(terminatedText + restrictedText);
                if (TTSHelper.instance != null) {
                    TTSHelper.instance.speak(restrictedText, true);
                }
                break;
            case PREVENTED_WALKING:
                view.setVisibility(View.VISIBLE);
                view.bringToFront();
                text.setText(preventedText);
                break;
            case NONE:
                view.setVisibility(View.INVISIBLE);
            default:
                // no action
        }
    }

    public static void disableOptionMenus(Menu menu) {
        boolean disabled = state == State.RESTRICTED_AREA || state == State.PREVENTED_WALKING;
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            item.setEnabled(!disabled);
            Drawable icon = item.getIcon();
            if (icon != null) {
                item.getIcon().setColorFilter(disabled ? colorDisabled : colorEnabled, PorterDuff.Mode.SRC_ATOP);
            }
        }
    }

    public void onCheckedRestrictedArea(boolean restricted) {
        Log.d("ScreenFilter", "onCheckedRestrictedArea: " + restricted);
        if(restricted) {
            state = ScreenFilter.State.RESTRICTED_AREA;
        } else {
            state = ScreenFilter.State.NONE;
        }
        if(mListener == null) return;
        mListener.onCheckedRestrictedArea(restricted);
    }

    public void onCheckedPreventWalkingArea(boolean prevented) {
        Log.d("ScreenFilter", "onCheckedPreventWalkingArea: " + prevented);
        if(prevented) {
            state = ScreenFilter.State.PREVENTED_WALKING;
        } else {
            state = ScreenFilter.State.NONE;
        }
        if(mListener == null) return;
        mListener.onCheckedPreventWalkingArea(prevented);
    }


    public interface Listener {
        void onCheckedRestrictedArea(boolean restricted);
        void onCheckedPreventWalkingArea(boolean prevented);
    }
}
