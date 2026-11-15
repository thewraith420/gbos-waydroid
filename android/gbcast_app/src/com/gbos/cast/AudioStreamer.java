package com.gbos.cast;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Sends the phone's own playback audio to the laptop (audio_relay.py, port 8101) as raw PCM, so the cast
 * has sound on the Googlebook. It rides the same MediaProjection grant as the video, so it needs no extra
 * consent, only RECORD_AUDIO (which Android requires for playback capture too).
 *
 * Wire format: one ASCII header line "GBAUD1 <token> <rate> <channels>\n", then raw signed 16-bit little-endian
 * interleaved PCM until the connection ends. Raw PCM at 48 kHz stereo is ~1.5 Mbit/s: negligible on a LAN, and
 * it avoids an encoder (and its latency) on both ends.
 *
 * Failure here never affects the video: every problem is logged and retried, the screen cast carries on.
 */
final class AudioStreamer {
    private static final String TAG = "GBAudio";
    static final int PORT = 8101;
    private static final int RATE = 48000;
    private static final int CHANNELS = 2;

    private final Context ctx;
    private final MediaProjection projection;
    private volatile boolean running;
    private Thread thread;
    private AudioRecord rec;
    private volatile long peak;           // loudest sample in the last chunk, for the "is anything playing" check
    private volatile boolean linked;      // the laptop acknowledged us ("OK"): someone is really listening
    private final Runnable onLinkChange;

    AudioStreamer(Context ctx, MediaProjection projection, Runnable onLinkChange) {
        this.ctx = ctx;
        this.projection = projection;
        this.onLinkChange = onLinkChange;
    }

    /** True only while the laptop has a cast window open and accepted this connection. */
    boolean isLinked() { return linked; }

    private void setLinked(boolean v) {
        if (linked == v) return;
        linked = v;
        Log.i(TAG, v ? "audio link up: a cast window is open" : "audio link down");
        try {
            if (onLinkChange != null) onLinkChange.run();
        } catch (Exception e) {
            Log.w(TAG, "link change handler failed", e);
        }
    }

    boolean start() {
        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO not granted — no audio (grant it in the app or: adb shell pm grant "
                    + ctx.getPackageName() + " android.permission.RECORD_AUDIO)");
            return false;
        }
        AudioPlaybackCaptureConfiguration cfg = new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build();
        AudioFormat fmt = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build();
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        try {
            rec = new AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(cfg)
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(Math.max(min * 2, 8192))
                    .build();
            rec.startRecording();
        } catch (Exception e) {
            Log.e(TAG, "playback capture could not start", e);
            release();
            return false;
        }
        running = true;
        thread = new Thread(this::loop, "gbos-audio");
        thread.start();
        Log.i(TAG, "capturing playback audio " + RATE + " Hz x" + CHANNELS + " -> "
                + CaptureService.host(ctx) + ":" + PORT);
        return true;
    }

    void stop() {
        running = false;
        release();
        Thread t = thread;
        if (t != null) t.interrupt();
        thread = null;
    }

    private void release() {
        try {
            if (rec != null) { rec.stop(); }
        } catch (Exception ignored) {
        }
        try {
            if (rec != null) { rec.release(); }
        } catch (Exception ignored) {
        }
        rec = null;
    }

    private void loop() {
        byte[] buf = new byte[RATE * CHANNELS * 2 / 50];        // 20 ms chunks
        Socket s = null;
        OutputStream out = null;
        long lastLog = 0;
        while (running) {
            AudioRecord r = rec;
            if (r == null) break;
            int n = r.read(buf, 0, buf.length);                  // blocks; returns silence while nothing plays
            if (n <= 0) {
                if (n < 0) { Log.w(TAG, "AudioRecord.read -> " + n); break; }
                continue;
            }
            long pk = 0;
            for (int i = 0; i + 1 < n; i += 2) {
                int v = (short) ((buf[i] & 0xff) | (buf[i + 1] << 8));
                if (v < 0) v = -v;
                if (v > pk) pk = v;
            }
            peak = pk;
            long now = System.currentTimeMillis();
            if (now - lastLog > 10000) {
                lastLog = now;
                Log.i(TAG, "audio level peak=" + pk + " connected=" + (out != null));
            }
            if (out == null) {
                try {
                    s = new Socket();
                    s.connect(new InetSocketAddress(CaptureService.host(ctx), PORT), 1500);
                    s.setTcpNoDelay(true);
                    out = s.getOutputStream();
                    out.write(("GBAUD1 " + CaptureService.INPUT_TOKEN + " " + RATE + " " + CHANNELS + "\n")
                            .getBytes("US-ASCII"));
                    // The laptop answers "OK" only while a cast window is open; otherwise it just closes.
                    s.setSoTimeout(1500);
                    InputStream in = s.getInputStream();
                    byte[] ack = new byte[3];
                    int got = 0;
                    while (got < 3) {
                        int k = in.read(ack, got, 3 - got);
                        if (k < 0) break;
                        got += k;
                    }
                    if (got != 3 || ack[0] != 'O' || ack[1] != 'K') throw new java.io.IOException("no ack");
                    s.setSoTimeout(0);
                    setLinked(true);
                } catch (Exception e) {
                    close(s); s = null; out = null;
                    setLinked(false);
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { break; }
                    continue;
                }
            }
            try {
                out.write(buf, 0, n);
            } catch (Exception e) {
                Log.w(TAG, "audio send failed: " + e);
                close(s); s = null; out = null;
                setLinked(false);
            }
        }
        close(s);
        setLinked(false);
    }

    private static void close(Socket s) {
        try {
            if (s != null) s.close();
        } catch (Exception ignored) {
        }
    }

    long lastPeak() { return peak; }
}
