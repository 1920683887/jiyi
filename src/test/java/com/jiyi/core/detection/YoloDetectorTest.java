package com.jiyi.core.detection;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * YOLO 推理集成测试：验证 ONNX 输出转型（float[][][] → float[][]）修复。
 * 依赖真实模型文件 ./models/yolov11.onnx，仅在本地运行。
 */
class YoloDetectorTest {

    @Test
    void testDetect_realModel_noCastException() {
        // 用真实模型跑一张随机图：不要求检测到棋盘，但必须不抛 ClassCastException
        var detector = new YoloDetector("./models/yolov11.onnx");
        var img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        var rand = new Random(42);
        for (int y = 0; y < 600; y += 4) {
            for (int x = 0; x < 800; x += 4) {
                int rgb = (rand.nextInt(256) << 16) | (rand.nextInt(256) << 8) | rand.nextInt(256);
                img.setRGB(x, y, rgb);
            }
        }
        DetectionResult result = assertDoesNotThrow(() -> detector.detect(img));
        assertNotNull(result);
        detector.close();
    }
}
