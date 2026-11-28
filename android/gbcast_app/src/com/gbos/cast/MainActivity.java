package com.gbos.cast;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * GBOS Cast — the Pixel-side capture app.
 *
 * Why this exists: AppCast used to pull the phone's screen with `adb exec-out screenrecord`, which
 * capped each run at 180 s and forced wireless debugging to stay enabled. This does the same job
 * properly: MediaProjection -> MediaCodec (H.264) -> TCP straight to the laptop, so there is no
 * adb in the loop and no time limit. The host relay already accepts exactly this (host_relay.py
 * --listen); the Android capture stack decides when frames exist, so a static screen stays static.
 */
public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 1001;
    /** Host-side `phone_rearm.py`. Only used by the Re-arm button. */
    private static final int REARM_PORT = 8091;
    /** Host-side `discover_host.py`: answers a broadcast probe with the video port to use. */
    private static final int DISCOVER_PORT = 8096;
    /** Set by BootReceiver: start casting, then get out of the way. */
    static final String EXTRA_AUTOSTART = "autostart";
    private TextView status, inputStatus, connStatus;
    private EditText hostBox, portBox;
    private android.widget.CheckBox autoBox;
    private boolean autoStarted;

    private int dp(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private TextView createSectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setTextColor(getColor(R.color.m3_primary));
        tv.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(22), dp(4), dp(8));
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(getDrawable(R.drawable.card_surface));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(lp);
        return card;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(getColor(R.color.m3_surface));

        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(20), dp(44), dp(20), dp(36));

        TextView title = new TextView(this);
        title.setText("GBOS Cast");
        title.setTextSize(28);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setTextColor(getColor(R.color.m3_on_surface));

        TextView subtitle = new TextView(this);
        subtitle.setText("Pixel screen sharing for Googlebook");
        subtitle.setTextSize(14);
        subtitle.setTextColor(getColor(R.color.m3_on_surface_variant));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(2);
        subtitle.setLayoutParams(subLp);

        // Status Card
        LinearLayout statusCard = createCard();
        LinearLayout.LayoutParams statusCardLp = (LinearLayout.LayoutParams) statusCard.getLayoutParams();
        statusCardLp.topMargin = dp(20);
        statusCard.setLayoutParams(statusCardLp);

        TextView statusLabel = new TextView(this);
        statusLabel.setText("STATUS");
        statusLabel.setTextSize(11);
        statusLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        statusLabel.setTextColor(getColor(R.color.m3_primary));
        statusLabel.setLetterSpacing(0.08f);

        status = new TextView(this);
        status.setText("Idle. Start casting, then pick the screen (or a single app) to share.");
        status.setTextSize(14);
        status.setTextColor(getColor(R.color.m3_on_surface));
        status.setLineSpacing(dp(2), 1.15f);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(6);
        status.setLayoutParams(statusLp);

        statusCard.addView(statusLabel);
        statusCard.addView(status);

        // Primary Action Row: Start / Stop
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionRowLp.topMargin = dp(14);
        actionRow.setLayoutParams(actionRowLp);

        Button start = new Button(this);
        start.setText("Start casting");
        start.setTextSize(15);
        start.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        start.setTextColor(getColor(R.color.m3_on_primary));
        start.setBackground(getDrawable(R.drawable.btn_primary));
        start.setStateListAnimator(null);
        start.setOnClickListener(v -> begin());
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                0, dp(50), 1.0f);
        start.setLayoutParams(startLp);

        Button stop = new Button(this);
        stop.setText("Stop");
        stop.setTextSize(15);
        stop.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        stop.setTextColor(getColor(R.color.m3_on_surface));
        stop.setBackground(getDrawable(R.drawable.btn_outlined));
        stop.setStateListAnimator(null);
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
            CastTileService.SHELL_ACTIVE = false;
            CastTileService.refreshTiles(this);
            status.setText("Stopping \u2026");
            final String h = hostBox != null && !hostBox.getText().toString().trim().isEmpty()
                    ? hostBox.getText().toString().trim() : CaptureService.host(this);
            new Thread(() -> {
                askRearm(h, "CAST_STOP", 3000);
                runOnUiThread(() -> status.setText("Stopped."));
            }).start();
        });
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(
                dp(96), dp(50));
        stopLp.leftMargin = dp(10);
        stop.setLayoutParams(stopLp);

        actionRow.addView(start);
        actionRow.addView(stop);

        // Connection Card
        LinearLayout connCard = createCard();

        LinearLayout hostRow = new LinearLayout(this);
        hostRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams hostRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hostRow.setLayoutParams(hostRowLp);

        hostBox = new EditText(this);
        hostBox.setHint("Laptop IP");
        hostBox.setInputType(InputType.TYPE_CLASS_TEXT);
        hostBox.setSingleLine(true);
        hostBox.setText(CaptureService.host(this));
        hostBox.setTextSize(15);
        hostBox.setTextColor(getColor(R.color.m3_on_surface));
        hostBox.setHintTextColor(getColor(R.color.m3_on_surface_variant));
        hostBox.setBackground(getDrawable(R.drawable.edit_text_bg));
        LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(
                0, dp(50), 2.8f);
        hostBox.setLayoutParams(hostLp);

        portBox = new EditText(this);
        portBox.setHint("Port");
        portBox.setInputType(InputType.TYPE_CLASS_NUMBER);
        portBox.setSingleLine(true);
        portBox.setText(String.valueOf(CaptureService.port(this)));
        portBox.setTextSize(15);
        portBox.setTextColor(getColor(R.color.m3_on_surface));
        portBox.setHintTextColor(getColor(R.color.m3_on_surface_variant));
        portBox.setBackground(getDrawable(R.drawable.edit_text_bg));
        LinearLayout.LayoutParams portLp = new LinearLayout.LayoutParams(
                0, dp(50), 1.2f);
        portLp.leftMargin = dp(8);
        portBox.setLayoutParams(portLp);

        hostRow.addView(hostBox);
        hostRow.addView(portBox);

        Button find = new Button(this);
        find.setText("Find laptop on this network");
        find.setTextSize(14);
        find.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        find.setTextColor(getColor(R.color.m3_on_secondary_container));
        find.setBackground(getDrawable(R.drawable.btn_tonal));
        find.setStateListAnimator(null);
        find.setOnClickListener(v -> findHost());
        LinearLayout.LayoutParams findLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        findLp.topMargin = dp(12);
        find.setLayoutParams(findLp);

        connStatus = new TextView(this);
        connStatus.setTextSize(13);
        connStatus.setTextColor(getColor(R.color.m3_on_surface_variant));
        connStatus.setLineSpacing(dp(2), 1.15f);
        LinearLayout.LayoutParams connStatusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        connStatusLp.topMargin = dp(8);
        connStatus.setLayoutParams(connStatusLp);
        connStatus.setVisibility(View.GONE);

        connCard.addView(hostRow);
        connCard.addView(find);
        connCard.addView(connStatus);

        // Touch Forwarding Card
        LinearLayout touchCard = createCard();

        inputStatus = new TextView(this);
        inputStatus.setText("Touch forwarding: unknown. Tap Re-arm after a reboot.");
        inputStatus.setTextSize(13);
        inputStatus.setTextColor(getColor(R.color.m3_on_surface_variant));
        inputStatus.setLineSpacing(dp(2), 1.15f);

        Button rearm = new Button(this);
        rearm.setText("Re-arm touch forwarding (after reboot)");
        rearm.setTextSize(14);
        rearm.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        rearm.setTextColor(getColor(R.color.m3_on_secondary_container));
        rearm.setBackground(getDrawable(R.drawable.btn_tonal));
        rearm.setStateListAnimator(null);
        rearm.setOnClickListener(v -> rearm());
        LinearLayout.LayoutParams rearmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        rearmLp.topMargin = dp(12);
        rearm.setLayoutParams(rearmLp);

        Button enableInput = new Button(this);
        enableInput.setText("Accessibility settings (blocked by Advanced Protection)");
        enableInput.setTextSize(13);
        enableInput.setTextColor(getColor(R.color.m3_on_surface_variant));
        enableInput.setBackground(getDrawable(R.drawable.btn_outlined));
        enableInput.setStateListAnimator(null);
        enableInput.setMinHeight(dp(48));
        enableInput.setPadding(dp(16), dp(10), dp(16), dp(10));
        enableInput.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        LinearLayout.LayoutParams enableLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        enableLp.topMargin = dp(8);
        enableInput.setLayoutParams(enableLp);

        touchCard.addView(inputStatus);
        touchCard.addView(rearm);
        touchCard.addView(enableInput);

        // Preferences Card
        LinearLayout prefCard = createCard();

        autoBox = new android.widget.CheckBox(this);
        autoBox.setText("Start casting automatically after a reboot");
        autoBox.setTextSize(14);
        autoBox.setTextColor(getColor(R.color.m3_on_surface));
        autoBox.setButtonTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.m3_primary)));
        autoBox.setChecked(getSharedPreferences("gbos", MODE_PRIVATE).getBoolean("autoStart", true));
        autoBox.setOnCheckedChangeListener((v, on) ->
                getSharedPreferences("gbos", MODE_PRIVATE).edit().putBoolean("autoStart", on).apply());
        prefCard.addView(autoBox);

        android.widget.CheckBox muteBox = new android.widget.CheckBox(this);
        muteBox.setText("Mute this phone's sound while casting (it plays on the Googlebook instead)");
        muteBox.setTextSize(14);
        muteBox.setTextColor(getColor(R.color.m3_on_surface));
        muteBox.setButtonTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.m3_primary)));
        muteBox.setChecked(getSharedPreferences("gbos", MODE_PRIVATE).getBoolean("muteLocal", true));
        muteBox.setOnCheckedChangeListener((v, on) -> {
            getSharedPreferences("gbos", MODE_PRIVATE).edit().putBoolean("muteLocal", on).apply();
            if (CaptureService.ACTIVE) {          // apply to a cast that is already running
                startService(new Intent(this, CaptureService.class).setAction(CaptureService.ACTION_APPLY_MUTE));
            }
        });
        prefCard.addView(muteBox);

        // Assemble hierarchy
        ll.addView(title);
        ll.addView(subtitle);
        ll.addView(statusCard);
        ll.addView(actionRow);
        ll.addView(createSectionHeader("CONNECTION"));
        ll.addView(connCard);
        ll.addView(createSectionHeader("TOUCH FORWARDING"));
        ll.addView(touchCard);
        ll.addView(createSectionHeader("PREFERENCES"));
        ll.addView(prefCard);

        sv.addView(ll);
        setContentView(sv);

        // Started by BootReceiver after a reboot: run the same consent path as the button, then
        // drop to the background so the phone is usable and the cast mirrors whatever is opened.
        autoStarted = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_AUTOSTART, false);
        if (autoStarted) {
            status.setText("Reconnecting the cast after reboot \u2026");
            new android.os.Handler(getMainLooper()).postDelayed(this::begin, 800);
        }
    }

    private Integer probeLocalAgent() {
        int[] ports = new int[]{8099, 8110, 8111};
        for (int p : ports) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", p), 250);
                return p;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Re-arm the shell-uid gesture agent after a phone reboot.
     *
     * If the agent is already active and armed, reports that directly without churning
     * wireless debugging or displaying a false failure.
     */
    private void rearm() {
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            inputStatus.setText("Re-arm needs this once, over adb:\n\n"
                    + "adb shell pm grant com.gbos.cast "
                    + "android.permission.WRITE_SECURE_SETTINGS");
            return;
        }

        String rawHost = hostBox != null ? hostBox.getText().toString().trim() : "";
        final String host = !rawHost.isEmpty() ? rawHost : CaptureService.host(this);

        inputStatus.setText("Checking touch forwarding status \u2026");
        new Thread(() -> {
            Integer localPort = probeLocalAgent();
            if (localPort != null) {
                runOnUiThread(() -> inputStatus.setText(
                        "Touch forwarding: ALREADY ACTIVE \u2014 agent is armed and running on port "
                                + localPort + "."));
                try { ask(host, "STATUS"); } catch (Exception ignored) { }
                return;
            }

            runOnUiThread(() -> inputStatus.setText("Re-arming via " + host + ":" + REARM_PORT + " \u2026"));
            String reply;
            boolean weEnabled = false;
            try {
                reply = ask(host, "STATUS");
                if (reply.startsWith("ALREADY_ARMED") || reply.startsWith("OK")) {
                    final String r = reply;
                    runOnUiThread(() -> {
                        String p = r.contains(" ") ? r.substring(r.indexOf(' ')).trim() : "";
                        inputStatus.setText("Touch forwarding: ALREADY ACTIVE \u2014 agent is armed on port " + p + ".");
                    });
                    return;
                }

                boolean alreadyOn = Settings.Global.getInt(getContentResolver(),
                        "adb_wifi_enabled", 0) == 1;
                if (!alreadyOn) {
                    Settings.Global.putInt(getContentResolver(), "adb_wifi_enabled", 1);
                    weEnabled = true;
                    Thread.sleep(1500);     // let adbd bind before the laptop scans for it
                }
                reply = ask(host, "REARM");
                if (weEnabled && !reply.startsWith("OK") && !reply.startsWith("ALREADY_ARMED")) {
                    Settings.Global.putInt(getContentResolver(), "adb_wifi_enabled", 0);
                    reply += " (wireless debugging switched back off)";
                }
            } catch (Exception e) {
                if (weEnabled) {
                    try { Settings.Global.putInt(getContentResolver(), "adb_wifi_enabled", 0); }
                    catch (Exception ignored) { }
                }
                reply = "ERR " + e.getMessage();
            }
            final String r = reply;
            runOnUiThread(() -> {
                if (r.startsWith("ALREADY_ARMED")) {
                    String p = r.substring(13).trim();
                    inputStatus.setText("Touch forwarding: ALREADY ACTIVE \u2014 agent is armed on port " + p + ".");
                } else if (r.startsWith("OK")) {
                    String p = r.substring(2).trim();
                    inputStatus.setText("Touch forwarding: ON \u2014 newly armed on port " + p + ".");
                } else {
                    inputStatus.setText("Re-arm status: " + r);
                }
            });
        }).start();
    }

    /**
     * Fill in the laptop's address by asking the network instead of the user.
     *
     * The app broadcasts a probe on UDP 8096; `discover_host.py` on the laptop answers with the
     * port to push video to, and the reply's source address IS the laptop's address. That removes
     * the hand-typed IP and survives DHCP moving it. Runs on a background thread; the caller
     * usually follows up with begin().
     */
    private void findHost() {
        connStatus.setVisibility(View.VISIBLE);
        connStatus.setText("Looking for the laptop on this network \u2026");
        new Thread(() -> {
            String[] hp = discover();
            runOnUiThread(() -> {
                if (hp == null) {
                    connStatus.setText("No laptop found. Is the App Cast session running? "
                            + "You can still type its address.");
                    return;
                }
                hostBox.setText(hp[0]);
                portBox.setText(hp[1]);
                saveTarget(hp[0], parseInt(hp[1], CaptureService.port(MainActivity.this)));
                connStatus.setText("Found laptop at " + hp[0] + ":" + hp[1]);
            });
        }).start();
    }

    /** Broadcast/sweep probe; returns {ip, port} or null. Blocks for at most ~2 s. */
    private String[] discover() {
        DatagramSocket s = null;
        android.net.wifi.WifiManager.MulticastLock mlock = null;
        try {
            try {
                android.net.wifi.WifiManager wm =
                        (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wm != null) {
                    mlock = wm.createMulticastLock("gbcast_discover");
                    mlock.setReferenceCounted(false);
                    mlock.acquire();
                    android.util.Log.i("GBOSCast", "discover: multicast lock acquired");
                }
            } catch (Throwable t) {
                android.util.Log.w("GBOSCast", "discover: multicast lock: " + t);
            }

            s = new DatagramSocket();
            s.setBroadcast(true);
            s.setSoTimeout(500);

            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network wifiNet = null;
            try {
                for (Network n : cm.getAllNetworks()) {
                    NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                    if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        wifiNet = n;
                        n.bindSocket(s);
                        android.util.Log.i("GBOSCast", "discover: probe bound to wifi net " + n);
                        break;
                    }
                }
            } catch (Exception e) {
                android.util.Log.i("GBOSCast", "discover: bindSocket failed: " + e);
            }

            byte[] probe = "GBOSCAST?\n".getBytes(StandardCharsets.UTF_8);
            java.util.List<InetAddress> targets = new java.util.ArrayList<>();
            targets.add(InetAddress.getByName("255.255.255.255"));

            // Compute directed subnet broadcast and default gateway from LinkProperties.
            if (cm != null && wifiNet != null) {
                try {
                    android.net.LinkProperties lp = cm.getLinkProperties(wifiNet);
                    if (lp != null) {
                        for (android.net.LinkAddress la : lp.getLinkAddresses()) {
                            InetAddress a = la.getAddress();
                            if (a instanceof java.net.Inet4Address) {
                                byte[] ip = a.getAddress();
                                int prefix = la.getPrefixLength();
                                if (prefix >= 8 && prefix <= 30) {
                                    int mask = (int) (0xffffffffL << (32 - prefix));
                                    byte[] bcast = new byte[4];
                                    for (int i = 0; i < 4; i++) {
                                        int bmask = (mask >> (24 - 8 * i)) & 0xff;
                                        bcast[i] = (byte) ((ip[i] & bmask) | (~bmask & 0xff));
                                    }
                                    try { targets.add(InetAddress.getByAddress(bcast)); } catch (Exception ignored) { }
                                }
                                // For standard /24 LAN subnets, add a fast unicast sweep so discovery
                                // succeeds even if the Wi-Fi AP drops or isolates broadcast packets.
                                if (prefix == 24) {
                                    for (int h = 1; h < 255; h++) {
                                        if (h == (ip[3] & 0xff)) continue;
                                        try {
                                            targets.add(InetAddress.getByAddress(new byte[]{ip[0], ip[1], ip[2], (byte) h}));
                                        } catch (Exception ignored) { }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.i("GBOSCast", "discover: link properties: " + e);
                }
            }

            try {
                for (java.net.NetworkInterface nif : java.util.Collections.list(
                        java.net.NetworkInterface.getNetworkInterfaces())) {
                    if (!nif.isUp() || nif.isLoopback()) continue;
                    for (java.net.InterfaceAddress ia : nif.getInterfaceAddresses()) {
                        InetAddress b = ia.getBroadcast();
                        if (b != null && !targets.contains(b)) targets.add(b);
                    }
                }
            } catch (Exception ignored) { }

            android.util.Log.i("GBOSCast", "discover: sending probe to " + targets.size() + " targets");
            for (InetAddress t : targets) {
                try {
                    s.send(new DatagramPacket(probe, probe.length, t, DISCOVER_PORT));
                } catch (IOException ignored) { }
            }

            long deadline = System.currentTimeMillis() + 2500;
            byte[] buf = new byte[256];
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try {
                    s.receive(p);
                } catch (java.net.SocketTimeoutException te) {
                    continue;
                }
                String reply = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim();
                android.util.Log.i("GBOSCast", "discover: received reply: " + reply + " from " + p.getAddress());
                if (!reply.toUpperCase().startsWith("GBOSCAST")) continue;
                String[] parts = reply.split("\\s+");
                String port = parts.length > 1 ? parts[1] : String.valueOf(CaptureService.port(this));
                return new String[]{p.getAddress().getHostAddress(), port};
            }
        } catch (Exception e) {
            android.util.Log.e("GBOSCast", "discover: exception", e);
        } finally {
            if (s != null) s.close();
            if (mlock != null && mlock.isHeld()) {
                try { mlock.release(); } catch (Exception ignored) { }
            }
        }
        return null;
    }

    private void saveTarget(String host, int port) {
        getSharedPreferences("gbos", MODE_PRIVATE).edit()
                .putString("host", host).putInt("port", port).apply();
    }

    /** One line request/response with the laptop's re-arm server (with explicit timeout). */
    static String askRearm(String host, String msg, int timeoutMs) {
        if (host == null || host.isEmpty()) return "ERR no-host";
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, REARM_PORT), 3000);
            s.setSoTimeout(timeoutMs);
            s.getOutputStream().write((msg + "\n").getBytes("UTF-8"));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String line = in.readLine();
            return line == null ? "ERR no-reply" : line.trim();
        } catch (IOException e) {
            return "ERR " + e.getMessage();
        } finally {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    /** One line request/response with the laptop's re-arm server. */
    private String ask(String host, String msg) throws IOException {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, REARM_PORT), 5000);
            // Room for a port scan plus pushing and starting the agent on the far side.
            s.setSoTimeout(90000);
            s.getOutputStream().write((msg + "\n").getBytes("UTF-8"));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String line = in.readLine();
            return line == null ? "ERR no-reply" : line.trim();
        } finally {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A cast killed mid-way would leave the music volume at 0 with the old level saved: put it back.
        if (!CaptureService.ACTIVE) CaptureService.recoverVolume(this);
        // Playback capture (the audio sent to the Googlebook) needs the RECORD_AUDIO permission.
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 77);
        }
        final String host = hostBox != null && !hostBox.getText().toString().trim().isEmpty()
                ? hostBox.getText().toString().trim() : CaptureService.host(this);
        new Thread(() -> {
            String castRep = askRearm(host, "CAST_STATUS", 2000);
            boolean shellOn = castRep.startsWith("SHELL_CAST") && castRep.contains("enabled=true");
            CastTileService.SHELL_ACTIVE = shellOn;
            CastTileService.refreshTiles(MainActivity.this);
            Integer p = probeLocalAgent();
            runOnUiThread(() -> {
                if (!autoStarted && !CaptureService.ACTIVE) {
                    if (shellOn) {
                        status.setText("Casting via Shell agent (no red pill).\n"
                                + "Audio routes automatically while a cast window is open.");
                    } else {
                        status.setText("Idle. Start casting to mirror without the red pill.");
                    }
                }
                if (p != null) {
                    inputStatus.setText("Touch forwarding: ACTIVE (armed on port " + p + ")");
                } else if (inputEnabled()) {
                    inputStatus.setText("Touch forwarding: ON via accessibility.");
                } else {
                    inputStatus.setText("Touch forwarding: idle. Tap Re-arm after a reboot.");
                }
            });
        }).start();
    }

    private boolean inputEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String me = getPackageName() + "/" + CastAccessibilityService.class.getName();
        for (String part : enabled.split(":")) {
            if (part.equalsIgnoreCase(me)) return true;
        }
        return false;
    }

    private void finishShellCastStart() {
        CastTileService.SHELL_ACTIVE = true;
        CastTileService.refreshTiles(this);
        AppSync.pushAsync(this);
        status.setText("Casting via Shell agent (no red pill).\n"
                + "This screen can be locked; audio follows open cast windows.");
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(BootReceiver.NOTIF_ID);
        if (autoStarted) {
            autoStarted = false;
            moveTaskToBack(true);
        }
    }

    private void begin() {
        final String host = hostBox.getText().toString().trim();
        final int port = parseInt(portBox.getText().toString().trim(), CaptureService.port(this));
        saveTarget(host, port);
        // Stop any legacy MediaProjection capture first so it never fights ShellCast for :8098.
        stopService(new Intent(this, CaptureService.class));
        // If what we have does not answer, ask the network before opening the consent dialog:
        // that is what makes "start casting after a reboot" survive the laptop's DHCP lease
        // changing while the phone was asleep.
        //
        // MUST NOT run on the main thread: both the reachability probe and the broadcast are
        // socket calls, and Android kills the app with NetworkOnMainThreadException otherwise
        // (measured: pressing Start casting closed the app outright).
        status.setText("Checking " + host + ":" + port + " \u2026");
        new Thread(() -> {
            String[] hp = hostAnswers(host, port) ? null : discover();
            final String targetHost = (hp != null) ? hp[0] : host;
            final String targetPort = (hp != null) ? hp[1] : String.valueOf(port);
            if (hp != null) {
                saveTarget(targetHost, parseInt(targetPort, port));
                runOnUiThread(() -> {
                    hostBox.setText(targetHost);
                    portBox.setText(targetPort);
                    status.setText("Found the laptop at " + targetHost + ":" + targetPort + " \u2026");
                });
            }

            String castRep = askRearm(targetHost, "CAST_START", 4000);
            if (castRep.startsWith("SHELL_CAST")) {
                runOnUiThread(this::finishShellCastStart);
                return;
            }

            if ("NO_SHELL_CAST".equals(castRep)
                    && checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                            == PackageManager.PERMISSION_GRANTED) {
                boolean weEnabled = false;
                try {
                    boolean alreadyOn = Settings.Global.getInt(
                            getContentResolver(), "adb_wifi_enabled", 0) == 1;
                    if (!alreadyOn) {
                        Settings.Global.putInt(getContentResolver(), "adb_wifi_enabled", 1);
                        weEnabled = true;
                        Thread.sleep(1500);
                    }
                    String rearmRep = askRearm(targetHost, "REARM", 90000);
                    if (rearmRep.startsWith("OK") || rearmRep.startsWith("ALREADY_ARMED")) {
                        String retryCast = askRearm(targetHost, "CAST_START", 4000);
                        if (retryCast.startsWith("SHELL_CAST")) {
                            runOnUiThread(this::finishShellCastStart);
                            return;
                        }
                    } else if (weEnabled) {
                        Settings.Global.putInt(getContentResolver(), "adb_wifi_enabled", 0);
                        weEnabled = false;
                    }
                } catch (Exception ignored) {
                    if (weEnabled) {
                        try {
                            Settings.Global.putInt(getContentResolver(), "adb_wifi_enabled", 0);
                        } catch (Exception ignored2) { }
                    }
                }
            }

            runOnUiThread(this::startConsent);
        }).start();
    }

    /** The consent dialog + service start; UI thread only. */
    private void startConsent() {
        MediaProjectionManager mpm =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_CAPTURE) return;
        if (res != RESULT_OK || data == null) {
            status.setText("Capture permission declined.");
            return;
        }
        Intent i = new Intent(this, CaptureService.class);
        i.setAction(CaptureService.ACTION_START);
        i.putExtra(CaptureService.EXTRA_RESULT, data);
        startForegroundService(i);
        status.setText("Casting to " + CaptureService.host(this) + ":" + CaptureService.port(this)
                + ".\nThis screen can be locked; the cast keeps running.");
        // Clear the "tap to resume" prompt from BootReceiver: capture is running now.
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(BootReceiver.NOTIF_ID);
        if (autoStarted) {
            // Boot path only: get out of the way, otherwise the mirror is just this app's UI.
            autoStarted = false;
            moveTaskToBack(true);
        }
    }

    /** Cheap reachability probe of the configured host: does the relay port accept a connection? */
    private boolean hostAnswers(String host, int port) {
        if (host == null || host.isEmpty()) return false;
        try {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(host, port), 700);
                return true;
            } finally {
                s.close();
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }
}
