package com.stockkeep.app.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.stockkeep.app.Prefs;
import com.stockkeep.app.R;
import com.stockkeep.app.data.ImageStore;
import com.stockkeep.app.data.ItemWithBatches;
import com.stockkeep.app.data.Repository;
import com.stockkeep.app.logic.DateUtil;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private MainViewModel vm;
    private ItemAdapter adapter;
    private View root;
    private View emptyView;
    private TextView emptyTitle;
    private TextView emptyText;
    private ExtendedFloatingActionButton fab;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vm = new ViewModelProvider(this).get(MainViewModel.class);
        root = findViewById(R.id.root);
        emptyView = findViewById(R.id.empty);
        emptyTitle = findViewById(R.id.empty_title);
        emptyText = findViewById(R.id.empty_text);
        fab = findViewById(R.id.fab);

        setupToolbar();
        setupChips();

        RecyclerView list = findViewById(R.id.list);
        adapter = new ItemAdapter(iwb -> startActivity(DetailActivity.intent(this, iwb.item.id)));
        list.setLayoutManager(new GridLayoutManager(this, getResources().getInteger(R.integer.grid_columns)));
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 8) fab.shrink();
                else if (dy < -8) fab.extend();
            }
        });
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder a,
                                  @NonNull RecyclerView.ViewHolder b) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                deleteWithUndo(adapter.itemAt(pos));
            }
        }).attachToRecyclerView(list);

        fab.setOnClickListener(v -> startActivity(EditItemActivity.intent(this, 0)));

        vm.items().observe(this, this::showItems);

        askForNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.setSettings(Prefs.soonDays(this), DateUtil.today());
        vm.refresh();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.main);
        MenuItem searchItem = toolbar.getMenu().findItem(R.id.action_search);
        SearchView search = (SearchView) searchItem.getActionView();
        if (search != null) {
            search.setQueryHint(getString(R.string.search_hint));
            search.setMaxWidth(Integer.MAX_VALUE);
            search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    search.clearFocus();
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String text) {
                    vm.setQuery(text);
                    return true;
                }
            });
        }
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_sort) {
                showSortDialog();
                return true;
            } else if (id == R.id.action_settings) {
                startActivity(new android.content.Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    private void setupChips() {
        ChipGroup chips = findViewById(R.id.chips);
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? R.id.chip_all : ids.get(0);
            MainViewModel.Filter f = MainViewModel.Filter.ALL;
            if (id == R.id.chip_soon) f = MainViewModel.Filter.SOON;
            else if (id == R.id.chip_expired) f = MainViewModel.Filter.EXPIRED;
            else if (id == R.id.chip_opened) f = MainViewModel.Filter.OPENED;
            else if (id == R.id.chip_low) f = MainViewModel.Filter.LOW;
            vm.setFilter(f);
        });
    }

    private void showSortDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sort_by)
                .setSingleChoiceItems(R.array.sort_options, vm.getSort(), (d, which) -> {
                    vm.setSort(which);
                    d.dismiss();
                })
                .show();
    }

    private void showItems(List<ItemWithBatches> items) {
        adapter.submitList(items);
        boolean empty = items == null || items.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            if (vm.hasAnyItems()) {
                emptyTitle.setText(R.string.empty_filtered_title);
                emptyText.setText(R.string.empty_filtered_text);
            } else {
                emptyTitle.setText(R.string.empty_title);
                emptyText.setText(R.string.empty_text);
            }
        }
    }

    private void deleteWithUndo(ItemWithBatches iwb) {
        Repository repo = Repository.get(this);
        repo.deleteItem(iwb.item);
        Snackbar.make(root, getString(R.string.item_deleted, iwb.item.name), Snackbar.LENGTH_LONG)
                .setAnchorView(fab)
                .setAction(R.string.undo, v -> repo.restore(iwb))
                .addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar sb, int event) {
                        if (event != DISMISS_EVENT_ACTION) {
                            ImageStore.delete(getApplicationContext(), iwb.item.photo);
                        }
                    }
                })
                .show();
    }

    private void askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || Prefs.askedNotificationPermission(this)) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        Prefs.setAskedNotificationPermission(this);
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }
}
