package com.android.wifi.x.android.hardware.wifi;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;

/* loaded from: classes.dex */
public interface IWifiEventCallback extends IInterface {
    public static final String DESCRIPTOR = "android$hardware$wifi$IWifiEventCallback".replace('$', '.');

    String getInterfaceHash() throws android.os.RemoteException;

    int getInterfaceVersion() throws android.os.RemoteException;

    void onFailure(int i) throws android.os.RemoteException;

    void onStart() throws android.os.RemoteException;

    void onStop() throws android.os.RemoteException;

    void onSubsystemRestart(int i) throws android.os.RemoteException;

    public abstract class Stub extends Binder implements IWifiEventCallback {
        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        public Stub() {
            markVintfStability();
            attachInterface(this, IWifiEventCallback.DESCRIPTOR);
        }

        @Override // android.os.Binder
        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws android.os.RemoteException {
            if (i >= 1 && i <= 16777215) {
                parcel.enforceInterface(IWifiEventCallback.DESCRIPTOR);
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
                onFailure(parcel.readInt());
            } else if (i == 2) {
                onStart();
            } else if (i == 3) {
                onStop();
            } else if (i == 4) {
                onSubsystemRestart(parcel.readInt());
            } else {
                return super.onTransact(i, parcel, parcel2, i2);
            }
            return true;
        }
    }
}
