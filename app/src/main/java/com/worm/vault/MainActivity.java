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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_FILE = 1001;
    private String password = "";
    private LinearLayout filesContainer;
    private TextView countView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
                password = pw;
                showVaultScreen();
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
                password = "";
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
            copyToVault(data.getData());
            refreshFiles();
        }
    }

    private void copyToVault(Uri uri) {
        try {
            String name = getFileName(uri);
            File dir = new File(getFilesDir(), "vault");
            dir.mkdirs();
            File outFile = new File(dir, name);
            InputStream in = getContentResolver().openInputStream(uri);
            FileOutputStream out = new FileOutputStream(outFile);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            Toast.makeText(this, "أُضيف: " + name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "خطأ: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshFiles() {
        File dir = new File(getFilesDir(), "vault");
        File[] files = dir.listFiles();
        filesContainer.removeAllViews();
        int count = 0;
        if (files != null) {
            for (File f : files) {
                TextView tv = new TextView(this);
                long kb = f.length() / 1024;
                tv.setText("📄 " + f.getName() + "\n    " + kb + " KB");
                tv.setTextSize(14);
                tv.setPadding(20, 30, 20, 30);
                filesContainer.addView(tv);
                count++;
            }
        }
        countView.setText(count + " ملفات");
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
