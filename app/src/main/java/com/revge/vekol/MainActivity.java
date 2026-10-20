package com.revge.vekol;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends Activity {

    private TextView status;
    private Button recordButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final int REQUEST_MIC = 100;
    private static final int SAMPLE_RATE = 16000;
    private static final int RECORD_SECONDS = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(32, 40, 32, 32);

        TextView title = new TextView(this);
        title.setText("یاریدەدەری کوردی");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        status = new TextView(this);
        status.setTextSize(17);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 24, 0, 24);
        layout.addView(status);

        Button checkButton = new Button(this);
        checkButton.setText("پشکنینی مۆدێل");
        layout.addView(checkButton);
        checkButton.setOnClickListener(v -> checkModels());

        recordButton = new Button(this);
        recordButton.setText("🎙️ تۆمارکردنی دەنگ");
        layout.addView(recordButton);
        recordButton.setOnClickListener(v -> requestMic());

        setContentView(layout);
        checkModels();
    }

    private void checkModels() {
        File dir = new File(getFilesDir(), "vekol");

        File encoder = new File(dir, "encoder_model.onnx");
        File decoder = new File(dir, "decoder_model_merged.onnx");

        boolean ok = encoder.isFile() && decoder.isFile()
                && encoder.length() > 0 && decoder.length() > 0;

        if (ok) {
            status.setText("فایلەکانی مۆدێل هەن.\nئامادەی تۆمارکردنی دەنگین.");
        } else {
            status.setText("فایلەکانی مۆدێل نەدۆزرایەوە.\nسەرەتا دابەزاندنیان تەواو بکە.");
        }
    }

    private void requestMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_MIC);
            return;
        }
        recordAudio();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (requestCode == REQUEST_MIC && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            recordAudio();
        } else if (requestCode == REQUEST_MIC) {
            status.setText("بۆ تۆمارکردن پێویستە مۆڵەتی مایک بدەیت.");
        }
    }

    private void recordAudio() {
        recordButton.setEnabled(false);
        status.setText("تۆمارکردنی دەنگ دەستی پێکرد...");

        new Thread(() -> {
            int minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            if (minBuffer <= 0) {
                showError("ئامادەکردنی مایک سەرکەوتوو نەبوو.");
                return;
            }

            AudioRecord recorder = null;

            try {
                recorder = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(minBuffer, SAMPLE_RATE * 2));

                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    throw new IOException("مایک ئامادە نییە.");
                }

                short[] samples = new short[SAMPLE_RATE * RECORD_SECONDS];
                recorder.startRecording();

                int offset = 0;
                while (offset < samples.length) {
                    int n = recorder.read(
                            samples, offset, samples.length - offset);

                    if (n < 0) {
                        throw new IOException("هەڵە لە خوێندنەوەی دەنگ.");
                    }
                    offset += n;
                }

                recorder.stop();

                File output = new File(getFilesDir(), "recording.pcm");
                try (FileOutputStream out = new FileOutputStream(output)) {
                    for (short sample : samples) {
                        out.write(sample & 0xff);
                        out.write((sample >> 8) & 0xff);
                    }
                }

                handler.post(() -> {
                    status.setText(
                            "تۆمارکردن تەواو بوو.\n" +
                            "فایل: recording.pcm\n" +
                            "ئەمە هێشتا دەقی ناسراو نییە.");
                    recordButton.setEnabled(true);
                });

            } catch (Exception e) {
                showError("هەڵە: " + e.getMessage());
            } finally {
                if (recorder != null) {
                    try {
                        recorder.release();
                    } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private void showError(String message) {
        handler.post(() -> {
            status.setText(message);
            recordButton.setEnabled(true);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }
}
