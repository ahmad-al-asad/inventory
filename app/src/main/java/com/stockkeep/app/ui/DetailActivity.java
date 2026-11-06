package com.stockkeep.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.stockkeep.app.Prefs;
import com.stockkeep.app.R;
import com.stockkeep.app.data.Batch;
import com.stockkeep.app.data.ImageStore;
import com.stockkeep.app.data.ItemWithBatches;
import com.stockkeep.app.data.Repository;
import com.stockkeep.app.logic.DateUtil;
import com.stockkeep.app.logic.Expiry;

import java.util.ArrayList;
import java.util.List;

public class DetailActivity extends AppCompatActivity {
    private static final String EXTRA_ID = "item_id";

    public static Intent intent(@NonNull Context c, long itemId) {
        return new Intent(c, DetailActivity.class).putExtra(EXTRA_ID, itemId);
    }

    private long itemId;
    private Repository repo;
    private ItemWithBatches current;

    private ImageView photo;
    private View photoCard;
    private TextView name;
    private TextView total;
    private TextView summary;
    private TextView batchesEmpty;
    private MaterialButton btnUse;
    private MaterialButton btnBuy;
    private BatchAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        itemId = getIntent().getLongExtra(EXTRA_ID, 0);
        repo = Repository.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.detail);
        toolbar.setOnMenuItemClickListener(mi -> {
            if (mi.getItemId() == R.id.action_edit) {
                startActivity(EditItemActivity.intent(this, itemId));
                return true;
            } else if (mi.getItemId() == R.id.action_delete) {
                confirmDelete();
                return true;
            }
            return false;
        });

        photo = findViewById(R.id.photo);
        photoCard = findViewById(R.id.photo_card);
        name = findViewById(R.id.name);
        total = findViewById(R.id.total);
        summary = findViewById(R.id.summary);
        batchesEmpty = findViewById(R.id.batches_empty);
        btnUse = findViewById(R.id.btn_use);
        MaterialButton btnRestock = findViewById(R.id.btn_restock);
        btnBuy = findViewById(R.id.btn_buy);

        btnUse.setOnClickListener(v -> repo.useOne(itemId));
        btnRestock.setOnClickListener(v -> {
            if (current != null) BatchDialog.show(this, current.item, null, repo::addBatch);
        });
        btnBuy.setOnClickListener(v -> {
            if (current != null) UiUtil.openLink(this, current.item.buyLink);
        });

        adapter = new BatchAdapter(new BatchAdapter.Actions() {
            @Override
            public void onPlus(@NonNull Batch b) {
                repo.changeQuantity(b, 1);
            }

            @Override
            public void onMinus(@NonNull Batch b) {
                repo.changeQuantity(b, -1);
            }

            @Override
            public void onOpen(@NonNull Batch b) {
                repo.openOne(b);
            }

            @Override
            public void onEdit(@NonNull Batch b) {
                if (current != null) BatchDialog.show(DetailActivity.this, current.item, b, repo::updateBatch);
            }

            @Override
            public void onDelete(@NonNull Batch b) {
                new MaterialAlertDialogBuilder(DetailActivity.this)
                        .setTitle(R.string.delete_batch_title)
                        .setMessage(R.string.delete_batch_text)
                        .setPositiveButton(R.string.delete, (d, w) -> repo.deleteBatch(b))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
        RecyclerView batches = findViewById(R.id.batches);
        batches.setLayoutManager(new LinearLayoutManager(this));
        batches.setNestedScrollingEnabled(false);
        batches.setAdapter(adapter);

        repo.observe(itemId).observe(this, iwb -> {
            if (iwb == null) {
                finish();
                return;
            }
            current = iwb;
            bind();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (current != null) bind();
    }

    private void bind() {
        ItemWithBatches iwb = current;
        long today = DateUtil.today();
        int soonDays = Prefs.soonDays(this);
        int count = iwb.totalQuantity();
        int opened = iwb.openedCount();
        String unit = TextUtils.isEmpty(iwb.item.unit) ? "" : " " + iwb.item.unit;

        name.setText(iwb.item.name);
        photoCard.setVisibility(View.VISIBLE);
        UiUtil.loadPhoto(photo, iwb.item.photo);

        String totalText;
        if (count == 0) totalText = getString(R.string.out_of_stock);
        else totalText = getString(R.string.qty_in_stock, count, unit);
        if (opened > 0) totalText += " · " + getString(R.string.n_opened, opened);
        total.setText(totalText);

        Long soonest = Expiry.soonest(iwb);
        if (soonest != null) {
            summary.setVisibility(View.VISIBLE);
            summary.setText(getString(R.string.use_first, DateUtil.relative(this, soonest, today), DateUtil.format(soonest)));
            summary.setTextColor(Expiry.status(soonest, today, soonDays) == Expiry.Status.OK
                    ? total.getCurrentTextColor()
                    : UiUtil.statusColor(this, Expiry.status(soonest, today, soonDays)));
        } else {
            summary.setVisibility(View.GONE);
        }

        btnUse.setEnabled(count > 0);
        btnBuy.setVisibility(TextUtils.isEmpty(iwb.item.buyLink) ? View.GONE : View.VISIBLE);

        List<Batch> sorted = new ArrayList<>(iwb.batches);
        sorted.sort(Expiry.useOrder(iwb.item));
        adapter.submit(iwb.item, sorted, soonDays, today);
        batchesEmpty.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmDelete() {
        if (current == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete_item_title, current.item.name))
                .setMessage(R.string.delete_item_text)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    String photoName = current.item.photo;
                    repo.deleteItem(current.item);
                    ImageStore.delete(getApplicationContext(), photoName);
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
