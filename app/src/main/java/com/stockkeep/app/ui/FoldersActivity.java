package com.stockkeep.app.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.stockkeep.app.R;
import com.stockkeep.app.data.Category;
import com.stockkeep.app.data.ItemWithBatches;
import com.stockkeep.app.data.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Settings → Folders: drag to reorder, rename, delete and add folders. The Default folder is pinned first. */
public class FoldersActivity extends AppCompatActivity {

    private Repository repo;
    private final List<Category> folders = new ArrayList<>();
    private final Map<Long, Integer> counts = new HashMap<>();
    private FolderAdapter adapter;
    private ItemTouchHelper dragger;
    private boolean dragging;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folders);
        repo = Repository.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        findViewById(R.id.fab).setOnClickListener(v ->
                FolderDialogs.askName(this, R.string.new_folder, null, name -> repo.addCategory(name, null)));

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FolderAdapter();
        list.setAdapter(adapter);

        dragger = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                // The Default folder (always row 0) can't be moved.
                return vh.getBindingAdapterPosition() == 0 ? 0 : super.getMovementFlags(rv, vh);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int a = from.getBindingAdapterPosition();
                int b = to.getBindingAdapterPosition();
                if (a <= 0 || b <= 0) return false;
                Collections.swap(folders, a, b);
                adapter.notifyItemMoved(a, b);
                return true;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder vh, int actionState) {
                super.onSelectedChanged(vh, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) dragging = true;
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                if (!dragging) return;
                dragging = false;
                List<Long> order = new ArrayList<>();
                for (int i = 1; i < folders.size(); i++) order.add(folders.get(i).id);
                repo.reorderCategories(order);
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
            }
        });
        dragger.attachToRecyclerView(list);

        repo.observeCategories().observe(this, cats -> {
            if (dragging) return;
            folders.clear();
            folders.addAll(cats);
            adapter.refresh();
        });
        repo.observeAll().observe(this, items -> {
            counts.clear();
            for (ItemWithBatches iwb : items) {
                for (Long f : iwb.folderIds) counts.merge(f, 1, Integer::sum);
            }
            adapter.refresh();
        });
    }

    private void edit(Category c) {
        if (c.isDefault) {
            FolderDialogs.askName(this, R.string.rename_folder, c.name, name -> repo.renameCategory(c, name));
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(c.name)
                .setItems(new String[]{getString(R.string.rename), getString(R.string.delete)}, (d, which) -> {
                    if (which == 0) {
                        FolderDialogs.askName(this, R.string.rename_folder, c.name, name -> repo.renameCategory(c, name));
                    } else {
                        FolderDialogs.confirmDelete(this, c);
                    }
                })
                .show();
    }

    private class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.Holder> {

        @SuppressLint("NotifyDataSetChanged")
        void refresh() {
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return folders.size();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder, parent, false));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Category c = folders.get(position);
            Integer n = counts.get(c.id);
            int count = n == null ? 0 : n;
            h.name.setText(c.name);
            String sub = getResources().getQuantityString(R.plurals.folder_items, count, count);
            if (c.isDefault) sub = getString(R.string.default_folder_note, sub);
            h.subtitle.setText(sub);
            h.handle.setVisibility(c.isDefault ? View.INVISIBLE : View.VISIBLE);
            h.pin.setVisibility(c.isDefault ? View.VISIBLE : View.GONE);
            h.handle.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) dragger.startDrag(h);
                return false;
            });
            h.itemView.setOnClickListener(v -> edit(c));
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView subtitle;
            final View handle;
            final View pin;

            Holder(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.folder_name);
                subtitle = v.findViewById(R.id.folder_subtitle);
                handle = v.findViewById(R.id.drag_handle);
                pin = v.findViewById(R.id.pin);
            }
        }
    }
}
