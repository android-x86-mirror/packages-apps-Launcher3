/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.widget;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.WidgetPreviewLoader;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.util.LabelComparator;
import com.android.launcher3.util.MultiHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * List view adapter for the widget tray.
 *
 * <p>Memory vs. Performance:
 * The less number of types of views are inserted into a {@link RecyclerView}, the more recycling
 * happens and less memory is consumed. {@link #getItemViewType} was not overridden as there is
 * only a single type of view.
 */
public class WidgetsListAdapter extends Adapter<WidgetsRowViewHolder> {

    private static final String TAG = "WidgetsListAdapter";
    private static final boolean DEBUG = false;

    private final WidgetPreviewLoader mWidgetPreviewLoader;
    private final LayoutInflater mLayoutInflater;

    private final View.OnClickListener mIconClickListener;
    private final View.OnLongClickListener mIconLongClickListener;

    private final ArrayList<WidgetListRowEntry> mEntries = new ArrayList<>();
    private final AlphabeticIndexCompat mIndexer;

    private final int mIndent;

    public WidgetsListAdapter(View.OnClickListener iconClickListener,
            View.OnLongClickListener iconLongClickListener,
            Context context) {
        mLayoutInflater = LayoutInflater.from(context);
        mWidgetPreviewLoader = LauncherAppState.getInstance(context).getWidgetCache();

        mIndexer = new AlphabeticIndexCompat(context);

        mIconClickListener = iconClickListener;
        mIconLongClickListener = iconLongClickListener;
        mIndent = context.getResources().getDimensionPixelSize(R.dimen.widget_section_indent);
    }

    public void setWidgets(MultiHashMap<PackageItemInfo, WidgetItem> widgets) {
        mEntries.clear();
        WidgetItemComparator widgetComparator = new WidgetItemComparator();

        for (Map.Entry<PackageItemInfo, ArrayList<WidgetItem>> entry : widgets.entrySet()) {
            WidgetListRowEntry row = new WidgetListRowEntry(entry.getKey(), entry.getValue());
            row.titleSectionName = mIndexer.computeSectionName(row.pkgItem.title);
            Collections.sort(row.widgets, widgetComparator);
            mEntries.add(row);
        }

        Collections.sort(mEntries, new WidgetListRowEntryComparator());
    }

    @Override
    public int getItemCount() {
        return mEntries.size();
    }

    public String getSectionName(int pos) {
        return mEntries.get(pos).titleSectionName;
    }

    @Override
    public void onBindViewHolder(WidgetsRowViewHolder holder, int pos) {
        WidgetListRowEntry entry = mEntries.get(pos);
        List<WidgetItem> infoList = entry.widgets;

        ViewGroup row = holder.cellContainer;
        if (DEBUG) {
            Log.d(TAG, String.format(
                    "onBindViewHolder [pos=%d, widget#=%d, row.getChildCount=%d]",
                    pos, infoList.size(), row.getChildCount()));
        }

        // Add more views.
        // if there are too many, hide them.
        int diff = infoList.size() - row.getChildCount();

        if (diff > 0) {
            for (int i = 0; i < diff; i++) {
                WidgetCell widget = (WidgetCell) mLayoutInflater.inflate(
                        R.layout.widget_cell, row, false);

                // set up touch.
                widget.setOnClickListener(mIconClickListener);
                widget.setOnLongClickListener(mIconLongClickListener);
                LayoutParams lp = widget.getLayoutParams();
                lp.height = widget.cellSize;
                lp.width = widget.cellSize;
                widget.setLayoutParams(lp);

                row.addView(widget);
            }
        } else if (diff < 0) {
            for (int i=infoList.size() ; i < row.getChildCount(); i++) {
                row.getChildAt(i).setVisibility(View.GONE);
            }
        }

        // Bind the views in the application info section.
        holder.title.applyFromPackageItemInfo(entry.pkgItem);

        // Bind the view in the widget horizontal tray region.
        for (int i=0; i < infoList.size(); i++) {
            WidgetCell widget = (WidgetCell) row.getChildAt(i);
            widget.applyFromCellItem(infoList.get(i), mWidgetPreviewLoader);
            widget.ensurePreview();
            widget.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public WidgetsRowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (DEBUG) {
            Log.v(TAG, "\nonCreateViewHolder");
        }

        ViewGroup container = (ViewGroup) mLayoutInflater.inflate(
                R.layout.widgets_list_row_view, parent, false);
        LinearLayout cellList = (LinearLayout) container.findViewById(R.id.widgets_cell_list);

        // if the end padding is 0, then container view (horizontal scroll view) doesn't respect
        // the end of the linear layout width + the start padding and doesn't allow scrolling.
        cellList.setPaddingRelative(mIndent, 0, 1, 0);

        return new WidgetsRowViewHolder(container);
    }

    @Override
    public void onViewRecycled(WidgetsRowViewHolder holder) {
        int total = holder.cellContainer.getChildCount();
        for (int i = 0; i < total; i++) {
            WidgetCell widget = (WidgetCell) holder.cellContainer.getChildAt(i);
            widget.clear();
        }
    }

    public boolean onFailedToRecycleView(WidgetsRowViewHolder holder) {
        // If child views are animating, then the RecyclerView may choose not to recycle the view,
        // causing extraneous onCreateViewHolder() calls.  It is safe in this case to continue
        // recycling this view, and take care in onViewRecycled() to cancel any existing
        // animations.
        return true;
    }

    @Override
    public long getItemId(int pos) {
        return pos;
    }

    /**
     * Comparator for sorting WidgetListRowEntry based on package title
     */
    public static class WidgetListRowEntryComparator implements Comparator<WidgetListRowEntry> {

        private final LabelComparator mComparator = new LabelComparator();

        @Override
        public int compare(WidgetListRowEntry a, WidgetListRowEntry b) {
            return mComparator.compare(a.pkgItem.title.toString(), b.pkgItem.title.toString());
        }
    }

}
