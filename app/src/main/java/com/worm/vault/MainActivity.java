package com.worm.vault;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_FILE = 1001;
    private static final int PICK_MULTI = 1002;
    private static final int EXPORT_BACKUP = 1003;
    private static final int IMPORT_BACKUP = 1004;
    private static final String PREFS = "wormvault";
    private static final String KEY_BIO = "biometric_enabled";
    private static final String KEY_BIO_PW = "biometric_password";

    private VaultManager vault;
    private LinearLayout filesContainer;
    private TextView countView;
    private String currentPassword;
    private SharedPreferences prefs;
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        vault = new VaultManager(this);
        if (prefs.getBoolean(KEY_BIO, false) && canUseBiometric()) {
            showBiometricLogin();
        } else {
            showLoginScreen();
        }
    }

    private boolean canUseBiometric() {
        BiometricManager bm = BiometricManager.from(this);
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void showBiometricLogin() {
        Executor ex = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, ex, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult r) {
                String pw = prefs.getString(KEY_BIO_PW, "");
                if (!pw.isEmpty() && vault.unlock(pw)) {
                    currentPassword = pw;
                    showVaultScreen();
                } else showLoginScreen();
            }
            @Override
            public void onAuthenticationError(int c, @NonNull CharSequence m) {
                showLoginScreen();
            }
        });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
            .setTitle("WormVault")
            .setSubtitle("افتح الخزنة ببصمتك")
            .setNegativeButtonText("استخدام كلمة السر")
            .build();
        prompt.authenticate(info);
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
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(input);
        Button loginBtn = new Button(this);
        loginBtn.setText("دخول");
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pw = input.getText().toString();
                if (pw.length() < 4) { toast("كلمة السر قصيرة"); return; }
                if (vault.unlock(pw)) {
                    currentPassword = pw;
                    if (prefs.getBoolean(KEY_BIO, false)) prefs.edit().putString(KEY_BIO_PW, pw).apply();
                    showVaultScreen();
                } else toast("كلمة السر غير صحيحة");
            }
        });
        layout.addView(loginBtn);
        setContentView(layout);
    }

    private void showVaultScreen() {
        ScrollView outer = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 60, 40, 40);
        TextView title = new TextView(this);
        title.setText("الخزنة مفتوحة");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        countView = new TextView(this);
        countView.setTextSize(14);
        countView.setGravity(Gravity.CENTER);
        countView.setPadding(0, 10, 0, 10);
        root.addView(countView);
        final EditText search = new EditText(this);
        search.setHint("بحث...");
        search.setSingleLine(true);
        search.setText(searchQuery);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { searchQuery = s.toString(); refreshFiles(); }
        });
        root.addView(search);
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        Button addBtn = new Button(this);
        addBtn.setText("+ ملف");
        addBtn.setLayoutParams(lp1);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickSingleFile(); }
        });
        row1.addView(addBtn);
        Button addMultiBtn = new Button(this);
        addMultiBtn.setText("+ متعددة");
        addMultiBtn.setLayoutParams(lp1);
        addMultiBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickMultipleFiles(); }
        });
        row1.addView(addMultiBtn);
        root.addView(row1);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        Button changePwBtn = new Button(this);
        changePwBtn.setText("كلمة السر");
        changePwBtn.setLayoutParams(lp1);
        changePwBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showChangePwDialog(); }
        });
        row2.addView(changePwBtn);
        boolean bioOn = prefs.getBoolean(KEY_BIO, false);
        Button bioBtn = new Button(this);
        bioBtn.setText(bioOn ? "بصمة ON" : "بصمة");
        bioBtn.setLayoutParams(lp1);
        bioBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleBiometric(); }
        });
        row2.addView(bioBtn);
        root.addView(row2);
        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        Button exportBtn = new Button(this);
        exportBtn.setText("تصدير");
        exportBtn.setLayoutParams(lp1);
        exportBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startExportBackup(); }
        });
        row3.addView(exportBtn);
        Button importBtn = new Button(this);
        importBtn.setText("استيراد");
        importBtn.setLayoutParams(lp1);
        importBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startImportBackup(); }
        });
        row3.addView(importBtn);
        root.addView(row3);
        Button lockBtn = new Button(this);
        lockBtn.setText("قفل");
        lockBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vault.lock();
                vault.clearTemp();
                currentPassword = null;
                prefs.edit().remove(KEY_BIO_PW).apply();
                searchQuery = "";
                showLoginScreen();
            }
        });
        root.addView(lockBtn);
        filesContainer = new LinearLayout(this);
        filesContainer.setOrientation(LinearLayout.VERTICAL);
        filesContainer.setPadding(0, 20, 0, 0);
        root.addView(filesContainer);
        outer.addView(root);
        setContentView(outer);
        refreshFiles();
    }

    private void pickSingleFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, PICK_FILE);
    }

    private void pickMultipleFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, PICK_MULTI);
    }

    private void startExportBackup() {
        final EditText pw = new EditText(this);
        pw.setHint("كلمة سر النسخة الاحتياطية");
        pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
            .setTitle("تصدير نسخة احتياطية")
            .setMessage("أدخل كلمة سر للنسخة (مختلفة عن كلمة سر الخزنة)")
            .setView(pw)
            .setPositiveButton("التالي", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String backupPw = pw.getText().toString();
                    if (backupPw.length() < 4) { toast("كلمة سر النسخة قصيرة"); return; }
                    String ts = String.valueOf(System.currentTimeMillis());
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/octet-stream");
                    i.putExtra(Intent.EXTRA_TITLE, "wormvault-backup-" + ts + ".enc");
                    i.putExtra("backup_pw", backupPw);
                    startActivityForResult(i, EXPORT_BACKUP);
                }
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void doExportBackup(Uri dest, String backupPw) {
        try {
            OutputStream out = getContentResolver().openOutputStream(dest);
            if (vault.exportBackup(out, backupPw)) toast("تم التصدير");
            else toast("فشل التصدير");
            out.close();
        } catch (Exception e) { toast("خطأ: " + e.getMessage()); }
    }

    private void startImportBackup() {
        new AlertDialog.Builder(this)
            .setTitle("استيراد نسخة احتياطية")
            .setMessage("سيُستبدل كل محتوى الخزنة الحالي. متأكد؟")
            .setPositiveButton("اختر ملفاً", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, IMPORT_BACKUP);
                }
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void askImportPassword(final Uri uri) {
        final EditText pw = new EditText(this);
        pw.setHint("كلمة سر النسخة الاحتياطية");
        pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
            .setTitle("كلمة سر النسخة")
            .setView(pw)
            .setPositiveButton("استيراد", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    doImportBackup(uri, pw.getText().toString());
                }
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void doImportBackup(Uri src, String backupPw) {
        try {
            InputStream in = getContentResolver().openInputStream(src);
            if (vault.importBackup(in, backupPw)) {
                in.close();
                toast("تم الاستيراد — ادخل كلمة سر الخزنة");
                vault.lock();
                currentPassword = null;
                prefs.edit().remove(KEY_BIO_PW).apply();
                showLoginScreen();
            } else {
                in.close();
                toast("فشل — كلمة سر خاطئة");
            }
        } catch (Exception e) { toast("خطأ: " + e.getMessage()); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == PICK_FILE) {
            addToVault(data.getData());
            refreshFiles();
        } else if (req == PICK_MULTI) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                for (int i = 0; i < n; i++) uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            int count = 0;
            for (Uri u : uris) if (addToVault(u)) count++;
            toast("شُفّر " + count + " ملف");
            refreshFiles();
        } else if (req == EXPORT_BACKUP) {
            String backupPw = data.getStringExtra("backup_pw");
            doExportBackup(data.getData(), backupPw == null ? "" : backupPw);
        } else if (req == IMPORT_BACKUP) {
            askImportPassword(data.getData());
        }
    }

    private boolean addToVault(Uri uri) {
        try {
            String name = getFileName(uri);
            InputStream in = getContentResolver().openInputStream(uri);
            vault.addFile(in, name);
            return true;
        } catch (Exception e) {
            toast("خطأ: " + e.getMessage());
            return false;
        }
    }

    private void refreshFiles() {
        String[] allNames = vault.listFiles();
        List<String> filtered = new ArrayList<>();
        String q = searchQuery.toLowerCase().trim();
        for (String n : allNames) {
            if (q.isEmpty() || n.toLowerCase().contains(q)) filtered.add(n);
        }
        filesContainer.removeAllViews();
        for (final String name : filtered) {
            final LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(20, 20, 20, 20);
            card.setBackgroundColor(0xFF1A1A1A);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.setMargins(0, 0, 0, 15);
            card.setLayoutParams(clp);
            TextView tv = new TextView(this);
            long kb = vault.fileSize(name) / 1024;
            String icon = isImage(name) ? "[IMG]" : isVideo(name) ? "[VID]" : "[DOC]";
            tv.setText(icon + " " + name + "\n    " + kb + " KB");
            tv.setTextSize(14);
            tv.setTextColor(0xFFEEEEEE);
            card.addView(tv);
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            Button openBtn = new Button(this);
            openBtn.setText("فتح");
            openBtn.setLayoutParams(ap);
            openBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { previewFile(name); }
            });
            actions.addView(openBtn);
            Button shareBtn = new Button(this);
            shareBtn.setText("مشاركة");
            shareBtn.setLayoutParams(ap);
            shareBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { shareFile(name); }
            });
            actions.addView(shareBtn);
            Button renameBtn = new Button(this);
            renameBtn.setText("إعادة");
            renameBtn.setLayoutParams(ap);
            renameBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { renameFileDialog(name); }
            });
            actions.addView(renameBtn);
            Button delBtn = new Button(this);
            delBtn.setText("حذف");
            delBtn.setLayoutParams(ap);
            delBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { deleteFileDialog(name); }
            });
            actions.addView(delBtn);
            card.addView(actions);
            filesContainer.addView(card);
        }
        countView.setText(filtered.size() + " / " + allNames.length + " ملف");
    }

    private void shareFile(String name) {
        try {
            File f = vault.decryptToTemp(name);
            Uri uri = FileProvider.getUriForFile(this, "com.worm.vault.fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(getMime(name));
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "مشاركة " + name));
        } catch (Exception e) { toast("خطأ: " + e.getMessage()); }
    }

    private void renameFileDialog(final String oldName) {
        final EditText input = new EditText(this);
        input.setText(oldName);
        input.setSelection(oldName.length());
        new AlertDialog.Builder(this)
            .setTitle("إعادة تسمية")
            .setView(input)
            .setPositiveButton("حفظ", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty() || newName.equals(oldName)) return;
                    if (vault.renameFile(oldName, newName)) { toast("تم"); refreshFiles(); }
                    else toast("فشل");
                }
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void deleteFileDialog(final String name) {
        new AlertDialog.Builder(this)
            .setTitle("حذف")
            .setMessage("حذف " + name + "؟")
            .setPositiveButton("حذف", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    vault.deleteFile(name);
                    toast("حُذف");
                    refreshFiles();
                }
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void toggleBiometric() {
        boolean current = prefs.getBoolean(KEY_BIO, false);
        if (current) {
            prefs.edit().putBoolean(KEY_BIO, false).remove(KEY_BIO_PW).apply();
            toast("تم إيقاف البصمة");
            showVaultScreen();
            return;
        }
        if (!canUseBiometric()) { toast("جهازك لا يدعم البصمة"); return; }
        final EditText pwInput = new EditText(this);
        pwInput.setHint("أدخل كلمة السر");
        pwInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
            .setTitle("تفعيل البصمة")
            .setView(pwInput)
            .setPositiveButton("تفعيل", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String pw = pwInput.getText().toString();
                    VaultManager test = new VaultManager(MainActivity.this);
                    if (test.unlock(pw)) {
                        prefs.edit().putBoolean(KEY_BIO, true).putString(KEY_BIO_PW, pw).apply();
                        toast("تم");
                        showVaultScreen();
                    } else toast("كلمة السر خاطئة");
                }
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void showChangePwDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);
        final EditText oldPw = new EditText(this);
        oldPw.setHint("كلمة السر الحالية");
        oldPw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(oldPw);
        final EditText newPw = new EditText(this);
        newPw.setHint("كلمة السر الجديدة");
        newPw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(newPw);
        final EditText confirmPw = new EditText(this);
        confirmPw.setHint("تأكيد الجديدة");
        confirmPw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(confirmPw);
        new AlertDialog.Builder(this)
            .setTitle("تغيير كلمة السر")
            .setView(layout)
            .setPositiveButton("تغيير", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String o = oldPw.getText().toString();
                    String n = newPw.getText().toString();
                    String c = confirmPw.getText().toString();
                    if (n.length() < 4) { toast("كلمة السر الجديدة قصيرة"); return; }
                    if (!n.equals(c)) { toast("غير متطابقتين"); return; }
                    if (vault.changePassword(o, n)) {
                        currentPassword = n;
                        if (prefs.getBoolean(KEY_BIO, false)) prefs.edit().putString(KEY_BIO_PW, n).apply();
                        toast("تم");
                    } else toast("خاطئة");
                }
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private boolean isImage(String name) { String m = getMime(name); return m != null && m.startsWith("image/"); }
    private boolean isVideo(String name) { String m = getMime(name); return m != null && m.startsWith("video/"); }

    private void previewFile(String name) {
        try {
            File f = vault.decryptToTemp(name);
            if (isImage(name)) showImagePreview(f, name);
            else if (isVideo(name)) showVideoPreview(f, name);
            else {
                Uri uri = FileProvider.getUriForFile(this, "com.worm.vault.fileprovider", f);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, getMime(name));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(i);
            }
        } catch (Exception e) { toast("خطأ: " + e.getMessage()); }
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
        layout.addView(iv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        Button back = new Button(this);
        back.setText("رجوع");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showVaultScreen(); }
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
        layout.addView(vv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        vv.start();
        Button back = new Button(this);
        back.setText("رجوع");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showVaultScreen(); }
        });
        layout.addView(back);
        setContentView(layout);
    }

    private String getMime(String name) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(name);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext == null ? "" : ext.toLowerCase());
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
