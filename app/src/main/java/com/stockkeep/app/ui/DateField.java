package com.stockkeep.app.ui;

import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;
import com.stockkeep.app.R;
import com.stockkeep.app.logic.DateUtil;

/** A text field that opens a calendar when tapped and has an "x" to clear the date. */
final class DateField {
    private final TextInputLayout layout;
    private final EditText edit;
    private final FragmentManager fragments;
    private final String title;
    @Nullable
    private Long value;

    DateField(@NonNull TextInputLayout layout, @NonNull FragmentManager fragments, @NonNull String title) {
        this.layout = layout;
        this.edit = layout.getEditText();
        this.fragments = fragments;
        this.title = title;
        edit.setFocusable(false);
        edit.setClickable(true);
        edit.setCursorVisible(false);
        edit.setOnClickListener(v -> pick());
        layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        layout.setEndIconDrawable(R.drawable.ic_close);
        layout.setEndIconContentDescription(R.string.clear_date);
        layout.setEndIconOnClickListener(v -> set(null));
        set(null);
    }

    private void pick() {
        long selection = value != null ? DateUtil.toPickerMillis(value) : MaterialDatePicker.todayInUtcMilliseconds();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(title)
                .setSelection(selection)
                .build();
        picker.addOnPositiveButtonClickListener(sel -> {
            if (sel != null) set(DateUtil.fromPickerMillis(sel));
        });
        picker.show(fragments, "date_picker");
    }

    void set(@Nullable Long epochDay) {
        value = epochDay;
        edit.setText(epochDay == null ? "" : DateUtil.format(epochDay));
        layout.setEndIconVisible(epochDay != null);
    }

    @Nullable
    Long get() {
        return value;
    }
}
