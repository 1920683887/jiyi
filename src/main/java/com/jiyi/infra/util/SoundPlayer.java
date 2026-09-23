package com.jiyi.infra.util;

import javafx.scene.media.AudioClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoundPlayer {
    private static final Logger log = LoggerFactory.getLogger(SoundPlayer.class);

    private AudioClip moveSound;
    private AudioClip captureSound;
    private boolean warned;

    public SoundPlayer() {
        // 懒加载：资源在首次播放时加载，避免构造阻塞 FX 线程
    }

    public void playMove() {
        if (moveSound == null) {
            load();
        }
        if (moveSound != null) moveSound.play();
    }

    public void playCapture() {
        if (captureSound == null) {
            load();
        }
        if (captureSound != null) captureSound.play();
    }

    private void load() {
        try {
            var moveUrl = getClass().getResource("/sound/move.wav");
            var captureUrl = getClass().getResource("/sound/capture.wav");
            if (moveUrl != null) moveSound = new AudioClip(moveUrl.toString());
            if (captureUrl != null) captureSound = new AudioClip(captureUrl.toString());
            if (moveSound == null && captureSound == null && !warned) {
                warned = true;
                log.warn("Sound files not found (move.wav/capture.wav), sounds disabled");
            }
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                log.warn("Sound files not found, sounds disabled: {}", e.getMessage());
            }
        }
    }
}
