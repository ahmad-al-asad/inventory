package com.stockkeep.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.stockkeep.app.Prefs;
import com.stockkeep.app.R;
import com.stockkeep.app.data.Batch;
import com.stockkeep.app.data.ImageStore;
import com.stockkeep.app.data.ItemWithBatches;
import com.stockkeep.app.data.Link;
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

    private MaterialToolbar toolbar;
    private ImageView photo;
    private TextView name;
    private TextView total;
    private TextView summary;
    private TextView batchesEmpty;
    private View archivedBanner;
    private View linksSection;
    private LinearLayout linksList;
    private MaterialButton btnUse;
    private MaterialButton btnBuy;
    private BatchAdapter adapter;
    private String linksKey = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        itemId = getIntent().getLongExtra(EXTRA_ID, 0);
        repo = Repository.get(this);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.detail);
        toolbar.setOnMenuItemClickListener(mi -> {
            int id = mi.getItemId();
            if (id == R.id.action_edit) {
                startActivity(EditItemActivity.intent(this, itemId));
                return true;
            } else if (id == R.id.action_archive) {
                if (current != null) setArchived(!current.item.archived);
                return true;
            } else if (id == R.id.action_delete) {
                confirmDelete();
                return true;
            }
            return false;
        });

        photo = findViewById(R.id.photo);
        name = findViewById(R.id.name);
        total = findViewById(R.id.total);
        summary = findViewById(R.id.summary);
        batchesEmpty = findViewById(R.id.batches_empty);
        archivedBanner = findViewById(R.id.archived_banner);
        linksSection = findViewById(R.id.links_section);
        linksList = findViewById(R.id.links_list);
        btnUse = findViewById(R.id.btn_use);
        MaterialButton btnRestock = findViewById(R.id.btn_restock);
        btnBuy = findViewById(R.id.btn_buy);

        btnUse.setOnClickListener(v -> repo.useOne(itemId));
        btnRestock.setOnClickListener(v -> {
            if (current != null) BatchDialog.show(this, current.item, null, repo::addBatch);
        });
        btnBuy.setOnClickListener(v -> buy());
        findViewById(R.id.btn_unarchive).setOnClickListener(v -> setArchived(false));

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
        UiUtil.loadPhoto(photo, iwb.item.photo);

        archivedBanner.setVisibility(iwb.item.archived ? View.VISIBLE : View.GONE);
        MenuItem archive = toolbar.getMenu().findItem(R.id.action_archive);
        if (archive != null) {
            archive.setTitle(iwb.item.archived ? R.string.mark_in_use : R.string.archive);
            archive.setIcon(iwb.item.archived ? R.drawable.ic_unarchive : R.drawable.ic_archive);
        }

        String totalText;
        if (count == 0) totalText = getString(R.string.out_of_stock);
        else totalText = getString(R.string.qty_in_stock, count, unit);
        if (opened > 0) totalText += " · " + getString(R.string.n_opened, opened);
        total.setText(totalText);

        Long soonest = Expiry.soonest(iwb);
        if (soonest != null) {
            Expiry.Status s = Expiry.status(soonest, today, soonDays);
            summary.setVisibility(View.VISIBLE);
            summary.setText(getString(R.string.use_first, DateUtil.relative(this, soonest, today), DateUtil.format(soonest)));
            summary.setTextColor(s == Expiry.Status.OK ? total.getCurrentTextColor() : UiUtil.statusColor(this, s));
        } else {
            summary.setVisibility(View.GONE);
        }

        btnUse.setEnabled(count > 0);
        bindLinks(iwb.sortedLinks());

        List<Batch> sorted = new ArrayList<>(iwb.batches);
        sorted.sort(Expiry.useOrder(iwb.item));
        adapter.submit(iwb.item, sorted, soonDays, today);
        batchesEmpty.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void bindLinks(List<Link> links) {
        btnBuy.setVisibility(links.isEmpty() ? View.GONE : View.VISIBLE);
        linksSection.setVisibility(links.isEmpty() ? View.GONE : View.VISIBLE);
        StringBuilder key = new StringBuilder();
        for (Link l : links) key.append(l.contentKey());
        if (key.toString().equals(linksKey)) return;
        linksKey = key.toString();

        linksList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Link l : links) {
            View row = inflater.inflate(R.layout.item_link, linksList, false);
            ((TextView) row.findViewById(R.id.link_title)).setText(l.title());
            ((TextView) row.findViewById(R.id.link_url)).setText(l.url);
            row.setContentDescription(getString(R.string.open_shop, l.title()));
            row.setOnClickListener(v -> UiUtil.openLink(this, l.url));
            linksList.addView(row);
        }
    }

    /** One shop: open it. Several: let the user pick one. */
    private void buy() {
        if (current == null) return;
        List<Link> links = current.sortedLinks();
        if (links.isEmpty()) return;
        if (links.size() == 1) {
            UiUtil.openLink(this, links.get(0).url);
            return;
        }
        String[] names = new String[links.size()];
        for (int i = 0; i < links.size(); i++) names[i] = links.get(i).title();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.where_to_buy)
                .setItems(names, (d, which) -> UiUtil.openLink(this, links.get(which).url))
                .show();
    }

    private void setArchived(boolean archived) {
        repo.setArchived(itemId, archived);
        Snackbar.make(findViewById(R.id.batches), archived ? R.string.archived_done : R.string.unarchived_done,
                        Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> repo.setArchived(itemId, !archived))
                .show();
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
