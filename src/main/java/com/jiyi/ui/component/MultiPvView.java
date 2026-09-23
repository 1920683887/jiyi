package com.jiyi.ui.component;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;

/**
 * MultiPV显示组件
 * 显示引擎的多条主变（PV线）
 */
public class MultiPvView extends VBox {

    public static class PvLine {
        private final SimpleIntegerProperty rank = new SimpleIntegerProperty();
        private final SimpleIntegerProperty depth = new SimpleIntegerProperty();
        private final SimpleIntegerProperty score = new SimpleIntegerProperty();
        private final SimpleStringProperty pvMoves = new SimpleStringProperty();

        public PvLine(int rank, int depth, int score, String pvMoves) {
            this.rank.set(rank);
            this.depth.set(depth);
            this.score.set(score);
            this.pvMoves.set(pvMoves);
        }

        public int getRank() { return rank.get(); }
        public int getDepth() { return depth.get(); }
        public int getScore() { return score.get(); }
        public String getPvMoves() { return pvMoves.get(); }

        public SimpleIntegerProperty rankProperty() { return rank; }
        public SimpleIntegerProperty depthProperty() { return depth; }
        public SimpleIntegerProperty scoreProperty() { return score; }
        public SimpleStringProperty pvMovesProperty() { return pvMoves; }
    }

    private final TableView<PvLine> pvTable;
    private final ObservableList<PvLine> pvLines;

    public MultiPvView() {
        pvLines = FXCollections.observableArrayList();
        pvTable = new TableView<>(pvLines);

        // 序号列
        TableColumn<PvLine, Integer> rankCol = new TableColumn<>("#");
        rankCol.setCellValueFactory(cellData -> cellData.getValue().rankProperty().asObject());
        rankCol.setPrefWidth(30);

        // 深度列
        TableColumn<PvLine, Integer> depthCol = new TableColumn<>("深度");
        depthCol.setCellValueFactory(cellData -> cellData.getValue().depthProperty().asObject());
        depthCol.setPrefWidth(50);

        // 评分列
        TableColumn<PvLine, Integer> scoreCol = new TableColumn<>("评分");
        scoreCol.setCellValueFactory(cellData -> cellData.getValue().scoreProperty().asObject());
        scoreCol.setPrefWidth(60);

        // 着法序列列
        TableColumn<PvLine, String> pvCol = new TableColumn<>("着法");
        pvCol.setCellValueFactory(cellData -> cellData.getValue().pvMovesProperty());
        pvCol.setPrefWidth(300);

        pvTable.getColumns().addAll(rankCol, depthCol, scoreCol, pvCol);
        pvTable.setPlaceholder(new Label("等待引擎分析..."));

        getChildren().add(pvTable);
    }

    /**
     * 更新PV线
     */
    public void updatePvLine(int rank, int depth, int score, String pvMoves) {
        // 查找是否已存在该序号的PV线
        for (PvLine line : pvLines) {
            if (line.getRank() == rank) {
                line.depth.set(depth);
                line.score.set(score);
                line.pvMoves.set(pvMoves);
                return;
            }
        }
        // 不存在则添加
        pvLines.add(new PvLine(rank, depth, score, pvMoves));
        pvLines.sort((a, b) -> Integer.compare(a.getRank(), b.getRank()));
    }

    /**
     * 清空所有PV线
     */
    public void clear() {
        pvLines.clear();
    }

    /**
     * 设置点击事件
     */
    public void setOnPvLineClicked(java.util.function.Consumer<PvLine> callback) {
        pvTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                PvLine selected = pvTable.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    callback.accept(selected);
                }
            }
        });
    }

    public TableView<PvLine> getTable() {
        return pvTable;
    }
}
