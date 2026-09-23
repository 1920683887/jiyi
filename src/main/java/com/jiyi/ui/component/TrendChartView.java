package com.jiyi.ui.component;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 评分趋势图组件
 * 显示对局过程中的局面评分变化曲线
 */
public class TrendChartView {

    private final LineChart<Number, Number> chart;
    private final XYChart.Series<Number, Number> redSeries;
    private final XYChart.Series<Number, Number> blackSeries;
    private final List<ScorePoint> scoreHistory = new ArrayList<>();

    public record ScorePoint(int moveNum, int score, boolean isRed) {}

    public TrendChartView() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("步数");
        xAxis.setAutoRanging(true);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("评分（厘）");
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(-1000);
        yAxis.setUpperBound(1000);

        chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("局面评分趋势");
        chart.setCreateSymbols(true);
        chart.setLegendVisible(true);

        redSeries = new XYChart.Series<>();
        redSeries.setName("红方视角");

        blackSeries = new XYChart.Series<>();
        blackSeries.setName("黑方视角");

        chart.getData().addAll(redSeries, blackSeries);

        // 应用样式
        chart.setStyle("-fx-background-color: #f5f5f5;");
    }

    /**
     * 添加评分数据点
     * @param moveNum 步数
     * @param score 评分（厘，红方视角）
     * @param isRed 是否红方走棋
     */
    public void addScore(int moveNum, int score, boolean isRed) {
        scoreHistory.add(new ScorePoint(moveNum, score, isRed));

        // 红方视角的分数
        var redData = new XYChart.Data<Number, Number>(moveNum, score);
        redSeries.getData().add(redData);

        // 黑方视角的分数（取反）
        var blackData = new XYChart.Data<Number, Number>(moveNum, -score);
        blackSeries.getData().add(blackData);

        // 添加Tooltip
        addTooltip(redData, moveNum, score, true);
        addTooltip(blackData, moveNum, -score, false);

        // 限制数据点数量，避免太多（series 与 scoreHistory 同步裁剪）
        if (redSeries.getData().size() > 200) {
            redSeries.getData().remove(0);
            blackSeries.getData().remove(0);
            if (!scoreHistory.isEmpty()) scoreHistory.remove(0);
        }

        // 动态调整Y轴范围
        adjustYAxis();
    }

    /**
     * 清空所有数据
     */
    public void clear() {
        redSeries.getData().clear();
        blackSeries.getData().clear();
        scoreHistory.clear();
    }

    /**
     * 重置到初始状态
     */
    public void reset() {
        clear();
        chart.getYAxis().setAutoRanging(false);
        ((NumberAxis) chart.getYAxis()).setLowerBound(-1000);
        ((NumberAxis) chart.getYAxis()).setUpperBound(1000);
    }

    /**
     * 获取图表组件
     */
    public LineChart<Number, Number> getChart() {
        return chart;
    }

    /**
     * 设置点击事件回调
     * @param callback 回调函数，参数为点击的步数
     */
    public void setOnPointClicked(java.util.function.Consumer<Integer> callback) {
        for (var series : chart.getData()) {
            for (var data : series.getData()) {
                data.getNode().setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2) {
                        callback.accept(data.getXValue().intValue());
                    }
                });
                data.getNode().setStyle("-fx-cursor: hand;");
            }
        }
    }

    private void addTooltip(XYChart.Data<Number, Number> data, int moveNum, int score, boolean isRed) {
        Tooltip tooltip = new Tooltip(
            String.format("第%d步\n%s评分: %+d厘",
                moveNum,
                isRed ? "红方" : "黑方",
                score)
        );
        // data.getNode() 在图表首次布局前为 null，延迟到 FX 布局后绑定
        javafx.application.Platform.runLater(() -> {
            if (data.getNode() != null) {
                Tooltip.install(data.getNode(), tooltip);
            }
        });
    }

    private void adjustYAxis() {
        if (scoreHistory.isEmpty()) return;

        // 同时考虑红方 score 与黑方 -score，保证双色数据点都在可视范围内
        int maxScore = scoreHistory.stream()
            .mapToInt(p -> Math.max(p.score(), -p.score()))
            .max()
            .orElse(1000);

        int minScore = -maxScore;

        // 添加10%的边距
        int margin = (int) ((maxScore - minScore) * 0.1);
        NumberAxis yAxis = (NumberAxis) chart.getYAxis();
        yAxis.setLowerBound(Math.min(minScore - margin, -100));
        yAxis.setUpperBound(Math.max(maxScore + margin, 100));
    }

    /**
     * 获取最新评分
     */
    public int getLatestScore() {
        if (scoreHistory.isEmpty()) return 0;
        return scoreHistory.get(scoreHistory.size() - 1).score();
    }

    /**
     * 获取历史评分数据
     */
    public List<ScorePoint> getScoreHistory() {
        return new ArrayList<>(scoreHistory);
    }
}
