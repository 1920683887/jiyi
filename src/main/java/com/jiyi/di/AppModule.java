package com.jiyi.di;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.jiyi.core.event.EventBus;
import com.jiyi.core.rule.CheckDetector;
import com.jiyi.core.rule.ChineseTranslator;
import com.jiyi.core.rule.MateDetector;
import com.jiyi.core.rule.MoveGenerator;
import com.jiyi.core.rule.MoveValidator;
import com.jiyi.infra.config.Config;
import com.jiyi.infra.config.ConfigManager;
import com.jiyi.infra.platform.Platform;
import com.jiyi.infra.platform.WindowsPlatform;
import com.jiyi.service.EngineService;
import com.jiyi.service.GameService;
import com.jiyi.service.BookService;
import com.jiyi.service.ManualService;

public class AppModule extends AbstractModule {

    private static Injector injector;

    @Override
    protected void configure() {
        bind(EventBus.class).in(Singleton.class);
        bind(CheckDetector.class).in(Singleton.class);
        bind(MateDetector.class).in(Singleton.class);
        bind(MoveGenerator.class).in(Singleton.class);
        bind(MoveValidator.class).in(Singleton.class);
        bind(ChineseTranslator.class).in(Singleton.class);
        bind(GameService.class).in(Singleton.class);
        bind(EngineService.class).in(Singleton.class);
        bind(ManualService.class).in(Singleton.class);
        bind(BookService.class).in(Singleton.class);
    }

    public static Injector createInjector() {
        injector = Guice.createInjector(new AppModule());
        return injector;
    }

    public static Injector getInjector() {
        return injector;
    }

    @Provides
    @Singleton
    public Config provideConfig(ConfigManager manager) {
        return manager.load();
    }

    @Provides
    @Singleton
    public ConfigManager provideConfigManager() {
        return new ConfigManager();
    }

    @Provides
    @Singleton
    public Platform providePlatform() {
        return new WindowsPlatform();
    }
}
