package com.jiyi.ui;

import com.google.inject.Injector;
import com.jiyi.di.AppModule;
import com.jiyi.infra.config.Config;
import com.jiyi.infra.config.ConfigManager;
import com.jiyi.ui.controller.MainController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App extends Application {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    public static final String VERSION = "1.0.0";

    private static Injector injector;
    private MainController mainController;

    @Override
    public void start(Stage stage) throws Exception {
        injector = AppModule.createInjector();
        var config = injector.getInstance(Config.class);
        // 校验配置合法性，非法时告警但继续（B31）
        if (!config.validate()) {
            log.warn("Config validation failed, some settings may be invalid");
        }

        var fxmlUrl = getClass().getResource("/fxml/main.fxml");
        if (fxmlUrl == null) {
            log.error("main.fxml not found");
            Platform.exit();
            return;
        }

        var loader = new FXMLLoader(fxmlUrl);
        loader.setControllerFactory(injector::getInstance);

        try {
            // 屏幕自适应：窗口尺寸超过屏幕可用区 90% 时收缩到 85%
            // （换小屏/低分辨率后避免窗口过大或超出屏幕）
            double w = config.app().windowWidth();
            double h = config.app().windowHeight();
            var bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
            if (bounds.getWidth() > 0 && (w > bounds.getWidth() * 0.9 || h > bounds.getHeight() * 0.9)) {
                w = Math.min(w, bounds.getWidth() * 0.85);
                h = Math.min(h, bounds.getHeight() * 0.85);
                log.info("Window size adjusted to screen: {}x{}", (int) w, (int) h);
            }
            var scene = new Scene(loader.load(), w, h);

            // 全局样式
            var css = getClass().getResource("/css/app.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            } else {
                log.warn("app.css not found, running without styles");
            }

            stage.setTitle("极弈 " + VERSION);
            // 最小窗口尺寸：保证工具栏/引擎区/棋盘布局完整，缩小窗口时不裁剪丢失按钮
            stage.setMinWidth(840);
            stage.setMinHeight(560);
            stage.setScene(scene);
            stage.show();

            mainController = (MainController) loader.getController();
            mainController.initialize(stage);

            log.info("极弈 v{} started", VERSION);
        } catch (Exception e) {
            log.error("Failed to start application", e);
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        // 关窗（X）兜底清理：检测线程（非 daemon，不停则 JVM 挂住）、连线服务、引擎进程、开局库连接
        if (injector != null) {
            try { injector.getInstance(com.jiyi.service.AutomationService.class).stop(); } catch (Exception ignored) {}
            try { injector.getInstance(com.jiyi.service.DetectionService.class).close(); } catch (Exception ignored) {}
            try { injector.getInstance(com.jiyi.service.EngineService.class).stopEngine(); } catch (Exception ignored) {}
            try { injector.getInstance(com.jiyi.service.BookService.class).close(); } catch (Exception ignored) {}
        }
        // 注销 UI 订阅，防泄漏
        if (mainController != null) {
            try { mainController.shutdown(); } catch (Exception ignored) {}
        }
        if (injector != null) {
            injector.getInstance(ConfigManager.class).save();
        }
        log.info("极弈 stopped");
    }

    public static Injector getInjector() { return injector; }
}
