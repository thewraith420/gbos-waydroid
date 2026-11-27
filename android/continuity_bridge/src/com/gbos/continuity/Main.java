package com.gbos.continuity;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/**
 * Container-side CompanionDeviceManager RawTransport bridge for Android 17 Task Continuity.
 *
 * Watches for an active com.google.ambient.streaming association (with FLAG_TASK_CONTINUITY),
 * attaches a RawTransport backed by a TCP socket to the host continuity server (default
 * 192.168.240.1:8095), and automatically re-attaches if the host restarts or CDS creates a
 * newer association.
 *
 * Pure reflection: compiles with plain javac (no Android framework SDK jar required).
 */
public class Main {
    private static final String PKG = "com.google.ambient.streaming";
    private static final int FLAG_TASK_CONTINUITY = 2;
    private static final int TRANSPORT_TYPE_RAW = 1;

    private static Object getCompanionService() throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Class<?> ibinder = Class.forName("android.os.IBinder");
        Object b = sm.getMethod("getService", String.class).invoke(null, "companiondevice");
        if (b == null) return null;
        Class<?> stub = Class.forName("android.companion.ICompanionDeviceManager$Stub");
        return stub.getMethod("asInterface", ibinder).invoke(null, b);
    }

    /** Pick the newest non-revoked com.google.ambient.streaming association with task continuity enabled. */
    private static int findBestAssociationId(Object cdm, int userId) {
        try {
            Method getAll = cdm.getClass().getMethod("getAllAssociationsForUser", int.class);
            List<?> list = (List<?>) getAll.invoke(cdm, userId);
            if (list == null || list.isEmpty()) return -1;

            int bestReal = -1;
            int bestAny = -1;
            for (Object a : list) {
                Class<?> ac = a.getClass();
                String pkg = (String) ac.getMethod("getPackageName").invoke(a);
                if (!PKG.equals(pkg)) continue;
                boolean revoked = false;
                try {
                    revoked = (Boolean) ac.getMethod("isRevoked").invoke(a);
                } catch (Throwable ignored) {}
                if (revoked) continue;

                int flags = 0;
                try {
                    flags = (Integer) ac.getMethod("getSystemDataSyncFlags").invoke(a);
                } catch (Throwable ignored) {}
                if ((flags & FLAG_TASK_CONTINUITY) == 0) continue;

                int id = (Integer) ac.getMethod("getId").invoke(a);
                CharSequence nameCs = (CharSequence) ac.getMethod("getDisplayName").invoke(a);
                String name = nameCs != null ? nameCs.toString() : "";

                if (id > bestAny) bestAny = id;
                if (!"SyntheticPhone".equals(name) && id > bestReal) bestReal = id;
            }
            return bestReal > 0 ? bestReal : bestAny;
        } catch (Throwable t) {
            System.err.println("[gb-continuity] findBestAssociationId failed: " + t);
            return -1;
        }
    }

    private static boolean isAttached(Object cdm, int assocId) {
        try {
            Method m = cdm.getClass().getMethod("isSystemDataTransportAttached", int.class);
            return (Boolean) m.invoke(cdm, assocId);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "192.168.240.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8095;
        int userId = args.length > 2 ? Integer.parseInt(args[2]) : 10;

        System.out.println("[gb-continuity] starting target=" + host + ":" + port + " user=" + userId);

        while (true) {
            Socket sock = null;
            Object cdm = null;
            int assocId = -1;
            try {
                cdm = getCompanionService();
                if (cdm == null) {
                    Thread.sleep(3000);
                    continue;
                }

                assocId = findBestAssociationId(cdm, userId);
                if (assocId < 0) {
                    Thread.sleep(3000);
                    continue;
                }

                sock = new Socket();
                sock.connect(new InetSocketAddress(host, port), 5000);
                sock.setTcpNoDelay(true);
                sock.setKeepAlive(true);

                Class<?> pfdClass = Class.forName("android.os.ParcelFileDescriptor");
                Object pfd = pfdClass.getMethod("fromSocket", Socket.class).invoke(null, sock);

                cdm.getClass().getMethod("overrideTransportType", int.class).invoke(cdm, TRANSPORT_TYPE_RAW);
                cdm.getClass().getMethod(
                        "attachSystemDataTransport",
                        String.class, int.class, int.class, pfdClass)
                        .invoke(cdm, PKG, userId, assocId, pfd);

                System.out.println("[gb-continuity] attached RawTransport assocId=" + assocId);

                // Monitor transport liveness and association changes
                while (true) {
                    Thread.sleep(3000);
                    if (!isAttached(cdm, assocId)) {
                        System.out.println("[gb-continuity] transport detached for assocId=" + assocId + "; reconnecting");
                        break;
                    }
                    int newest = findBestAssociationId(cdm, userId);
                    if (newest > 0 && newest != assocId) {
                        System.out.println("[gb-continuity] newer assocId=" + newest + " (was " + assocId + "); switching");
                        try {
                            cdm.getClass().getMethod("detachSystemDataTransport", String.class, int.class, int.class)
                                    .invoke(cdm, PKG, userId, assocId);
                        } catch (Throwable ignored) {}
                        break;
                    }
                }
            } catch (Throwable t) {
                System.err.println("[gb-continuity] loop error: " + t.getMessage());
            } finally {
                if (sock != null) {
                    try { sock.close(); } catch (Throwable ignored) {}
                }
            }
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        }
    }
}
