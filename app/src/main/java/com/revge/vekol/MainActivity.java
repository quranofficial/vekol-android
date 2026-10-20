package com.revge.vekol;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private TextView status;
    private Button folderButton;
    private Button downloadButton;
    private Button recordButton;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final int REQUEST_FOLDER = 201;
    private static final int REQUEST_MIC = 202;

    private static final int SAMPLE_RATE = 16000;
    private static final int RECORD_SECONDS = 5;

    private static final String PREFS = "vekol_settings";
    private static final String FOLDER_URI = "model_folder_uri";

    private static final String BASE_URL =
            "https://huggingface.co/RevgeAI/vekol-stt-ckb-tiny/resolve/main/";

    private static final String ENCODER = "encoder_model.onnx";
    private static final String DECODER = "decoder_model_merged.onnx";
    private static final String VOCAB = "vocab.json";
    private static final String MEL = "mel_filters.npy";

    private volatile boolean downloading = false;
    private volatile boolean recording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(24, 32, 24, 24);

        TextView title = new TextView(this);
        title.setText("یاریدەدەری کوردی\nVekol STT");
        title.setTextSize(27);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 24, 0, 24);
        layout.addView(status);

        folderButton = new Button(this);
        folderButton.setText("📁 هەڵبژاردنی بوخچەی مۆدێل");
        layout.addView(folderButton);
        folderButton.setOnClickListener(v -> chooseFolder());

        downloadButton = new Button(this);
        downloadButton.setText("⬇️ پشکنین و دابەزاندنی ٤ فایل");
        layout.addView(downloadButton);
        downloadButton.setOnClickListener(v -> startDownloads());

        recordButton = new Button(this);
        recordButton.setText("🎙️ تۆمارکردنی دەنگ");
        layout.addView(recordButton);
        recordButton.setOnClickListener(v -> requestMic());

        TextView note = new TextView(this);
        note.setText(
                "\nتێبینی: تۆمارکردن کار دەکات، بەڵام " +
                "بەشی گۆڕینی دەنگ بۆ دەق هێشتا جێبەجێ نەکراوە."
        );
        note.setTextSize(14);
        layout.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);

        checkModels();
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        startActivityForResult(intent, REQUEST_FOLDER);
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_FOLDER ||
                resultCode != RESULT_OK ||
                data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();

        try {
            int flags = data.getFlags() &
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                     Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            getContentResolver().takePersistableUriPermission(uri, flags);

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(FOLDER_URI, uri.toString())
                    .apply();

            DocumentFile folder = DocumentFile.fromTreeUri(this, uri);

            if (folder != null && folder.isDirectory() && folder.canWrite()) {
                status.setText(
                        "بوخچە هەڵبژێردرا.\n" +
                        "ئێستا پشکنین و دابەزاندن بکە."
                );
                checkModels();
            } else {
                status.setText("نەتوانرا بوخچەکە بۆ نووسین بەکاربهێنرێت.");
            }

        } catch (Exception e) {
            status.setText("هەڵە لە هەڵبژاردنی بوخچە: " + errorText(e));
        }
    }

    private DocumentFile getFolder() {
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(FOLDER_URI, null);

        if (saved == null) return null;

        try {
            DocumentFile folder = DocumentFile.fromTreeUri(
                    this, Uri.parse(saved));

            if (folder != null &&
                    folder.isDirectory() &&
                    folder.canWrite()) {
                return folder;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private DocumentFile findFile(DocumentFile folder, String name) {
        if (folder == null) return null;

        try {
            for (DocumentFile file : folder.listFiles()) {
                if (name.equals(file.getName())) {
                    return file;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private boolean validFile(
            DocumentFile folder, String name, long minimumBytes) {

        DocumentFile file = findFile(folder, name);

        return file != null &&
                file.isFile() &&
                file.length() >= minimumBytes;
    }

    private boolean allModelsReady(DocumentFile folder) {
        return validFile(folder, ENCODER, 5_000_000L) &&
                validFile(folder, DECODER, 50_000_000L) &&
                validFile(folder, VOCAB, 1_000L) &&
                validFile(folder, MEL, 1_000L);
    }

    private void checkModels() {
        DocumentFile folder = getFolder();

        if (folder == null) {
            status.setText(
                    "سەرەتا بوخچەی Download/VekolModels هەڵبژێرە."
            );
            return;
        }

        StringBuilder result = new StringBuilder();

        appendFileStatus(result, folder, ENCODER, 5_000_000L);
        appendFileStatus(result, folder, DECODER, 50_000_000L);
        appendFileStatus(result, folder, VOCAB, 1_000L);
        appendFileStatus(result, folder, MEL, 1_000L);

        if (allModelsReady(folder)) {
            result.append("\nهەموو ٤ فایلەکە بوونیان هەیە.");
            result.append("\nفایلەکان پێویستیان بە دابەزاندنەوە نییە.");
        } else {
            result.append("\nهەندێک فایل نییە یان قەبارەی گونجاوی نییە.");
            result.append("\nدوگمەی دابەزاندن دابگرە.");
        }

        status.setText(result.toString());
    }

    private void appendFileStatus(
            StringBuilder result,
            DocumentFile folder,
            String name,
            long minimum) {

        result.append(validFile(folder, name, minimum) ? "✅ " : "❌ ");
        result.append(name).append("\n");
    }

    private void startDownloads() {
        if (downloading) return;

        DocumentFile folder = getFolder();

        if (folder == null) {
            Toast.makeText(
                    this,
                    "سەرەتا بوخچەکە هەڵبژێرە.",
                    Toast.LENGTH_LONG
            ).show();
            chooseFolder();
            return;
        }

        downloading = true;
        downloadButton.setEnabled(false);
        folderButton.setEnabled(false);

        new Thread(() -> {
            try {
                downloadIfMissing(folder, ENCODER, 5_000_000L);
                downloadIfMissing(folder, DECODER, 50_000_000L);
                downloadIfMissing(folder, VOCAB, 1_000L);
                downloadIfMissing(folder, MEL, 1_000L);

                handler.post(() -> {
                    checkModels();

                    if (allModelsReady(folder)) {
                        status.setText(status.getText() +
                                "\n\nپشکنینی قەبارە تەواو بوو.");
                    }
                });

            } catch (Exception e) {
                String message = errorText(e);

                handler.post(() -> status.setText(
                        "دابەزاندن تەواو نەبوو:\n" + message
                ));

            } finally {
                downloading = false;

                handler.post(() -> {
                    downloadButton.setEnabled(true);
                    folderButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void downloadIfMissing(
            DocumentFile folder,
            String name,
            long minimumBytes) throws Exception {

        DocumentFile existing = findFile(folder, name);

        if (existing != null &&
                existing.isFile() &&
                existing.length() >= minimumBytes) {
            updateStatus("✅ " + name + " هەیە؛ دابەزاندنەوە پێویست نییە.");
            return;
        }

        if (existing != null && !existing.delete()) {
            throw new IOException("نەتوانرا فایلی کۆن بسڕدرێتەوە: " + name);
        }

        DocumentFile oldTemp = findFile(folder, name + ".part");
        if (oldTemp != null) oldTemp.delete();

        DocumentFile temp = folder.createFile(
                "application/octet-stream", name + ".part");

        if (temp == null) {
            throw new IOException("نەتوانرا فایل دروست بکرێت: " + name);
        }

        boolean success = false;
        HttpURLConnection connection = null;

        try {
            URL url = new URL(BASE_URL + name);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Vekol-Android");

            int response = connection.getResponseCode();

            if (response != HttpURLConnection.HTTP_OK) {
                throw new IOException(
                        "HTTP " + response + " بۆ " + name);
            }

            long total = connection.getContentLengthLong();
            long written = 0;
            int lastPercent = -1;

            try (InputStream in = connection.getInputStream();
                 OutputStream out = getContentResolver()
                         .openOutputStream(temp.getUri(), "wt")) {

                if (out == null) {
                    throw new IOException("نەتوانرا فایل بنووسرێت: " + name);
                }

                byte[] buffer = new byte[64 * 1024];
                int count;

                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    written += count;

                    if (total > 0) {
                        int percent = (int) (written * 100 / total);

                        if (percent != lastPercent) {
                            lastPercent = percent;
                            updateStatus(
                                    "دابەزاندنی " + name + "\n" +
                                    percent + "% — " +
                                    (written / (1024 * 1024)) + " MB"
                            );
                        }
                    } else {
                        updateStatus(
                                "دابەزاندنی " + name + "\n" +
                                (written / (1024 * 1024)) + " MB"
                        );
                    }
                }

                out.flush();
            }

            if (written < minimumBytes || temp.length() < minimumBytes) {
                throw new IOException("فایلی دابەزێنراو ناتەواوە: " + name);
            }

            if (!temp.renameTo(name)) {
                throw new IOException("نەتوانرا ناوی فایل بگۆڕدرێت: " + name);
            }

            success = true;

        } finally {
            if (connection != null) connection.disconnect();
            if (!success) temp.delete();
        }
    }

    private String errorText(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }

    private void updateStatus(String message) {
        handler.post(() -> status.setText(message));
    }

    private void requestMic() {
        if (recording) return;

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

        if (requestCode == REQUEST_MIC &&
                results.length > 0 &&
                results[0] == PackageManager.PERMISSION_GRANTED) {
            recordAudio();
        } else if (requestCode == REQUEST_MIC) {
            status.setText("بۆ تۆمارکردن پێویستە مۆڵەتی مایک بدەیت.");
        }
    }

    private void recordAudio() {
        if (recording) return;

        recording = true;
        recordButton.setEnabled(false);
        status.setText("تۆمارکردنی دەنگ دەستی پێکرد...");

        new Thread(() -> {
            AudioRecord recorder = null;

            try {
                int minBuffer = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);

                if (minBuffer <= 0) {
                    throw new IOException("ئامادەکردنی مایک سەرکەوتوو نەبوو.");
                }

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

                    if (n <= 0) {
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

                handler.post(() -> status.setText(
                        "تۆمارکردن تەواو بوو.\n" +
                        "فایلی recording.pcm دروست کرا.\n" +
                        "ئەمە هێشتا دەقی ناسراو نییە."
                ));

            } catch (Exception e) {
                String message = errorText(e);
                handler.post(() -> status.setText(
                        "هەڵەی تۆمارکردن: " + message
                ));

            } finally {
                if (recorder != null) {
                    try {
                        recorder.release();
                    } catch (Exception ignored) {
                    }
                }

                recording = false;
                handler.post(() -> recordButton.setEnabled(true));
            }
        }).start();
    }
}
