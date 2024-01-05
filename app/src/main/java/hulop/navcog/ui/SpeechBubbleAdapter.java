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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;

import hulop.navcog.R;

public class SpeechBubbleAdapter extends BaseAdapter {
    private final Context _context;
    private ArrayList<Comment> _comments = new ArrayList<>();
    private final LayoutInflater _inflater;

    private static final int NUMBER_OF_VIEW_TYPES = 2;

    public SpeechBubbleAdapter(Context context, Comment[] comments) {
        _context = context;
        Collections.addAll(this._comments, comments);
        _inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void addItem(Comment comment) {
        this._comments.add(comment);
        this.notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return this._comments.size();
    }

    @Override
    public Object getItem(int position) {
        return this._comments.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Comment comment = this._comments.get(position);

        CommentViewHolder holder;
        if (convertView == null || (holder = (CommentViewHolder) convertView.getTag()).type != comment.type) {
            int layoutResourceId = comment.type == Comment.bubbleType.fromLeft ?
                    R.layout.list_item_bubble_left : R.layout.list_item_bubble_right;
            convertView = _inflater.inflate(layoutResourceId, null);
            holder = new CommentViewHolder(convertView, comment.type);
            convertView.setTag(holder);
        }

        holder.commentTextView.setText(comment.text);
        holder.iconImageView.setImageDrawable(comment.userIcon);
        holder.nameText.setText(comment.userName);

        return convertView;
    }

    static class CommentViewHolder {
        private TextView commentTextView;

        private ImageView iconImageView;

        private TextView nameText;

        private Comment.bubbleType type;

        public CommentViewHolder(View view, Comment.bubbleType type) {
            this.commentTextView = view.findViewById(R.id.text_user_comment);
            this.iconImageView = view.findViewById(R.id.image_user_icon);
            this.nameText = view.findViewById(R.id.text_user_name);
            this.type = type;
        }
    }
}
