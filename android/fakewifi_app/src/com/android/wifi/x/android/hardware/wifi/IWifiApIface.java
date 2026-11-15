package com.android.wifi.x.android.hardware.wifi;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* loaded from: classes.dex */
public interface IWifiApIface extends IInterface {
    public static final String DESCRIPTOR = "android$hardware$wifi$IWifiApIface".replace('$', '.');

    String[] getBridgedInstances() throws android.os.RemoteException;

    String getName() throws android.os.RemoteException;

    void resetToFactoryMacAddress() throws android.os.RemoteException;

    void setCountryCode(byte[] bArr) throws android.os.RemoteException;

    void setMacAddress(byte[] bArr) throws android.os.RemoteException;

    public abstract class Stub extends Binder implements IWifiApIface {
        public static IWifiApIface asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(IWifiApIface.DESCRIPTOR);
            if (queryLocalInterface != null && (queryLocalInterface instanceof IWifiApIface)) {
                return (IWifiApIface) queryLocalInterface;
            }
            return new Proxy(iBinder);
        }

        static final class Proxy implements IWifiApIface {
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

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiApIface
            public String getName() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiApIface.DESCRIPTOR);
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

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiApIface
            public String[] getBridgedInstances() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiApIface.DESCRIPTOR);
                    if (!this.mRemote.transact(2, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return obtain2.createStringArray();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiApIface
            public void setCountryCode(byte[] bArr) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiApIface.DESCRIPTOR);
                    obtain.writeFixedArray(bArr, 0, 2);
                    if (!this.mRemote.transact(4, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiApIface
            public void resetToFactoryMacAddress() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiApIface.DESCRIPTOR);
                    if (!this.mRemote.transact(5, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiApIface
            public void setMacAddress(byte[] bArr) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiApIface.DESCRIPTOR);
                    obtain.writeFixedArray(bArr, 0, 6);
                    if (!this.mRemote.transact(6, obtain, obtain2, 0)) {
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
