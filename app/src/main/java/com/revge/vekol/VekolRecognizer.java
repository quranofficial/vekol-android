
package com.revge.vekol;

import android.content.Context;
import android.util.Log;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import androidx.documentfile.provider.DocumentFile;

public class VekolRecognizer implements AutoCloseable {

    private static final String TAG = "VekolRecognizer";

    private final OrtEnvironment environment;
    private OrtSession encoder;
    private OrtSession decoder;
    private JSONObject vocab;

    public VekolRecognizer() {
        environment = OrtEnvironment.getEnvironment();
    }

    /**
     * پشکنینی فایلەکانی مۆدێل و بارکردنی ONNX.
     * modelFolder دەبێت بوخچەیەک بێت کە فایلەکان تێدایە.
     */
    public void load(File modelFolder) throws Exception {
        if (modelFolder == null || !modelFolder.isDirectory()) {
            throw new Exception("بوخچەی مۆدێل نەدۆزرایەوە.");
        }

        File encoderFile = new File(
                modelFolder, "encoder_model.onnx");

        File decoderFile = new File(
                modelFolder, "decoder_model_merged.onnx");

        File vocabFile = new File(
                modelFolder, "vocab.json");

        File melFile = new File(
                modelFolder, "mel_filters.npy");

        requireFile(encoderFile);
        requireFile(decoderFile);
        requireFile(vocabFile);
        requireFile(melFile);

        OrtSession newEncoder = null;
        OrtSession newDecoder = null;

        try {
            newEncoder = environment.createSession(
                    encoderFile.getAbsolutePath(),
                    new OrtSession.SessionOptions());

            newDecoder = environment.createSession(
                    decoderFile.getAbsolutePath(),
                    new OrtSession.SessionOptions());

            JSONObject newVocab = readJson(vocabFile);

            encoder = newEncoder;
            decoder = newDecoder;
            vocab = newVocab;

            Log.i(TAG, "Encoder inputs: " +
                    encoder.getInputNames());

            Log.i(TAG, "Decoder inputs: " +
                    decoder.getInputNames());

            Log.i(TAG, "Vocab tokens: " + vocab.length());

        } catch (Exception e) {
            if (newEncoder != null) {
                try {
                    newEncoder.close();
                } catch (Exception ignored) {}
            }

            if (newDecoder != null) {
                try {
                    newDecoder.close();
                } catch (Exception ignored) {}
            }

            throw e;
        }
    }

    private void requireFile(File file) throws Exception {
        if (!file.exists() ||
                !file.isFile() ||
                file.length() == 0) {
            throw new Exception(
                    "فایل نییە یان بەتاڵە: " + file.getName());
        }
    }

    private JSONObject readJson(File file) throws Exception {
        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[8192];
            int count;

            while ((count = in.read(bytes)) != -1) {
                buffer.write(bytes, 0, count);
            }
        }

        return new JSONObject(
                new String(
                        buffer.toByteArray(),
                        StandardCharsets.UTF_8));
    }

    public boolean isLoaded() {
        return encoder != null &&
                decoder != null &&
                vocab != null;
    }

    public String getModelInfo() {
        if (!isLoaded()) {
            return "مۆدێل هێشتا بار نەکراوە.";
        }

        return "مۆدێلی Vekol بار کراوە.\n" +
                "Encoder: ئامادە\n" +
                "Decoder: ئامادە\n" +
                "Vocab: " + vocab.length() + " token";
    }

    @Override
    public void close() {
        if (encoder != null) {
            try {
                encoder.close();
            } catch (Exception ignored) {}

            encoder = null;
        }

        if (decoder != null) {
            try {
                decoder.close();
            } catch (Exception ignored) {}

            decoder = null;
        }

        vocab = null;
    }
}
