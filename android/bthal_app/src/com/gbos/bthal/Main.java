package com.gbos.bthal;

import android.hidl.base.V1_0.IBase;
import android.os.HwBinder;
import android.os.HwParcel;
import android.os.IHwBinder;
import android.os.RemoteException;
import android.util.Log;
import java.util.ArrayList;

/**
 * Bluetooth HCI HAL (android.hardware.bluetooth@1.1::IBluetoothHci/default) written in Java for the Waydroid container.
 * The container cannot open AF_BLUETOOTH sockets itself (not in the initial net namespace), so HCI packets are forwarded over TCP
 * to a root helper on the host (gb-bt-bridge.py) that owns the adapter via the kernel HCI user channel.
 * Run:  CLASSPATH=<apk>:/system/framework/android.hidl.base-V1.0-java.jar:/system/framework/android.hidl.manager-V1.0-java.jar
 *       app_process /system/bin com.gbos.bthal.Main
 */
public class Main {
    private static final String T = "gb-bthal";
    static final String D10 = "android.hardware.bluetooth@1.0::IBluetoothHci";
    static final String D11 = "android.hardware.bluetooth@1.1::IBluetoothHci";
    static final String C10 = "android.hardware.bluetooth@1.0::IBluetoothHciCallbacks";
    static final String C11 = "android.hardware.bluetooth@1.1::IBluetoothHciCallbacks";
    static final int TX_INTERFACE_CHAIN = 256067662, TX_INTERFACE_DESCRIPTOR = 256136003;
    // @1.0::IBluetoothHci
    static final int TX_INITIALIZE = 1, TX_SEND_HCI = 2, TX_SEND_ACL = 3, TX_SEND_SCO = 4, TX_CLOSE = 5;
    // @1.1::IBluetoothHci
    static final int TX_INITIALIZE_1_1 = 6, TX_SEND_ISO = 7;
    // callbacks
    static final int CB_INIT_COMPLETE = 1, CB_HCI_EVENT = 2, CB_ACL = 3, CB_SCO = 4, CB_ISO = 5;
    // H4 packet types
    static final int H4_CMD = 1, H4_ACL = 2, H4_SCO = 3, H4_EVT = 4, H4_ISO = 5;
    static final int STATUS_SUCCESS = 0, STATUS_INITIALIZATION_ERROR = 2;

    static String host = "192.168.240.1";
    static int port = 6611;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) host = args[0];
        if (args.length > 1) port = Integer.parseInt(args[1]);
        Hal hal = new Hal();
        HwBinder.configureRpcThreadpool(8, false);   // the service manager calls back into us while we register
        hal.registerService("default");
        Log.i(T, "registered " + D11 + "/default, bridge=" + host + ":" + port);
        while (true) Thread.sleep(3600_000L);
    }

    static final class Hal extends IBase.Stub implements Bridge.Listener {
        private final Bridge mBridge = new Bridge(this);
        private volatile IHwBinder mCb;
        private volatile boolean mCb11;
        private int mLogged;

        @Override
        public void onTransact(int code, HwParcel req, HwParcel reply, int flags) throws RemoteException {
            if (mLogged++ < 60 || code == TX_INITIALIZE || code == TX_INITIALIZE_1_1 || code == TX_CLOSE) Log.i(T, "tx code=" + code + " flags=" + flags);
            try {
                switch (code) {
                    case TX_INTERFACE_CHAIN: {
                        req.enforceInterface(IBase.kInterfaceName);
                        ArrayList<String> chain = new ArrayList<>();
                        chain.add(D11); chain.add(D10); chain.add(IBase.kInterfaceName);
                        reply.writeStatus(HwParcel.STATUS_SUCCESS);
                        reply.writeStringVector(chain);
                        reply.send();
                        return;
                    }
                    case TX_INTERFACE_DESCRIPTOR:
                        req.enforceInterface(IBase.kInterfaceName);
                        reply.writeStatus(HwParcel.STATUS_SUCCESS);
                        reply.writeString(D11);
                        reply.send();
                        return;
                    case TX_INITIALIZE:
                        req.enforceInterface(D10);
                        mCb = req.readStrongBinder(); mCb11 = false;
                        done(reply, flags); startAsync();
                        return;
                    case TX_INITIALIZE_1_1:
                        req.enforceInterface(D11);
                        mCb = req.readStrongBinder(); mCb11 = true;
                        done(reply, flags); startAsync();
                        return;
                    case TX_SEND_HCI: case TX_SEND_ACL: case TX_SEND_SCO: case TX_SEND_ISO: {
                        req.enforceInterface(code == TX_SEND_ISO ? D11 : D10);
                        ArrayList<Byte> v = req.readInt8Vector();
                        done(reply, flags);
                        byte[] b = new byte[v.size()];
                        for (int i = 0; i < b.length; i++) b[i] = v.get(i);
                        int type = code == TX_SEND_HCI ? H4_CMD : code == TX_SEND_ACL ? H4_ACL : code == TX_SEND_SCO ? H4_SCO : H4_ISO;
                        if (!mBridge.send(type, b)) Log.w(T, "drop packet type " + type + " (bridge closed)");
                        return;
                    }
                    case TX_CLOSE:
                        req.enforceInterface(D10);
                        mBridge.close(); mCb = null;
                        done(reply, flags);
                        return;
                    default:
                        super.onTransact(code, req, reply, flags);
                }
            } catch (RuntimeException e) {
                Log.e(T, "transaction " + code + " failed", e);
                throw e;
            }
        }

        private static void done(HwParcel reply, int flags) {
            if ((flags & 1) != 0) return;            // oneway: no reply
            reply.writeStatus(HwParcel.STATUS_SUCCESS);
            reply.send();
        }

        private void startAsync() {
            new Thread(() -> {
                boolean ok = mBridge.open(host, port);
                callback(CB_INIT_COMPLETE, C10, ok ? STATUS_SUCCESS : STATUS_INITIALIZATION_ERROR, null);
            }, "bthal-init").start();
        }

        /** Calls a method on the stack's callbacks object (one-way). */
        private void callback(int code, String token, int status, byte[] payload) {
            IHwBinder cb = mCb;
            if (cb == null) return;
            HwParcel data = new HwParcel(), rep = new HwParcel();
            try {
                data.writeInterfaceToken(token);
                if (payload == null) data.writeInt32(status);
                else {
                    ArrayList<Byte> v = new ArrayList<>(payload.length);
                    for (byte x : payload) v.add(x);
                    data.writeInt8Vector(v);
                }
                cb.transact(code, data, rep, 1 /* ONEWAY */);
            } catch (RemoteException e) {
                Log.w(T, "callback " + code + " failed: " + e);
            } finally {
                data.release(); rep.release();
            }
        }

        @Override public void onPacket(int type, byte[] p) {
            switch (type) {
                case H4_EVT: callback(CB_HCI_EVENT, C10, 0, p); break;
                case H4_ACL: callback(CB_ACL, C10, 0, p); break;
                case H4_SCO: callback(CB_SCO, C10, 0, p); break;
                case H4_ISO: if (mCb11) callback(CB_ISO, C11, 0, p); break;
                default: Log.w(T, "unknown H4 type " + type);
            }
        }

        @Override public void onClosed() { Log.w(T, "bridge closed by host (adapter released)"); }
    }
}
