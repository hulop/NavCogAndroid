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

package hulop.navcog.navigation;

import android.support.v7.app.AppCompatActivity;

import hulop.navcog.ProgressDialogFragment;
import hulop.navcog.WaitingDialogFragment;

public class NavUtil {

    public static WaitingDialogFragment showModalWaiting(AppCompatActivity activity, int resId) {
        WaitingDialogFragment fragment = WaitingDialogFragment.newInstance();
        fragment.setMessage(activity.getString(resId));
        fragment.setCancelable(false);
        fragment.show(activity.getSupportFragmentManager(), "waiting_dialog");
        return fragment;
    }

    public static ProgressDialogFragment showProgressDialog(AppCompatActivity activity, int resId) {
        ProgressDialogFragment fragment = ProgressDialogFragment.newInstance();
        fragment.setMessage(activity.getString(resId));
        fragment.setCancelable(false);
        fragment.show(activity.getSupportFragmentManager(), "progress_dialog");
        return fragment;
    }

}
