package com.jiyi.core.detection;

import java.awt.Point;
import java.awt.image.BufferedImage;

/**
 * 模板匹配：在截图中查找模板图（对齐 VinXiangQi ImageHelper.FindImageFromTop 的做法）。
 * 暴力像素扫描 + 绝对 RGB 色差阈值判定，支持 alpha 遮罩（模板透明像素不参与比较）。
 * 对"找固定按钮图"这类需求足够稳，且无需 OpenCV 依赖。
 */
public final class TemplateMatcher {

    private TemplateMatcher() {}

    /** 默认色差阈值（VinXiangQi 用 50） */
    public static final int DEFAULT_THRESHOLD = 50;

    /**
     * 在 sample 中自上而下查找 model，返回左上角坐标；未找到返回 null。
     * 匹配判定：模板所有非透明像素与截图对应像素的 |ΔR|+|ΔG|+|ΔB| ≤ threshold。
     */
    public static Point findFromTop(BufferedImage sample, BufferedImage model) {
        return findFromTop(sample, model, DEFAULT_THRESHOLD);
    }

    public static Point findFromTop(BufferedImage sample, BufferedImage model, int threshold) {
        if (sample == null || model == null) return null;
        int sW = sample.getWidth(), sH = sample.getHeight();
        int mW = model.getWidth(), mH = model.getHeight();
        if (mW <= 0 || mH <= 0 || mW > sW || mH > sH) return null;

        int[] sPix = sample.getRGB(0, 0, sW, sH, null, 0, sW);
        int[] mPix = model.getRGB(0, 0, mW, mH, null, 0, mW);
        int lastX = sW - mW, lastY = sH - mH;

        for (int y = 0; y <= lastY; y++) {
            for (int x = 0; x <= lastX; x++) {
                if (matches(sPix, sW, mPix, mW, x, y, threshold)) {
                    return new Point(x, y);
                }
            }
        }
        return null;
    }

    /** 模板左上角置于 (ox,oy) 时逐像素比较；第一个不匹配像素即整体不匹配（提前跳出，保证扫描速度） */
    private static boolean matches(int[] s, int sW, int[] m, int mW, int ox, int oy, int threshold) {
        for (int y = 0; y < m.length / mW; y++) {
            for (int x = 0; x < mW; x++) {
                int mp = m[y * mW + x];
                if (((mp >>> 24) & 0xFF) == 0) continue;  // 模板透明像素跳过
                int sp = s[(oy + y) * sW + (ox + x)];
                int diff = Math.abs(((sp >> 16) & 0xFF) - ((mp >> 16) & 0xFF))
                    + Math.abs(((sp >> 8) & 0xFF) - ((mp >> 8) & 0xFF))
                    + Math.abs((sp & 0xFF) - (mp & 0xFF));
                if (diff > threshold) return false;
            }
        }
        return true;
    }
}
