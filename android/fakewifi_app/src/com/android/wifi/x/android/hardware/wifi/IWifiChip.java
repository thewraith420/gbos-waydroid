package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import com.android.wifi.x.android.hardware.wifi.IWifiApIface;
import com.android.wifi.x.android.hardware.wifi.IWifiNanIface;
import com.android.wifi.x.android.hardware.wifi.IWifiP2pIface;
import com.android.wifi.x.android.hardware.wifi.IWifiRttController;
import com.android.wifi.x.android.hardware.wifi.IWifiStaIface;
import com.android.wifi.x.android.hardware.wifi.common.OuiKeyedData;

/* loaded from: classes.dex */
public interface IWifiChip extends IInterface {
    public static final String DESCRIPTOR = "android$hardware$wifi$IWifiChip".replace('$', '.');

    void configureChip(int i) throws android.os.RemoteException;

    IWifiApIface createApIface() throws android.os.RemoteException;

    IWifiApIface createApOrBridgedApIface(int i, OuiKeyedData[] ouiKeyedDataArr) throws android.os.RemoteException;

    IWifiApIface createApOrBridgedApIfaceWithParams(ApIfaceParams apIfaceParams) throws android.os.RemoteException;

    IWifiApIface createBridgedApIface() throws android.os.RemoteException;

    IWifiNanIface createNanIface() throws android.os.RemoteException;

    IWifiP2pIface createP2pIface() throws android.os.RemoteException;

    IWifiRttController createRttController(IWifiStaIface iWifiStaIface) throws android.os.RemoteException;

    IWifiStaIface createStaIface() throws android.os.RemoteException;

    void enableDebugErrorAlerts(boolean z) throws android.os.RemoteException;

    void enableStaChannelForPeerNetwork(int i) throws android.os.RemoteException;

    void flushRingBufferToFile() throws android.os.RemoteException;

    void forceDumpToDebugRingBuffer(String str) throws android.os.RemoteException;

    IWifiApIface getApIface(String str) throws android.os.RemoteException;

    String[] getApIfaceNames() throws android.os.RemoteException;

    ChipMode[] getAvailableModes() throws android.os.RemoteException;

    WifiDebugHostWakeReasonStats getDebugHostWakeReasonStats() throws android.os.RemoteException;

    WifiDebugRingBufferStatus[] getDebugRingBuffersStatus() throws android.os.RemoteException;

    int getFeatureSet() throws android.os.RemoteException;

    int getId() throws android.os.RemoteException;

    int getMode() throws android.os.RemoteException;

    IWifiNanIface getNanIface(String str) throws android.os.RemoteException;

    String[] getNanIfaceNames() throws android.os.RemoteException;

    IWifiP2pIface getP2pIface(String str) throws android.os.RemoteException;

    String[] getP2pIfaceNames() throws android.os.RemoteException;

    IWifiStaIface getStaIface(String str) throws android.os.RemoteException;

    String[] getStaIfaceNames() throws android.os.RemoteException;

    WifiRadioCombination[] getSupportedRadioCombinations() throws android.os.RemoteException;

    WifiUsableChannel[] getUsableChannels(int i, int i2, int i3) throws android.os.RemoteException;

    WifiChipCapabilities getWifiChipCapabilities() throws android.os.RemoteException;

    void registerEventCallback(IWifiChipEventCallback iWifiChipEventCallback) throws android.os.RemoteException;

    void removeApIface(String str) throws android.os.RemoteException;

    void removeIfaceInstanceFromBridgedApIface(String str, String str2) throws android.os.RemoteException;

    void removeNanIface(String str) throws android.os.RemoteException;

    void removeP2pIface(String str) throws android.os.RemoteException;

    void removeStaIface(String str) throws android.os.RemoteException;

    ChipDebugInfo requestChipDebugInfo() throws android.os.RemoteException;

    byte[] requestDriverDebugDump() throws android.os.RemoteException;

    byte[] requestFirmwareDebugDump() throws android.os.RemoteException;

    void resetTxPowerScenario() throws android.os.RemoteException;

    void selectTxPowerScenario(int i) throws android.os.RemoteException;

    void setAfcChannelAllowance(AfcChannelAllowance afcChannelAllowance) throws android.os.RemoteException;

    void setCoexUnsafeChannels(CoexUnsafeChannel[] coexUnsafeChannelArr, int i) throws android.os.RemoteException;

    void setCountryCode(byte[] bArr) throws android.os.RemoteException;

    void setLatencyMode(int i) throws android.os.RemoteException;

    void setMloMode(int i) throws android.os.RemoteException;

    void setMultiStaPrimaryConnection(String str) throws android.os.RemoteException;

    void setMultiStaUseCase(byte b) throws android.os.RemoteException;

    void setVoipMode(int i) throws android.os.RemoteException;

    void startLoggingToDebugRingBuffer(String str, int i, int i2, int i3) throws android.os.RemoteException;

    void stopLoggingToDebugRingBuffer() throws android.os.RemoteException;

    void triggerSubsystemRestart() throws android.os.RemoteException;

    public abstract class Stub extends Binder implements IWifiChip {
        public static IWifiChip asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(IWifiChip.DESCRIPTOR);
            if (queryLocalInterface != null && (queryLocalInterface instanceof IWifiChip)) {
                return (IWifiChip) queryLocalInterface;
            }
            return new Proxy(iBinder);
        }

        static final class Proxy implements IWifiChip {
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

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void configureChip(int i) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(1, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public IWifiApIface createApIface() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    boolean transact = this.mRemote.transact(2, obtain, obtain2, 0);
                    if (Build.VERSION.SDK_INT >= 33) {
                        obtain2.setPropagateAllowBlocking();
                    }
                    if (!transact) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    IWifiApIface asInterface = IWifiApIface.Stub.asInterface(obtain2.readStrongBinder());
                    obtain2.recycle();
                    obtain.recycle();
                    return asInterface;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw th;
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public IWifiApIface createBridgedApIface() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    boolean transact = this.mRemote.transact(3, obtain, obtain2, 0);
                    if (Build.VERSION.SDK_INT >= 33) {
                        obtain2.setPropagateAllowBlocking();
                    }
                    if (!transact) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    IWifiApIface asInterface = IWifiApIface.Stub.asInterface(obtain2.readStrongBinder());
                    obtain2.recycle();
                    obtain.recycle();
                    return asInterface;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw th;
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public IWifiNanIface createNanIface() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    boolean transact = this.mRemote.transact(4, obtain, obtain2, 0);
                    if (Build.VERSION.SDK_INT >= 33) {
                        obtain2.setPropagateAllowBlocking();
                    }
                    if (!transact) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    IWifiNanIface asInterface = IWifiNanIface.Stub.asInterface(obtain2.readStrongBinder());
                    obtain2.recycle();
                    obtain.recycle();
                    return asInterface;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw th;
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public IWifiP2pIface createP2pIface() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    boolean transact = this.mRemote.transact(5, obtain, obtain2, 0);
                    if (Build.VERSION.SDK_INT >= 33) {
                        obtain2.setPropagateAllowBlocking();
                    }
                    if (!transact) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    IWifiP2pIface asInterface = IWifiP2pIface.Stub.asInterface(obtain2.readStrongBinder());
                    obtain2.recycle();
                    obtain.recycle();
                    return asInterface;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw th;
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public IWifiRttController createRttController(IWifiStaIface iWifiStaIface) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeStrongInterface(iWifiStaIface);
                    boolean transact = this.mRemote.transact(6, obtain, obtain2, 0);
                    if (Build.VERSION.SDK_INT >= 33) {
                        obtain2.setPropagateAllowBlocking();
                    }
                    if (!transact) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    IWifiRttController asInterface = IWifiRttController.Stub.asInterface(obtain2.readStrongBinder());
                    obtain2.recycle();
                    obtain.recycle();
                    return asInterface;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw th;
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public IWifiStaIface createStaIface() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    boolean transact = this.mRemote.transact(7, obtain, obtain2, 0);
                    if (Build.VERSION.SDK_INT >= 33) {
                        obtain2.setPropagateAllowBlocking();
                    }
                    if (!transact) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    IWifiStaIface asInterface = IWifiStaIface.Stub.asInterface(obtain2.readStrongBinder());
                    obtain2.recycle();
                    obtain.recycle();
                    return asInterface;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw th;
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void enableDebugErrorAlerts(boolean z) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeBoolean(z);
                    if (!this.mRemote.transact(8, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void flushRingBufferToFile() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(9, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void forceDumpToDebugRingBuffer(String str) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(10, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public IWifiApIface getApIface(String str) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeString(str);
                    boolean transact = this.mRemote.transact(11, obtain, obtain2, 0);
                    if (Build.VERSION.SDK_INT >= 33) {
                        obtain2.setPropagateAllowBlocking();
                    }
                    if (!transact) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    IWifiApIface asInterface = IWifiApIface.Stub.asInterface(obtain2.readStrongBinder());
                    obtain2.recycle();
                    obtain.recycle();
                    return asInterface;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw th;
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public String[] getApIfaceNames() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(12, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.createStringArray();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public ChipMode[] getAvailableModes() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(13, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (ChipMode[]) obtain2.createTypedArray(ChipMode.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public int getFeatureSet() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(14, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.readInt();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public WifiDebugHostWakeReasonStats getDebugHostWakeReasonStats() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(15, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (WifiDebugHostWakeReasonStats) obtain2.readTypedObject(WifiDebugHostWakeReasonStats.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public WifiDebugRingBufferStatus[] getDebugRingBuffersStatus() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(16, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (WifiDebugRingBufferStatus[]) obtain2.createTypedArray(WifiDebugRingBufferStatus.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public int getId() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(17, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.readInt();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public int getMode() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(18, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.readInt();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public IWifiNanIface getNanIface(String str) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeString(str);
                    boolean transact = this.mRemote.transact(19, obtain, obtain2, 0);
                    if (Build.VERSION.SDK_INT >= 33) {
                        obtain2.setPropagateAllowBlocking();
                    }
                    if (!transact) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    IWifiNanIface asInterface = IWifiNanIface.Stub.asInterface(obtain2.readStrongBinder());
                    obtain2.recycle();
                    obtain.recycle();
                    return asInterface;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw th;
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public String[] getNanIfaceNames() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(20, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.createStringArray();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public IWifiP2pIface getP2pIface(String str) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeString(str);
                    boolean transact = this.mRemote.transact(21, obtain, obtain2, 0);
                    if (Build.VERSION.SDK_INT >= 33) {
                        obtain2.setPropagateAllowBlocking();
                    }
                    if (!transact) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    IWifiP2pIface asInterface = IWifiP2pIface.Stub.asInterface(obtain2.readStrongBinder());
                    obtain2.recycle();
                    obtain.recycle();
                    return asInterface;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw th;
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public String[] getP2pIfaceNames() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(22, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.createStringArray();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public IWifiStaIface getStaIface(String str) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeString(str);
                    boolean transact = this.mRemote.transact(23, obtain, obtain2, 0);
                    if (Build.VERSION.SDK_INT >= 33) {
                        obtain2.setPropagateAllowBlocking();
                    }
                    if (!transact) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    IWifiStaIface asInterface = IWifiStaIface.Stub.asInterface(obtain2.readStrongBinder());
                    obtain2.recycle();
                    obtain.recycle();
                    return asInterface;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw th;
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public String[] getStaIfaceNames() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(24, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.createStringArray();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public WifiRadioCombination[] getSupportedRadioCombinations() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(25, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (WifiRadioCombination[]) obtain2.createTypedArray(WifiRadioCombination.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public WifiChipCapabilities getWifiChipCapabilities() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(26, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (WifiChipCapabilities) obtain2.readTypedObject(WifiChipCapabilities.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public WifiUsableChannel[] getUsableChannels(int i, int i2, int i3) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeInt(i2);
                    obtain.writeInt(i3);
                    if (!this.mRemote.transact(27, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (WifiUsableChannel[]) obtain2.createTypedArray(WifiUsableChannel.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void setAfcChannelAllowance(AfcChannelAllowance afcChannelAllowance) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeTypedObject(afcChannelAllowance, 0);
                    if (!this.mRemote.transact(28, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void registerEventCallback(IWifiChipEventCallback iWifiChipEventCallback) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeStrongInterface(iWifiChipEventCallback);
                    if (!this.mRemote.transact(29, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void removeApIface(String str) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(30, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void removeIfaceInstanceFromBridgedApIface(String str, String str2) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeString(str);
                    obtain.writeString(str2);
                    if (!this.mRemote.transact(31, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void removeNanIface(String str) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(32, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void removeP2pIface(String str) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(33, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void removeStaIface(String str) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(34, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public ChipDebugInfo requestChipDebugInfo() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(35, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (ChipDebugInfo) obtain2.readTypedObject(ChipDebugInfo.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public byte[] requestDriverDebugDump() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(36, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.createByteArray();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public byte[] requestFirmwareDebugDump() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(37, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.createByteArray();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void resetTxPowerScenario() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(38, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void selectTxPowerScenario(int i) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(39, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void setCoexUnsafeChannels(CoexUnsafeChannel[] coexUnsafeChannelArr, int i) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeTypedArray(coexUnsafeChannelArr, 0);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(40, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void setCountryCode(byte[] bArr) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeFixedArray(bArr, 0, 2);
                    if (!this.mRemote.transact(41, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void setLatencyMode(int i) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(42, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void setMultiStaPrimaryConnection(String str) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(43, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void setMultiStaUseCase(byte b) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeByte(b);
                    if (!this.mRemote.transact(44, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void startLoggingToDebugRingBuffer(String str, int i, int i2, int i3) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeString(str);
                    obtain.writeInt(i);
                    obtain.writeInt(i2);
                    obtain.writeInt(i3);
                    if (!this.mRemote.transact(45, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void stopLoggingToDebugRingBuffer() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(46, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void triggerSubsystemRestart() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    if (!this.mRemote.transact(47, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void enableStaChannelForPeerNetwork(int i) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(48, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void setMloMode(int i) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(49, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public IWifiApIface createApOrBridgedApIface(int i, OuiKeyedData[] ouiKeyedDataArr) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeTypedArray(ouiKeyedDataArr, 0);
                    boolean transact = this.mRemote.transact(50, obtain, obtain2, 0);
                    if (Build.VERSION.SDK_INT >= 33) {
                        obtain2.setPropagateAllowBlocking();
                    }
                    if (!transact) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    IWifiApIface asInterface = IWifiApIface.Stub.asInterface(obtain2.readStrongBinder());
                    obtain2.recycle();
                    obtain.recycle();
                    return asInterface;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw th;
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public void setVoipMode(int i) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(51, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiChip
            public IWifiApIface createApOrBridgedApIfaceWithParams(ApIfaceParams apIfaceParams) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiChip.DESCRIPTOR);
                    obtain.writeTypedObject(apIfaceParams, 0);
                    boolean transact = this.mRemote.transact(52, obtain, obtain2, 0);
                    if (Build.VERSION.SDK_INT >= 33) {
                        obtain2.setPropagateAllowBlocking();
                    }
                    if (!transact) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    IWifiApIface asInterface = IWifiApIface.Stub.asInterface(obtain2.readStrongBinder());
                    obtain2.recycle();
                    obtain.recycle();
                    return asInterface;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw th;
                }
            }
        }
    }

    public class ChipConcurrencyCombinationLimit implements Parcelable {
        public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.IWifiChip.ChipConcurrencyCombinationLimit.1
            @Override // android.os.Parcelable.Creator
            public ChipConcurrencyCombinationLimit createFromParcel(Parcel parcel) {
                ChipConcurrencyCombinationLimit chipConcurrencyCombinationLimit = new ChipConcurrencyCombinationLimit();
                chipConcurrencyCombinationLimit.readFromParcel(parcel);
                return chipConcurrencyCombinationLimit;
            }

            @Override // android.os.Parcelable.Creator
            public ChipConcurrencyCombinationLimit[] newArray(int i) {
                return new ChipConcurrencyCombinationLimit[i];
            }
        };
        public int maxIfaces = 0;
        public int[] types;

        @Override // android.os.Parcelable
        public int describeContents() {
            return 0;
        }

        public final int getStability() {
            return 1;
        }

        @Override // android.os.Parcelable
        public final void writeToParcel(Parcel parcel, int i) {
            int dataPosition = parcel.dataPosition();
            parcel.writeInt(0);
            parcel.writeIntArray(this.types);
            parcel.writeInt(this.maxIfaces);
            int dataPosition2 = parcel.dataPosition();
            parcel.setDataPosition(dataPosition);
            parcel.writeInt(dataPosition2 - dataPosition);
            parcel.setDataPosition(dataPosition2);
        }

        public final void readFromParcel(Parcel parcel) {
            int dataPosition = parcel.dataPosition();
            int readInt = parcel.readInt();
            try {
                if (readInt < 4) {
                    throw new BadParcelableException("Parcelable too small");
                }
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.types = parcel.createIntArray();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.maxIfaces = parcel.readInt();
                        if (dataPosition > Integer.MAX_VALUE - readInt) {
                            Ssid$$ExternalSyntheticBUOutline0.m();
                            return;
                        }
                    } else if (dataPosition > Integer.MAX_VALUE - readInt) {
                        Ssid$$ExternalSyntheticBUOutline0.m();
                        return;
                    }
                } else if (dataPosition > Integer.MAX_VALUE - readInt) {
                    Ssid$$ExternalSyntheticBUOutline0.m();
                    return;
                }
                parcel.setDataPosition(dataPosition + readInt);
            } catch (Throwable th) {
                if (dataPosition > Integer.MAX_VALUE - readInt) {
                    Ssid$$ExternalSyntheticBUOutline0.m();
                } else {
                    parcel.setDataPosition(dataPosition + readInt);
                    throw th;
                }
            }
        }
    }

    public class ChipConcurrencyCombination implements Parcelable {
        public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.IWifiChip.ChipConcurrencyCombination.1
            @Override // android.os.Parcelable.Creator
            public ChipConcurrencyCombination createFromParcel(Parcel parcel) {
                ChipConcurrencyCombination chipConcurrencyCombination = new ChipConcurrencyCombination();
                chipConcurrencyCombination.readFromParcel(parcel);
                return chipConcurrencyCombination;
            }

            @Override // android.os.Parcelable.Creator
            public ChipConcurrencyCombination[] newArray(int i) {
                return new ChipConcurrencyCombination[i];
            }
        };
        public ChipConcurrencyCombinationLimit[] limits;

        public final int getStability() {
            return 1;
        }

        @Override // android.os.Parcelable
        public final void writeToParcel(Parcel parcel, int i) {
            int dataPosition = parcel.dataPosition();
            parcel.writeInt(0);
            parcel.writeTypedArray(this.limits, i);
            int dataPosition2 = parcel.dataPosition();
            parcel.setDataPosition(dataPosition);
            parcel.writeInt(dataPosition2 - dataPosition);
            parcel.setDataPosition(dataPosition2);
        }

        public final void readFromParcel(Parcel parcel) {
            int dataPosition = parcel.dataPosition();
            int readInt = parcel.readInt();
            try {
                if (readInt < 4) {
                    throw new BadParcelableException("Parcelable too small");
                }
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.limits = (ChipConcurrencyCombinationLimit[]) parcel.createTypedArray(ChipConcurrencyCombinationLimit.CREATOR);
                    if (dataPosition > Integer.MAX_VALUE - readInt) {
                        Ssid$$ExternalSyntheticBUOutline0.m();
                        return;
                    }
                } else if (dataPosition > Integer.MAX_VALUE - readInt) {
                    Ssid$$ExternalSyntheticBUOutline0.m();
                    return;
                }
                parcel.setDataPosition(dataPosition + readInt);
            } catch (Throwable th) {
                if (dataPosition > Integer.MAX_VALUE - readInt) {
                    Ssid$$ExternalSyntheticBUOutline0.m();
                } else {
                    parcel.setDataPosition(dataPosition + readInt);
                    throw th;
                }
            }
        }

        @Override // android.os.Parcelable
        public int describeContents() {
            return describeContents(this.limits);
        }

        private int describeContents(Object obj) {
            if (obj == null) {
                return 0;
            }
            if (obj instanceof Object[]) {
                int i = 0;
                for (Object obj2 : (Object[]) obj) {
                    i |= describeContents(obj2);
                }
                return i;
            }
            if (obj instanceof Parcelable) {
                return ((Parcelable) obj).describeContents();
            }
            return 0;
        }
    }

    public class ChipDebugInfo implements Parcelable {
        public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.IWifiChip.ChipDebugInfo.1
            @Override // android.os.Parcelable.Creator
            public ChipDebugInfo createFromParcel(Parcel parcel) {
                ChipDebugInfo chipDebugInfo = new ChipDebugInfo();
                chipDebugInfo.readFromParcel(parcel);
                return chipDebugInfo;
            }

            @Override // android.os.Parcelable.Creator
            public ChipDebugInfo[] newArray(int i) {
                return new ChipDebugInfo[i];
            }
        };
        public String driverDescription;
        public String firmwareDescription;

        @Override // android.os.Parcelable
        public int describeContents() {
            return 0;
        }

        public final int getStability() {
            return 1;
        }

        @Override // android.os.Parcelable
        public final void writeToParcel(Parcel parcel, int i) {
            int dataPosition = parcel.dataPosition();
            parcel.writeInt(0);
            parcel.writeString(this.driverDescription);
            parcel.writeString(this.firmwareDescription);
            int dataPosition2 = parcel.dataPosition();
            parcel.setDataPosition(dataPosition);
            parcel.writeInt(dataPosition2 - dataPosition);
            parcel.setDataPosition(dataPosition2);
        }

        public final void readFromParcel(Parcel parcel) {
            int dataPosition = parcel.dataPosition();
            int readInt = parcel.readInt();
            try {
                if (readInt < 4) {
                    throw new BadParcelableException("Parcelable too small");
                }
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.driverDescription = parcel.readString();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.firmwareDescription = parcel.readString();
                        if (dataPosition > Integer.MAX_VALUE - readInt) {
                            Ssid$$ExternalSyntheticBUOutline0.m();
                            return;
                        }
                    } else if (dataPosition > Integer.MAX_VALUE - readInt) {
                        Ssid$$ExternalSyntheticBUOutline0.m();
                        return;
                    }
                } else if (dataPosition > Integer.MAX_VALUE - readInt) {
                    Ssid$$ExternalSyntheticBUOutline0.m();
                    return;
                }
                parcel.setDataPosition(dataPosition + readInt);
            } catch (Throwable th) {
                if (dataPosition > Integer.MAX_VALUE - readInt) {
                    Ssid$$ExternalSyntheticBUOutline0.m();
                } else {
                    parcel.setDataPosition(dataPosition + readInt);
                    throw th;
                }
            }
        }
    }

    public class ChipMode implements Parcelable {
        public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.IWifiChip.ChipMode.1
            @Override // android.os.Parcelable.Creator
            public ChipMode createFromParcel(Parcel parcel) {
                ChipMode chipMode = new ChipMode();
                chipMode.readFromParcel(parcel);
                return chipMode;
            }

            @Override // android.os.Parcelable.Creator
            public ChipMode[] newArray(int i) {
                return new ChipMode[i];
            }
        };
        public ChipConcurrencyCombination[] availableCombinations;
        public int id = 0;

        public final int getStability() {
            return 1;
        }

        @Override // android.os.Parcelable
        public final void writeToParcel(Parcel parcel, int i) {
            int dataPosition = parcel.dataPosition();
            parcel.writeInt(0);
            parcel.writeInt(this.id);
            parcel.writeTypedArray(this.availableCombinations, i);
            int dataPosition2 = parcel.dataPosition();
            parcel.setDataPosition(dataPosition);
            parcel.writeInt(dataPosition2 - dataPosition);
            parcel.setDataPosition(dataPosition2);
        }

        public final void readFromParcel(Parcel parcel) {
            int dataPosition = parcel.dataPosition();
            int readInt = parcel.readInt();
            try {
                if (readInt < 4) {
                    throw new BadParcelableException("Parcelable too small");
                }
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.id = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.availableCombinations = (ChipConcurrencyCombination[]) parcel.createTypedArray(ChipConcurrencyCombination.CREATOR);
                        if (dataPosition > Integer.MAX_VALUE - readInt) {
                            Ssid$$ExternalSyntheticBUOutline0.m();
                            return;
                        }
                    } else if (dataPosition > Integer.MAX_VALUE - readInt) {
                        Ssid$$ExternalSyntheticBUOutline0.m();
                        return;
                    }
                } else if (dataPosition > Integer.MAX_VALUE - readInt) {
                    Ssid$$ExternalSyntheticBUOutline0.m();
                    return;
                }
                parcel.setDataPosition(dataPosition + readInt);
            } catch (Throwable th) {
                if (dataPosition > Integer.MAX_VALUE - readInt) {
                    Ssid$$ExternalSyntheticBUOutline0.m();
                } else {
                    parcel.setDataPosition(dataPosition + readInt);
                    throw th;
                }
            }
        }

        @Override // android.os.Parcelable
        public int describeContents() {
            return describeContents(this.availableCombinations);
        }

        private int describeContents(Object obj) {
            if (obj == null) {
                return 0;
            }
            if (obj instanceof Object[]) {
                int i = 0;
                for (Object obj2 : (Object[]) obj) {
                    i |= describeContents(obj2);
                }
                return i;
            }
            if (obj instanceof Parcelable) {
                return ((Parcelable) obj).describeContents();
            }
            return 0;
        }
    }

    public class CoexUnsafeChannel implements Parcelable {
        public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.IWifiChip.CoexUnsafeChannel.1
            @Override // android.os.Parcelable.Creator
            public CoexUnsafeChannel createFromParcel(Parcel parcel) {
                CoexUnsafeChannel coexUnsafeChannel = new CoexUnsafeChannel();
                coexUnsafeChannel.readFromParcel(parcel);
                return coexUnsafeChannel;
            }

            @Override // android.os.Parcelable.Creator
            public CoexUnsafeChannel[] newArray(int i) {
                return new CoexUnsafeChannel[i];
            }
        };
        public int band;
        public int channel = 0;
        public int powerCapDbm = 0;

        @Override // android.os.Parcelable
        public int describeContents() {
            return 0;
        }

        public final int getStability() {
            return 1;
        }

        @Override // android.os.Parcelable
        public final void writeToParcel(Parcel parcel, int i) {
            int dataPosition = parcel.dataPosition();
            parcel.writeInt(0);
            parcel.writeInt(this.band);
            parcel.writeInt(this.channel);
            parcel.writeInt(this.powerCapDbm);
            int dataPosition2 = parcel.dataPosition();
            parcel.setDataPosition(dataPosition);
            parcel.writeInt(dataPosition2 - dataPosition);
            parcel.setDataPosition(dataPosition2);
        }

        public final void readFromParcel(Parcel parcel) {
            int dataPosition = parcel.dataPosition();
            int readInt = parcel.readInt();
            try {
                if (readInt < 4) {
                    throw new BadParcelableException("Parcelable too small");
                }
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.band = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.channel = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.powerCapDbm = parcel.readInt();
                            if (dataPosition > Integer.MAX_VALUE - readInt) {
                                Ssid$$ExternalSyntheticBUOutline0.m();
                                return;
                            }
                        } else if (dataPosition > Integer.MAX_VALUE - readInt) {
                            Ssid$$ExternalSyntheticBUOutline0.m();
                            return;
                        }
                    } else if (dataPosition > Integer.MAX_VALUE - readInt) {
                        Ssid$$ExternalSyntheticBUOutline0.m();
                        return;
                    }
                } else if (dataPosition > Integer.MAX_VALUE - readInt) {
                    Ssid$$ExternalSyntheticBUOutline0.m();
                    return;
                }
                parcel.setDataPosition(dataPosition + readInt);
            } catch (Throwable th) {
                if (dataPosition > Integer.MAX_VALUE - readInt) {
                    Ssid$$ExternalSyntheticBUOutline0.m();
                } else {
                    parcel.setDataPosition(dataPosition + readInt);
                    throw th;
                }
            }
        }
    }

    public class ApIfaceParams implements Parcelable {
        public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.IWifiChip.ApIfaceParams.1
            @Override // android.os.Parcelable.Creator
            public ApIfaceParams createFromParcel(Parcel parcel) {
                ApIfaceParams apIfaceParams = new ApIfaceParams();
                apIfaceParams.readFromParcel(parcel);
                return apIfaceParams;
            }

            @Override // android.os.Parcelable.Creator
            public ApIfaceParams[] newArray(int i) {
                return new ApIfaceParams[i];
            }
        };
        public int ifaceType;
        public boolean usesMlo = false;
        public OuiKeyedData[] vendorData;

        public final int getStability() {
            return 1;
        }

        @Override // android.os.Parcelable
        public final void writeToParcel(Parcel parcel, int i) {
            int dataPosition = parcel.dataPosition();
            parcel.writeInt(0);
            parcel.writeInt(this.ifaceType);
            parcel.writeBoolean(this.usesMlo);
            parcel.writeTypedArray(this.vendorData, i);
            int dataPosition2 = parcel.dataPosition();
            parcel.setDataPosition(dataPosition);
            parcel.writeInt(dataPosition2 - dataPosition);
            parcel.setDataPosition(dataPosition2);
        }

        public final void readFromParcel(Parcel parcel) {
            int dataPosition = parcel.dataPosition();
            int readInt = parcel.readInt();
            try {
                if (readInt < 4) {
                    throw new BadParcelableException("Parcelable too small");
                }
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.ifaceType = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.usesMlo = parcel.readBoolean();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.vendorData = (OuiKeyedData[]) parcel.createTypedArray(OuiKeyedData.CREATOR);
                            if (dataPosition > Integer.MAX_VALUE - readInt) {
                                Ssid$$ExternalSyntheticBUOutline0.m();
                                return;
                            }
                        } else if (dataPosition > Integer.MAX_VALUE - readInt) {
                            Ssid$$ExternalSyntheticBUOutline0.m();
                            return;
                        }
                    } else if (dataPosition > Integer.MAX_VALUE - readInt) {
                        Ssid$$ExternalSyntheticBUOutline0.m();
                        return;
                    }
                } else if (dataPosition > Integer.MAX_VALUE - readInt) {
                    Ssid$$ExternalSyntheticBUOutline0.m();
                    return;
                }
                parcel.setDataPosition(dataPosition + readInt);
            } catch (Throwable th) {
                if (dataPosition > Integer.MAX_VALUE - readInt) {
                    Ssid$$ExternalSyntheticBUOutline0.m();
                } else {
                    parcel.setDataPosition(dataPosition + readInt);
                    throw th;
                }
            }
        }

        @Override // android.os.Parcelable
        public int describeContents() {
            return describeContents(this.vendorData);
        }

        private int describeContents(Object obj) {
            if (obj == null) {
                return 0;
            }
            if (obj instanceof Object[]) {
                int i = 0;
                for (Object obj2 : (Object[]) obj) {
                    i |= describeContents(obj2);
                }
                return i;
            }
            if (obj instanceof Parcelable) {
                return ((Parcelable) obj).describeContents();
            }
            return 0;
        }
    }
}
