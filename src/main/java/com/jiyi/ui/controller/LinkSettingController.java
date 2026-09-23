package com.jiyi.ui.controller;

import com.google.inject.Inject;
import com.jiyi.core.engine.EngineConfig;
import com.jiyi.infra.config.Config;
import com.jiyi.infra.config.ConfigManager;
import com.jiyi.service.DetectionService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

/**
 * 连线设置对话框控制器
 * Link Settings Dialog Controller
 *
 * 功能 Features:
 * 1. 扫描间隔设置 - Scan interval settings (ms)
 * 2. 后台模式开关 - Background mode toggle
 * 3. 动画确认开关 - Animation confirmation toggle
 * 4. 鼠标移动延迟 - Mouse move delay (ms)
 * 5. 鼠标点击延迟 - Mouse click delay (ms)
 * 6. 显示信息开关 - Show info toggle
 * 7. 检测模型选择/重载 - Detection model selection & reload
 */
public class LinkSettingController {

    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(LinkSettingController.class);

    @Inject private Config config;
    @Inject private ConfigManager configManager;
    @Inject private DetectionService detectionService;
    @Inject private com.jiyi.service.AutomationService automationService;
    @Inject private com.jiyi.infra.platform.Platform platform;

    // 扫描间隔设置 (Scan interval settings)
    @FXML private Spinner<Integer> scanIntervalSpinner;
    @FXML private Label scanIntervalDescLabel;

    // 截图方式 (Capture method)
    @FXML private ComboBox<String> captureMethodCombo;

    // 后台模式开关 (Background mode toggle)
    @FXML private CheckBox backModeCheckBox;
    @FXML private Label backModeDescLabel;

    // 动画确认开关 (Animation confirmation toggle)
    @FXML private CheckBox animationCheckBox;
    @FXML private Label animationDescLabel;

    // 鼠标移动延迟 (Mouse move delay)
    @FXML private Spinner<Integer> mouseMoveDelaySpinner;
    @FXML private Label mouseMoveDelayDescLabel;

    // 鼠标点击延迟 (Mouse click delay)
    @FXML private Spinner<Integer> mouseClickDelaySpinner;
    @FXML private Label mouseClickDelayDescLabel;

    // 显示信息开关 (Show info toggle)
    @FXML private CheckBox showInfoCheckBox;
    @FXML private Label showInfoDescLabel;

    // 调试截图开关 (Debug screenshot toggle)
    @FXML private CheckBox saveScreenshotCheckBox;

    // 自动续盘（VinXiangQi 方式）
    @FXML private CheckBox autoClickCheckBox;
    @FXML private CheckBox stopWhenMateCheckBox;
    @FXML private Button calibrateButton;
    @FXML private Label autoclickStatusLabel;

    // 检测模型 (Detection model)
    @FXML private TextField modelPathField;

    // 引擎配置 (Engine config for link mode)
    @FXML private ComboBox<String> engineCombo;
    @FXML private ComboBox<String> engineColorCombo;
    @FXML private ComboBox<Integer> threadCombo;
    @FXML private ComboBox<Integer> hashCombo;
    @FXML private ComboBox<String> analysisModelCombo;
    @FXML private TextField analysisValueField;

    // 状态标签 (Status label)
    @FXML private Label statusLabel;

    /**
     * 初始化控制器，加载配置到UI组件
     * Initialize controller and load config to UI components
     */
    @FXML
    public void initialize() {
        initializeScanInterval();
        initializeCaptureMethod();
        initializeBackMode();
        initializeAnimation();
        initializeMouseMoveDelay();
        initializeMouseClickDelay();
        initializeShowInfo();
        initializeSaveScreenshot();
        initializeAutoClick();
        initializeModelPath();
        initializeEngineConfig();
        setupListeners();
        updateStatusLabel();
    }

    /**
     * 初始化调试截图开关
     */
    private void initializeSaveScreenshot() {
        saveScreenshotCheckBox.setSelected(config.link().saveScreenshot());
    }

    /**
     * 初始化自动续盘设置（VinXiangQi 方式）
     */
    private void initializeAutoClick() {
        autoClickCheckBox.setSelected(config.link().autoClick());
        stopWhenMateCheckBox.setSelected(config.link().stopWhenMate());
        refreshAutoclickStatus();
    }

    /** 刷新续盘按钮模板状态标签（显示 autoclick 目录下已标定模板数量） */
    private void refreshAutoclickStatus() {
        if (autoclickStatusLabel == null) return;
        var dir = new java.io.File(config.link().autoclickDir());
        File[] files = dir.isDirectory()
            ? dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"))
            : null;
        int count = files == null ? 0 : files.length;
        autoclickStatusLabel.setText(count > 0
            ? "已标定 " + count + " 个模板（" + config.link().autoclickDir() + "）"
            : "未标定（先点左侧按钮框选\"再来一局\"按钮）");
    }

    /** 保存自动续盘设置并即时生效（对齐 VinXiangQi Settings.AutoClick 即改即生效） */
    private void saveAutoClick() {
        config.link().setAutoClick(autoClickCheckBox.isSelected());
        config.link().setStopWhenMate(stopWhenMateCheckBox.isSelected());
        automationService.setAutoClick(config.link().autoClick());
        automationService.setStopWhenMate(config.link().stopWhenMate());
        log.info("Auto click saved: on={}, stopWhenMate={}", config.link().autoClick(),
            config.link().stopWhenMate());
    }

    /**
     * 标定续盘按钮：截图 → 对话框框选按钮 → 保存模板（对齐 VinXiangQi 自动点击续盘管理）。
     * ★ 优先截目标窗口客户区（与 AutoClickLoop 匹配截图同坐标系，避免全屏含窗口阴影/边缘导致匹配失败）；
     *   未选窗口时降级全屏截图。
     */
    @FXML
    public void onCalibrate() {
        try {
            java.awt.image.BufferedImage shot = captureCalibrateShot();
            if (shot == null) {
                statusLabel.setText("标定失败: 无法截图（请先选择连线窗口）");
                return;
            }
            var saveDir = new java.io.File(config.link().autoclickDir());
            if (!saveDir.exists()) saveDir.mkdirs();
            var owner = calibrateButton.getScene() != null
                ? calibrateButton.getScene().getWindow() : null;
            com.jiyi.ui.AutoClickCalibrateDialog.show(owner, shot, saveDir);
            refreshAutoclickStatus();
        } catch (Exception e) {
            log.error("Calibrate failed", e);
            statusLabel.setText("标定失败: " + e.getMessage());
        }
    }

    /** 标定截图：连线窗口客户区优先，无窗口时全屏兜底（BitBlt 物理 1:1） */
    private java.awt.image.BufferedImage captureCalibrateShot() {
        try {
            if (platform instanceof com.jiyi.infra.platform.WindowsPlatform wp) {
                long hwnd = wp.getCurrentHwnd();
                if (hwnd != 0) {
                    var rect = wp.getClientRectScreen(hwnd);
                    if (rect != null && rect.width > 0 && rect.height > 0) {
                        return wp.captureScreen(rect);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Client-area calibrate capture failed, falling back to fullscreen", e);
        }
        try {
            return platform.captureScreen(null);
        } catch (Exception e) {
            log.error("Fullscreen capture failed", e);
            return null;
        }
    }

    /**
     * 初始化截图方式
     * Initialize capture method
     */
    private void initializeCaptureMethod() {
        captureMethodCombo.getItems().setAll("自动（PrintWindow→Robot）", "PrintWindow（后台）", "Robot（前台截图）");
        String cur = config.link().captureMethod();
        captureMethodCombo.setValue(switch (cur) {
            case "BITBLT" -> "Robot（前台截图）";  // 旧值兼容：BITBLT 已移除，等价 Robot
            case "ROBOT" -> "Robot（前台截图）";
            case "PRINT_WINDOW" -> "PrintWindow（后台）";
            default -> "自动（PrintWindow→Robot）";
        });
        captureMethodCombo.valueProperty().addListener((obs, old, val) -> {
            if (val != null) {
                log.info("Capture method selected: {}", val);
            }
        });
    }

    /**
     * 初始化引擎配置（连线模式走棋引擎）
     * Initialize engine configuration for link mode
     */
    private void initializeEngineConfig() {
        var engine = config.engine();

        engineCombo.getItems().setAll(
            engine.list().stream().map(EngineConfig::name).toList());
        var defaultName = engine.defaultEngine();
        if ((defaultName == null || defaultName.isEmpty()) && !engine.list().isEmpty()) {
            defaultName = engine.list().get(0).name();
        }
        if (defaultName != null) engineCombo.setValue(defaultName);

        // 引擎执色（AUTO=按外部行棋方自动判定）
        engineColorCombo.getItems().setAll("自动（按行棋方判定）", "引擎执红", "引擎执黑");
        engineColorCombo.setValue(switch (config.link().engineColor()) {
            case "RED" -> "引擎执红";
            case "BLACK" -> "引擎执黑";
            default -> "自动（按行棋方判定）";
        });

        threadCombo.getItems().setAll(1, 2, 4, 8, 16);
        hashCombo.getItems().setAll(16, 32, 64, 128, 256, 512, 1024, 2048, 4096);
        analysisModelCombo.getItems().setAll("固定时间", "固定深度", "无限");

        var ec = engine.getDefaultEngineConfig().orElse(null);
        if (ec != null) {
            threadCombo.setValue(ec.threads());
            hashCombo.setValue(ec.hash());
            analysisModelCombo.setValue(switch (ec.analysisModel()) {
                case "FIXED_STEPS" -> "固定深度";
                case "INFINITE" -> "无限";
                default -> "固定时间";
            });
            analysisValueField.setText(String.valueOf(ec.analysisValue()));
        } else {
            threadCombo.setValue(4);
            hashCombo.setValue(256);
            analysisModelCombo.setValue("固定时间");
            analysisValueField.setText("5000");
        }
    }

    /**
     * 保存引擎配置到 Config（连线时 onLinkStart 自动启动该引擎）
     * Save engine configuration to Config
     */
    private void saveEngineConfig() {
        String name = engineCombo.getValue();
        if (name == null) return;
        var ec = config.engine().findByName(name).orElse(null);
        if (ec == null) return;
        if (threadCombo.getValue() != null) ec.setThreads(threadCombo.getValue());
        if (hashCombo.getValue() != null) ec.setHash(hashCombo.getValue());
        String model = analysisModelCombo.getValue();
        if (model != null) {
            ec.setAnalysisModel(switch (model) {
                case "固定深度" -> "FIXED_STEPS";
                case "无限" -> "INFINITE";
                default -> "FIXED_TIME";
            });
        }
        try {
            ec.setAnalysisValue(Long.parseLong(analysisValueField.getText()));
        } catch (NumberFormatException ignored) {}
        config.engine().setDefaultEngine(name);

        // 引擎执色
        String color = engineColorCombo.getValue() == null ? "" : engineColorCombo.getValue();
        config.link().setEngineColor(switch (color) {
            case "引擎执红" -> "RED";
            case "引擎执黑" -> "BLACK";
            default -> "AUTO";
        });
        log.info("Engine config saved: {} threads={} hash={} model={} value={} color={}",
            name, ec.threads(), ec.hash(), ec.analysisModel(), ec.analysisValue(), config.link().engineColor());
    }

    /**
     * 初始化扫描间隔设置
     * Initialize scan interval settings
     */
    private void initializeScanInterval() {
        var linkCfg = config.link();

        // 扫描间隔 (10-1000ms, 步进10ms)
        scanIntervalSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(
                10, 1000, (int) linkCfg.scanIntervalMs(), 10));

        // 设置为可编辑
        scanIntervalSpinner.setEditable(true);

        // 设置描述标签
        if (scanIntervalDescLabel != null) {
            scanIntervalDescLabel.setText("棋盘扫描频率，数值越小响应越快但CPU占用越高");
        }
    }

    /**
     * 初始化后台模式开关
     * Initialize background mode toggle
     */
    private void initializeBackMode() {
        var linkCfg = config.link();

        // 设置当前值
        backModeCheckBox.setSelected(linkCfg.backMode());

        // 设置描述标签
        if (backModeDescLabel != null) {
            backModeDescLabel.setText("启用后程序可以在后台运行，不需要保持在最前端");
        }
    }

    /**
     * 初始化动画确认开关
     * Initialize animation confirmation toggle
     */
    private void initializeAnimation() {
        var linkCfg = config.link();

        // 设置当前值
        animationCheckBox.setSelected(linkCfg.animation());

        // 设置描述标签
        if (animationDescLabel != null) {
            animationDescLabel.setText("启用后会等待走棋动画完成再进行下一步操作");
        }
    }

    /**
     * 初始化鼠标移动延迟
     * Initialize mouse move delay
     */
    private void initializeMouseMoveDelay() {
        var linkCfg = config.link();

        // 鼠标移动延迟 (0-1000ms, 步进10ms)
        mouseMoveDelaySpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(
                0, 1000, linkCfg.mouseMoveDelayMs(), 10));

        // 设置为可编辑
        mouseMoveDelaySpinner.setEditable(true);

        // 设置描述标签
        if (mouseMoveDelayDescLabel != null) {
            mouseMoveDelayDescLabel.setText("鼠标移动到目标位置后的等待时间");
        }
    }

    /**
     * 初始化鼠标点击延迟
     * Initialize mouse click delay
     */
    private void initializeMouseClickDelay() {
        var linkCfg = config.link();

        // 鼠标点击延迟 (0-1000ms, 步进10ms)
        mouseClickDelaySpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(
                0, 1000, linkCfg.mouseClickDelayMs(), 10));

        // 设置为可编辑
        mouseClickDelaySpinner.setEditable(true);

        // 设置描述标签
        if (mouseClickDelayDescLabel != null) {
            mouseClickDelayDescLabel.setText("鼠标点击前的等待时间，防止点击过快");
        }
    }

    /**
     * 初始化显示信息开关
     * Initialize show info toggle
     */
    private void initializeShowInfo() {
        var linkCfg = config.link();

        // 设置当前值
        showInfoCheckBox.setSelected(linkCfg.showInfo());

        // 设置描述标签
        if (showInfoDescLabel != null) {
            showInfoDescLabel.setText("启用后在界面上显示连线状态和调试信息");
        }
    }

    /**
     * 初始化检测模型路径
     * Initialize detection model path
     */
    private void initializeModelPath() {
        if (modelPathField != null) {
            modelPathField.setText(config.link().modelPath());
        }
    }

    /**
     * 浏览选择模型文件
     * Browse for a model file
     */
    @FXML
    public void onBrowseModel() {
        var chooser = new FileChooser();
        chooser.setTitle("选择 YOLO 模型文件");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("ONNX 模型", "*.onnx"));
        File f = chooser.showOpenDialog(modelPathField.getScene().getWindow());
        if (f != null) {
            modelPathField.setText(f.getAbsolutePath());
            updateStatusLabel();
        }
    }

    /**
     * 重新加载检测模型
     * Reload the detection model
     */
    @FXML
    public void onReloadModel() {
        String path = modelPathField.getText() == null ? "" : modelPathField.getText().trim();
        if (path.isEmpty()) {
            showAlert("提示", "请先选择模型文件", Alert.AlertType.WARNING);
            return;
        }
        if (detectionService.reloadModel(path)) {
            config.link().setModelPath(path);
            configManager.save();
            if (statusLabel != null) {
                statusLabel.setText("模型加载成功");
                statusLabel.setStyle("-fx-text-fill: green;");
            }
        } else {
            if (statusLabel != null) {
                statusLabel.setText("模型加载失败: " + path);
                statusLabel.setStyle("-fx-text-fill: red;");
            }
        }
    }

    /**
     * 保存截图方式到 Config
     * Save capture method to Config
     */
    private void saveCaptureMethod() {
        String sel = captureMethodCombo.getValue();
        String method = switch (sel == null ? "" : sel) {
            case "Robot（前台截图）" -> "ROBOT";
            case "PrintWindow（后台）" -> "PRINT_WINDOW";
            default -> "AUTO";
        };
        config.link().setCaptureMethod(method);
        log.info("Capture method saved: {}", method);
    }

    /**
     * 设置各种监听器
     * Setup listeners for UI components
     */
    private void setupListeners() {
        // 扫描间隔变化监听
        scanIntervalSpinner.valueProperty().addListener(
            (obs, oldVal, newVal) -> {
                updateStatusLabel();
                validateScanInterval(newVal);
            });

        // 后台模式变化监听
        backModeCheckBox.selectedProperty().addListener(
            (obs, oldVal, newVal) -> updateStatusLabel());

        // 动画确认变化监听
        animationCheckBox.selectedProperty().addListener(
            (obs, oldVal, newVal) -> updateStatusLabel());

        // 鼠标移动延迟变化监听
        mouseMoveDelaySpinner.valueProperty().addListener(
            (obs, oldVal, newVal) -> updateStatusLabel());

        // 鼠标点击延迟变化监听
        mouseClickDelaySpinner.valueProperty().addListener(
            (obs, oldVal, newVal) -> updateStatusLabel());

        // 显示信息变化监听
        showInfoCheckBox.selectedProperty().addListener(
            (obs, oldVal, newVal) -> updateStatusLabel());
    }

    /**
     * 验证扫描间隔
     * Validate scan interval
     */
    private void validateScanInterval(int value) {
        if (value < 50) {
            if (statusLabel != null) {
                statusLabel.setText("警告: 扫描间隔过小可能导致CPU占用过高");
                statusLabel.setStyle("-fx-text-fill: orange;");
            }
        } else if (value > 500) {
            if (statusLabel != null) {
                statusLabel.setText("提示: 扫描间隔较大可能降低响应速度");
                statusLabel.setStyle("-fx-text-fill: blue;");
            }
        }
    }

    /**
     * 更新状态标签
     * Update status label
     */
    private void updateStatusLabel() {
        if (statusLabel != null) {
            int scanInterval = scanIntervalSpinner.getValue();

            // 检查是否有警告信息
            if (scanInterval < 50) {
                statusLabel.setText("警告: 扫描间隔过小可能导致CPU占用过高");
                statusLabel.setStyle("-fx-text-fill: orange;");
            } else if (scanInterval > 500) {
                statusLabel.setText("提示: 扫描间隔较大可能降低响应速度");
                statusLabel.setStyle("-fx-text-fill: blue;");
            } else {
                statusLabel.setText("准备就绪");
                statusLabel.setStyle("-fx-text-fill: #666666;");
            }
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
            var linkCfg = config.link();

            // 保存扫描间隔
            linkCfg.setScanIntervalMs(scanIntervalSpinner.getValue());

            // 保存截图方式
            saveCaptureMethod();

            // 保存后台模式
            linkCfg.setBackMode(backModeCheckBox.isSelected());

            // 保存动画确认
            linkCfg.setAnimation(animationCheckBox.isSelected());

            // 保存鼠标移动延迟
            linkCfg.setMouseMoveDelayMs(mouseMoveDelaySpinner.getValue());

            // 保存鼠标点击延迟
            linkCfg.setMouseClickDelayMs(mouseClickDelaySpinner.getValue());

            // 保存显示信息
            linkCfg.setShowInfo(showInfoCheckBox.isSelected());

            // 保存调试截图开关
            linkCfg.setSaveScreenshot(saveScreenshotCheckBox.isSelected());

            // 保存自动续盘设置（即时生效）
            saveAutoClick();

            // ★ 保存模型路径（此前 onConfirm/onApply 均未写回，手输/重置的路径不落盘）
            if (modelPathField.getText() != null && !modelPathField.getText().isBlank()) {
                linkCfg.setModelPath(modelPathField.getText());
            }

            // 保存引擎配置
            saveEngineConfig();

            // 保存配置到文件
            configManager.save();

            // 关闭对话框
            closeDialog();

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
        confirmAlert.setHeaderText("重置连线设置");
        confirmAlert.setContentText("确定要恢复所有连线设置为默认值吗?");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // 重置为默认值（对齐 Config 默认：后台模式关、点击 40ms、移动 20ms）
                scanIntervalSpinner.getValueFactory().setValue(100);
                captureMethodCombo.setValue("自动（PrintWindow→Robot）");
                backModeCheckBox.setSelected(false);
                animationCheckBox.setSelected(true);
                mouseMoveDelaySpinner.getValueFactory().setValue(20);
                mouseClickDelaySpinner.getValueFactory().setValue(40);
                showInfoCheckBox.setSelected(true);
                autoClickCheckBox.setSelected(false);
                stopWhenMateCheckBox.setSelected(false);
                modelPathField.setText("./models/yolov11.onnx");
                engineColorCombo.setValue("自动（按行棋方判定）");

                if (statusLabel != null) {
                    statusLabel.setText("已重置为默认值");
                    statusLabel.setStyle("-fx-text-fill: green;");
                }
            }
        });
    }

    /**
     * 验证输入是否有效
     * Validate input values
     */
    private boolean validateInput() {
        // 验证扫描间隔
        int scanInterval = scanIntervalSpinner.getValue();
        if (scanInterval < 10) {
            showAlert("输入错误", "扫描间隔不能小于10毫秒", Alert.AlertType.WARNING);
            return false;
        }
        if (scanInterval > 1000) {
            showAlert("输入错误", "扫描间隔不能大于1000毫秒", Alert.AlertType.WARNING);
            return false;
        }

        // 验证鼠标移动延迟
        int mouseMoveDelay = mouseMoveDelaySpinner.getValue();
        if (mouseMoveDelay < 0) {
            showAlert("输入错误", "鼠标移动延迟不能为负数", Alert.AlertType.WARNING);
            return false;
        }
        if (mouseMoveDelay > 1000) {
            showAlert("输入错误", "鼠标移动延迟不能大于1000毫秒", Alert.AlertType.WARNING);
            return false;
        }

        // 验证鼠标点击延迟
        int mouseClickDelay = mouseClickDelaySpinner.getValue();
        if (mouseClickDelay < 0) {
            showAlert("输入错误", "鼠标点击延迟不能为负数", Alert.AlertType.WARNING);
            return false;
        }
        if (mouseClickDelay > 1000) {
            showAlert("输入错误", "鼠标点击延迟不能大于1000毫秒", Alert.AlertType.WARNING);
            return false;
        }

        // 如果扫描间隔过小，给出确认提示
        if (scanInterval < 50) {
            Alert warningAlert = new Alert(Alert.AlertType.CONFIRMATION);
            warningAlert.setTitle("性能警告");
            warningAlert.setHeaderText("扫描间隔设置较小");
            warningAlert.setContentText(
                String.format("当前扫描间隔为 %d 毫秒，这可能导致CPU占用过高。\n是否继续保存?", scanInterval));

            var result = warningAlert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return false;
            }
        }

        return true;
    }

    /**
     * 应用设置按钮点击事件 - 应用设置但不关闭对话框
     * Apply button click event - Apply settings without closing dialog
     */
    @FXML
    public void onApply() {
        // 验证输入
        if (!validateInput()) {
            return;
        }

        try {
            var linkCfg = config.link();

            // 保存所有设置
            linkCfg.setScanIntervalMs(scanIntervalSpinner.getValue());
            saveCaptureMethod();
            linkCfg.setBackMode(backModeCheckBox.isSelected());
            linkCfg.setAnimation(animationCheckBox.isSelected());
            linkCfg.setMouseMoveDelayMs(mouseMoveDelaySpinner.getValue());
            linkCfg.setMouseClickDelayMs(mouseClickDelaySpinner.getValue());
            linkCfg.setShowInfo(showInfoCheckBox.isSelected());

            // 保存调试截图开关
            linkCfg.setSaveScreenshot(saveScreenshotCheckBox.isSelected());

            // 保存自动续盘设置（即时生效）
            saveAutoClick();

            // ★ 保存模型路径（此前 onConfirm/onApply 均未写回，手输/重置的路径不落盘）
            if (modelPathField.getText() != null && !modelPathField.getText().isBlank()) {
                linkCfg.setModelPath(modelPathField.getText());
            }

            // 保存引擎配置
            saveEngineConfig();

            // 保存配置到文件
            configManager.save();

            // 更新状态标签
            if (statusLabel != null) {
                statusLabel.setText("设置已应用");
                statusLabel.setStyle("-fx-text-fill: green;");
            }

        } catch (Exception e) {
            showAlert("应用失败", "应用配置时发生错误: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * 关闭对话框
     * Close dialog
     */
    private void closeDialog() {
        Stage stage = (Stage) scanIntervalSpinner.getScene().getWindow();
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
