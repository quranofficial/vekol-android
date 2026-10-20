package com.revge.vekol;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 48, 32, 32);
        layout.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("یاریدەدەری کوردی");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        status = new TextView(this);
        status.setText("بەخێربێیت! ئامادەین بۆ ڕێکخستنی Vekol.");
        status.setTextSize(18);
        status.setPadding(0, 32, 0, 32);
        layout.addView(status);

        Button mic = new Button(this);
        mic.setText("🎙️ دەستپێکردنی تۆمارکردنی دەنگ");
        layout.addView(mic);

        mic.setOnClickListener(v -> {
            status.setText("هێشتا سیستەمی ناسینەوەی دەنگ پێکەوە نەبەستراوە.");
            Toast.makeText(this,
                "هەنگاوی داهاتوو: پێکەوەبەستنی مۆدێلی Vekol",
                Toast.LENGTH_LONG).show();
        });

        setContentView(layout);
    }
}
