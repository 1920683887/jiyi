package com.jiyi.ui.controller;

import com.google.inject.Inject;
import com.jiyi.core.engine.EngineConfig;
import com.jiyi.infra.config.Config;
import com.jiyi.infra.config.ConfigManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class EngineManagerController {

    @Inject private Config config;
    @Inject private ConfigManager configManager;

    @FXML private TableView<EngineConfig> engineTable;
    @FXML private TableColumn<EngineConfig, String> defaultCol;
    @FXML private TableColumn<EngineConfig, String> nameCol;
    @FXML private TableColumn<EngineConfig, String> pathCol;
    @FXML private TableColumn<EngineConfig, Integer> threadCol;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        pathCol.setCellValueFactory(new PropertyValueFactory<>("path"));
        threadCol.setCellValueFactory(new PropertyValueFactory<>("threads"));

        defaultCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setText("");
                else {
                    EngineConfig ec = getTableView().getItems().get(getIndex());
                    setText(ec.name().equals(config.engine().defaultEngine()) ? "★" : "");
                }
            }
        });

        refreshTable();
    }

    @FXML
    public void onAdd() {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择引擎文件");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("引擎文件(*.exe)", "*.exe"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("所有文件", "*.*"));
        File file = fc.showOpenDialog(engineTable.getScene().getWindow());
        if (file == null) return;

        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);

        TextInputDialog tid = new TextInputDialog(name);
        tid.setTitle("确认引擎名称");
        tid.setHeaderText("引擎: " + file.getName());
        tid.setContentText("引擎显示名称:");
        var result = tid.showAndWait();
        if (result.isEmpty()) return;
        name = result.get().trim();
        if (name.isEmpty()) return;

        var cfg = new EngineConfig(name, file.getAbsolutePath());
        config.engine().list().add(cfg);
        if (config.engine().defaultEngine().isEmpty()) {
            config.engine().setDefaultEngine(name);
        }
        configManager.save();
        refreshTable();
        statusLabel.setText("已添加: " + name);
    }

    @FXML
    public void onDelete() {
        var selected = engineTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        config.engine().list().remove(selected);
        if (config.engine().defaultEngine().equals(selected.name())) {
            if (!config.engine().list().isEmpty()) {
                config.engine().setDefaultEngine(config.engine().list().get(0).name());
            } else {
                config.engine().setDefaultEngine("");
            }
        }
        configManager.save();
        refreshTable();
        statusLabel.setText("已删除: " + selected.name());
    }

    @FXML
    public void onSetDefault() {
        var selected = engineTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        config.engine().setDefaultEngine(selected.name());
        configManager.save();
        refreshTable();
        statusLabel.setText("默认引擎: " + selected.name());
    }

    private void refreshTable() {
        engineTable.getItems().clear();
        engineTable.getItems().addAll(config.engine().list());
    }
}
