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
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.webkit.URLUtil;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

public class PostSurveyDialogFragment extends DialogFragment {

    private boolean needToShow = false;
    private String html = "";

    public static String getDialogMessage(File postSurveyFile) {
        String message = "";
        try {
            if (postSurveyFile != null && postSurveyFile.exists()) {
                JSONObject postSurveyJsonObject = Utils.readJSON(postSurveyFile);
                //System.out.println(postSurveyJsonObject.toString());
                String curLang = Locale.getDefault().getLanguage();
                JSONObject postSurveySettingsObject = null;
                if (postSurveyJsonObject.has(curLang)) {
                    postSurveySettingsObject = postSurveyJsonObject.getJSONObject(curLang);
                } else {
                    if (postSurveyJsonObject.has("en")) {
                        postSurveySettingsObject = postSurveyJsonObject.getJSONObject("en");
                    }
                }
                if (postSurveySettingsObject != null) {
                    String urlS, preS, postS, linkS;
                    preS = postSurveySettingsObject.optString("pre_message", "");
                    postS = postSurveySettingsObject.optString("post_message", "");
                    linkS = postSurveySettingsObject.optString("link_message", null);
                    urlS = postSurveySettingsObject.optString("url", null);
                    //TODO sanitize
                    if (linkS != null && urlS != null && URLUtil.isValidUrl(urlS)) {
                        message = "<p>" + preS + " <a href=\"" + urlS + "\"> " + linkS + "</a> " + postS + "</p>";
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return message;
    }

    public void setDialogMessage(String html) {
        this.html = html;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("").setMessage(Html.fromHtml(html)).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }
        );
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            AlertDialog alertDialog = (AlertDialog) getDialog();
            ((TextView) alertDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
