package com.worm.vault;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class CalculatorActivity extends AppCompatActivity {
    private TextView display;
    private StringBuilder currentInput = new StringBuilder();
    private double result = 0;
    private String currentOp = "";
    private boolean justEvaluated = false;

    private final String[] buttons = {
        "C", "(", ")", "/",
        "7", "8", "9", "*",
        "4", "5", "6", "-",
        "1", "2", "3", "+",
        "0", ".", "%", "="
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1C1C1E);
        root.setPadding(20, 60, 20, 20);

        display = new TextView(this);
        display.setText("0");
        display.setTextSize(48);
        display.setTextColor(0xFFFFFFFF);
        display.setGravity(Gravity.END | Gravity.BOTTOM);
        display.setPadding(20, 40, 20, 40);
        display.setMinHeight(200);
        root.addView(display);

        // Buttons grid
        for (int row = 0; row < 5; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            rowLayout.setLayoutParams(rowParams);

            for (int col = 0; col < 4; col++) {
                int idx = row * 4 + col;
                if (idx >= buttons.length) break;
                final String label = buttons[idx];
                Button btn = new Button(this);
                btn.setText(label);
                btn.setTextSize(24);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                p.setMargins(5, 5, 5, 5);
                btn.setLayoutParams(p);

                if ("=".equals(label)) {
                    btn.setBackgroundColor(0xFFE83E3E);
                } else if ("C".equals(label) || "/".equals(label) || "*".equals(label)
                        || "-".equals(label) || "+".equals(label)) {
                    btn.setBackgroundColor(0xFF505050);
                } else {
                    btn.setBackgroundColor(0xFF333333);
                }
                btn.setTextColor(0xFFFFFFFF);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { onButton(label); }
                });
                rowLayout.addView(btn);
            }
            root.addView(rowLayout);
        }

        setContentView(root);
    }

    private void onButton(String label) {
        if ("=".equals(label)) {
            onEquals();
            return;
        }
        if ("C".equals(label)) {
            currentInput.setLength(0);
            result = 0;
            currentOp = "";
            justEvaluated = false;
            display.setText("0");
            return;
        }
        if (justEvaluated) {
            currentInput.setLength(0);
            justEvaluated = false;
        }
        if ("+".equals(label) || "-".equals(label) || "*".equals(label)
                || "/".equals(label) || "%".equals(label)) {
            String input = currentInput.toString();
            if (input.isEmpty()) return;
            // Check secret password
            if (checkSecret(input)) return;

            // Calculate previous
            try {
                double val = Double.parseDouble(input);
                if (currentOp.isEmpty()) {
                    result = val;
                } else {
                    result = applyOp(result, val, currentOp);
                }
                currentOp = label;
                currentInput.setLength(0);
                display.setText(formatNum(result) + " " + label);
            } catch (Exception e) {
                display.setText("خطأ");
            }
            return;
        }
        // Digit / .
        currentInput.append(label);
        display.setText(currentInput.toString());
    }

    private void onEquals() {
        String input = currentInput.toString();
        // Secret password check
        if (checkSecret(input)) return;

        try {
            double val = Double.parseDouble(input);
            double res;
            if (currentOp.isEmpty()) res = val;
            else res = applyOp(result, val, currentOp);
            result = res;
            display.setText(formatNum(res));
            currentInput.setLength(0);
            currentInput.append(formatNum(res));
            currentOp = "";
            justEvaluated = true;
        } catch (Exception e) {
            display.setText("خطأ");
        }
    }

    private boolean checkSecret(String input) {
        // Try to unlock the vault with the input as password
        VaultManager vm = new VaultManager(this);
        if (vm.unlock(input)) {
            vm.lock();
            // Move to MainActivity
            Intent i = new Intent(CalculatorActivity.this, MainActivity.class);
            i.putExtra("unlocked_with", input);
            startActivity(i);
            finish();
            return true;
        }
        return false;
    }

    private double applyOp(double a, double b, String op) {
        switch (op) {
            case "+": return a + b;
            case "-": return a - b;
            case "*": return a * b;
            case "/": return b == 0 ? 0 : a / b;
            case "%": return a % b;
        }
        return b;
    }

    private String formatNum(double d) {
        if (d == (long) d) return String.valueOf((long) d);
        return String.valueOf(d);
    }
}
