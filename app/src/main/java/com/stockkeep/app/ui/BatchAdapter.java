package com.stockkeep.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.stockkeep.app.R;
import com.stockkeep.app.data.Batch;
import com.stockkeep.app.data.Item;
import com.stockkeep.app.logic.DateUtil;
import com.stockkeep.app.logic.Expiry;

import java.util.ArrayList;
import java.util.List;

public class BatchAdapter extends ListAdapter<Batch, BatchAdapter.Holder> {

    public interface Actions {
        void onPlus(@NonNull Batch b);

        void onMinus(@NonNull Batch b);

        void onOpen(@NonNull Batch b);

        void onEdit(@NonNull Batch b);

        void onDelete(@NonNull Batch b);
    }

    private static final DiffUtil.ItemCallback<Batch> DIFF = new DiffUtil.ItemCallback<Batch>() {
        @Override
        public boolean areItemsTheSame(@NonNull Batch a, @NonNull Batch b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Batch a, @NonNull Batch b) {
            return a.contentKey().equals(b.contentKey());
        }
    };

    private final Actions actions;
    private Item item;
    private int soonDays = 3;
    private long today = DateUtil.today();

    public BatchAdapter(@NonNull Actions actions) {
        super(DIFF);
        this.actions = actions;
    }

    @SuppressWarnings("NotifyDataSetChanged")
    public void submit(@NonNull Item item, @NonNull List<Batch> batches, int soonDays, long today) {
        boolean itemChanged = this.item == null || !this.item.contentKey().equals(item.contentKey());
        boolean settingsChanged = soonDays != this.soonDays || today != this.today;
        this.item = item;
        this.soonDays = soonDays;
        this.today = today;
        submitList(new ArrayList<>(batches));
        if (itemChanged || settingsChanged) notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_batch, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Batch b = getItem(position);
        Context c = h.itemView.getContext();

        h.state.setText(b.isOpened()
                ? c.getString(R.string.batch_opened_on, DateUtil.format(b.openedDate))
                : c.getString(R.string.batch_sealed));

        Long useBy = Expiry.effective(b, item);
        Expiry.Status status = Expiry.status(useBy, today, soonDays);
        h.strip.setBackgroundColor(UiUtil.statusColor(c, status));
        if (useBy == null) {
            h.useBy.setText(R.string.no_expiry);
        } else {
            h.useBy.setText(c.getString(R.string.use_by, DateUtil.format(useBy), DateUtil.relative(c, useBy, today)));
        }

        StringBuilder details = new StringBuilder();
        if (b.expiryDate != null && !b.expiryDate.equals(useBy)) {
            details.append(c.getString(R.string.printed_expiry, DateUtil.format(b.expiryDate)));
        }
        Integer life = item.openLifeDays();
        if (b.isOpened() && life != null) {
            if (details.length() > 0) details.append('\n');
            details.append(c.getResources().getQuantityString(R.plurals.open_life_days, life, life));
        }
        if (b.purchaseDate != null) {
            if (details.length() > 0) details.append('\n');
            details.append(c.getString(R.string.bought_on, DateUtil.format(b.purchaseDate)));
        }
        h.details.setText(details);
        h.details.setVisibility(details.length() > 0 ? View.VISIBLE : View.GONE);

        h.quantity.setText(String.valueOf(b.quantity));
        h.open.setVisibility(b.isOpened() ? View.GONE : View.VISIBLE);

        h.plus.setOnClickListener(v -> actions.onPlus(b));
        h.minus.setOnClickListener(v -> actions.onMinus(b));
        h.open.setOnClickListener(v -> actions.onOpen(b));
        h.edit.setOnClickListener(v -> actions.onEdit(b));
        h.delete.setOnClickListener(v -> actions.onDelete(b));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View strip;
        final TextView state;
        final TextView useBy;
        final TextView details;
        final TextView quantity;
        final MaterialButton plus;
        final MaterialButton minus;
        final MaterialButton open;
        final MaterialButton edit;
        final MaterialButton delete;

        Holder(@NonNull View v) {
            super(v);
            strip = v.findViewById(R.id.strip);
            state = v.findViewById(R.id.state);
            useBy = v.findViewById(R.id.use_by);
            details = v.findViewById(R.id.details);
            quantity = v.findViewById(R.id.quantity);
            plus = v.findViewById(R.id.btn_plus);
            minus = v.findViewById(R.id.btn_minus);
            open = v.findViewById(R.id.btn_open);
            edit = v.findViewById(R.id.btn_edit);
            delete = v.findViewById(R.id.btn_delete);
        }
    }
}
