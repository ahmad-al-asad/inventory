package com.stockkeep.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.stockkeep.app.Prefs;
import com.stockkeep.app.R;
import com.stockkeep.app.data.Category;
import com.stockkeep.app.data.ItemWithBatches;
import com.stockkeep.app.data.Repository;
import com.stockkeep.app.logic.DateUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private MainViewModel vm;
    private Repository repo;
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
    private Chip activeFilter;
    private final List<Category> folders = new ArrayList<>();
    private boolean buildingTabs;
    private String currentQuery = "";
    @androidx.annotation.Nullable
    private ActionMode selectionMode;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vm = new ViewModelProvider(this).get(MainViewModel.class);
        repo = Repository.get(this);
        root = findViewById(R.id.root);
        emptyView = findViewById(R.id.empty);
        emptyTitle = findViewById(R.id.empty_title);
        emptyText = findViewById(R.id.empty_text);
        fab = findViewById(R.id.fab);
        activeFilter = findViewById(R.id.active_filter);
        activeFilter.setOnCloseIconClickListener(v -> setFilter(MainViewModel.Filter.ALL));
        activeFilter.setOnClickListener(v -> showFilterDialog());

        setupToolbar();

        list = findViewById(R.id.list);
        // Tap opens an item; long-press starts selecting several items.
        adapter = new ItemAdapter(iwb -> startActivity(DetailActivity.intent(this, iwb.item.id)), this::toggleSelect);
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

        // New items go into the folder being viewed (or the Default folder when viewing All).
        fab.setOnClickListener(v -> startActivity(EditItemActivity.newInFolder(this, vm.getCategory())));

        folderTabs = findViewById(R.id.folder_tabs);
        folderTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (buildingTabs || !(tab.getTag() instanceof Long)) return;
                vm.setCategory((Long) tab.getTag());
                updateSelectionMenu();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        repo.observeCategories().observe(this, cats -> {
            folders.clear();
            folders.addAll(cats);
            // Start on the Default folder (the first one).
            if (!vm.isCategoryChosen() && !cats.isEmpty()) vm.setCategory(cats.get(0).id);
            buildFolderTabs();
        });

        vm.items().observe(this, items -> {
            showItems(items);
            buildFolderTabs(); // item counts per folder may have changed
        });

        showActiveFilter();
        askForNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.setSettings(Prefs.soonDays(this), DateUtil.today());
        vm.refresh();
    }

    // ---------- top bar ----------

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
            if (id == R.id.action_filter) {
                showFilterDialog();
            } else if (id == R.id.action_view) {
                showViewDialog();
            } else if (id == R.id.action_sort) {
                showSortDialog();
            } else if (id == R.id.action_select) {
                if (adapter.getItemCount() > 0) {
                    adapter.selectAll();
                    onSelectionChanged();
                }
            } else if (id == R.id.action_folders) {
                startActivity(new Intent(this, FoldersActivity.class));
            } else if (id == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else {
                return false;
            }
            return true;
        });
    }

    // ---------- filter (hidden behind the filter button) ----------

    private void showFilterDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter)
                .setSingleChoiceItems(R.array.filter_options, vm.getFilter().ordinal(), (d, which) -> {
                    setFilter(MainViewModel.Filter.values()[which]);
                    d.dismiss();
                })
                .show();
    }

    private void setFilter(MainViewModel.Filter f) {
        vm.setFilter(f);
        showActiveFilter();
        updateSelectionMenu();
    }

    /** A small chip under the tabs says which filter is on; the filter icon is highlighted too. */
    private void showActiveFilter() {
        MainViewModel.Filter f = vm.getFilter();
        boolean on = f != MainViewModel.Filter.ALL;
        activeFilter.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) activeFilter.setText(getResources().getStringArray(R.array.filter_labels)[f.ordinal()]);
        MenuItem item = toolbar.getMenu().findItem(R.id.action_filter);
        if (item != null) {
            int color = MaterialColors.getColor(toolbar, on
                    ? androidx.appcompat.R.attr.colorPrimary
                    : com.google.android.material.R.attr.colorOnSurfaceVariant);
            MenuItemCompat.setIconTintList(item, ColorStateList.valueOf(color));
        }
    }

    // ---------- folder tabs ----------

    /** Tabs: Default folder first, then the other folders in your order, then All. Each shows its item count. */
    private void buildFolderTabs() {
        if (folders.isEmpty()) return;
        Map<Long, Integer> counts = vm.activeCounts();
        long selected = vm.getCategory();
        boolean exists = selected == MainViewModel.CATEGORY_ALL;
        for (Category c : folders) if (c.id == selected) exists = true;
        if (!exists) { // the folder being viewed was deleted
            selected = folders.get(0).id;
            vm.setCategory(selected);
        }

        List<Long> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Category c : folders) {
            ids.add(c.id);
            labels.add(c.name);
        }
        ids.add(MainViewModel.CATEGORY_ALL);
        labels.add(getString(R.string.folder_all));

        // Only rebuild if folders were added, removed or reordered; otherwise just refresh names and counts.
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

    private boolean viewingRealFolder() {
        long c = vm.getCategory();
        if (c == MainViewModel.CATEGORY_ALL) return false;
        for (Category f : folders) if (f.id == c) return !f.isDefault;
        return false;
    }

    private String folderName(long id) {
        for (Category f : folders) if (f.id == id) return f.name;
        return "";
    }

    // ---------- selecting several items ----------

    private void toggleSelect(@NonNull ItemWithBatches iwb) {
        adapter.toggle(iwb.item.id);
        onSelectionChanged();
    }

    private void onSelectionChanged() {
        int n = adapter.selectedIds().size();
        if (n == 0) {
            if (selectionMode != null) selectionMode.finish();
            return;
        }
        if (selectionMode == null) {
            selectionMode = startSupportActionMode(selectionCallback);
            fab.hide();
        }
        if (selectionMode != null) {
            selectionMode.setTitle(getResources().getQuantityString(R.plurals.n_selected, n, n));
            updateSelectionMenu();
        }
    }

    private void updateSelectionMenu() {
        if (selectionMode == null) return;
        Menu m = selectionMode.getMenu();
        MenuItem remove = m.findItem(R.id.action_remove_folder);
        if (remove != null) {
            boolean real = viewingRealFolder();
            remove.setVisible(real);
            if (real) remove.setTitle(getString(R.string.remove_from_named_folder, folderName(vm.getCategory())));
        }
        MenuItem archive = m.findItem(R.id.action_archive_selected);
        if (archive != null) {
            archive.setTitle(vm.getFilter() == MainViewModel.Filter.ARCHIVED
                    ? R.string.unarchive_selected : R.string.archive_selected);
        }
    }

    private final ActionMode.Callback selectionCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.selection, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            List<Long> ids = adapter.selectedIds();
            int n = ids.size();
            int id = item.getItemId();
            if (id == R.id.action_select_all) {
                adapter.selectAll();
                onSelectionChanged();
            } else if (id == R.id.action_move) {
                FolderDialogs.pickOne(MainActivity.this, R.string.move_to_folder, folders, folderId -> {
                    repo.setFolders(ids, java.util.Collections.singletonList(folderId));
                    done(getResources().getQuantityString(R.plurals.moved_to, n, n, folderName(folderId)));
                });
            } else if (id == R.id.action_add_folder) {
                FolderDialogs.pickOne(MainActivity.this, R.string.add_to_folder, folders, folderId -> {
                    repo.addToFolder(ids, folderId);
                    done(getResources().getQuantityString(R.plurals.added_to, n, n, folderName(folderId)));
                });
            } else if (id == R.id.action_remove_folder) {
                long folderId = vm.getCategory();
                String name = folderName(folderId);
                repo.removeFromFolder(ids, folderId);
                done(getResources().getQuantityString(R.plurals.removed_from, n, n, name));
            } else if (id == R.id.action_archive_selected) {
                boolean archive = vm.getFilter() != MainViewModel.Filter.ARCHIVED;
                repo.setArchived(ids, archive);
                done(getResources().getQuantityString(archive ? R.plurals.archived_n : R.plurals.unarchived_n, n, n));
            } else {
                return false;
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            selectionMode = null;
            adapter.clearSelection();
            fab.show();
        }
    };

    private void done(String message) {
        if (selectionMode != null) selectionMode.finish();
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).setAnchorView(fab).show();
    }

    // ---------- view & sort ----------

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

    // ---------- list ----------

    private void showItems(List<ItemWithBatches> items) {
        adapter.submitList(items, () -> {
            adapter.keepSelectionVisible();
            if (selectionMode != null) onSelectionChanged();
        });
        boolean empty = items == null || items.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) return;
        if (!TextUtils.isEmpty(currentQuery)) {
            emptyTitle.setText(R.string.empty_filtered_title);
            emptyText.setText(R.string.empty_filtered_text);
        } else if (vm.getFilter() == MainViewModel.Filter.ARCHIVED) {
            emptyTitle.setText(R.string.empty_archived_title);
            emptyText.setText(R.string.empty_archived_text);
        } else if (vm.getFilter() != MainViewModel.Filter.ALL) {
            emptyTitle.setText(R.string.empty_filtered_title);
            emptyText.setText(R.string.empty_filtered_text);
        } else if (!vm.hasAnyItems()) {
            emptyTitle.setText(R.string.empty_title);
            emptyText.setText(R.string.empty_text);
        } else {
            emptyTitle.setText(R.string.empty_folder_title);
            emptyText.setText(R.string.empty_folder_text);
        }
    }

    private void askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || Prefs.askedNotificationPermission(this)) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        Prefs.setAskedNotificationPermission(this);
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }
}
