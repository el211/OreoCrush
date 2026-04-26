package dev.oreo;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.AdvancedPlayer;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AudioManager {

    private final Map<String, Clip> cache = new ConcurrentHashMap<>();
    private Clip musicClip;
    private Mp3Playback mp3Music;
    private Mp3Playback mp3Voice;

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
        if (resourcePath == null) return;

        if (resourcePath.toLowerCase().endsWith(".mp3")) {
            mp3Music = new Mp3Playback(resourcePath, true);
            mp3Music.start();
            return;
        }

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

        if (mp3Music != null) {
            mp3Music.stop();
            mp3Music = null;
        }
    }

    public void playSfx(String resourcePath) {
        if (resourcePath != null && resourcePath.toLowerCase().endsWith(".mp3")) {
            spawnMp3Sfx(resourcePath);
            return;
        }

        Clip clip = loadClip(resourcePath);
        if (clip == null) return;

        try {
            clip.stop();
            clip.setFramePosition(0);
            applyVolume(clip, sfxVolumeDb);
            clip.start();
        } catch (Exception ignored) {}
    }

    public void playVoice(String resourcePath) {
        playMp3Voice(resourcePath);
    }

    private void spawnMp3Sfx(String resourcePath) {
        Thread t = new Thread(() -> {
            InputStream raw = null;
            try {
                raw = getClass().getResourceAsStream(resourcePath);
                if (raw == null) {
                    System.err.println("Missing audio: " + resourcePath);
                    return;
                }
                AdvancedPlayer player = new AdvancedPlayer(new BufferedInputStream(raw));
                player.play();
            } catch (JavaLayerException ignored) {
            } catch (Exception e) {
                System.err.println("MP3 sfx error: " + resourcePath);
            } finally {
                if (raw != null) try { raw.close(); } catch (Exception ignored) {}
            }
        }, "oreo-mp3-sfx");
        t.setDaemon(true);
        t.start();
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

    private void playMp3Voice(String resourcePath) {
        stopMp3Voice();
        mp3Voice = new Mp3Playback(resourcePath, false);
        mp3Voice.start();
    }

    private void stopMp3Voice() {
        if (mp3Voice != null) {
            mp3Voice.stop();
            mp3Voice = null;
        }
    }

    private final class Mp3Playback {
        private final String resourcePath;
        private final boolean loop;
        private volatile boolean stopped = false;
        private volatile AdvancedPlayer currentPlayer;
        private final Thread thread;

        private Mp3Playback(String resourcePath, boolean loop) {
            this.resourcePath = resourcePath;
            this.loop = loop;
            this.thread = new Thread(() -> {
                do {
                    if (stopped) break;
                    InputStream raw = null;
                    try {
                        raw = AudioManager.this.getClass().getResourceAsStream(resourcePath);
                        if (raw == null) {
                            System.err.println("Missing audio: " + resourcePath);
                            break;
                        }
                        BufferedInputStream buffered = new BufferedInputStream(raw);
                        AdvancedPlayer player = new AdvancedPlayer(buffered);
                        synchronized (Mp3Playback.this) {
                            if (stopped) {
                                closeQuietly(raw);
                                break;
                            }
                            currentPlayer = player;
                        }
                        player.play();
                    } catch (JavaLayerException e) {
                        // expected when stop() closes the player mid-playback
                    } catch (Exception e) {
                        System.err.println("MP3 playback error: " + resourcePath);
                        break;
                    } finally {
                        closeQuietly(raw);
                    }
                } while (loop && !stopped);
            }, loop ? "oreo-mp3-music" : "oreo-mp3-voice");
            this.thread.setDaemon(true);
        }

        private void start() {
            thread.start();
        }

        private synchronized void stop() {
            stopped = true;
            AdvancedPlayer p = currentPlayer;
            if (p != null) {
                try { p.close(); } catch (Exception ignored) {}
                currentPlayer = null;
            }
        }
    }

    private void closeQuietly(InputStream in) {
        if (in == null) return;
        try {
            in.close();
        } catch (Exception ignored) {}
    }

    private void applyVolume(Clip clip, float db) {
        if (clip == null) return;
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float v = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db));
            gain.setValue(v);
        } catch (Exception ignored) {}
    }
}
