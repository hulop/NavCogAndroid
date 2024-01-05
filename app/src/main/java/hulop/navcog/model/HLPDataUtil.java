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

package hulop.navcog.model;

import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class HLPDataUtil {

    public static void postRequest(String url, Map<String, String> data, ResponseCallback callback) {
        new PostRequestTask(url, data, callback).execute();

    }

    public interface ResponseCallback {
        void callback(JSONObject jsonObject);
    }
}


class PostRequestTask extends AsyncTask<String, Integer, String> {
    private URL url;
    private String query;
    private HLPDataUtil.ResponseCallback callback;

    PostRequestTask(@NonNull String url, Map<String, String> data, @NonNull HLPDataUtil.ResponseCallback callback) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> e : data.entrySet()) {
            builder.append(e.getKey() + "=" + Uri.encode(e.getValue()) + "&");
        }
        this.query = builder.toString();
        try {
            this.url = new URL(url);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        this.callback = callback;
    }

    @Override
    protected String doInBackground(String... strings) {
        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) this.url.openConnection();
            try {
                connection.setRequestMethod("POST");
            } catch (ProtocolException e) {
                this.callback.callback(null);
                e.printStackTrace();
                return null;
            }
            byte[] data = query.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Content-Length", String.valueOf(data.length));
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);

            connection.connect();

            // post message body
            OutputStream out = connection.getOutputStream();
            out.write(data);
            out.flush();
            out.close();
            int status = connection.getResponseCode();

            this.callback.callback(new JSONObject());
        } catch (IOException e) {
            this.callback.callback(null);
            e.printStackTrace();
        }
        return null;
    }
}
