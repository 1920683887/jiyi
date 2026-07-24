package com.jiyi.infra.util;

import javafx.scene.media.AudioClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoundPlayer {
    private static final Logger log = LoggerFactory.getLogger(SoundPlayer.class);

    private AudioClip moveSound;
    private AudioClip captureSound;

    public SoundPlayer() {
        try {
            var moveUrl = getClass().getResource("/sound/move.wav");
            var captureUrl = getClass().getResource("/sound/capture.wav");
            if (moveUrl != null) moveSound = new AudioClip(moveUrl.toString());
            if (captureUrl != null) captureSound = new AudioClip(captureUrl.toString());
        } catch (Exception e) {
            log.debug("Sound files not found, sounds disabled");
        }
    }

    public void playMove() { if (moveSound != null) moveSound.play(); }
    public void playCapture() { if (captureSound != null) captureSound.play(); }
}
