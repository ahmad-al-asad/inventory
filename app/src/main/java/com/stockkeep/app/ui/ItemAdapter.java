package com.stockkeep.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import com.stockkeep.app.R;
import com.stockkeep.app.data.Item;
import com.stockkeep.app.data.ItemWithBatches;
import com.stockkeep.app.logic.DateUtil;
import com.stockkeep.app.logic.Expiry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ItemAdapter extends ListAdapter<ItemWithBatches, ItemAdapter.Holder> {

    /** How the home screen shows items. The numbers are saved in settings, so don't reorder them. */
    public static final int VIEW_GRID = 0;
    public static final int VIEW_GRID_SMALL = 1;
    public static final int VIEW_LIST = 2;
    public static final int VIEW_COMPACT = 3;

    public interface OnItemClick {
        void onClick(@NonNull ItemWithBatches iwb);
    }

    public interface OnItemLongClick {
        void onLongClick(@NonNull ItemWithBatches iwb);
    }

    private static final DiffUtil.ItemCallback<ItemWithBatches> DIFF = new DiffUtil.ItemCallback<ItemWithBatches>() {
        @Override
        public boolean areItemsTheSame(@NonNull ItemWithBatches a, @NonNull ItemWithBatches b) {
            return a.item.id == b.item.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ItemWithBatches a, @NonNull ItemWithBatches b) {
            return a.contentKey().equals(b.contentKey());
        }
    };

    private final OnItemClick onClick;
    private final OnItemLongClick onLongClick;
    /** Ids of items ticked in selection mode. */
    private final Set<Long> selected = new LinkedHashSet<>();
    private int soonDays = 3;
    private long today = DateUtil.today();
    private int viewMode = VIEW_GRID;

    public ItemAdapter(@NonNull OnItemClick onClick, @NonNull OnItemLongClick onLongClick) {
        super(DIFF);
        this.onClick = onClick;
        this.onLongClick = onLongClick;
    }

    // ---------- selection ----------

    public boolean isSelecting() {
        return !selected.isEmpty();
    }

    public void toggle(long itemId) {
        if (!selected.remove(itemId)) selected.add(itemId);
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
    }

    public void selectAll() {
        for (ItemWithBatches iwb : getCurrentList()) selected.add(iwb.item.id);
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
    }

    public void clearSelection() {
        if (selected.isEmpty()) return;
        selected.clear();
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
    }

    @NonNull
    public List<Long> selectedIds() {
        return new ArrayList<>(selected);
    }

    /** Drops ticks for items that are no longer shown (e.g. after a filter change). */
    public void keepSelectionVisible() {
        Set<Long> visible = new HashSet<>();
        for (ItemWithBatches iwb : getCurrentList()) visible.add(iwb.item.id);
        if (selected.retainAll(visible)) notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
    }

    private static final Object PAYLOAD_SELECTION = new Object();

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
        if (payloads.size() == 1 && payloads.get(0) == PAYLOAD_SELECTION) {
            bindSelection(h, getItem(position));
        } else {
            super.onBindViewHolder(h, position, payloads);
        }
    }

    private void bindSelection(@NonNull Holder h, @NonNull ItemWithBatches iwb) {
        boolean on = selected.contains(iwb.item.id);
        if (h.itemView instanceof MaterialCardView) {
            ((MaterialCardView) h.itemView).setChecked(on);
        } else {
            h.itemView.setBackgroundColor(on
                    ? MaterialColors.getColor(h.itemView, com.google.android.material.R.attr.colorSecondaryContainer)
                    : Color.TRANSPARENT);
        }
        h.itemView.setActivated(on);
    }

    @SuppressWarnings("NotifyDataSetChanged")
    public void setSettings(int soonDays, long today) {
        if (soonDays == this.soonDays && today == this.today) return;
        this.soonDays = soonDays;
        this.today = today;
        notifyDataSetChanged();
    }

    @SuppressWarnings("NotifyDataSetChanged")
    public void setViewMode(int mode) {
        if (mode == viewMode) return;
        viewMode = mode;
        notifyDataSetChanged();
    }

    public ItemWithBatches itemAt(int position) {
        return getItem(position);
    }

    @Override
    public int getItemViewType(int position) {
        return viewMode;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout;
        switch (viewType) {
            case VIEW_GRID_SMALL:
                layout = R.layout.item_card_small;
                break;
            case VIEW_LIST:
                layout = R.layout.item_row;
                break;
            case VIEW_COMPACT:
                layout = R.layout.item_row_compact;
                break;
            default:
                layout = R.layout.item_card;
                break;
        }
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new Holder(v, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ItemWithBatches iwb = getItem(position);
        Item item = iwb.item;
        Context c = h.itemView.getContext();
        int total = iwb.totalQuantity();
        int opened = iwb.openedCount();
        boolean compact = h.viewType == VIEW_COMPACT || h.viewType == VIEW_GRID_SMALL;
        String unit = item.unit == null || item.unit.isEmpty() ? "" : " " + item.unit;

        h.name.setText(item.title());
        UiUtil.loadPhoto(h.photo, item.photo);

        // "In stock" counts sealed units; opened ones are shown separately.
        int sealed = total - opened;
        if (compact) {
            String q;
            if (total == 0) q = c.getString(R.string.out_short);
            else if (opened == 0) q = c.getString(R.string.qty_short, sealed, unit);
            else q = c.getString(R.string.qty_short_opened, sealed, opened);
            h.quantity.setText(q);
        } else if (total == 0) {
            h.quantity.setText(R.string.out_of_stock);
        } else if (sealed == 0) {
            h.quantity.setText(R.string.none_sealed);
        } else {
            h.quantity.setText(c.getString(R.string.qty_in_stock, sealed, unit));
        }

        h.opened.setVisibility(!compact && opened > 0 ? View.VISIBLE : View.GONE);
        if (!compact) h.opened.setText(c.getString(R.string.n_opened, opened));

        if (item.archived) {
            h.badge.setVisibility(View.VISIBLE);
            UiUtil.styleBadge(h.badge, Expiry.Status.NONE, c.getString(R.string.badge_archived));
        } else {
            Long date = Expiry.soonest(iwb);
            Expiry.Status status = Expiry.status(date, today, soonDays);
            if (total == 0) {
                h.badge.setVisibility(View.VISIBLE);
                UiUtil.styleBadge(h.badge, Expiry.Status.EXPIRED, c.getString(R.string.badge_out));
            } else if (date != null) {
                h.badge.setVisibility(View.VISIBLE);
                UiUtil.styleBadge(h.badge, status, DateUtil.badge(c, date, today));
            } else {
                h.badge.setVisibility(View.GONE);
            }
        }
        // Archived items are shown faded so they stand out from things in use.
        h.itemView.setAlpha(item.archived ? 0.6f : 1f);

        CharSequence desc = item.title() + ", " + h.quantity.getText()
                + (item.archived ? ", " + c.getString(R.string.badge_archived) : "");
        h.itemView.setContentDescription(desc);
        h.itemView.setOnClickListener(v -> {
            if (isSelecting()) onLongClick.onLongClick(iwb);
            else onClick.onClick(iwb);
        });
        h.itemView.setOnLongClickListener(v -> {
            onLongClick.onLongClick(iwb);
            return true;
        });
        bindSelection(h, iwb);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final int viewType;
        final ImageView photo;
        final TextView name;
        final TextView quantity;
        final TextView opened;
        final TextView badge;

        Holder(@NonNull View v, int viewType) {
            super(v);
            this.viewType = viewType;
            photo = v.findViewById(R.id.photo);
            name = v.findViewById(R.id.name);
            quantity = v.findViewById(R.id.quantity);
            opened = v.findViewById(R.id.opened);
            badge = v.findViewById(R.id.badge);
        }
    }
}
