package com.worm.vault;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_FILE = 1001;
    private VaultManager vault;
    private LinearLayout filesContainer;
    private TextView countView;

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
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(input);

        Button loginBtn = new Button(this);
        loginBtn.setText("دخول");
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pw = input.getText().toString();
                if (pw.length() < 4) {
                    Toast.makeText(MainActivity.this,
                        "كلمة السر قصيرة جداً", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (vault.unlock(pw)) {
                    showVaultScreen();
                } else {
                    Toast.makeText(MainActivity.this,
                        "كلمة السر غير صحيحة", Toast.LENGTH_SHORT).show();
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

        Button lockBtn = new Button(this);
        lockBtn.setText("قفل");
        lockBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vault.lock();
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
            Toast.makeText(this, "🔒 مشفّر: " + name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "خطأ: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshFiles() {
        String[] names = vault.listFiles();
        filesContainer.removeAllViews();
        for (String name : names) {
            TextView tv = new TextView(this);
            long kb = vault.fileSize(name) / 1024;
            tv.setText("🔒 " + name + "\n    " + kb + " KB (مشفّر)");
            tv.setTextSize(14);
            tv.setPadding(20, 30, 20, 30);
            filesContainer.addView(tv);
        }
        countView.setText(names.length + " ملفات مشفّرة");
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
