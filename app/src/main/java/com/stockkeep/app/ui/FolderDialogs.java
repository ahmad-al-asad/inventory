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

import java.util.List;
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

    /** Lists folders; tap one to rename or delete it, or add a new one. */
    static void manage(@NonNull Context c, @NonNull List<Category> folders) {
        Repository repo = Repository.get(c);
        String[] names = new String[folders.size() + 1];
        for (int i = 0; i < folders.size(); i++) names[i] = folders.get(i).name;
        names[folders.size()] = c.getString(R.string.new_folder_item);
        new MaterialAlertDialogBuilder(c)
                .setTitle(R.string.manage_folders)
                .setItems(names, (d, which) -> {
                    if (which == folders.size()) {
                        askName(c, R.string.new_folder, null, name -> repo.addCategory(name, null));
                    } else {
                        folderOptions(c, folders.get(which));
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private static void folderOptions(@NonNull Context c, @NonNull Category folder) {
        Repository repo = Repository.get(c);
        new MaterialAlertDialogBuilder(c)
                .setTitle(folder.name)
                .setItems(new String[]{c.getString(R.string.rename), c.getString(R.string.delete)}, (d, which) -> {
                    if (which == 0) {
                        askName(c, R.string.rename_folder, folder.name, name -> repo.renameCategory(folder, name));
                    } else {
                        new MaterialAlertDialogBuilder(c)
                                .setTitle(c.getString(R.string.delete_folder_title, folder.name))
                                .setMessage(R.string.delete_folder_text)
                                .setPositiveButton(R.string.delete, (x, y) -> repo.deleteCategory(folder.id))
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    }
                })
                .show();
    }
}
