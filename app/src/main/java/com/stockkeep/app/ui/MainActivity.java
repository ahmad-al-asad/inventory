package com.stockkeep.app.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.text.TextUtils;
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
import com.google.android.material.tabs.TabLayout;
import com.stockkeep.app.Prefs;
import com.stockkeep.app.R;
import com.stockkeep.app.data.Category;
import com.stockkeep.app.data.ImageStore;
import com.stockkeep.app.data.ItemWithBatches;
import com.stockkeep.app.data.Repository;
import com.stockkeep.app.logic.DateUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private MainViewModel vm;
    private ItemAdapter adapter;
    private View root;
    private View emptyView;
    private TextView emptyTitle;
    private TextView emptyText;
    private ExtendedFloatingActionButton fab;
    private RecyclerView list;
    private GridLayoutManager layoutManager;
    private MaterialToolbar toolbar;
    private TabLayout folderTabs;
    private final List<Category> folders = new ArrayList<>();
    private boolean buildingTabs;
    private String currentQuery = "";

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

        list = findViewById(R.id.list);
        adapter = new ItemAdapter(iwb -> startActivity(DetailActivity.intent(this, iwb.item.id)));
        layoutManager = new GridLayoutManager(this, 1);
        list.setLayoutManager(layoutManager);
        list.setAdapter(adapter);
        applyViewMode(Prefs.viewMode(this));
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

        fab.setOnClickListener(v -> startActivity(EditItemActivity.newInFolder(this, vm.getCategory())));

        folderTabs = findViewById(R.id.folder_tabs);
        folderTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (!buildingTabs && tab.getTag() instanceof Long) vm.setCategory((Long) tab.getTag());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        Repository.get(this).observeCategories().observe(this, cats -> {
            folders.clear();
            folders.addAll(cats);
            buildFolderTabs();
        });

        vm.items().observe(this, items -> {
            showItems(items);
            buildFolderTabs(); // item counts per folder may have changed
        });

        askForNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.setSettings(Prefs.soonDays(this), DateUtil.today());
        vm.refresh();
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
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
                    currentQuery = text;
                    vm.setQuery(text);
                    return true;
                }
            });
        }
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_view) {
                showViewDialog();
                return true;
            } else if (id == R.id.action_sort) {
                showSortDialog();
                return true;
            } else if (id == R.id.action_folders) {
                FolderDialogs.manage(this, new ArrayList<>(folders));
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
            else if (id == R.id.chip_archived) f = MainViewModel.Filter.ARCHIVED;
            vm.setFilter(f);
        });
    }

    /** One tab per folder, plus "All" and (if needed) "No folder", each with its number of items in use. */
    private void buildFolderTabs() {
        if (folders.isEmpty()) {
            folderTabs.setVisibility(View.GONE);
            if (vm.getCategory() != MainViewModel.CATEGORY_ALL) vm.setCategory(MainViewModel.CATEGORY_ALL);
            return;
        }
        Map<Long, Integer> counts = vm.activeCounts();
        long selected = vm.getCategory();
        boolean selectedExists = selected == MainViewModel.CATEGORY_ALL || selected == MainViewModel.CATEGORY_NONE;
        for (Category c : folders) if (c.id == selected) selectedExists = true;
        if (!selectedExists) {
            selected = MainViewModel.CATEGORY_ALL;
            vm.setCategory(selected);
        }

        // Only rebuild the tabs if the set of folders changed; otherwise just refresh the counts,
        // so the tab bar doesn't jump around while you use the app.
        List<Long> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        ids.add(MainViewModel.CATEGORY_ALL);
        labels.add(getString(R.string.folder_all));
        for (Category c : folders) {
            ids.add(c.id);
            labels.add(c.name);
        }
        Integer loose = counts.get(MainViewModel.CATEGORY_NONE);
        if ((loose != null && loose > 0) || selected == MainViewModel.CATEGORY_NONE) {
            ids.add(MainViewModel.CATEGORY_NONE);
            labels.add(getString(R.string.no_folder));
        }

        boolean same = folderTabs.getTabCount() == ids.size();
        for (int i = 0; same && i < ids.size(); i++) {
            TabLayout.Tab t = folderTabs.getTabAt(i);
            same = t != null && ids.get(i).equals(t.getTag());
        }
        buildingTabs = true;
        if (!same) folderTabs.removeAllTabs();
        for (int i = 0; i < ids.size(); i++) {
            Integer n = counts.get(ids.get(i));
            String text = n == null || n == 0 ? labels.get(i) : getString(R.string.folder_with_count, labels.get(i), n);
            if (same) {
                TabLayout.Tab t = folderTabs.getTabAt(i);
                if (t != null) t.setText(text);
            } else {
                folderTabs.addTab(folderTabs.newTab().setText(text).setTag(ids.get(i)), ids.get(i) == selected);
            }
        }
        buildingTabs = false;
        folderTabs.setVisibility(View.VISIBLE);
    }

    private void showViewDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.view_as)
                .setSingleChoiceItems(R.array.view_options, Prefs.viewMode(this), (d, which) -> {
                    Prefs.setViewMode(this, which);
                    applyViewMode(which);
                    d.dismiss();
                })
                .show();
    }

    /** Switches between grid, small grid, list and compact list. Column counts grow on tablets. */
    private void applyViewMode(int mode) {
        int columnsRes;
        int icon;
        switch (mode) {
            case ItemAdapter.VIEW_GRID_SMALL:
                columnsRes = R.integer.grid_columns_small;
                icon = R.drawable.ic_view_grid;
                break;
            case ItemAdapter.VIEW_LIST:
            case ItemAdapter.VIEW_COMPACT:
                columnsRes = R.integer.list_columns;
                icon = R.drawable.ic_view_list;
                break;
            default:
                columnsRes = R.integer.grid_columns;
                icon = R.drawable.ic_view_grid;
                break;
        }
        layoutManager.setSpanCount(getResources().getInteger(columnsRes));
        adapter.setViewMode(mode);
        list.setPadding(mode == ItemAdapter.VIEW_COMPACT ? 0 : dp(10), list.getPaddingTop(),
                mode == ItemAdapter.VIEW_COMPACT ? 0 : dp(10), list.getPaddingBottom());
        MenuItem viewItem = toolbar.getMenu().findItem(R.id.action_view);
        if (viewItem != null) viewItem.setIcon(icon);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
            if (vm.getCategory() != MainViewModel.CATEGORY_ALL && vm.getFilter() == MainViewModel.Filter.ALL
                    && !TextUtils.isEmpty(currentQuery)) {
                emptyTitle.setText(R.string.empty_filtered_title);
                emptyText.setText(R.string.empty_filtered_text);
            } else if (vm.getCategory() != MainViewModel.CATEGORY_ALL && vm.getFilter() == MainViewModel.Filter.ALL) {
                emptyTitle.setText(R.string.empty_folder_title);
                emptyText.setText(R.string.empty_folder_text);
            } else if (vm.getFilter() == MainViewModel.Filter.ARCHIVED) {
                emptyTitle.setText(R.string.empty_archived_title);
                emptyText.setText(R.string.empty_archived_text);
            } else if (vm.hasAnyItems()) {
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
