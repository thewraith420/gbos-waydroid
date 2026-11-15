package com.gbos.bthal;

import android.util.Log;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** TCP link to the host helper (gb-bt-bridge.py). Frames: 2-byte big-endian length, then one H4 packet (type byte + payload). */
final class Bridge {
    private static final String T = "gb-bthal";
    interface Listener { void onPacket(int type, byte[] payload); void onClosed(); }

    private final Listener mListener;
    private Socket mSock;
    private DataOutputStream mOut;
    private volatile boolean mOpen;
    private volatile CountDownLatch mHello;
    private volatile boolean mHelloOk;

    Bridge(Listener l) { mListener = l; }

    /** Connects to the host helper; the helper takes the adapter exclusively once we connect. Returns false on failure. */
    synchronized boolean open(String host, int port) {
        close();
        try {
            Socket s = new Socket();
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(host, port), 5000);
            mSock = s;
            mOut = new DataOutputStream(s.getOutputStream());
            mOpen = true;
            final DataInputStream in = new DataInputStream(s.getInputStream());
            Thread t = new Thread(() -> readLoop(s, in), "bthal-reader");
            t.setDaemon(true);
            t.start();
            Log.i(T, "bridge connected to " + host + ":" + port + ", waiting for the host to take the adapter");
            mHello = new CountDownLatch(1); mHelloOk = false;
            send(0x00, new byte[0]);                     // hello: the host helper takes the adapter now
            if (!mHello.await(20, TimeUnit.SECONDS) || !mHelloOk) {
                Log.w(T, "host helper did not hand over the adapter");
                close();
                return false;
            }
            Log.i(T, "host adapter ready");
            return true;
        } catch (IOException | InterruptedException e) {
            Log.w(T, "bridge connect failed: " + e);
            mOpen = false;
            return false;
        }
    }

    private void readLoop(Socket s, DataInputStream in) {
        try {
            while (true) {
                int len = in.readUnsignedShort();
                if (len < 1) continue;
                byte[] pkt = new byte[len];
                in.readFully(pkt);
                int type = pkt[0] & 0xff;
                if (type == 0x00 || type == 0xff) {          // handshake reply from the host helper
                    mHelloOk = (type == 0x00);
                    CountDownLatch l = mHello; if (l != null) l.countDown();
                    continue;
                }
                byte[] payload = new byte[len - 1];
                System.arraycopy(pkt, 1, payload, 0, len - 1);
                mListener.onPacket(type, payload);
            }
        } catch (IOException e) {
            Log.w(T, "bridge read ended: " + e);
        } finally {
            boolean wasCurrent;
            synchronized (this) { wasCurrent = (s == mSock); if (wasCurrent) { mOpen = false; } }
            try { s.close(); } catch (IOException ignored) {}
            if (wasCurrent) mListener.onClosed();
        }
    }

    synchronized boolean send(int type, byte[] payload) {
        if (!mOpen || mOut == null) return false;
        try {
            mOut.writeShort(payload.length + 1);
            mOut.writeByte(type);
            mOut.write(payload);
            mOut.flush();
            return true;
        } catch (IOException e) {
            Log.w(T, "bridge write failed: " + e);
            mOpen = false;
            return false;
        }
    }

    synchronized void close() {
        mOpen = false;
        if (mSock != null) { try { mSock.close(); } catch (IOException ignored) {} mSock = null; mOut = null; }
    }
}
