package com.endpoem.audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;

/**
 * Streams the vanilla credits track (music/game/end/alpha.ogg) through
 * stb_vorbis + javax.sound. Decoding is identical to the game's music asset;
 * playback loops while the poem is on screen.
 */
public final class Music {
    private volatile boolean running;
    private volatile SourceDataLine line;
    private Thread thread;

    public synchronized void start(Path ogg) {
        stop();
        running = true;
        thread = new Thread(() -> run(ogg), "end-poem-music");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        SourceDataLine current = line;
        if (current != null) {
            try {
                current.stop();
                current.flush();
                current.close();
            } catch (Throwable ignored) {
            }
            line = null;
        }
        if (thread != null) {
            try {
                thread.join(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    private void run(Path ogg) {
        long handle = 0;
        SourceDataLine audio = null;
        try {
            IntBuffer error = BufferUtils.createIntBuffer(1);
            handle = STBVorbis.stb_vorbis_open_filename(ogg.toString(), error, null);
            if (handle == 0L) {
                System.err.println("audio: cannot open " + ogg + " (error " + error.get(0) + ")");
                return;
            }
            STBVorbisInfo info = STBVorbisInfo.malloc();
            STBVorbis.stb_vorbis_get_info(handle, info);
            try (info) {
                int channels = info.channels();
                int sampleRate = info.sample_rate();
                AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
                audio = AudioSystem.getSourceDataLine(format);
                audio.open(format, Math.max(4096, sampleRate / 8 * channels * 2));
                line = audio;
                audio.start();
                ShortBuffer pcm = BufferUtils.createShortBuffer(4096 * channels);
                ByteBuffer bytes = BufferUtils.createByteBuffer(4096 * channels * 2);
                while (running) {
                    pcm.clear();
                    int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcm);
                    if (frames <= 0) {
                        STBVorbis.stb_vorbis_seek_start(handle);
                        continue;
                    }
                    bytes.clear();
                    int samples = frames * channels;
                    for (int i = 0; i < samples; i++) {
                        short s = pcm.get(i);
                        bytes.put((byte) (s & 0xFF));
                        bytes.put((byte) (s >> 8 & 0xFF));
                    }
                    bytes.flip();
                    byte[] chunk = new byte[bytes.remaining()];
                    bytes.get(chunk);
                    audio.write(chunk, 0, chunk.length);
                }
            }
        } catch (Throwable t) {
            System.err.println("audio unavailable: " + t);
        } finally {
            if (audio != null) {
                try {
                    audio.stop();
                    audio.flush();
                    audio.close();
                } catch (Throwable ignored) {
                }
            }
            if (handle != 0L) {
                STBVorbis.stb_vorbis_close(handle);
            }
            line = null;
        }
    }
}
