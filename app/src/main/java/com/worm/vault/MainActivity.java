package com.worm.vault;

import android.os.Bundle;
import android.widget.TextView;
import android.view.Gravity;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("WormVault\n\nv1.0\n\nالخزنة المشفرة");
        tv.setTextSize(28);
        tv.setGravity(Gravity.CENTER);
        setContentView(tv);
    }
}
