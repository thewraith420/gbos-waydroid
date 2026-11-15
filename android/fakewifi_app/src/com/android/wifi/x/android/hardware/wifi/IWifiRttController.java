package com.android.wifi.x.android.hardware.wifi;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.android.wifi.x.android.hardware.wifi.IWifiStaIface;

/* loaded from: classes.dex */
public interface IWifiRttController extends IInterface {
    public static final String DESCRIPTOR = "android$hardware$wifi$IWifiRttController".replace('$', '.');

    IWifiStaIface getBoundIface() throws android.os.RemoteException;

    RttCapabilities getCapabilities() throws android.os.RemoteException;

    void rangeCancel(int i, MacAddress[] macAddressArr) throws android.os.RemoteException;

    void rangeRequest(int i, RttConfig[] rttConfigArr) throws android.os.RemoteException;

    void registerEventCallback(IWifiRttControllerEventCallback iWifiRttControllerEventCallback) throws android.os.RemoteException;

    public abstract class Stub extends Binder implements IWifiRttController {
        public static IWifiRttController asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(IWifiRttController.DESCRIPTOR);
            if (queryLocalInterface != null && (queryLocalInterface instanceof IWifiRttController)) {
                return (IWifiRttController) queryLocalInterface;
            }
            return new Proxy(iBinder);
        }

        static final class Proxy implements IWifiRttController {
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

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiRttController
            public IWifiStaIface getBoundIface() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiRttController.DESCRIPTOR);
                    if (!this.mRemote.transact(3, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return IWifiStaIface.Stub.asInterface(obtain2.readStrongBinder());
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiRttController
            public RttCapabilities getCapabilities() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiRttController.DESCRIPTOR);
                    if (!this.mRemote.transact(4, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                    return (RttCapabilities) obtain2.readTypedObject(RttCapabilities.CREATOR);
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiRttController
            public void rangeCancel(int i, MacAddress[] macAddressArr) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiRttController.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeTypedArray(macAddressArr, 0);
                    if (!this.mRemote.transact(6, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiRttController
            public void rangeRequest(int i, RttConfig[] rttConfigArr) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiRttController.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeTypedArray(rttConfigArr, 0);
                    if (!this.mRemote.transact(7, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiRttController
            public void registerEventCallback(IWifiRttControllerEventCallback iWifiRttControllerEventCallback) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiRttController.DESCRIPTOR);
                    obtain.writeStrongInterface(iWifiRttControllerEventCallback);
                    if (!this.mRemote.transact(8, obtain, obtain2, 0)) {
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
