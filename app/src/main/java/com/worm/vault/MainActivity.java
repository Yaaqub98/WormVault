package com.worm.vault;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private EditText passwordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showLoginScreen();
    }

    private void showLoginScreen() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 200, 60, 60);
        layout.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("WormVault");
        title.setTextSize(36);
        title.setGravity(android.view.Gravity.CENTER);
        layout.addView(title);

        TextView sub = new TextView(this);
        sub.setText("الخزنة المشفرة");
        sub.setTextSize(16);
        sub.setGravity(android.view.Gravity.CENTER);
        sub.setPadding(0, 20, 0, 80);
        layout.addView(sub);

        passwordInput = new EditText(this);
        passwordInput.setHint("كلمة السر");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passwordInput);

        Button loginBtn = new Button(this);
        loginBtn.setText("دخول");
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pw = passwordInput.getText().toString();
                if (pw.length() < 4) {
                    Toast.makeText(MainActivity.this,
                        "كلمة السر قصيرة جداً", Toast.LENGTH_SHORT).show();
                    return;
                }
                showVaultScreen(pw);
            }
        });
        layout.addView(loginBtn);

        setContentView(layout);
    }

    private void showVaultScreen(String password) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 100, 40, 40);

        TextView title = new TextView(this);
        title.setText("الخزنة مفتوحة");
        title.setTextSize(28);
        title.setGravity(android.view.Gravity.CENTER);
        layout.addView(title);

        TextView count = new TextView(this);
        count.setText("0 ملفات");
        count.setTextSize(16);
        count.setGravity(android.view.Gravity.CENTER);
        count.setPadding(0, 30, 0, 30);
        layout.addView(count);

        Button addBtn = new Button(this);
        addBtn.setText("+ إضافة ملف");
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this,
                    "الإضافة قيد التطوير", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(addBtn);

        Button lockBtn = new Button(this);
        lockBtn.setText("قفل");
        lockBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLoginScreen();
            }
        });
        layout.addView(lockBtn);

        setContentView(layout);
    }
}
