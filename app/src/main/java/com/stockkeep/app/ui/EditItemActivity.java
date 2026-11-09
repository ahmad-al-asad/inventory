package com.stockkeep.app.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.stockkeep.app.R;
import com.stockkeep.app.data.Batch;
import com.stockkeep.app.data.Category;
import com.stockkeep.app.data.ImageStore;
import com.stockkeep.app.data.Item;
import com.stockkeep.app.data.ItemWithBatches;
import com.stockkeep.app.data.Link;
import com.stockkeep.app.data.Repository;
import com.stockkeep.app.logic.DateUtil;
import com.stockkeep.app.security.AppLock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class EditItemActivity extends AppCompatActivity {
    private static final String EXTRA_ID = "item_id";
    private static final String EXTRA_FOLDER = "folder_id";
    private static final String STATE_FOLDER = "folder";
    private static final String STATE_PHOTO = "photo";
    private static final String STATE_ORIGINAL_PHOTO = "original_photo";
    private static final String STATE_TEMP_PHOTOS = "temp_photos";
    private static final String STATE_CAMERA_URI = "camera_uri";
    private static final String STATE_LIFE_UNIT = "life_unit";
    private static final String STATE_BOUGHT = "bought";
    private static final String STATE_EXPIRY = "expiry";
    private static final String STATE_OPENED = "opened";
    private static final String STATE_LINK_LABELS = "link_labels";
    private static final String STATE_LINK_URLS = "link_urls";

    public static Intent intent(@NonNull Context c, long itemId) {
        Intent i = new Intent(c, EditItemActivity.class);
        if (itemId > 0) i.putExtra(EXTRA_ID, itemId);
        return i;
    }

    /** New item, already placed in the given folder (if any). */
    public static Intent newInFolder(@NonNull Context c, @Nullable Long folderId) {
        Intent i = new Intent(c, EditItemActivity.class);
        if (folderId != null && folderId > 0) i.putExtra(EXTRA_FOLDER, folderId);
        return i;
    }

    private Repository repo;
    private long itemId;
    @Nullable
    private Item editing;

    private ImageView photoView;
    private View photoHint;
    private MaterialButton btnRemovePhoto;
    private TextInputLayout tilName;
    private TextInputLayout tilQty;
    private EditText nameField;
    private EditText displayNameField;
    private MaterialAutoCompleteTextView folderField;
    private final List<Category> folders = new ArrayList<>();
    /** Folders this item is (or will be) in. Empty = the Default folder. */
    private final Set<Long> folderIds = new LinkedHashSet<>();
    private EditText qtyField;
    private EditText lifeAmountField;
    private MaterialAutoCompleteTextView unitField;
    private MaterialAutoCompleteTextView lifeUnitField;
    private MaterialSwitch notifyLow;
    private MaterialSwitch notifyOut;
    private MaterialSwitch inUse;
    private LinearLayout linksContainer;
    private final List<View> linkRows = new ArrayList<>();
    private MaterialSwitch alreadyOpened;
    private DateField bought;
    private DateField expiry;
    private DateField openedOn;
    private View tilOpened;

    @Nullable
    private String photoName;
    @Nullable
    private String originalPhoto;
    private ArrayList<String> tempPhotos = new ArrayList<>();
    @Nullable
    private Uri cameraUri;
    private int lifeUnitIndex = Item.LIFE_DAYS;
    private boolean saved;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) importPhoto(uri);
            });

    private final ActivityResultLauncher<Uri> takePhoto =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && cameraUri != null) importPhoto(cameraUri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_item);
        repo = Repository.get(this);
        itemId = getIntent().getLongExtra(EXTRA_ID, 0);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(mi -> {
            if (mi.getItemId() == R.id.action_save) {
                save();
                return true;
            }
            return false;
        });

        photoView = findViewById(R.id.photo);
        photoHint = findViewById(R.id.photo_hint);
        btnRemovePhoto = findViewById(R.id.btn_remove_photo);
        tilName = findViewById(R.id.til_name);
        tilQty = findViewById(R.id.til_qty);
        nameField = findViewById(R.id.name);
        displayNameField = findViewById(R.id.display_name);
        folderField = findViewById(R.id.folder);
        folderField.setFocusable(false);
        folderField.setOnClickListener(v -> FolderDialogs.pickFolders(this, folders, folderIds, chosen -> {
            folderIds.clear();
            folderIds.addAll(chosen);
            showFolders();
        }));
        repo.observeCategories().observe(this, cats -> {
            folders.clear();
            folders.addAll(cats);
            showFolders();
        });
        qtyField = findViewById(R.id.qty);
        lifeAmountField = findViewById(R.id.life_amount);
        unitField = findViewById(R.id.unit);
        lifeUnitField = findViewById(R.id.life_unit);
        notifyLow = findViewById(R.id.notify_low);
        notifyOut = findViewById(R.id.notify_out);
        inUse = findViewById(R.id.in_use);
        linksContainer = findViewById(R.id.links_container);
        findViewById(R.id.btn_add_link).setOnClickListener(v -> {
            View row = addLinkRow(null, null);
            row.findViewById(R.id.link_url).requestFocus();
        });
        alreadyOpened = findViewById(R.id.already_opened);
        tilOpened = findViewById(R.id.til_opened);
        bought = new DateField(findViewById(R.id.til_bought), getSupportFragmentManager(), getString(R.string.date_bought));
        expiry = new DateField(findViewById(R.id.til_expiry), getSupportFragmentManager(), getString(R.string.date_expiry));
        openedOn = new DateField(findViewById(R.id.til_opened), getSupportFragmentManager(), getString(R.string.date_opened));

        unitField.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                getResources().getStringArray(R.array.unit_suggestions)));
        String[] lifeUnits = getResources().getStringArray(R.array.open_life_units);
        lifeUnitField.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, lifeUnits));
        lifeUnitField.setOnItemClickListener((parent, view, position, id) -> lifeUnitIndex = position);

        findViewById(R.id.photo_card).setOnClickListener(v -> launchGallery());
        findViewById(R.id.btn_gallery).setOnClickListener(v -> launchGallery());
        findViewById(R.id.btn_camera).setOnClickListener(v -> launchCamera());
        btnRemovePhoto.setOnClickListener(v -> {
            photoName = null;
            showPhoto();
        });
        alreadyOpened.setOnCheckedChangeListener((sw, checked) -> tilOpened.setVisibility(checked ? View.VISIBLE : View.GONE));
        findViewById(R.id.btn_save).setOnClickListener(v -> save());

        boolean isEdit = itemId > 0;
        toolbar.setTitle(isEdit ? R.string.edit_item : R.string.new_item);
        findViewById(R.id.initial_section).setVisibility(isEdit ? View.GONE : View.VISIBLE);

        if (savedInstanceState != null) {
            photoName = savedInstanceState.getString(STATE_PHOTO);
            originalPhoto = savedInstanceState.getString(STATE_ORIGINAL_PHOTO);
            ArrayList<String> temps = savedInstanceState.getStringArrayList(STATE_TEMP_PHOTOS);
            if (temps != null) tempPhotos = temps;
            String cam = savedInstanceState.getString(STATE_CAMERA_URI);
            cameraUri = cam == null ? null : Uri.parse(cam);
            lifeUnitIndex = savedInstanceState.getInt(STATE_LIFE_UNIT, Item.LIFE_DAYS);
            long[] saved = savedInstanceState.getLongArray(STATE_FOLDER);
            if (saved != null) for (long f : saved) folderIds.add(f);
            bought.set(getLongOrNull(savedInstanceState, STATE_BOUGHT));
            expiry.set(getLongOrNull(savedInstanceState, STATE_EXPIRY));
            openedOn.set(getLongOrNull(savedInstanceState, STATE_OPENED));
            ArrayList<String> labels = savedInstanceState.getStringArrayList(STATE_LINK_LABELS);
            ArrayList<String> urls = savedInstanceState.getStringArrayList(STATE_LINK_URLS);
            if (labels != null && urls != null) {
                for (int i = 0; i < urls.size(); i++) addLinkRow(labels.get(i), urls.get(i));
            }
        } else if (!isEdit) {
            long f = getIntent().getLongExtra(EXTRA_FOLDER, 0);
            if (f > 0) folderIds.add(f);
            addLinkRow(null, null);
            qtyField.setText("1");
            bought.set(DateUtil.today());
            openedOn.set(DateUtil.today());
        }
        lifeUnitField.setText(lifeUnits[lifeUnitIndex], false);
        tilOpened.setVisibility(alreadyOpened.isChecked() ? View.VISIBLE : View.GONE);
        showPhoto();

        if (isEdit) {
            repo.load(itemId, iwb -> {
                if (iwb == null) {
                    finish();
                    return;
                }
                editing = iwb.item;
                if (savedInstanceState == null) fill(iwb, lifeUnits);
            });
        }
    }

    private void fill(ItemWithBatches iwb, String[] lifeUnits) {
        Item item = iwb.item;
        nameField.setText(item.name);
        displayNameField.setText(item.displayName == null ? "" : item.displayName);
        folderIds.clear();
        folderIds.addAll(iwb.folderIds);
        showFolders();
        unitField.setText(item.unit == null ? "" : item.unit, false);
        for (Link l : iwb.sortedLinks()) addLinkRow(l.label, l.url);
        if (linkRows.isEmpty()) addLinkRow(null, null);
        inUse.setChecked(!item.archived);
        lifeAmountField.setText(item.openLifeAmount == null ? "" : String.valueOf(item.openLifeAmount));
        lifeUnitIndex = Math.max(0, Math.min(lifeUnits.length - 1, item.openLifeUnit));
        lifeUnitField.setText(lifeUnits[lifeUnitIndex], false);
        notifyLow.setChecked(item.notifyLow);
        notifyOut.setChecked(item.notifyOut);
        photoName = item.photo;
        originalPhoto = item.photo;
        showPhoto();
    }

    /** Shows the chosen folders, e.g. "Kitchen, Pantry". None chosen = the Default folder. */
    private void showFolders() {
        String names = FolderDialogs.names(folders, folderIds);
        if (names.isEmpty() && !folders.isEmpty()) names = folders.get(0).name; // the Default folder
        folderField.setText(names, false);
    }

    /** Adds one editable shop row (name + link) to the "Where to buy" list. */
    private View addLinkRow(@Nullable String label, @Nullable String url) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_link_edit, linksContainer, false);
        ((EditText) row.findViewById(R.id.link_label)).setText(label == null ? "" : label);
        ((EditText) row.findViewById(R.id.link_url)).setText(url == null ? "" : url);
        row.findViewById(R.id.btn_remove_link).setOnClickListener(v -> {
            linksContainer.removeView(row);
            linkRows.remove(row);
            renumberLinks();
        });
        linksContainer.addView(row);
        linkRows.add(row);
        renumberLinks();
        return row;
    }

    private void renumberLinks() {
        for (int i = 0; i < linkRows.size(); i++) {
            ((TextView) linkRows.get(i).findViewById(R.id.link_number)).setText(getString(R.string.shop_n, i + 1));
        }
    }

    private static String text(View row, int id) {
        CharSequence t = ((EditText) row.findViewById(id)).getText();
        return t == null ? "" : t.toString().trim();
    }

    private void launchGallery() {
        AppLock.openingPicker();
        pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void launchCamera() {
        try {
            cameraUri = ImageStore.newCameraUri(this);
            AppLock.openingPicker();
            takePhoto.launch(cameraUri);
        } catch (Exception e) {
            Toast.makeText(this, R.string.camera_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void importPhoto(@NonNull Uri uri) {
        Context app = getApplicationContext();
        repo.io().execute(() -> {
            String name = ImageStore.importImage(app, uri);
            runOnUiThread(() -> {
                if (name == null) {
                    Toast.makeText(app, R.string.photo_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                tempPhotos.add(name);
                if (isDestroyed()) {
                    ImageStore.delete(app, name);
                    return;
                }
                photoName = name;
                showPhoto();
            });
        });
    }

    private void showPhoto() {
        boolean has = photoName != null;
        photoHint.setVisibility(has ? View.GONE : View.VISIBLE);
        btnRemovePhoto.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            UiUtil.loadPhoto(photoView, photoName);
        } else {
            com.bumptech.glide.Glide.with(photoView).clear(photoView);
            photoView.setImageDrawable(null);
            photoView.setBackground(null);
        }
    }

    private void save() {
        if (itemId > 0 && editing == null) return; // still loading

        // "name" is the product name (required); the display name is optional and shown instead if set.
        String name = nameField.getText() == null ? "" : nameField.getText().toString().trim();
        String display = displayNameField.getText() == null ? "" : displayNameField.getText().toString().trim();
        if (name.isEmpty() && !display.isEmpty()) {
            name = display; // only a display name was typed: use it as the product name
            display = "";
            nameField.setText(name);
            displayNameField.setText("");
        }
        if (name.isEmpty()) {
            tilName.setError(getString(R.string.err_name));
            nameField.requestFocus();
            return;
        }
        tilName.setError(null);

        List<Link> links = new ArrayList<>();
        for (View row : linkRows) {
            TextInputLayout tilUrl = row.findViewById(R.id.til_link_url);
            String label = text(row, R.id.link_label);
            String url = UiUtil.normalizeLink(text(row, R.id.link_url));
            if (url == null) {
                tilUrl.setError(label.isEmpty() ? null : getString(R.string.err_link_missing));
                if (label.isEmpty()) continue; // completely empty row: just ignore it
                row.findViewById(R.id.link_url).requestFocus();
                return;
            }
            if (!UiUtil.isValidLink(url)) {
                tilUrl.setError(getString(R.string.err_link));
                row.findViewById(R.id.link_url).requestFocus();
                return;
            }
            tilUrl.setError(null);
            Link l = new Link();
            l.label = label.isEmpty() ? null : label;
            l.url = url;
            links.add(l);
        }

        Item item = editing != null ? editing : new Item();
        item.name = name;
        item.displayName = display.isEmpty() || display.equals(name) ? null : display;
        String unit = unitField.getText() == null ? "" : unitField.getText().toString().trim();
        item.unit = unit.isEmpty() ? null : unit;
        item.buyLink = null;
        item.archived = !inUse.isChecked();
        int lifeAmount = UiUtil.parseInt(lifeAmountField.getText(), 0);
        item.openLifeAmount = lifeAmount > 0 ? lifeAmount : null;
        item.openLifeUnit = lifeUnitIndex;
        item.notifyLow = notifyLow.isChecked();
        item.notifyOut = notifyOut.isChecked();
        item.photo = photoName;

        if (editing != null) {
            repo.updateItem(item, links, new ArrayList<>(folderIds));
        } else {
            int qty = UiUtil.parseInt(qtyField.getText(), -1);
            if (qty < 0) {
                tilQty.setError(getString(R.string.err_quantity_zero));
                return;
            }
            tilQty.setError(null);
            List<Batch> batches = new ArrayList<>();
            if (qty > 0) {
                Batch sealed = new Batch();
                sealed.purchaseDate = bought.get();
                sealed.expiryDate = expiry.get();
                if (alreadyOpened.isChecked()) {
                    Batch opened = sealed.copy();
                    opened.quantity = 1;
                    Long day = openedOn.get();
                    opened.openedDate = day != null ? day : DateUtil.today();
                    batches.add(opened);
                    sealed.quantity = qty - 1;
                } else {
                    sealed.quantity = qty;
                }
                batches.add(sealed);
            }
            repo.createItem(item, batches, links, new ArrayList<>(folderIds), null);
        }

        saved = true;
        for (String temp : tempPhotos) {
            if (!temp.equals(photoName)) ImageStore.delete(getApplicationContext(), temp);
        }
        if (originalPhoto != null && !originalPhoto.equals(photoName)) {
            ImageStore.delete(getApplicationContext(), originalPhoto);
        }
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_PHOTO, photoName);
        out.putString(STATE_ORIGINAL_PHOTO, originalPhoto);
        out.putStringArrayList(STATE_TEMP_PHOTOS, tempPhotos);
        if (cameraUri != null) out.putString(STATE_CAMERA_URI, cameraUri.toString());
        out.putInt(STATE_LIFE_UNIT, lifeUnitIndex);
        long[] f = new long[folderIds.size()];
        int i = 0;
        for (Long id : folderIds) f[i++] = id;
        out.putLongArray(STATE_FOLDER, f);
        putLongOrNull(out, STATE_BOUGHT, bought.get());
        putLongOrNull(out, STATE_EXPIRY, expiry.get());
        putLongOrNull(out, STATE_OPENED, openedOn.get());
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> urls = new ArrayList<>();
        for (View row : linkRows) {
            labels.add(text(row, R.id.link_label));
            urls.add(text(row, R.id.link_url));
        }
        out.putStringArrayList(STATE_LINK_LABELS, labels);
        out.putStringArrayList(STATE_LINK_URLS, urls);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Leaving without saving: throw away photos that were picked in this screen.
        if (isFinishing() && !saved) {
            for (String temp : tempPhotos) ImageStore.delete(getApplicationContext(), temp);
        }
    }

    private static void putLongOrNull(Bundle b, String key, @Nullable Long value) {
        if (value != null) b.putLong(key, value);
    }

    @Nullable
    private static Long getLongOrNull(Bundle b, String key) {
        return b.containsKey(key) ? b.getLong(key) : null;
    }
}
