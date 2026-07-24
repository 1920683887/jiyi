package com.jiyi.service;

import com.google.inject.Inject;
import com.jiyi.core.book.*;
import com.jiyi.core.model.Board;
import com.jiyi.infra.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BookService {
    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private final Config config;
    private final BookSelector selector = new BookSelector();
    private final List<OpenBook> localBooks = new ArrayList<>();
    private CloudOpenBook cloudBook;

    @Inject
    public BookService(Config config) {
        this.config = config;
        initLocalBooks();
        if (config.book().cloudEnabled()) {
            cloudBook = new CloudOpenBook(config.book().cloudTimeoutMs());
        }
    }

    private void initLocalBooks() {
        for (String path : config.book().files()) {
            try {
                if (path.endsWith(".obk")) {
                    localBooks.add(new BhOpenBook(path));
                    log.info("Loaded book: {}", path);
                }
            } catch (Exception e) {
                log.warn("Failed to load book: {}", path, e);
            }
        }
    }

    public String queryBestMove(Board board, boolean redGo, int offManualSteps) {
        var strategy = BookSelector.Strategy.valueOf(config.book().moveRule());

        if (config.book().localFirst()) {
            for (var book : localBooks) {
                var entries = book.query(board, redGo);
                if (!entries.isEmpty()) {
                    var selected = selector.select(entries, strategy);
                    if (selected != null) {
                        log.debug("Local book: {} score={}", selected.move(), selected.score());
                        return selected.move();
                    }
                }
            }
        }

        if (config.book().cloudEnabled() && cloudBook != null) {
            var entries = cloudBook.query(board, redGo);
            if (!entries.isEmpty()) {
                var selected = selector.select(entries, strategy);
                if (selected != null) {
                    log.debug("Cloud book: {} score={}", selected.move(), selected.score());
                    return selected.move();
                }
            }
        }

        return null;
    }

    public void close() {
        for (var book : localBooks) book.close();
    }
}
