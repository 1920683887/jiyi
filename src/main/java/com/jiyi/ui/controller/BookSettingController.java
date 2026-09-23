package com.jiyi.ui.controller;

import com.google.inject.Inject;
import com.jiyi.infra.config.Config;
import com.jiyi.infra.config.ConfigManager;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

/**
 * 开局库设置对话框控制器
 * Opening Book Settings Dialog Controller
 *
 * 功能 Features:
 * 1. 显示已添加的开局库列表 - Display added opening book list (TableView)
 * 2. 添加按钮 - Add button (FileChooser for .obk/.xqb/.pf files)
 * 3. 删除按钮 - Delete button
 * 4. 上移/下移优先级 - Move up/down priority
 * 5. 云库开关 - Cloud book switch
 * 6. 选招策略 - Move selection strategy (highest score/highest win rate/random)
 */
public class BookSettingController {

    /**
     * 开局库条目类 - 用于TableView显示
     * Opening book entry class - For TableView display
     */
    public static class BookRow {
        private final SimpleIntegerProperty priority = new SimpleIntegerProperty();
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final SimpleStringProperty path = new SimpleStringProperty();

        public BookRow(int priority, String path) {
            this.priority.set(priority);
            this.path.set(path);
            this.name.set(extractFileName(path));
        }

        /**
         * 从完整路径提取文件名
         * Extract file name from full path
         */
        private String extractFileName(String path) {
            if (path == null || path.isEmpty()) {
                return "";
            }
            int lastSeparator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            return lastSeparator >= 0 ? path.substring(lastSeparator + 1) : path;
        }

        public int getPriority() { return priority.get(); }
        public SimpleIntegerProperty priorityProperty() { return priority; }
        public String getName() { return name.get(); }
        public SimpleStringProperty nameProperty() { return name; }
        public String getPath() { return path.get(); }
        public SimpleStringProperty pathProperty() { return path; }
    }

    @Inject private Config config;
    @Inject private ConfigManager configManager;

    // 开局库列表表格 (Opening book list table)
    @FXML private TableView<BookRow> bookTable;
    @FXML private TableColumn<BookRow, Integer> priorityCol;
    @FXML private TableColumn<BookRow, String> nameCol;
    @FXML private TableColumn<BookRow, String> pathCol;

    // 按钮 (Buttons)
    @FXML private Button addButton;
    @FXML private Button deleteButton;
    @FXML private Button moveUpButton;
    @FXML private Button moveDownButton;

    // 云库设置 (Cloud book settings)
    @FXML private CheckBox cloudEnabledCheckBox;

    // 选招策略 (Move selection strategy)
    @FXML private ComboBox<String> moveRuleCombo;

    // 状态标签 (Status label)
    @FXML private Label statusLabel;

    // 开局库数据列表 (Opening book data list)
    private ObservableList<BookRow> bookEntries = FXCollections.observableArrayList();

    /**
     * 初始化控制器，加载配置到UI组件
     * Initialize controller and load config to UI components
     */
    @FXML
    public void initialize() {
        initializeTableView();
        initializeCloudSettings();
        initializeMoveStrategySettings();
        loadBookFilesFromConfig();
        setupListeners();
    }

    /**
     * 初始化表格视图
     * Initialize table view
     */
    private void initializeTableView() {
        // 优先级列 (Priority column)
        priorityCol.setCellValueFactory(cellData ->
            cellData.getValue().priorityProperty().asObject());

        // 文件名列 (File name column)
        nameCol.setCellValueFactory(cellData ->
            cellData.getValue().nameProperty());

        // 文件路径列 (File path column)
        pathCol.setCellValueFactory(cellData ->
            cellData.getValue().pathProperty());

        // 绑定数据列表
        bookTable.setItems(bookEntries);

        // 设置表格选择模式为单选
        bookTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    /**
     * 初始化云库设置
     * Initialize cloud book settings
     */
    private void initializeCloudSettings() {
        var bookCfg = config.book();
        cloudEnabledCheckBox.setSelected(bookCfg.cloudEnabled());
    }

    /**
     * 初始化选招策略设置
     * Initialize move selection strategy settings
     */
    private void initializeMoveStrategySettings() {
        var bookCfg = config.book();

        // 添加选招策略选项
        moveRuleCombo.getItems().addAll("最高分", "最高胜率", "随机");

        // 根据当前配置选择对应的策略
        String currentStrategy = bookCfg.moveRule();
        String displayStrategy = convertStrategyToDisplay(currentStrategy);
        moveRuleCombo.setValue(displayStrategy);
    }

    /**
     * 从配置加载开局库文件列表
     * Load opening book files from configuration
     */
    private void loadBookFilesFromConfig() {
        bookEntries.clear();
        var bookCfg = config.book();
        var files = bookCfg.files();

        for (int i = 0; i < files.size(); i++) {
            String filePath = files.get(i);
            bookEntries.add(new BookRow(i + 1, filePath));
        }

        updateStatusLabel();
    }

    /**
     * 设置各种监听器
     * Setup listeners for UI components
     */
    private void setupListeners() {
        // 表格选择变化监听
        bookTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> updateButtonStates());

        // 初始按钮状态
        updateButtonStates();
    }

    /**
     * 更新按钮状态（启用/禁用）
     * Update button states (enabled/disabled)
     */
    private void updateButtonStates() {
        BookRow selected = bookTable.getSelectionModel().getSelectedItem();
        boolean hasSelection = selected != null;
        boolean hasBooks = !bookEntries.isEmpty();

        if (deleteButton != null) {
            deleteButton.setDisable(!hasSelection);
        }
        if (moveUpButton != null) {
            moveUpButton.setDisable(!hasSelection || selected.getPriority() == 1);
        }
        if (moveDownButton != null) {
            moveDownButton.setDisable(!hasSelection || selected.getPriority() == bookEntries.size());
        }
    }

    /**
     * 更新状态标签
     * Update status label
     */
    private void updateStatusLabel() {
        if (statusLabel != null) {
            int count = bookEntries.size();
            statusLabel.setText(String.format("已添加 %d 个开局库文件", count));
        }
    }

    /**
     * 添加按钮点击事件 - 选择开局库文件
     * Add button click event - Select opening book file
     */
    @FXML
    public void onAddBook() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择开局库文件");

        // 设置文件过滤器
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("开局库文件", "*.obk", "*.xqb", "*.pf"),
            new FileChooser.ExtensionFilter("OBK文件", "*.obk"),
            new FileChooser.ExtensionFilter("XQB文件", "*.xqb"),
            new FileChooser.ExtensionFilter("PF文件", "*.pf"),
            new FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        // 显示文件选择对话框
        File file = fileChooser.showOpenDialog(bookTable.getScene().getWindow());
        if (file == null) {
            return;
        }

        String filePath = file.getAbsolutePath();

        // 检查是否已存在
        for (BookRow entry : bookEntries) {
            if (entry.getPath().equals(filePath)) {
                showAlert("重复文件", "该开局库文件已经添加过了", Alert.AlertType.WARNING);
                return;
            }
        }

        // 添加到列表
        int priority = bookEntries.size() + 1;
        bookEntries.add(new BookRow(priority, filePath));

        // 更新状态
        updateStatusLabel();
        updateButtonStates();

        if (statusLabel != null) {
            statusLabel.setText("已添加: " + file.getName());
        }
    }

    /**
     * 删除按钮点击事件 - 删除选中的开局库
     * Delete button click event - Delete selected opening book
     */
    @FXML
    public void onDeleteBook() {
        BookRow selected = bookTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            if (statusLabel != null) {
                statusLabel.setText("请先选择一项");
            }
            return;
        }

        // 确认删除
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("确认删除");
        confirmAlert.setHeaderText("删除开局库");
        confirmAlert.setContentText("确定要删除 \"" + selected.getName() + "\" 吗?");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // 删除选中项
                bookEntries.remove(selected);

                // 重新计算优先级
                recalculatePriorities();

                // 更新状态
                updateStatusLabel();
                updateButtonStates();

                if (statusLabel != null) {
                    statusLabel.setText("已删除: " + selected.getName());
                }
            }
        });
    }

    /**
     * 上移按钮点击事件 - 提高优先级
     * Move up button click event - Increase priority
     */
    @FXML
    public void onMoveUp() {
        BookRow selected = bookTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getPriority() == 1) {
            return;
        }

        int currentIndex = bookEntries.indexOf(selected);
        int targetIndex = currentIndex - 1;

        // 交换位置
        bookEntries.remove(currentIndex);
        bookEntries.add(targetIndex, selected);

        // 重新计算优先级
        recalculatePriorities();

        // 保持选中状态
        bookTable.getSelectionModel().select(selected);

        if (statusLabel != null) {
            statusLabel.setText("已上移: " + selected.getName());
        }
    }

    /**
     * 下移按钮点击事件 - 降低优先级
     * Move down button click event - Decrease priority
     */
    @FXML
    public void onMoveDown() {
        BookRow selected = bookTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getPriority() == bookEntries.size()) {
            return;
        }

        int currentIndex = bookEntries.indexOf(selected);
        int targetIndex = currentIndex + 1;

        // 交换位置
        bookEntries.remove(currentIndex);
        bookEntries.add(targetIndex, selected);

        // 重新计算优先级
        recalculatePriorities();

        // 保持选中状态
        bookTable.getSelectionModel().select(selected);

        if (statusLabel != null) {
            statusLabel.setText("已下移: " + selected.getName());
        }
    }

    /**
     * 重新计算所有条目的优先级
     * Recalculate priorities for all entries
     */
    private void recalculatePriorities() {
        for (int i = 0; i < bookEntries.size(); i++) {
            bookEntries.get(i).priority.set(i + 1);
        }
        bookTable.refresh();
    }

    /**
     * 将策略代码转换为显示文本
     * Convert strategy code to display text
     */
    private String convertStrategyToDisplay(String strategy) {
        if (strategy == null) {
            return "最高分";
        }
        switch (strategy) {
            case "BEST_SCORE":
                return "最高分";
            case "BEST_WIN_RATE":
            case "BEST_WINRATE":
                return "最高胜率";
            case "RANDOM":
                return "随机";
            default:
                return "最高分";
        }
    }

    /**
     * 将显示文本转换为策略代码
     * Convert display text to strategy code
     */
    private String convertDisplayToStrategy(String display) {
        if (display == null) {
            return "BEST_SCORE";
        }
        switch (display) {
            case "最高分":
                return "BEST_SCORE";
            case "最高胜率":
                return "BEST_WIN_RATE";
            case "随机":
                return "RANDOM";
            default:
                return "BEST_SCORE";
        }
    }

    /**
     * 确认按钮点击事件 - 保存设置到配置文件
     * Confirm button click event - Save settings to config file
     */
    @FXML
    public void onConfirm() {
        try {
            var bookCfg = config.book();

            // 保存开局库文件列表
            bookCfg.clearBookFiles();
            for (BookRow entry : bookEntries) {
                bookCfg.addBookFile(entry.getPath());
            }

            // 保存云库开关
            bookCfg.setCloudEnabled(cloudEnabledCheckBox.isSelected());

            // 保存选招策略
            String selectedStrategy = moveRuleCombo.getValue();
            bookCfg.setMoveRule(convertDisplayToStrategy(selectedStrategy));

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
        confirmAlert.setHeaderText("重置开局库设置");
        confirmAlert.setContentText("确定要清空所有开局库文件并恢复默认设置吗?");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // 清空开局库列表
                bookEntries.clear();

                // 恢复默认云库设置
                cloudEnabledCheckBox.setSelected(true);

                // 恢复默认选招策略
                moveRuleCombo.setValue("最高分");

                // 更新状态
                updateStatusLabel();
                updateButtonStates();

                if (statusLabel != null) {
                    statusLabel.setText("已重置为默认设置");
                }
            }
        });
    }

    /**
     * 关闭对话框
     * Close dialog
     */
    private void closeDialog() {
        Stage stage = (Stage) bookTable.getScene().getWindow();
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
