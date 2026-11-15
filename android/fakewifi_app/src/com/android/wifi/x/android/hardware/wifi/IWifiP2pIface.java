package com.android.wifi.x.android.hardware.wifi;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* loaded from: classes.dex */
public interface IWifiP2pIface extends IInterface {
    public static final String DESCRIPTOR = "android$hardware$wifi$IWifiP2pIface".replace('$', '.');

    String getName() throws android.os.RemoteException;

    public abstract class Stub extends Binder implements IWifiP2pIface {
        public static IWifiP2pIface asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(IWifiP2pIface.DESCRIPTOR);
            if (queryLocalInterface != null && (queryLocalInterface instanceof IWifiP2pIface)) {
                return (IWifiP2pIface) queryLocalInterface;
            }
            return new Proxy(iBinder);
        }

        static final class Proxy implements IWifiP2pIface {
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

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiP2pIface
            public String getName() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiP2pIface.DESCRIPTOR);
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
        }
    }
}
