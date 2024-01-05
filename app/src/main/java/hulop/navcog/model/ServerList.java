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

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import hulop.navcog.Utils;

public class ServerList {
    private List<Server> serverList;

    private ServerList(List<Server> serverList) {
        this.serverList = serverList;
    }

    public static ServerList loadFromJSON(File file) {
        List<String> userLanguageCandidates = Utils.userLanguageCandidates();
        List<Server> serverList = new ArrayList<>();
        try {
            JSONObject serversObj = Utils.readJSON(file);
            JSONArray servers = serversObj.getJSONArray("servers");
            for (int index = 0; index < servers.length(); index++) {
                JSONObject serverObj = servers.getJSONObject(index);
                Server server = new Server();
                server.setId(index);
                server.setServerId(serverObj.optString("server_id", ""));
                server.setAvailable(serverObj.optBoolean("available", false));
                JSONObject nameObj = serverObj.optJSONObject("name");
                JSONObject descObj = serverObj.optJSONObject("description");
                for (String userLanguage : userLanguageCandidates) {
                    if (nameObj != null && nameObj.has(userLanguage)) {
                        server.setName(nameObj.optString(userLanguage));
                        server.setDescription(descObj.optString(userLanguage));
                        break;
                    }
                }
                server.setNoCheckAgreement(serverObj.optBoolean("no_checkagreement", false));
                if (serverObj.has("settings")) {
                    JSONArray settingsObj = serverObj.getJSONArray("settings");
                    List<Server.Setting> settings = new ArrayList<>();
                    for (int i = 0; i < settingsObj.length(); i++) {
                        JSONObject obj = settingsObj.getJSONObject(i);
                        Server.Setting setting = new Server.Setting();
                        setting.setHostname(obj.getString("hostname"));
                        setting.setConfigFileName(obj.getString("config_file_name"));
                        settings.add(setting);
                    }
                    server.setSettings(settings);
                }
                server.setHostname(serverObj.getString("hostname"));
                server.setConfigFileName(serverObj.optString("config_file_name", ""));
                serverList.add(server);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ServerList(serverList);
    }

    public List<Server> getList() {
        return serverList;
    }
}
