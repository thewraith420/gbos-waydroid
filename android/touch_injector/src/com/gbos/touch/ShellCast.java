package com.gbos.touch;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * Privileged shell-uid (com.android.shell) screen & internal audio capture for App Cast:
 *  - Video mirrors display 0 via DisplayManager.createVirtualDisplay (no MediaProjection),
 *    so Android never shows the red status-bar timer pill or hides notification content.
 *  - Audio captures internal playback via REMOTE_SUBMIX only while the laptop's audio_relay
 *    answers "OK" (meaning an App Cast window is currently open).
 */
final class ShellCast {
    private static final int VIDEO_PORT = 8098;
    private static final int AUDIO_PORT = 8101;
    private static final String AUDIO_TOKEN = "gbos-cast-v1";

    private static final int MAX_SHORT = 720;
    private static final int MAX_LONG = 1600;
    private static final int FPS = 30;
    private static final int BITRATE = 12_000_000;
    private static final int IFRAME_SECONDS = 2;

    private static final int AUDIO_RATE = 48000;
    private static final int AUDIO_CHANNELS = 2;

    static volatile String hostIp;
    static volatile boolean enabled;
    static volatile boolean videoActive;
    static volatile boolean audioLinked;
    static volatile String activeSize = "none";

    private static volatile boolean explicitStop;
    private static final Object LIFECYCLE_LOCK = new Object();
    private static Thread videoThread;
    private static Thread audioThread;
    private static volatile Socket videoSock;
    private static volatile Socket audioSock;

    static boolean isEnabled() {
        return enabled;
    }

    static String statusLine() {
        return "cast enabled=" + enabled
                + " video=" + (videoActive ? "on" : "off")
                + " audio=" + (audioLinked ? "linked" : "idle")
                + " host=" + (hostIp != null ? hostIp : "none")
                + " size=" + activeSize;
    }

    static void start(String host) {
        synchronized (LIFECYCLE_LOCK) {
            explicitStop = false;
            boolean hostChanged = false;
            if (host != null && !host.isEmpty() && !host.equals(hostIp)) {
                hostIp = host;
                hostChanged = true;
            }
            enabled = true;
            if (hostChanged) {
                closeQuiet(videoSock);
                closeQuiet(audioSock);
            }
            if (videoThread == null || !videoThread.isAlive()) {
                videoThread = new Thread(new Runnable() {
                    public void run() { videoLoop(); }
                }, "gb-shell-video");
                videoThread.setDaemon(true);
                videoThread.start();
            }
            if (audioThread == null || !audioThread.isAlive()) {
                audioThread = new Thread(new Runnable() {
                    public void run() { audioLoop(); }
                }, "gb-shell-audio");
                audioThread.setDaemon(true);
                audioThread.start();
            }
        }
    }

    static void updateHost(String peerIp) {
        if (peerIp == null || peerIp.isEmpty()
                || "127.0.0.1".equals(peerIp) || "::1".equals(peerIp)) {
            return;
        }
        synchronized (LIFECYCLE_LOCK) {
            if (explicitStop) return;
            if (!enabled && hostIp == null) {
                start(peerIp);
            } else if (enabled && !peerIp.equals(hostIp)) {
                hostIp = peerIp;
                closeQuiet(videoSock);
                closeQuiet(audioSock);
            }
        }
    }

    static void stop() {
        synchronized (LIFECYCLE_LOCK) {
            explicitStop = true;
            enabled = false;
            closeQuiet(videoSock);
            closeQuiet(audioSock);
            if (videoThread != null) videoThread.interrupt();
            if (audioThread != null) audioThread.interrupt();
        }
    }

    private static int[] computeCaptureSize() {
        int capW = MAX_SHORT, capH = MAX_LONG;
        try {
            Class<?> dmgClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
            Object dmg = dmgClass.getMethod("getInstance").invoke(null);
            Object di = dmgClass.getMethod("getDisplayInfo", int.class).invoke(dmg, 0);
            if (di != null) {
                Field fw = di.getClass().getField("logicalWidth");
                Field fh = di.getClass().getField("logicalHeight");
                int lw = fw.getInt(di);
                int lh = fh.getInt(di);
                int physW = Math.min(lw, lh);
                int physH = Math.max(lw, lh);
                if (physW > 0 && physH > 0) {
                    float scale = Math.min(1.0f, Math.min((float) MAX_SHORT / physW, (float) MAX_LONG / physH));
                    capW = Math.max(16, (Math.round(physW * scale) / 16) * 16);
                    capH = Math.max(16, (Math.round(physH * scale) / 16) * 16);
                }
            }
        } catch (Throwable t) {
            System.out.println("SHELL_VIDEO computeCaptureSize fallback: " + t);
        }
        return new int[]{capW, capH};
    }

    private static MediaFormat buildVideoFormat(int w, int h, boolean highProfile) {
        MediaFormat fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_SECONDS);
        fmt.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        fmt.setInteger(MediaFormat.KEY_PRIORITY, 0);
        if (highProfile) {
            fmt.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
            fmt.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41);
        }
        return fmt;
    }

    private static VirtualDisplay createShellVirtualDisplay(int w, int h, Surface surface) throws Exception {
        Method m = DisplayManager.class.getMethod(
                "createVirtualDisplay", String.class, int.class, int.class, int.class, Surface.class);
        VirtualDisplay vd = (VirtualDisplay) m.invoke(null, "gbos-shell-cast", w, h, 0, surface);
        if (vd == null) throw new IllegalStateException("createVirtualDisplay returned null");
        return vd;
    }

    private static void videoLoop() {
        while (enabled) {
            String host = hostIp;
            if (host == null || host.isEmpty()) {
                sleepMs(1000);
                continue;
            }
            Socket s = null;
            MediaCodec codec = null;
            Surface inputSurface = null;
            VirtualDisplay vd = null;
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(host, VIDEO_PORT), 2000);
                s.setTcpNoDelay(true);
                videoSock = s;
                OutputStream out = s.getOutputStream();

                int[] sz = computeCaptureSize();
                int capW = sz[0], capH = sz[1];
                activeSize = capW + "x" + capH;

                codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                try {
                    codec.configure(buildVideoFormat(capW, capH, true),
                            null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                } catch (Exception hpErr) {
                    System.out.println("SHELL_VIDEO High profile rejected (" + hpErr.getMessage() + "), retrying default");
                    try { codec.release(); } catch (Throwable ignored) {}
                    codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                    codec.configure(buildVideoFormat(capW, capH, false),
                            null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                }
                inputSurface = codec.createInputSurface();
                codec.start();
                vd = createShellVirtualDisplay(capW, capH, inputSurface);

                videoActive = true;
                System.out.println("SHELL_VIDEO up " + capW + "x" + capH + " -> " + host + ":" + VIDEO_PORT);

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                byte[] csd = null;
                while (enabled && !s.isClosed()) {
                    int idx;
                    try {
                        idx = codec.dequeueOutputBuffer(info, 10000);
                    } catch (IllegalStateException e) {
                        break;
                    }
                    if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        csd = codecConfig(codec.getOutputFormat());
                    } else if (idx >= 0) {
                        boolean isConfig = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                        try {
                            if (!isConfig && info.size > 0) {
                                ByteBuffer buf = codec.getOutputBuffer(idx);
                                if (buf != null) {
                                    buf.position(info.offset);
                                    buf.limit(info.offset + info.size);
                                    byte[] data = new byte[info.size];
                                    buf.get(data);
                                    byte[] nal = isAnnexB(data) ? data : avccToAnnexB(data);
                                    boolean key = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                                    byte[] frame = key ? concat(csd, nal) : nal;
                                    out.write(frame);
                                    out.flush();
                                }
                            }
                        } catch (IOException io) {
                            break;
                        } finally {
                            codec.releaseOutputBuffer(idx, false);
                        }
                    }
                }
            } catch (Throwable t) {
                if (enabled) System.out.println("SHELL_VIDEO waiting/retry (" + t.getMessage() + ")");
            } finally {
                videoActive = false;
                videoSock = null;
                if (vd != null) { try { vd.release(); } catch (Throwable ignored) {} }
                if (codec != null) {
                    try { codec.stop(); } catch (Throwable ignored) {}
                    try { codec.release(); } catch (Throwable ignored) {}
                }
                if (inputSurface != null) { try { inputSurface.release(); } catch (Throwable ignored) {} }
                closeQuiet(s);
            }
            sleepMs(1500);
        }
    }

    private static AudioRecord createRemoteSubmixRecord() throws Exception {
        AudioAttributes.Builder ab = new AudioAttributes.Builder();
        Method setPreset = AudioAttributes.Builder.class.getMethod("setInternalCapturePreset", int.class);
        setPreset.invoke(ab, MediaRecorder.AudioSource.REMOTE_SUBMIX);
        AudioAttributes attrs = ab.build();

        AudioFormat fmt = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(AUDIO_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build();
        int minBuf = AudioRecord.getMinBufferSize(
                AUDIO_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);

        AudioRecord.Builder rb = new AudioRecord.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(Math.max(minBuf * 2, 8192));
        try {
            Method setCtx = AudioRecord.Builder.class.getMethod("setContext", Context.class);
            setCtx.invoke(rb, new ShellContext());
        } catch (NoSuchMethodException ignored) {}

        AudioRecord rec = rb.build();
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
            try { rec.release(); } catch (Throwable ignored) {}
            throw new IllegalStateException("AudioRecord state=" + rec.getState());
        }
        return rec;
    }

    private static void audioLoop() {
        byte[] buf = new byte[AUDIO_RATE * AUDIO_CHANNELS * 2 / 50]; // 20 ms PCM chunks
        while (enabled) {
            String host = hostIp;
            if (host == null || host.isEmpty()) {
                sleepMs(1000);
                continue;
            }
            Socket s = null;
            AudioRecord rec = null;
            boolean ok = false;
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(host, AUDIO_PORT), 1500);
                s.setTcpNoDelay(true);
                s.setSoTimeout(1500);
                audioSock = s;

                OutputStream out = s.getOutputStream();
                out.write(("GBAUD1 " + AUDIO_TOKEN + " " + AUDIO_RATE + " " + AUDIO_CHANNELS + "\n")
                        .getBytes("US-ASCII"));
                out.flush();

                InputStream in = s.getInputStream();
                byte[] ack = new byte[3];
                int got = 0;
                while (got < 3) {
                    int n = in.read(ack, got, 3 - got);
                    if (n < 0) break;
                    got += n;
                }
                if (got == 3 && ack[0] == 'O' && ack[1] == 'K' && ack[2] == '\n') {
                    ok = true;
                    rec = createRemoteSubmixRecord();
                    rec.startRecording();
                    audioLinked = true;
                    System.out.println("SHELL_AUDIO linked -> " + host + ":" + AUDIO_PORT);

                    while (enabled && !s.isClosed()) {
                        int n = rec.read(buf, 0, buf.length);
                        if (n < 0) {
                            System.out.println("SHELL_AUDIO AudioRecord.read -> " + n);
                            break;
                        }
                        if (n > 0) {
                            out.write(buf, 0, n);
                        }
                    }
                }
            } catch (Throwable t) {
                if (ok && enabled) System.out.println("SHELL_AUDIO stream ended: " + t.getMessage());
            } finally {
                audioLinked = false;
                audioSock = null;
                if (rec != null) {
                    try { rec.stop(); } catch (Throwable ignored) {}
                    try { rec.release(); } catch (Throwable ignored) {}
                }
                closeQuiet(s);
            }
            sleepMs(ok ? 800 : 1200);
        }
    }

    private static byte[] codecConfig(MediaFormat f) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        for (String key : new String[]{"csd-0", "csd-1"}) {
            ByteBuffer b = f.getByteBuffer(key);
            if (b == null) continue;
            byte[] t = new byte[b.remaining()];
            b.get(t);
            bo.write(t, 0, t.length);
        }
        return bo.toByteArray();
    }

    private static boolean isAnnexB(byte[] b) {
        return b.length >= 4 && b[0] == 0 && b[1] == 0 && b[2] == 0 && b[3] == 1;
    }

    private static byte[] avccToAnnexB(byte[] in) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(in.length + 32);
        int i = 0;
        while (i + 4 <= in.length) {
            int len = ((in[i] & 0xff) << 24) | ((in[i + 1] & 0xff) << 16)
                    | ((in[i + 2] & 0xff) << 8) | (in[i + 3] & 0xff);
            i += 4;
            if (len <= 0 || i + len > in.length) break;
            bo.write(0);
            bo.write(0);
            bo.write(0);
            bo.write(1);
            bo.write(in, i, len);
            i += len;
        }
        return bo.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        if (a == null || a.length == 0) return b;
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static void closeQuiet(Socket s) {
        if (s != null) {
            try { s.close(); } catch (Throwable ignored) {}
        }
    }

    private static void sleepMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
