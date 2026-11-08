package com.stockkeep.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.stockkeep.app.R;
import com.stockkeep.app.data.Item;
import com.stockkeep.app.data.ItemWithBatches;
import com.stockkeep.app.logic.DateUtil;
import com.stockkeep.app.logic.Expiry;

public class ItemAdapter extends ListAdapter<ItemWithBatches, ItemAdapter.Holder> {

    /** How the home screen shows items. The numbers are saved in settings, so don't reorder them. */
    public static final int VIEW_GRID = 0;
    public static final int VIEW_GRID_SMALL = 1;
    public static final int VIEW_LIST = 2;
    public static final int VIEW_COMPACT = 3;

    public interface OnItemClick {
        void onClick(@NonNull ItemWithBatches iwb);
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
    private int soonDays = 3;
    private long today = DateUtil.today();
    private int viewMode = VIEW_GRID;

    public ItemAdapter(@NonNull OnItemClick onClick) {
        super(DIFF);
        this.onClick = onClick;
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

        h.name.setText(item.name);
        UiUtil.loadPhoto(h.photo, item.photo);

        if (compact) {
            h.quantity.setText(total == 0 ? c.getString(R.string.out_short) : c.getString(R.string.qty_short, total, unit));
        } else if (total == 0) {
            h.quantity.setText(R.string.out_of_stock);
        } else if (total == 1) {
            h.quantity.setText(R.string.qty_last);
        } else {
            h.quantity.setText(c.getString(R.string.qty_in_stock, total, unit));
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

        CharSequence desc = item.name + ", " + h.quantity.getText()
                + (item.archived ? ", " + c.getString(R.string.badge_archived) : "");
        h.itemView.setContentDescription(desc);
        h.itemView.setOnClickListener(v -> onClick.onClick(iwb));
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
