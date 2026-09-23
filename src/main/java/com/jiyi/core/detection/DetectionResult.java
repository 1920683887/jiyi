package com.jiyi.core.detection;

import java.awt.Rectangle;
import java.util.List;

public record DetectionResult(
    Rectangle boardRect,
    List<PieceBox> pieces,
    List<Rectangle> boardCandidates
) {
    public DetectionResult {
        // 防御性拷贝：避免外部修改内部可变数据（Rectangle 非不可变、List 可改）
        boardRect = boardRect != null ? new Rectangle(boardRect) : null;
        pieces = List.copyOf(pieces);
        boardCandidates = boardCandidates == null
            ? (boardRect != null ? List.of(new Rectangle(boardRect)) : List.of())
            : boardCandidates.stream().map(Rectangle::new).toList();
    }

    /** 便捷构造：单候选（boardRect 即唯一候选） */
    public DetectionResult(Rectangle boardRect, List<PieceBox> pieces) {
        this(boardRect, pieces,
            boardRect != null ? List.of(new Rectangle(boardRect)) : List.of());
    }

    public record PieceBox(char label, float confidence, Rectangle rect) {
        public PieceBox {
            rect = new Rectangle(rect);
        }
    }
}
