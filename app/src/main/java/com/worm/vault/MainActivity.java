package com.worm.vault;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_FILE = 1001;
    private VaultManager vault;
    private LinearLayout filesContainer;
    private TextView countView;
    private String currentPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vault = new VaultManager(this);
        showLoginScreen();
    }

    private void showLoginScreen() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 200, 60, 60);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("WormVault");
        title.setTextSize(36);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView sub = new TextView(this);
        sub.setText("الخزنة المشفرة");
        sub.setTextSize(16);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 20, 0, 80);
        layout.addView(sub);

        final EditText input = new EditText(this);
        input.setHint("كلمة السر");
        input.setInputType(InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(input);

        Button loginBtn = new Button(this);
        loginBtn.setText("دخول");
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pw = input.getText().toString();
                if (pw.length() < 4) {
                    toast("كلمة السر قصيرة جداً");
                    return;
                }
                if (vault.unlock(pw)) {
                    currentPassword = pw;
                    showVaultScreen();
                } else {
                    toast("كلمة السر غير صحيحة");
                }
            }
        });
        layout.addView(loginBtn);
        setContentView(layout);
    }

    private void showVaultScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 80, 40, 40);

        TextView title = new TextView(this);
        title.setText("الخزنة مفتوحة");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        countView = new TextView(this);
        countView.setTextSize(16);
        countView.setGravity(Gravity.CENTER);
        countView.setPadding(0, 20, 0, 20);
        root.addView(countView);

        Button addBtn = new Button(this);
        addBtn.setText("+ إضافة ملف");
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, PICK_FILE);
            }
        });
        root.addView(addBtn);

        Button changePwBtn = new Button(this);
        changePwBtn.setText("تغيير كلمة السر");
        changePwBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChangePwDialog();
            }
        });
        root.addView(changePwBtn);

        Button lockBtn = new Button(this);
        lockBtn.setText("قفل");
        lockBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vault.lock();
                vault.clearTemp();
                currentPassword = null;
                showLoginScreen();
            }
        });
        root.addView(lockBtn);

        ScrollView scroll = new ScrollView(this);
        filesContainer = new LinearLayout(this);
        filesContainer.setOrientation(LinearLayout.VERTICAL);
        filesContainer.setPadding(0, 30, 0, 0);
        scroll.addView(filesContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        refreshFiles();
    }

    private void showChangePwDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText oldPw = new EditText(this);
        oldPw.setHint("كلمة السر الحالية");
        oldPw.setInputType(InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(oldPw);

        final EditText newPw = new EditText(this);
        newPw.setHint("كلمة السر الجديدة");
        newPw.setInputType(InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(newPw);

        final EditText confirmPw = new EditText(this);
        confirmPw.setHint("تأكيد كلمة السر الجديدة");
        confirmPw.setInputType(InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(confirmPw);

        new AlertDialog.Builder(this)
            .setTitle("تغيير كلمة السر")
            .setView(layout)
            .setPositiveButton("تغيير", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    String o = oldPw.getText().toString();
                    String n = newPw.getText().toString();
                    String c = confirmPw.getText().toString();
                    if (n.length() < 4) { toast("كلمة السر الجديدة قصيرة"); return; }
                    if (!n.equals(c)) { toast("كلمتا السر غير متطابقتين"); return; }
                    if (vault.changePassword(o, n)) {
                        currentPassword = n;
                        toast("✅ تم تغيير كلمة السر");
                    } else {
                        toast("❌ كلمة السر الحالية خاطئة");
                    }
                }
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_FILE && res == RESULT_OK && data != null) {
            addToVault(data.getData());
            refreshFiles();
        }
    }

    private void addToVault(Uri uri) {
        try {
            String name = getFileName(uri);
            InputStream in = getContentResolver().openInputStream(uri);
            vault.addFile(in, name);
            toast("🔒 مشفّر: " + name);
        } catch (Exception e) {
            toast("خطأ: " + e.getMessage());
        }
    }

    private void refreshFiles() {
        String[] names = vault.listFiles();
        filesContainer.removeAllViews();
        for (final String name : names) {
            TextView tv = new TextView(this);
            long kb = vault.fileSize(name) / 1024;
            String icon = isImage(name) ? "🖼️" : isVideo(name) ? "🎬" : "📄";
            tv.setText(icon + " " + name + "\n    " + kb + " KB");
            tv.setTextSize(14);
            tv.setPadding(20, 30, 20, 30);
            tv.setBackgroundColor(0xFF1A1A1A);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    previewFile(name);
                }
            });
            filesContainer.addView(tv);
        }
        countView.setText(names.length + " ملفات مشفّرة");
    }

    private boolean isImage(String name) {
        String m = getMime(name);
        return m != null && m.startsWith("image/");
    }

    private boolean isVideo(String name) {
        String m = getMime(name);
        return m != null && m.startsWith("video/");
    }

    private void previewFile(String name) {
        try {
            File f = vault.decryptToTemp(name);

            if (isImage(name)) {
                showImagePreview(f, name);
            } else if (isVideo(name)) {
                showVideoPreview(f, name);
            } else {
                // Fallback: open externally
                Uri uri = FileProvider.getUriForFile(this,
                    "com.worm.vault.fileprovider", f);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, getMime(name));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(i);
            }
        } catch (Exception e) {
            toast("خطأ: " + e.getMessage());
        }
    }

    private void showImagePreview(File f, String name) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xFF000000);
        layout.setPadding(20, 40, 20, 20);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        ImageView iv = new ImageView(this);
        Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (bmp != null) iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        layout.addView(iv, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button back = new Button(this);
        back.setText("← رجوع");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVaultScreen();
            }
        });
        layout.addView(back);

        setContentView(layout);
    }

    private void showVideoPreview(File f, String name) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xFF000000);
        layout.setPadding(20, 40, 20, 20);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        VideoView vv = new VideoView(this);
        vv.setVideoPath(f.getAbsolutePath());
        MediaController mc = new MediaController(this);
        mc.setAnchorView(vv);
        vv.setMediaController(mc);
        layout.addView(vv, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        vv.start();

        Button back = new Button(this);
        back.setText("← رجوع");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVaultScreen();
            }
        });
        layout.addView(back);

        setContentView(layout);
    }

    private String getMime(String name) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(name);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            ext == null ? "" : ext.toLowerCase());
        return mime == null ? "*/*" : mime;
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = c.getString(idx);
                c.close();
            }
        }
        if (result == null) result = uri.getLastPathSegment();
        if (result == null) result = "file_" + System.currentTimeMillis();
        return result;
    }
}
