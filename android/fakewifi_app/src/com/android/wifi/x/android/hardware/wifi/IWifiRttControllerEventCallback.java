package com.android.wifi.x.android.hardware.wifi;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;

/* loaded from: classes.dex */
public interface IWifiRttControllerEventCallback extends IInterface {
    public static final String DESCRIPTOR = "android$hardware$wifi$IWifiRttControllerEventCallback".replace('$', '.');

    String getInterfaceHash() throws android.os.RemoteException;

    int getInterfaceVersion() throws android.os.RemoteException;

    void onResults(int i, RttResult[] rttResultArr) throws android.os.RemoteException;

    public abstract class Stub extends Binder implements IWifiRttControllerEventCallback {
        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        public Stub() {
            markVintfStability();
            attachInterface(this, IWifiRttControllerEventCallback.DESCRIPTOR);
        }

        @Override // android.os.Binder
        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws android.os.RemoteException {
            if (i >= 1 && i <= 16777215) {
                parcel.enforceInterface(IWifiRttControllerEventCallback.DESCRIPTOR);
            }
            if (i == 16777215) {
                parcel2.writeNoException();
                parcel2.writeInt(getInterfaceVersion());
                return true;
            }
            if (i == 16777214) {
                parcel2.writeNoException();
                parcel2.writeString(getInterfaceHash());
                return true;
            }
            if (i == 1) {
                onResults(parcel.readInt(), (RttResult[]) parcel.createTypedArray(RttResult.CREATOR));
                return true;
            }
            return super.onTransact(i, parcel, parcel2, i2);
        }
    }
}
