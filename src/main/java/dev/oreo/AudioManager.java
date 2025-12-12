package dev.oreo;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AudioManager {

    private final Map<String, Clip> cache = new ConcurrentHashMap<>();
    private Clip musicClip;

    private float musicVolumeDb = -12f;
    private float sfxVolumeDb = -6f;

    public void setMusicVolumeDb(float db) {
        this.musicVolumeDb = db;
        applyVolume(musicClip, db);
    }

    public void setSfxVolumeDb(float db) {
        this.sfxVolumeDb = db;
    }

    public void playMusicLoop(String resourcePath) {
        stopMusic();
        musicClip = loadClip(resourcePath);
        if (musicClip == null) return;

        applyVolume(musicClip, musicVolumeDb);
        musicClip.setFramePosition(0);
        musicClip.loop(Clip.LOOP_CONTINUOUSLY);
        musicClip.start();
    }

    public void stopMusic() {
        if (musicClip != null) {
            try {
                musicClip.stop();
                musicClip.close();
            } catch (Exception ignored) {}
            musicClip = null;
        }
    }

    public void playSfx(String resourcePath) {
        Clip clip = loadClip(resourcePath);
        if (clip == null) return;

        try {
            clip.stop();
            clip.setFramePosition(0);
            applyVolume(clip, sfxVolumeDb);
            clip.start();
        } catch (Exception ignored) {}
    }

    private Clip loadClip(String resourcePath) {
        try {
            Clip cached = cache.get(resourcePath);
            if (cached != null && cached.isOpen()) return cached;

            InputStream in = getClass().getResourceAsStream(resourcePath);
            if (in == null) {
                System.err.println("Missing audio: " + resourcePath);
                return null;
            }

            AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(in));
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            cache.put(resourcePath, clip);
            return clip;
        } catch (Exception e) {
            System.err.println("Audio load failed: " + resourcePath);
            e.printStackTrace();
            return null;
        }
    }

    private void applyVolume(Clip clip, float db) {
        if (clip == null) return;
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float v = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db));
            gain.setValue(v);
        } catch (Exception ignored) {
            // volume control not supported on some systems
        }
    }
}
