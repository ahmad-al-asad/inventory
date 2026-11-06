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

    public ItemWithBatches itemAt(int position) {
        return getItem(position);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ItemWithBatches iwb = getItem(position);
        Item item = iwb.item;
        Context c = h.itemView.getContext();
        int total = iwb.totalQuantity();
        int opened = iwb.openedCount();
        String unit = item.unit == null || item.unit.isEmpty() ? "" : " " + item.unit;

        h.name.setText(item.name);
        UiUtil.loadPhoto(h.photo, item.photo);

        if (total == 0) h.quantity.setText(R.string.out_of_stock);
        else if (total == 1) h.quantity.setText(R.string.qty_last);
        else h.quantity.setText(c.getString(R.string.qty_in_stock, total, unit));

        h.opened.setVisibility(opened > 0 ? View.VISIBLE : View.GONE);
        h.opened.setText(c.getString(R.string.n_opened, opened));

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

        h.itemView.setContentDescription(item.name + ", " + h.quantity.getText());
        h.itemView.setOnClickListener(v -> onClick.onClick(iwb));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView photo;
        final TextView name;
        final TextView quantity;
        final TextView opened;
        final TextView badge;

        Holder(@NonNull View v) {
            super(v);
            photo = v.findViewById(R.id.photo);
            name = v.findViewById(R.id.name);
            quantity = v.findViewById(R.id.quantity);
            opened = v.findViewById(R.id.opened);
            badge = v.findViewById(R.id.badge);
        }
    }
}
