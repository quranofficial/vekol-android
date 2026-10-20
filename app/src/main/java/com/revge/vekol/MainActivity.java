package com.revge.vekol;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private TextView status;
    private ProgressBar progress;
    private Button downloadButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final String BASE_URL =
        "https://huggingface.co/RevgeAI/vekol-stt-ckb-tiny/resolve/main/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(32, 48, 32, 32);

        TextView title = new TextView(this);
        title.setText("یاریدەدەری کوردی");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        status = new TextView(this);
        status.setText("مۆدێلی Vekol ئامادە بکە");
        status.setTextSize(17);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 24, 0, 24);
        layout.addView(status);

        progress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        layout.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24));

        downloadButton = new Button(this);
        downloadButton.setText("⬇️ دابەزاندنی مۆدێل");
        layout.addView(downloadButton);

        downloadButton.setOnClickListener(v -> startDownloads());

        setContentView(layout);
    }

    private void startDownloads() {
        downloadButton.setEnabled(false);
        progress.setProgress(0);
        status.setText("ئامادەکاری دابەزاندن...");

        new Thread(() -> {
            String[] files = {
                "encoder_model.onnx",
                "decoder_model_merged.onnx"
            };

            try {
                File dir = new File(getFilesDir(), "vekol");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new Exception("نەتوانرا بوخچە دروست بکرێت");
                }

                for (String name : files) {
                    File target = new File(dir, name);

                    if (target.exists() && target.length() > 0) {
                        continue;
                    }

                    File temp = new File(dir, name + ".part");
                    URL url = new URL(BASE_URL + name);
                    HttpURLConnection connection =
                            (HttpURLConnection) url.openConnection();

                    connection.setConnectTimeout(20000);
                    connection.setReadTimeout(60000);
                    connection.setInstanceFollowRedirects(true);
                    connection.connect();

                    int code = connection.getResponseCode();
                    if (code != 200) {
                        connection.disconnect();
                        throw new Exception(
                            "هەڵەی دابەزاندن: HTTP " + code);
                    }

                    int total = connection.getContentLength();
                    long downloaded = 0;

                    try (InputStream in = connection.getInputStream();
                         FileOutputStream out = new FileOutputStream(temp)) {
                        byte[] buffer = new byte[65536];
                        int count;

                        while ((count = in.read(buffer)) != -1) {
                            out.write(buffer, 0, count);
                            downloaded += count;

                            if (total > 0) {
                                final int percent =
                                    (int) (downloaded * 100 / total);
                                handler.post(() -> {
                                    status.setText(
                                        "دابەزاندن: " + name +
                                        " — " + percent + "%");
                                    progress.setProgress(percent);
                                });
                            }
                        }
                    } finally {
                        connection.disconnect();
                    }

                    if (temp.length() == 0 ||
                        (total > 0 && temp.length() != total)) {
                        temp.delete();
                        throw new Exception("فایلی ناتەواو: " + name);
                    }

                    if (!temp.renameTo(target)) {
                        temp.delete();
                        throw new Exception("نەتوانرا فایل هەڵبگیرێت");
                    }
                }

                handler.post(() -> {
                    progress.setProgress(100);
                    status.setText(
                        "فایلەکان دابەزێنران. هێشتا پێویستە مۆدێلەکە پێکەوە ببەسترێت.");
                    downloadButton.setEnabled(true);
                    Toast.makeText(this, "دابەزاندن تەواو بوو",
                            Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                handler.post(() -> {
                    status.setText("دابەزاندن سەرکەوتوو نەبوو: " +
                            e.getMessage());
                    downloadButton.setEnabled(true);
                });
            }
        }).start();
    }
}
