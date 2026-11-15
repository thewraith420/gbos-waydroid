package com.android.wifi.x.android.hardware.wifi;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* loaded from: classes.dex */
public interface IWifiStaIface extends IInterface {
    public static final String DESCRIPTOR = "android$hardware$wifi$IWifiStaIface".replace('$', '.');

    void configureRoaming(StaRoamingConfig staRoamingConfig) throws android.os.RemoteException;

    void enableLinkLayerStatsCollection(boolean z) throws android.os.RemoteException;

    void enableNdOffload(boolean z) throws android.os.RemoteException;

    StaApfPacketFilterCapabilities getApfPacketFilterCapabilities() throws android.os.RemoteException;

    StaBackgroundScanCapabilities getBackgroundScanCapabilities() throws android.os.RemoteException;

    CachedScanData getCachedScanData() throws android.os.RemoteException;

    WifiDebugRxPacketFateReport[] getDebugRxPacketFates() throws android.os.RemoteException;

    WifiDebugTxPacketFateReport[] getDebugTxPacketFates() throws android.os.RemoteException;

    byte[] getFactoryMacAddress() throws android.os.RemoteException;

    int getFeatureSet() throws android.os.RemoteException;

    StaLinkLayerStats getLinkLayerStats() throws android.os.RemoteException;

    String getName() throws android.os.RemoteException;

    StaRoamingCapabilities getRoamingCapabilities() throws android.os.RemoteException;

    void installApfPacketFilter(byte[] bArr) throws android.os.RemoteException;

    byte[] readApfPacketFilterData() throws android.os.RemoteException;

    void registerEventCallback(IWifiStaIfaceEventCallback iWifiStaIfaceEventCallback) throws android.os.RemoteException;

    void setDtimMultiplier(int i) throws android.os.RemoteException;

    void setMacAddress(byte[] bArr) throws android.os.RemoteException;

    void setRoamingState(byte b) throws android.os.RemoteException;

    void setScanMode(boolean z) throws android.os.RemoteException;

    void startBackgroundScan(int i, StaBackgroundScanParameters staBackgroundScanParameters) throws android.os.RemoteException;

    void startDebugPacketFateMonitoring() throws android.os.RemoteException;

    void startRssiMonitoring(int i, int i2, int i3) throws android.os.RemoteException;

    void startSendingKeepAlivePackets(int i, byte[] bArr, char c, byte[] bArr2, byte[] bArr3, int i2) throws android.os.RemoteException;

    void stopBackgroundScan(int i) throws android.os.RemoteException;

    void stopRssiMonitoring(int i) throws android.os.RemoteException;

    void stopSendingKeepAlivePackets(int i) throws android.os.RemoteException;

    TwtCapabilities twtGetCapabilities() throws android.os.RemoteException;

    void twtSessionGetStats(int i, int i2) throws android.os.RemoteException;

    void twtSessionSetup(int i, TwtRequest twtRequest) throws android.os.RemoteException;

    void twtSessionTeardown(int i, int i2) throws android.os.RemoteException;

    public abstract class Stub extends Binder implements IWifiStaIface {
        public static IWifiStaIface asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(IWifiStaIface.DESCRIPTOR);
            if (queryLocalInterface != null && (queryLocalInterface instanceof IWifiStaIface)) {
                return (IWifiStaIface) queryLocalInterface;
            }
            return new Proxy(iBinder);
        }

        static final class Proxy implements IWifiStaIface {
            private IBinder mRemote;
            private int mCachedVersion = -1;
            private String mCachedHash = "-1";

            Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public String getName() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    if (!this.mRemote.transact(1, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.readString();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void configureRoaming(StaRoamingConfig staRoamingConfig) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeTypedObject(staRoamingConfig, 0);
                    if (!this.mRemote.transact(2, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void enableLinkLayerStatsCollection(boolean z) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeBoolean(z);
                    if (!this.mRemote.transact(4, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void enableNdOffload(boolean z) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeBoolean(z);
                    if (!this.mRemote.transact(5, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public StaApfPacketFilterCapabilities getApfPacketFilterCapabilities() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    if (!this.mRemote.transact(6, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (StaApfPacketFilterCapabilities) obtain2.readTypedObject(StaApfPacketFilterCapabilities.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public StaBackgroundScanCapabilities getBackgroundScanCapabilities() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    if (!this.mRemote.transact(7, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (StaBackgroundScanCapabilities) obtain2.readTypedObject(StaBackgroundScanCapabilities.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public int getFeatureSet() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    if (!this.mRemote.transact(8, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.readInt();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public WifiDebugRxPacketFateReport[] getDebugRxPacketFates() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    if (!this.mRemote.transact(9, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (WifiDebugRxPacketFateReport[]) obtain2.createTypedArray(WifiDebugRxPacketFateReport.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public WifiDebugTxPacketFateReport[] getDebugTxPacketFates() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    if (!this.mRemote.transact(10, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (WifiDebugTxPacketFateReport[]) obtain2.createTypedArray(WifiDebugTxPacketFateReport.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public byte[] getFactoryMacAddress() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    if (!this.mRemote.transact(11, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (byte[]) obtain2.createFixedArray(byte[].class, 6);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public StaLinkLayerStats getLinkLayerStats() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    if (!this.mRemote.transact(12, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (StaLinkLayerStats) obtain2.readTypedObject(StaLinkLayerStats.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public StaRoamingCapabilities getRoamingCapabilities() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    if (!this.mRemote.transact(13, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (StaRoamingCapabilities) obtain2.readTypedObject(StaRoamingCapabilities.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void installApfPacketFilter(byte[] bArr) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeByteArray(bArr);
                    if (!this.mRemote.transact(14, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public byte[] readApfPacketFilterData() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    if (!this.mRemote.transact(15, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.createByteArray();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void registerEventCallback(IWifiStaIfaceEventCallback iWifiStaIfaceEventCallback) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeStrongInterface(iWifiStaIfaceEventCallback);
                    if (!this.mRemote.transact(16, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void setMacAddress(byte[] bArr) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeFixedArray(bArr, 0, 6);
                    if (!this.mRemote.transact(17, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void setRoamingState(byte b) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeByte(b);
                    if (!this.mRemote.transact(18, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void setScanMode(boolean z) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeBoolean(z);
                    if (!this.mRemote.transact(19, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void startBackgroundScan(int i, StaBackgroundScanParameters staBackgroundScanParameters) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeTypedObject(staBackgroundScanParameters, 0);
                    if (!this.mRemote.transact(20, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void startDebugPacketFateMonitoring() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    if (!this.mRemote.transact(21, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void startRssiMonitoring(int i, int i2, int i3) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeInt(i2);
                    obtain.writeInt(i3);
                    if (!this.mRemote.transact(22, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void startSendingKeepAlivePackets(int i, byte[] bArr, char c, byte[] bArr2, byte[] bArr3, int i2) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeByteArray(bArr);
                    obtain.writeInt(c);
                    obtain.writeFixedArray(bArr2, 0, 6);
                    obtain.writeFixedArray(bArr3, 0, 6);
                    obtain.writeInt(i2);
                    if (!this.mRemote.transact(23, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void stopBackgroundScan(int i) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(24, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void stopRssiMonitoring(int i) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(25, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void stopSendingKeepAlivePackets(int i) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(26, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void setDtimMultiplier(int i) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(27, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public CachedScanData getCachedScanData() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    if (!this.mRemote.transact(28, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (CachedScanData) obtain2.readTypedObject(CachedScanData.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public TwtCapabilities twtGetCapabilities() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    if (!this.mRemote.transact(29, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (TwtCapabilities) obtain2.readTypedObject(TwtCapabilities.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void twtSessionSetup(int i, TwtRequest twtRequest) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeTypedObject(twtRequest, 0);
                    if (!this.mRemote.transact(30, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void twtSessionTeardown(int i, int i2) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeInt(i2);
                    if (!this.mRemote.transact(34, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiStaIface
            public void twtSessionGetStats(int i, int i2) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiStaIface.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeInt(i2);
                    if (!this.mRemote.transact(35, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }
        }
    }
}
