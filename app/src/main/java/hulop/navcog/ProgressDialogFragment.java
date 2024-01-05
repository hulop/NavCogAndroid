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

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ProgressDialogFragment extends DialogFragment {
    private String message = "";
    private int progress = 0;
    private TextView progressTextView;
    private ProgressBar progressBar;
//    private final Handler handler = new Handler();
//    private Runnable animationTask;

    public static ProgressDialogFragment newInstance() {
        return new ProgressDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        FragmentActivity activity = getActivity();
        View view = activity.getLayoutInflater().inflate(R.layout.progress_dialog, null);
        progressBar = view.findViewById(R.id.progressBar_progress_dialog);
        progressTextView = view.findViewById(R.id.textView_progress_dialog);

        updateUI();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(message)
                .setView(view);
        return builder.create();
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setProgress(int progress) {
        this.progress = progress;
        updateUI();
    }

    private void updateUI() {
        if (progressBar != null) {
            progressBar.setProgress(this.progress);
        }
        if (progressTextView != null) {
            progressTextView.setText(this.progress + "%");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        setProgress(0);
    }

//    public void startProgressBarAnimation() {
//        if (animationTask == null) {
//            animationTask = new Runnable() {
//                private int progress = 0;
//
//                @Override
//                public void run() {
//                    setProgress(progress);
//                    progress = (progress + 10) % 100;
//                    handler.postDelayed(this, 1000);
//                }
//
//            };
//        } else {
//            handler.post(animationTask);
//        }
//    }
//
//    public void resetProgressBarAnimation() {
//        if (animationTask != null) {
//            handler.removeCallbacks(animationTask);
//            animationTask = null;
//        }
//        handler.post(() -> setProgress(0));
//    }
//
//    @Override
//    public void onDismiss(DialogInterface dialog) {
//        resetProgressBarAnimation();
//        super.onDismiss(dialog);
//    }

}
