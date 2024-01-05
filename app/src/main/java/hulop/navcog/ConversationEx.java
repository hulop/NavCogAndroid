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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ibm.watson.developer_cloud.conversation.v1.model.Context;
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageInput;
import com.ibm.watson.developer_cloud.conversation.v1.model.OutputData;
import com.ibm.watson.developer_cloud.conversation.v1.model.RuntimeEntity;
import com.ibm.watson.developer_cloud.conversation.v1.model.RuntimeIntent;
import com.ibm.watson.developer_cloud.service.model.DynamicModel;
import com.ibm.watson.developer_cloud.util.GsonSerializationHelper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

class MessageResponse extends DynamicModel {
    private Type inputType = new TypeToken<MessageInput>() {
    }.getType();
    private Type intentsType = new TypeToken<List<RuntimeIntent>>() {
    }.getType();
    private Type entitiesType = new TypeToken<List<RuntimeEntity>>() {
    }.getType();
    private Type alternateIntentsType = new TypeToken<Boolean>() {
    }.getType();
    private Type contextType = new TypeToken<Context>() {
    }.getType();
    private Type outputType = new TypeToken<OutputData>() {
    }.getType();

    /**
     * Gets the input.
     *
     * @return the input
     */
    public MessageInput getInput() {
        return GsonSerializationHelper.serializeDynamicModelProperty(this.get("input"), inputType);
    }

    /**
     * Gets the intents.
     *
     * @return the intents
     */
    public List<RuntimeIntent> getIntents() {
        return GsonSerializationHelper.serializeDynamicModelProperty(this.get("intents"), intentsType);
    }

    /**
     * Gets the entities.
     *
     * @return the entities
     */
    public List<RuntimeEntity> getEntities() {
        return GsonSerializationHelper.serializeDynamicModelProperty(this.get("entities"), entitiesType);
    }

    /**
     * Gets the alternateIntents.
     *
     * @return the alternateIntents
     */
    public Boolean isAlternateIntents() {
        return GsonSerializationHelper.serializeDynamicModelProperty(this.get("alternateIntents"), alternateIntentsType);
    }

    /**
     * Gets the context.
     *
     * @return the context
     */
    public Context getContext() {
        return GsonSerializationHelper.serializeDynamicModelProperty(this.get("context"), contextType);
    }

    /**
     * Gets the output.
     *
     * @return the output
     */
    public OutputData getOutput() {
        return GsonSerializationHelper.serializeDynamicModelProperty(this.get("output"), outputType);
    }

    /**
     * Sets the input.
     *
     * @param input the new input
     */
    public void setInput(final MessageInput input) {
        this.put("input", input);
    }

    /**
     * Sets the intents.
     *
     * @param intents the new intents
     */
    public void setIntents(final List<RuntimeIntent> intents) {
        this.put("intents", intents);
    }

    /**
     * Sets the entities.
     *
     * @param entities the new entities
     */
    public void setEntities(final List<RuntimeEntity> entities) {
        this.put("entities", entities);
    }

    /**
     * Sets the alternateIntents.
     *
     * @param alternateIntents the new alternateIntents
     */
    public void setAlternateIntents(final Boolean alternateIntents) {
        this.put("alternateIntents", alternateIntents);
    }

    /**
     * Sets the context.
     *
     * @param context the new context
     */
    public void setContext(final Context context) {
        this.put("context", context);
    }

    /**
     * Sets the output.
     *
     * @param output the new output
     */
    public void setOutput(final OutputData output) {
        this.put("output", output);
    }
}

public class ConversationEx {

    public MessageResponse message(
            @Nullable String text,
            String server,
            String apiKey,
            @Nullable String clientId,
            @Nullable Context context
    ) {
        // build query string
        StringBuilder builder = new StringBuilder();
        builder.append("https://").append(server).append("/service")
                .append("?lang=").append(Locale.getDefault().getLanguage())
                .append("&api_key=").append(apiKey);
        if (text != null && !text.isEmpty()) {
            builder.append("&text=").append(text);
        }
        if (clientId != null) {
            builder.append("&id=").append(clientId);
        }
        String urlString = builder.toString();
        Log.d("ConversationEx", "api endpoint: " + urlString);

        // build message body
        String body = "{}";
        if (context != null) {
            body = "{\"context\": " + context.toString() + "}";
        }
        Log.d("ConversationEx", "message body: \n" + body);

        HttpURLConnection connection = null;
        MessageResponse messageResponse = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setChunkedStreamingMode(0);

            connection.connect();

            // post message body
            post(body, connection.getOutputStream());
            // read response
            messageResponse = toJSON(connection.getInputStream());

            Log.d("ConversationEx", "response: \n" + messageResponse.toString());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return messageResponse;
    }

    private void post(String body, OutputStream os) throws Exception {
        os.write(body.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();
    }

    @NonNull
    private MessageResponse toJSON(InputStream is) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            builder.append(line);
        }

        String jsonString = builder.toString();
        Gson gson = new Gson();
        MessageResponse messageResponse = gson.fromJson(jsonString, MessageResponse.class);

        Log.d("jsonString", jsonString);
        Log.d("json", "" + messageResponse.getContext().toString());

        return messageResponse;
    }
}