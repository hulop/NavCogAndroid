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

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import hulop.navcog.model.Server;

public class ServerListAdapter extends ArrayAdapter<Server> {
    private Context context;
    private int resource;
    private List<Server> serverList;

    ServerListAdapter(@NonNull Context context, int resource, @NonNull List<Server> objects) {
        super(context, resource, objects);
        this.context = context;
        this.resource = resource;
        this.serverList = objects;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view;
        if (convertView == null) {
            view = View.inflate(context, resource, null);
        } else {
            view = convertView;
        }

        Server server = getItem(position);

        TextView name = view.findViewById(R.id.server_list_item_name);
        name.setText(server.getName());

        TextView description = view.findViewById(R.id.server_list_item_description);
        description.setText(server.getDescription());

        if (server.isAvailable()) {
            name.setTextColor(Color.BLACK);
            description.setTextColor(Color.BLACK);
            view.setEnabled(true);
        } else {
            name.setTextColor(Color.LTGRAY);
            description.setTextColor(Color.LTGRAY);
            view.setEnabled(false);

        }
        return view;
    }

    @Nullable
    @Override
    public Server getItem(int position) {
        return serverList.get(position);
    }

    @Override
    public boolean isEnabled(int position) {
        return getItem(position).isAvailable();
    }
}
