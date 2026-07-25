package com.jiyi.core.detection;

import java.awt.Rectangle;
import java.util.List;

public record DetectionResult(
    Rectangle boardRect,
    List<PieceBox> pieces
) {
    public record PieceBox(char label, float confidence, Rectangle rect) {}
}
