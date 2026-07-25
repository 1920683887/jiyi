package com.jiyi.core.detection;

import ai.onnxruntime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.*;

public class YoloDetector implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(YoloDetector.class);
    private static final int SIZE = 640;
    private static final float CONF_THRESH = 0.5f;
    private static final float NMS_THRESH = 0.45f;
    private static final float BOARD_PADDING = 0.8f;

    static final String[] LABELS = {
        "n","b","a","k","r","c","p",
        "R","N","A","K","B","C","P","board"
    };

    private final OrtSession session;
    private final OrtEnvironment env;
    private final int numClasses;
    private final int stride;

    public YoloDetector(String modelPath) {
        try {
            this.env = OrtEnvironment.getEnvironment();
            var opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(2);
            this.session = env.createSession(modelPath, opts);

            var outputInfo = session.getOutputInfo().values().iterator().next();
            long[] shape = outputInfo.getInfo() instanceof ai.onnxruntime.TensorInfo ti
                ? ti.getShape() : new long[]{1, -1, -1};
            // shape is [1, total, stride] where stride = 4 + 1 + numClasses
            this.stride = (int) shape[2];
            this.numClasses = stride - 5;
            log.info("YOLO loaded: {} classes, stride={}, shape={}", numClasses, stride, shape);
        } catch (OrtException e) {
            throw new RuntimeException("Failed to load YOLO: " + modelPath, e);
        }
    }

    public DetectionResult detect(BufferedImage img) throws OrtException {
        int iw = img.getWidth(), ih = img.getHeight();
        float scale = Math.min((float) SIZE / iw, (float) SIZE / ih);
        int sw = Math.round(iw * scale), sh = Math.round(ih * scale);
        int dw = (SIZE - sw) / 2, dh = (SIZE - sh) / 2;

        float[] data = new float[3 * SIZE * SIZE];
        var scaled = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
        var g = scaled.createGraphics();
        g.drawImage(img, 0, 0, sw, sh, null);
        g.dispose();

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int sx = x - dw, sy = y - dh;
                int rgb;
                if (sx >= 0 && sx < sw && sy >= 0 && sy < sh) {
                    rgb = scaled.getRGB(sx, sy);
                } else {
                    rgb = 0x727272;
                }
                data[y * SIZE + x] = ((rgb >> 16) & 0xFF) / 255.0f;
                data[SIZE * SIZE + y * SIZE + x] = ((rgb >> 8) & 0xFF) / 255.0f;
                data[2 * SIZE * SIZE + y * SIZE + x] = (rgb & 0xFF) / 255.0f;
            }
        }

        try (var tensor = OnnxTensor.createTensor(env,
                FloatBuffer.wrap(data), new long[]{1, 3, SIZE, SIZE})) {
            var results = session.run(Map.of("images", tensor));
            float[][] output = (float[][]) results.get(0).getValue();
            return parseOutput(output, iw, ih, scale, dw, dh);
        }
    }

    private DetectionResult parseOutput(float[][] output, int iw, int ih,
                                         float scale, int dw, int dh) {
        int total = output[0].length / stride;
        List<DetectionResult.PieceBox> boxes = new ArrayList<>();
        Rectangle boardRect = null;
        float boardConf = 0;

        for (int i = 0; i < total; i++) {
            int base = i * stride;
            float cx = (output[0][base] - dw) / scale;
            float cy = (output[0][base + 1] - dh) / scale;
            float bw = output[0][base + 2] / scale;
            float bh = output[0][base + 3] / scale;
            float obj = sigmoid(output[0][base + 4]);

            int cls = 0;
            float maxCls = 0;
            for (int j = 0; j < numClasses; j++) {
                float p = sigmoid(output[0][base + 5 + j]) * obj;
                if (p > maxCls) { maxCls = p; cls = j; }
            }
            if (maxCls < CONF_THRESH) continue;

            int x = Math.max(0, Math.round(cx - bw / 2));
            int y = Math.max(0, Math.round(cy - bh / 2));
            int rw = Math.min(iw - x, Math.round(bw));
            int rh = Math.min(ih - y, Math.round(bh));

            if (cls == numClasses - 1) {
                if (boardRect == null || maxCls > boardConf) {
                    boardRect = new Rectangle(x, y, rw, rh);
                    boardConf = maxCls;
                }
            } else {
                boxes.add(new DetectionResult.PieceBox(
                    LABELS[cls].charAt(0), maxCls, new Rectangle(x, y, rw, rh)));
            }
        }

        // NMS
        boxes.sort((a, b) -> Float.compare(b.confidence(), a.confidence()));
        List<DetectionResult.PieceBox> kept = new ArrayList<>();
        while (!boxes.isEmpty()) {
            var first = boxes.removeFirst();
            kept.add(first);
            boxes.removeIf(b -> iou(first.rect(), b.rect()) > NMS_THRESH);
        }

        // Expand board rect with padding (TCHESS-compatible: per-axis)
        if (boardRect != null) {
            int padX = (int)(boardRect.width / 8.0 * BOARD_PADDING);
            int padY = (int)(boardRect.height / 9.0 * BOARD_PADDING);
            boardRect.x = Math.max(0, boardRect.x - padX);
            boardRect.y = Math.max(0, boardRect.y - padY);
            boardRect.width = Math.min(iw, boardRect.width + padX * 2) - boardRect.x;
            boardRect.height = Math.min(ih, boardRect.height + padY * 2) - boardRect.y;
        }

        return new DetectionResult(boardRect, kept);
    }

    private float sigmoid(float x) { return (float) (1.0 / (1.0 + Math.exp(-x))); }
    private float iou(Rectangle a, Rectangle b) {
        int x1 = Math.max(a.x, b.x), y1 = Math.max(a.y, b.y);
        int x2 = Math.min(a.x + a.width, b.x + b.width);
        int y2 = Math.min(a.y + a.height, b.y + b.height);
        float inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        float u = a.width * a.height + b.width * b.height - inter;
        return u > 0 ? inter / u : 0;
    }

    @Override
    public void close() {
        try { session.close(); } catch (OrtException e) { log.warn("close", e); }
    }
}
