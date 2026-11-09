package com.stockkeep.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.stockkeep.app.R;
import com.stockkeep.app.data.Category;
import com.stockkeep.app.data.Repository;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Small dialogs for creating, renaming and deleting folders. */
final class FolderDialogs {

    private FolderDialogs() {
    }

    /** Asks for a folder name. Calls onName with the trimmed, non-empty name. */
    static void askName(@NonNull Context c, int title, @Nullable String initial, @NonNull Consumer<String> onName) {
        View v = LayoutInflater.from(c).inflate(R.layout.dialog_folder_name, null);
        TextInputLayout til = v.findViewById(R.id.til_folder_name);
        EditText field = til.getEditText();
        if (initial != null) {
            field.setText(initial);
            field.setSelection(initial.length());
        }
        AlertDialog d = new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setView(v)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        d.setOnShowListener(x -> {
            field.requestFocus();
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
                String name = field.getText() == null ? "" : field.getText().toString().trim();
                if (name.isEmpty()) {
                    til.setError(c.getString(R.string.err_folder_name));
                    return;
                }
                onName.accept(name);
                d.dismiss();
            });
        });
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
    }

    static void confirmDelete(@NonNull Context c, @NonNull Category folder) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(c.getString(R.string.delete_folder_title, folder.name))
                .setMessage(R.string.delete_folder_text)
                .setPositiveButton(R.string.delete, (x, y) -> Repository.get(c).deleteCategory(folder.id))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Lets the user tick any number of folders. "New folder" creates one and ticks it.
     * Nothing ticked means the Default folder.
     */
    static void pickFolders(@NonNull Context c, @NonNull List<Category> folders, @NonNull Set<Long> selected,
                            @NonNull Consumer<Set<Long>> onDone) {
        String[] names = new String[folders.size()];
        boolean[] checked = new boolean[folders.size()];
        for (int i = 0; i < folders.size(); i++) {
            names[i] = folders.get(i).name;
            checked[i] = selected.contains(folders.get(i).id);
        }
        new MaterialAlertDialogBuilder(c)
                .setTitle(R.string.choose_folders)
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.done, (d, w) -> {
                    Set<Long> out = new LinkedHashSet<>();
                    for (int i = 0; i < folders.size(); i++) if (checked[i]) out.add(folders.get(i).id);
                    onDone.accept(out);
                })
                .setNeutralButton(R.string.new_folder, (d, w) -> {
                    Set<Long> keep = new LinkedHashSet<>();
                    for (int i = 0; i < folders.size(); i++) if (checked[i]) keep.add(folders.get(i).id);
                    askName(c, R.string.new_folder, null, name -> Repository.get(c).addCategory(name, id -> {
                        keep.add(id);
                        onDone.accept(keep);
                    }));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Pick one folder (or make a new one). Used for moving/adding several selected items at once. */
    static void pickOne(@NonNull Context c, int title, @NonNull List<Category> folders, @NonNull Consumer<Long> onPick) {
        String[] names = new String[folders.size() + 1];
        for (int i = 0; i < folders.size(); i++) names[i] = folders.get(i).name;
        names[folders.size()] = c.getString(R.string.new_folder_item);
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setItems(names, (d, which) -> {
                    if (which < folders.size()) {
                        onPick.accept(folders.get(which).id);
                    } else {
                        askName(c, R.string.new_folder, null, name -> Repository.get(c).addCategory(name, onPick));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** "Kitchen, Pantry" — the names of the given folders, in folder order. */
    @NonNull
    static String names(@NonNull List<Category> folders, @NonNull Collection<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Category f : folders) {
            if (!ids.contains(f.id)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(f.name);
        }
        return sb.toString();
    }
}
