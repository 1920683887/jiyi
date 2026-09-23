package com.jiyi.ui.controller;

import com.google.inject.Inject;
import com.jiyi.infra.config.Config;
import com.jiyi.infra.config.ConfigManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

/**
 * 时间设置对话框控制器
 * Time Settings Dialog Controller
 *
 * 功能 Features:
 * 1. 引擎延迟设置 - Engine delay settings (start/end random range)
 * 2. 库招延迟设置 - Opening book delay settings (start/end range)
 * 3. 鼠标点击间隔 - Mouse click interval
 * 4. 分析模式选择 - Analysis mode selection (fixed time/fixed depth/infinite)
 * 5. 分析值输入 - Analysis value input
 */
public class TimeSettingController {

    @Inject private Config config;
    @Inject private ConfigManager configManager;

    // 引擎延迟设置 (Engine delay settings)
    @FXML private Spinner<Integer> engineDelayStartSpinner;
    @FXML private Spinner<Integer> engineDelayEndSpinner;
    @FXML private Label engineDelayRangeLabel;

    // 库招延迟设置 (Opening book delay settings)
    @FXML private Spinner<Integer> bookDelayStartSpinner;
    @FXML private Spinner<Integer> bookDelayEndSpinner;
    @FXML private Label bookDelayRangeLabel;

    // 鼠标点击间隔 (Mouse click interval)
    @FXML private Spinner<Integer> mouseClickIntervalSpinner;

    // 分析模式设置 (Analysis mode settings)
    @FXML private ComboBox<String> analysisModeComboBox;
    @FXML private TextField analysisValueField;
    @FXML private Label analysisValueUnitLabel;

    /**
     * 初始化控制器，加载配置到UI组件
     * Initialize controller and load config to UI components
     */
    @FXML
    public void initialize() {
        initializeEngineDelaySettings();
        initializeBookDelaySettings();
        initializeMouseClickInterval();
        initializeAnalysisModeSettings();
        setupListeners();
    }

    /**
     * 初始化引擎延迟设置
     * Initialize engine delay settings
     */
    private void initializeEngineDelaySettings() {
        var engineCfg = config.engine();

        // 引擎延迟起始时间 (0-10000ms, 步进100ms)
        engineDelayStartSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(
                0, 10000, engineCfg.delayStartMs(), 100));

        // 引擎延迟结束时间 (0-10000ms, 步进100ms)
        engineDelayEndSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(
                0, 10000, engineCfg.delayEndMs(), 100));

        // 设置为可编辑
        engineDelayStartSpinner.setEditable(true);
        engineDelayEndSpinner.setEditable(true);

        updateEngineDelayRangeLabel();
    }

    /**
     * 初始化库招延迟设置
     * Initialize opening book delay settings
     */
    private void initializeBookDelaySettings() {
        var bookCfg = config.book();

        // 库招延迟起始时间 (0-10000ms, 步进100ms)
        bookDelayStartSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(
                0, 10000, bookCfg.bookDelayStartMs(), 100));

        // 库招延迟结束时间 (0-10000ms, 步进100ms)
        bookDelayEndSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(
                0, 10000, bookCfg.bookDelayEndMs(), 100));

        // 设置为可编辑
        bookDelayStartSpinner.setEditable(true);
        bookDelayEndSpinner.setEditable(true);

        updateBookDelayRangeLabel();
    }

    /**
     * 初始化鼠标点击间隔
     * Initialize mouse click interval
     */
    private void initializeMouseClickInterval() {
        var linkCfg = config.link();

        // 鼠标点击间隔 (0-1000ms, 步进10ms)
        mouseClickIntervalSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(
                0, 1000, linkCfg.mouseClickDelayMs(), 10));

        mouseClickIntervalSpinner.setEditable(true);
    }

    /**
     * 初始化分析模式设置
     * Initialize analysis mode settings
     */
    private void initializeAnalysisModeSettings() {
        var engineCfg = config.engine();

        // 添加分析模式选项
        analysisModeComboBox.getItems().addAll("固定时间", "固定深度", "无限");

        // 根据当前配置选择对应的模式
        String currentModel = engineCfg.analysisModel();
        String displayMode = convertModelToDisplay(currentModel);
        analysisModeComboBox.setValue(displayMode);

        // 设置分析值
        analysisValueField.setText(String.valueOf(engineCfg.analysisValue()));

        // 根据当前模式更新单位标签
        updateAnalysisValueUnit(displayMode);
    }

    /**
     * 设置各种监听器
     * Setup listeners for UI components
     */
    private void setupListeners() {
        // 引擎延迟范围变化监听
        engineDelayStartSpinner.valueProperty().addListener(
            (obs, oldVal, newVal) -> updateEngineDelayRangeLabel());
        engineDelayEndSpinner.valueProperty().addListener(
            (obs, oldVal, newVal) -> updateEngineDelayRangeLabel());

        // 库招延迟范围变化监听
        bookDelayStartSpinner.valueProperty().addListener(
            (obs, oldVal, newVal) -> updateBookDelayRangeLabel());
        bookDelayEndSpinner.valueProperty().addListener(
            (obs, oldVal, newVal) -> updateBookDelayRangeLabel());

        // 分析模式变化监听
        analysisModeComboBox.valueProperty().addListener(
            (obs, oldVal, newVal) -> {
                updateAnalysisValueUnit(newVal);
                // 无限模式下禁用分析值输入
                boolean isInfinite = "无限".equals(newVal);
                analysisValueField.setDisable(isInfinite);
                if (isInfinite) {
                    analysisValueField.setText("0");
                }
            });
    }

    /**
     * 更新引擎延迟范围标签
     * Update engine delay range label
     */
    private void updateEngineDelayRangeLabel() {
        if (engineDelayRangeLabel != null) {
            int start = engineDelayStartSpinner.getValue();
            int end = engineDelayEndSpinner.getValue();

            if (start == end) {
                engineDelayRangeLabel.setText(String.format("固定延迟: %d 毫秒", start));
            } else if (start < end) {
                engineDelayRangeLabel.setText(String.format("随机范围: %d - %d 毫秒", start, end));
            } else {
                engineDelayRangeLabel.setText("警告: 起始时间应小于或等于结束时间");
                engineDelayRangeLabel.setStyle("-fx-text-fill: red;");
                return;
            }
            engineDelayRangeLabel.setStyle("-fx-text-fill: #666666;");
        }
    }

    /**
     * 更新库招延迟范围标签
     * Update book delay range label
     */
    private void updateBookDelayRangeLabel() {
        if (bookDelayRangeLabel != null) {
            int start = bookDelayStartSpinner.getValue();
            int end = bookDelayEndSpinner.getValue();

            if (start == end) {
                bookDelayRangeLabel.setText(String.format("固定延迟: %d 毫秒", start));
            } else if (start < end) {
                bookDelayRangeLabel.setText(String.format("随机范围: %d - %d 毫秒", start, end));
            } else {
                bookDelayRangeLabel.setText("警告: 起始时间应小于或等于结束时间");
                bookDelayRangeLabel.setStyle("-fx-text-fill: red;");
                return;
            }
            bookDelayRangeLabel.setStyle("-fx-text-fill: #666666;");
        }
    }

    /**
     * 更新分析值单位标签
     * Update analysis value unit label
     */
    private void updateAnalysisValueUnit(String mode) {
        if (analysisValueUnitLabel != null) {
            switch (mode) {
                case "固定时间":
                    analysisValueUnitLabel.setText("毫秒 (ms)");
                    break;
                case "固定深度":
                    analysisValueUnitLabel.setText("步数 (步)");
                    break;
                case "无限":
                    analysisValueUnitLabel.setText("(不限制)");
                    break;
                default:
                    analysisValueUnitLabel.setText("");
            }
        }
    }

    /**
     * 将配置中的模型代码转换为显示文本
     * Convert model code to display text
     */
    private String convertModelToDisplay(String model) {
        if (model == null) {
            return "固定时间";
        }
        switch (model) {
            case "FIXED_TIME":
                return "固定时间";
            case "FIXED_STEPS":
            case "FIXED_DEPTH":
                return "固定深度";
            case "INFINITE":
                return "无限";
            default:
                return "固定时间";
        }
    }

    /**
     * 将显示文本转换为配置中的模型代码
     * Convert display text to model code
     */
    private String convertDisplayToModel(String display) {
        if (display == null) {
            return "FIXED_TIME";
        }
        switch (display) {
            case "固定时间":
                return "FIXED_TIME";
            case "固定深度":
                return "FIXED_STEPS";
            case "无限":
                return "INFINITE";
            default:
                return "FIXED_TIME";
        }
    }

    /**
     * 确认按钮点击事件 - 保存设置到配置文件
     * Confirm button click event - Save settings to config file
     */
    @FXML
    public void onConfirm() {
        // 验证输入
        if (!validateInput()) {
            return;
        }

        try {
            // 保存引擎延迟设置
            config.engine().setDelayStartMs(engineDelayStartSpinner.getValue());
            config.engine().setDelayEndMs(engineDelayEndSpinner.getValue());

            // 保存库招延迟设置（BookConfig.bookDelayStartMs/bookDelayEndMs）
            config.book().setBookDelayStartMs(bookDelayStartSpinner.getValue());
            config.book().setBookDelayEndMs(bookDelayEndSpinner.getValue());

            // 保存鼠标点击间隔
            config.link().setMouseClickDelayMs(mouseClickIntervalSpinner.getValue());

            // 保存分析模式
            String selectedMode = analysisModeComboBox.getValue();
            config.engine().setAnalysisModel(convertDisplayToModel(selectedMode));

            // 保存分析值
            if (!"无限".equals(selectedMode)) {
                long analysisValue = Long.parseLong(analysisValueField.getText().trim());
                config.engine().setAnalysisValue(analysisValue);
            } else {
                config.engine().setAnalysisValue(0);
            }

            // 保存配置到文件
            configManager.save();

            // 关闭对话框
            closeDialog();

        } catch (NumberFormatException e) {
            showAlert("输入错误", "分析值必须是有效的数字", Alert.AlertType.ERROR);
        } catch (Exception e) {
            showAlert("保存失败", "保存配置时发生错误: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * 取消按钮点击事件
     * Cancel button click event
     */
    @FXML
    public void onCancel() {
        closeDialog();
    }

    /**
     * 重置按钮点击事件 - 恢复默认值
     * Reset button click event - Restore default values
     */
    @FXML
    public void onReset() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("确认重置");
        confirmAlert.setHeaderText("重置时间设置");
        confirmAlert.setContentText("确定要恢复所有时间设置为默认值吗?");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // 重置为默认值
                engineDelayStartSpinner.getValueFactory().setValue(0);
                engineDelayEndSpinner.getValueFactory().setValue(0);
                bookDelayStartSpinner.getValueFactory().setValue(0);
                bookDelayEndSpinner.getValueFactory().setValue(500);
                mouseClickIntervalSpinner.getValueFactory().setValue(2);
                analysisModeComboBox.setValue("固定时间");
                analysisValueField.setText("5000");
            }
        });
    }

    /**
     * 验证输入是否有效
     * Validate input values
     */
    private boolean validateInput() {
        // 验证引擎延迟范围
        int engineStart = engineDelayStartSpinner.getValue();
        int engineEnd = engineDelayEndSpinner.getValue();
        if (engineStart > engineEnd) {
            showAlert("输入错误", "引擎延迟起始时间不能大于结束时间", Alert.AlertType.WARNING);
            return false;
        }

        // 验证库招延迟范围
        int bookStart = bookDelayStartSpinner.getValue();
        int bookEnd = bookDelayEndSpinner.getValue();
        if (bookStart > bookEnd) {
            showAlert("输入错误", "库招延迟起始时间不能大于结束时间", Alert.AlertType.WARNING);
            return false;
        }

        // 验证鼠标点击间隔
        int mouseClickInterval = mouseClickIntervalSpinner.getValue();
        if (mouseClickInterval < 0) {
            showAlert("输入错误", "鼠标点击间隔不能为负数", Alert.AlertType.WARNING);
            return false;
        }

        // 验证分析值
        String selectedMode = analysisModeComboBox.getValue();
        if (!"无限".equals(selectedMode)) {
            String valueText = analysisValueField.getText().trim();
            if (valueText.isEmpty()) {
                showAlert("输入错误", "请输入分析值", Alert.AlertType.WARNING);
                return false;
            }

            try {
                long analysisValue = Long.parseLong(valueText);
                if (analysisValue <= 0) {
                    showAlert("输入错误", "分析值必须大于0", Alert.AlertType.WARNING);
                    return false;
                }

                // 固定时间模式下，检查时间是否合理
                if ("固定时间".equals(selectedMode) && analysisValue > 3600000) {
                    showAlert("输入警告", "分析时间超过1小时，是否确认?", Alert.AlertType.WARNING);
                    // 允许继续，但给出警告
                }

                // 固定深度模式下，检查深度是否合理
                if ("固定深度".equals(selectedMode) && analysisValue > 1000) {
                    showAlert("输入警告", "分析深度超过1000步，可能导致性能问题", Alert.AlertType.WARNING);
                    // 允许继续，但给出警告
                }

            } catch (NumberFormatException e) {
                showAlert("输入错误", "分析值必须是有效的整数", Alert.AlertType.ERROR);
                return false;
            }
        }

        return true;
    }

    /**
     * 关闭对话框
     * Close dialog
     */
    private void closeDialog() {
        Stage stage = (Stage) engineDelayStartSpinner.getScene().getWindow();
        stage.close();
    }

    /**
     * 显示提示对话框
     * Show alert dialog
     */
    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
