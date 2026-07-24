package com.jiyi.ui;

import com.google.inject.Injector;
import com.jiyi.di.AppModule;
import com.jiyi.infra.config.Config;
import com.jiyi.infra.config.ConfigManager;
import com.jiyi.ui.controller.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App extends Application {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    public static final String VERSION = "1.0.0";

    private static Injector injector;

    @Override
    public void start(Stage stage) throws Exception {
        injector = AppModule.createInjector();
        var config = injector.getInstance(Config.class);

        var fxmlUrl = getClass().getResource("/fxml/main.fxml");
        if (fxmlUrl == null) {
            log.error("main.fxml not found");
            return;
        }

        var loader = new FXMLLoader(fxmlUrl);
        loader.setControllerFactory(injector::getInstance);

        var scene = new Scene(loader.load(), config.app().windowWidth(), config.app().windowHeight());

        stage.setTitle("极弈 " + VERSION);
        stage.setScene(scene);
        stage.show();

        var controller = (MainController) loader.getController();
        controller.initialize(stage);

        log.info("极弈 v{} started", VERSION);
    }

    @Override
    public void stop() {
        if (injector != null) {
            injector.getInstance(ConfigManager.class).save();
        }
        log.info("极弈 stopped");
    }

    public static Injector getInjector() { return injector; }
}
