package com.stockkeep.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;
import com.stockkeep.app.R;
import com.stockkeep.app.data.Batch;
import com.stockkeep.app.data.Item;
import com.stockkeep.app.logic.DateUtil;

/** Dialog for adding new stock (restock) or editing an existing batch. */
final class BatchDialog {

    interface OnSave {
        void onSave(@NonNull Batch batch);
    }

    private BatchDialog() {
    }

    static void show(@NonNull AppCompatActivity activity, @NonNull Item item, @Nullable Batch existing,
                     @NonNull OnSave onSave) {
        View v = LayoutInflater.from(activity).inflate(R.layout.dialog_batch, null);
        TextInputLayout tilQty = v.findViewById(R.id.til_qty);
        EditText qty = tilQty.getEditText();
        DateField bought = new DateField(v.findViewById(R.id.til_bought),
                activity.getSupportFragmentManager(), activity.getString(R.string.date_bought));
        DateField expiry = new DateField(v.findViewById(R.id.til_expiry),
                activity.getSupportFragmentManager(), activity.getString(R.string.date_expiry));
        TextInputLayout tilOpened = v.findViewById(R.id.til_opened);
        DateField openedOn = new DateField(tilOpened,
                activity.getSupportFragmentManager(), activity.getString(R.string.date_opened));
        MaterialSwitch opened = v.findViewById(R.id.switch_opened);

        Batch b;
        if (existing != null) {
            b = existing.copy();
        } else {
            b = new Batch();
            b.itemId = item.id;
            b.quantity = 1;
            b.purchaseDate = DateUtil.today();
        }

        qty.setText(String.valueOf(b.quantity));
        bought.set(b.purchaseDate);
        expiry.set(b.expiryDate);
        opened.setChecked(b.isOpened());
        openedOn.set(b.openedDate != null ? b.openedDate : DateUtil.today());
        tilOpened.setVisibility(b.isOpened() ? View.VISIBLE : View.GONE);
        opened.setOnCheckedChangeListener((sw, checked) -> {
            tilOpened.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked && openedOn.get() == null) openedOn.set(DateUtil.today());
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(existing == null ? R.string.restock_title : R.string.edit_batch_title)
                .setView(v)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            int q = UiUtil.parseInt(qty.getText(), 0);
            if (q < 1) {
                tilQty.setError(activity.getString(R.string.err_quantity));
                return;
            }
            b.quantity = q;
            b.purchaseDate = bought.get();
            b.expiryDate = expiry.get();
            if (opened.isChecked()) {
                Long day = openedOn.get();
                b.openedDate = day != null ? day : DateUtil.today();
            } else {
                b.openedDate = null;
            }
            onSave.onSave(b);
            dialog.dismiss();
        }));
        dialog.show();
    }
}
