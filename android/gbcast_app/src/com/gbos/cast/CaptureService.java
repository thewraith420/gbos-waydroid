package com.gbos.cast;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * Captures the phone screen with MediaProjection, encodes H.264, and pushes Annex-B framed video
 * to the laptop on a plain TCP socket — the same contract `phone_agent.sh`'s `screenrecord | nc`
 * used, so host_relay.py --listen accepts either without changes.
 *
 * Two deliberate choices:
 *  - Frames are dropped, never queued, when the socket is down: the encoder must keep draining or
 *    the whole capture stalls, and a stale backlog is worse than a gap (the host waits for a
 *    keyframe anyway).
 *  - Every keyframe is preceded by the codec's SPS/PPS, so a receiver that attaches mid-stream can
 *    start decoding without waiting for the next IDR.
 */
public class CaptureService extends Service {
    public static final String ACTION_START = "com.gbos.cast.START";
    public static final String EXTRA_RESULT = "result";
    /** Notification button / app switch: flip "mute this phone while casting" and apply it at once. */
    public static final String ACTION_TOGGLE_MUTE = "com.gbos.cast.TOGGLE_MUTE";
    public static final String ACTION_APPLY_MUTE = "com.gbos.cast.APPLY_MUTE";
    /** True while a cast is up (lets the UI know whether a saved volume is stale). */
    static volatile boolean ACTIVE;

    private static final String CHANNEL = "gbos_cast";
    private static final int NOTIF_ID = 41;
    static final String TAG = "GBOSCast";

    /**
     * Shared secret for the gesture channel. Both ends are ours, and the listener additionally
     * only accepts connections from the configured laptop address, so a stray device on the LAN
     * cannot drive the phone.
     */
    static final String INPUT_TOKEN = "gbos-cast-v1";

    /** Matches what the existing pipeline already negotiated with the GBOS decoder. */
    private static final int WIDTH = 1376;
    private static final int HEIGHT = 736;
    private static final int DPI = 160;
    private static final int FPS = 30;
    private static final int BITRATE = 6000000;
    private static final int IFRAME_SECONDS = 2;

    private volatile boolean running;
    private MediaProjection projection;
    private VirtualDisplay display;
    private MediaCodec codec;
    private Surface inputSurface;
    private Thread pump;
    private Socket socket;
    private OutputStream out;
    private ServerSocket inputSrv;
    private Thread inputThread;
    private PowerManager.WakeLock awake;
    private AudioStreamer audio;
    private boolean mutedByUs;

    static String host(Context c) {
        return c.getSharedPreferences("gbos", MODE_PRIVATE).getString("host", "");
    }

    static int port(Context c) {
        return c.getSharedPreferences("gbos", MODE_PRIVATE).getInt("port", 8098);
    }

    /** Gestures arrive on their own port; 8098 is the video push and must not be reused. */
    static int inputPort(Context c) {
        return c.getSharedPreferences("gbos", MODE_PRIVATE).getInt("inputPort", 8097);
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && (ACTION_TOGGLE_MUTE.equals(intent.getAction())
                || ACTION_APPLY_MUTE.equals(intent.getAction()))) {
            if (ACTIVE) {
                if (ACTION_TOGGLE_MUTE.equals(intent.getAction())) {
                    SharedPreferences sp = getSharedPreferences("gbos", MODE_PRIVATE);
                    sp.edit().putBoolean("muteLocal", !sp.getBoolean("muteLocal", true)).apply();
                }
                applyMute();
                refreshNotification();
                return START_NOT_STICKY;
            }
            // Not casting: nothing to mute, and a foreground-start was never promised for this intent.
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIF_ID, notification());
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        }
        // Capture is running, so any "tap to resume" prompt from the boot path is stale. This is
        // also what makes the boot flow silent when Android did allow the automatic start.
        try {
            NotificationManager bootNm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (bootNm != null) bootNm.cancel(BootReceiver.NOTIF_ID);
        } catch (Exception ignored) {
        }

        Intent data = intent.getParcelableExtra(EXTRA_RESULT);
        MediaProjectionManager mpm =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (data == null || mpm == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            projection = mpm.getMediaProjection(Activity.RESULT_OK, data);
        } catch (Exception e) {
            Log.e(TAG, "getMediaProjection failed", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        // Android 14+ refuses createVirtualDisplay() unless a callback is registered first
        // ("Must register a callback before starting capture"); it also gives us a clean stop
        // when the user revokes sharing from the system UI.
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.i(TAG, "projection stopped by the system");
                running = false;
            }
        }, new Handler(getMainLooper()));

        startInput();
        recoverVolume(this);                 // a previous run killed mid-cast may have left the volume at 0
        try {
            audio = new AudioStreamer(this, projection, () -> { applyMute(); refreshNotification(); });
            if (!audio.start()) audio = null;
        } catch (Exception e) {
            Log.w(TAG, "audio streamer failed to start", e);
            audio = null;
        }
        ACTIVE = true;
        applyMute();
        refreshNotification();
        // Send the phone's real app list + icons to the laptop for GBOS's Phone Hub drawer. Background
        // thread, retried until the laptop's relay is up; failure never affects the cast.
        AppSync.pushAsync(this);
        // MediaProjection mirrors the display, so a sleeping display mirrors as a black screen.
        // Without this the cast goes black a few seconds after the phone is left alone.
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            awake = pm.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "gbos:cast");
            awake.acquire();
            Log.i(TAG, "holding the screen awake while casting");
        } catch (Exception e) {
            Log.w(TAG, "could not hold the screen awake", e);
        }
        Log.i(TAG, "starting capture -> " + host(this) + ":" + port(this));

        running = true;
        pump = new Thread(this::pump, "gbos-cast");
        pump.start();
        return START_NOT_STICKY;
    }

    private void pump() {
        try {
            MediaFormat fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                    WIDTH, HEIGHT);
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
            fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_SECONDS);
            fmt.setInteger(MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = codec.createInputSurface();
            codec.start();

            display = projection.createVirtualDisplay("gbos-cast", WIDTH, HEIGHT, DPI,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, inputSurface, null, null);
            Log.i(TAG, "virtual display up " + WIDTH + "x" + HEIGHT);

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            byte[] csd = null;

            while (running) {
                int idx;
                try {
                    idx = codec.dequeueOutputBuffer(info, 10000);
                } catch (IllegalStateException e) {
                    break;
                }
                if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    csd = codecConfig(codec.getOutputFormat());
                } else if (idx >= 0) {
                    boolean isConfig =
                            (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                    if (!isConfig) {
                        ByteBuffer buf = codec.getOutputBuffer(idx);
                        if (buf != null && info.size > 0) {
                            buf.position(info.offset);
                            buf.limit(info.offset + info.size);
                            byte[] data = new byte[info.size];
                            buf.get(data);
                            // MEASURED on this Pixel: c2.android.avc.encoder already hands back
                            // Annex-B (00 00 00 01 …), not AVCC. Reading that start code as a
                            // 4-byte length silently produced zero-length frames, so detect the
                            // framing rather than assuming it.
                            byte[] nal = isAnnexB(data) ? data : avccToAnnexB(data);
                            boolean key = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                            byte[] out2 = key ? concat(csd, nal) : nal;
                            Log.d(TAG, "frame size=" + info.size + " key=" + key
                                    + " annexb=" + nal.length + " sent=" + out2.length
                                    + " head=" + hexHead(out2));
                            send(out2);
                        }
                    }
                    codec.releaseOutputBuffer(idx, false);
                }
            }
        } catch (Exception e) {
            // Nothing useful to do from a background thread; drop out and let teardown run.
            Log.e(TAG, "capture loop failed", e);
        } finally {
            teardown();
            stopSelf();
        }
    }

    /**
     * Gesture channel: the laptop connects here and sends "<token> tap X Y" lines, the same
     * protocol the shell agent used, so input_relay.py only needs a different address.
     */
    private void startInput() {
        try {
            inputSrv = new ServerSocket();
            inputSrv.setReuseAddress(true);
            inputSrv.bind(new InetSocketAddress(inputPort(this)));
        } catch (IOException e) {
            Log.e(TAG, "gesture listener could not bind port " + inputPort(this), e);
            inputSrv = null;
            return;
        }
        inputThread = new Thread(this::inputLoop, "gbos-input");
        inputThread.start();
        Log.i(TAG, "gesture listener on " + inputPort(this));
    }

    private void inputLoop() {
        String allowed = host(this);
        while (running && inputSrv != null && !inputSrv.isClosed()) {
            Socket s = null;
            try {
                s = inputSrv.accept();
                String from = s.getInetAddress().getHostAddress();
                if (!from.equals(allowed)) {
                    Log.w(TAG, "gesture connection refused from " + from);
                    s.close();
                    continue;
                }
                BufferedReader r = new BufferedReader(
                        new InputStreamReader(s.getInputStream(), "UTF-8"));
                String line;
                while ((line = r.readLine()) != null) handleGesture(line);
            } catch (IOException e) {
                if (running) Log.w(TAG, "gesture channel: " + e);
            } finally {
                try {
                    if (s != null) s.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void handleGesture(String line) {
        String[] p = line.trim().split("\\s+");
        if (p.length < 2 || !INPUT_TOKEN.equals(p[0])) return;
        CastAccessibilityService acc = CastAccessibilityService.get();
        if (acc == null) {
            Log.w(TAG, "gesture dropped — enable GBOS Cast under Settings > Accessibility");
            return;
        }
        try {
            switch (p[1]) {
                case "tap":
                    acc.tap(Float.parseFloat(p[2]), Float.parseFloat(p[3]));
                    break;
                case "swipe":
                    acc.swipe(Float.parseFloat(p[2]), Float.parseFloat(p[3]),
                            Float.parseFloat(p[4]), Float.parseFloat(p[5]),
                            p.length > 6 ? Long.parseLong(p[6]) : 150);
                    break;
                case "key":
                    // Only the global actions; arbitrary key injection needs a privileged app.
                    if ("KEYCODE_BACK".equals(p[2])) acc.global(android.accessibilityservice
                            .AccessibilityService.GLOBAL_ACTION_BACK);
                    else if ("KEYCODE_HOME".equals(p[2])) acc.global(android.accessibilityservice
                            .AccessibilityService.GLOBAL_ACTION_HOME);
                    else if ("KEYCODE_APP_SWITCH".equals(p[2])) acc.global(
                            android.accessibilityservice.AccessibilityService
                                    .GLOBAL_ACTION_RECENTS);
                    break;
                case "launch":
                    Intent li = getPackageManager().getLaunchIntentForPackage(p[2]);
                    if (li != null) {
                        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(li);
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "bad gesture: " + line);
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

    /** AVCC (4-byte big-endian lengths) -> Annex-B, for codecs that don't emit Annex-B. */
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

    private static String hexHead(byte[] b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(8, b.length); i++) sb.append(String.format("%02x", b[i]));
        return sb.toString();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        if (a == null || a.length == 0) return b;
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private void send(byte[] data) {
        if (out == null && !connect()) return;   // keep draining even with nowhere to send
        try {
            out.write(data);
            out.flush();
        } catch (Exception e) {
            closeSocket();
        }
    }

    private boolean connect() {
        String h = host(this);
        int p = port(this);
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(h, p), 3000);
            s.setTcpNoDelay(true);
            socket = s;
            out = s.getOutputStream();
            Log.i(TAG, "connected to " + h + ":" + p);
            return true;
        } catch (Exception e) {
            closeSocket();
            return false;
        }
    }

    private void closeSocket() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        socket = null;
        out = null;
    }

    private void teardown() {
        running = false;
        ACTIVE = false;
        CastTileService.refreshTiles(this);
        try {
            if (audio != null) audio.stop();
        } catch (Exception ignored) {
        }
        audio = null;
        restoreVolume();
        try {
            if (display != null) display.release();
        } catch (Exception ignored) {
        }
        try {
            if (codec != null) codec.stop();
        } catch (Exception ignored) {
        }
        try {
            if (codec != null) codec.release();
        } catch (Exception ignored) {
        }
        try {
            if (projection != null) projection.stop();
        } catch (Exception ignored) {
        }
        try {
            if (awake != null && awake.isHeld()) awake.release();
        } catch (Exception ignored) {
        }
        awake = null;
        closeSocket();
        try {
            if (inputSrv != null) inputSrv.close();
        } catch (IOException ignored) {
        }
        inputSrv = null;
        display = null;
        codec = null;
        projection = null;
    }

    // ---- local mute -------------------------------------------------------------------------------------
    // While the sound plays on the Googlebook the phone's own speaker is just noise, so the music stream goes
    // to 0 for the length of the cast. Only done when audio is really being streamed (never mute the phone
    // for a cast whose audio capture failed to start). The previous level is persisted FIRST, so a killed
    // service cannot leave the phone silent: recoverVolume() puts it back at the next start or app open.
    // If you raise the volume yourself during a cast, that wins and is left alone.
    private AudioManager am() {
        return (AudioManager) getSystemService(AUDIO_SERVICE);
    }

    private synchronized void applyMute() {
        SharedPreferences sp = getSharedPreferences("gbos", MODE_PRIVATE);
        // Only while the laptop is really playing it (a cast window is open): with no window the phone keeps its own sound.
        boolean want = sp.getBoolean("muteLocal", true) && audio != null && audio.isLinked() && ACTIVE;
        try {
            if (want && !mutedByUs) {
                int cur = am().getStreamVolume(AudioManager.STREAM_MUSIC);
                if (cur > 0) {
                    sp.edit().putInt("savedMusicVol", cur).commit();
                    am().setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                    Log.i(TAG, "phone music volume " + cur + " -> 0 while casting");
                }
                mutedByUs = true;
            } else if (!want && mutedByUs) {
                restoreVolume();
            }
        } catch (Exception e) {
            Log.w(TAG, "mute change failed", e);
        }
    }

    private synchronized void restoreVolume() {
        if (mutedByUs) {
            mutedByUs = false;
            recoverVolume(this);
        }
    }

    /** Put back the level saved when muting, unless the user already changed it. Safe to call any time. */
    static void recoverVolume(Context c) {
        SharedPreferences sp = c.getSharedPreferences("gbos", MODE_PRIVATE);
        int saved = sp.getInt("savedMusicVol", -1);
        if (saved < 0) return;
        try {
            AudioManager a = (AudioManager) c.getSystemService(AUDIO_SERVICE);
            if (a.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                a.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0);
                Log.i(TAG, "phone music volume restored to " + saved);
            }
        } catch (Exception e) {
            Log.w(TAG, "volume restore failed", e);
        }
        sp.edit().remove("savedMusicVol").commit();
    }

    private void refreshNotification() {
        CastTileService.refreshTiles(this);
        try {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID, notification());
        } catch (Exception ignored) {
        }
    }

    private Notification notification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Casting",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Screen capture to the Googlebook");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean muting = getSharedPreferences("gbos", MODE_PRIVATE).getBoolean("muteLocal", true);
        Intent tog = new Intent(this, CaptureService.class).setAction(ACTION_TOGGLE_MUTE);
        PendingIntent togPi = PendingIntent.getService(this, 1, tog,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("Casting to the Googlebook")
                .setContentText(host(this) + ":" + port(this)
                        + (audio != null && audio.isLinked() ? (muting ? " · sound on the Googlebook" : " · sound on both") : ""))
                .setSmallIcon(R.drawable.ic_stat)
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat),
                        muting ? "Unmute phone" : "Mute phone", togPi).build())
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        running = false;
        teardown();
        super.onDestroy();
    }
}
