package com.android.wifi.x.android.hardware.wifi;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.android.wifi.x.android.hardware.wifi.IWifiChip;

/* loaded from: classes.dex */
public interface IWifi extends IInterface {
    public static final String DESCRIPTOR = "android$hardware$wifi$IWifi".replace('$', '.');

    IWifiChip getChip(int i) throws android.os.RemoteException;

    int[] getChipIds() throws android.os.RemoteException;

    int getInterfaceVersion() throws android.os.RemoteException;

    boolean isStarted() throws android.os.RemoteException;

    void registerEventCallback(IWifiEventCallback iWifiEventCallback) throws android.os.RemoteException;

    void start() throws android.os.RemoteException;

    void stop() throws android.os.RemoteException;

    public abstract class Stub extends Binder implements IWifi {
        public static IWifi asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(IWifi.DESCRIPTOR);
            if (queryLocalInterface != null && (queryLocalInterface instanceof IWifi)) {
                return (IWifi) queryLocalInterface;
            }
            return new Proxy(iBinder);
        }

        static final class Proxy implements IWifi {
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

            @Override // com.android.wifi.x.android.hardware.wifi.IWifi
            public IWifiChip getChip(int i) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifi.DESCRIPTOR);
                    obtain.writeInt(i);
                    boolean transact = this.mRemote.transact(1, obtain, obtain2, 0);
                    if (Build.VERSION.SDK_INT >= 33) {
                        obtain2.setPropagateAllowBlocking();
                    }
                    if (!transact) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    IWifiChip asInterface = IWifiChip.Stub.asInterface(obtain2.readStrongBinder());
                    obtain2.recycle();
                    obtain.recycle();
                    return asInterface;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                    throw th;
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifi
            public int[] getChipIds() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifi.DESCRIPTOR);
                    if (!this.mRemote.transact(2, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.createIntArray();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifi
            public boolean isStarted() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifi.DESCRIPTOR);
                    if (!this.mRemote.transact(3, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.readBoolean();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifi
            public void registerEventCallback(IWifiEventCallback iWifiEventCallback) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifi.DESCRIPTOR);
                    obtain.writeStrongInterface(iWifiEventCallback);
                    if (!this.mRemote.transact(4, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifi
            public void start() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifi.DESCRIPTOR);
                    if (!this.mRemote.transact(5, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifi
            public void stop() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifi.DESCRIPTOR);
                    if (!this.mRemote.transact(6, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifi
            public int getInterfaceVersion() throws android.os.RemoteException {
                if (this.mCachedVersion == -1) {
                    Parcel obtain = Parcel.obtain();
                    Parcel obtain2 = Parcel.obtain();
                    try {
                        obtain.writeInterfaceToken(IWifi.DESCRIPTOR);
                        this.mRemote.transact(16777215, obtain, obtain2, 0);
                        obtain2.readException();
                        this.mCachedVersion = obtain2.readInt();
                    } finally {
                        obtain2.recycle();
                        obtain.recycle();
                    }
                }
                return this.mCachedVersion;
            }
        }
    }
}
