package com.android.wifi.x.android.hardware.wifi;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;

/* loaded from: classes.dex */
public interface IWifiStaIfaceEventCallback extends IInterface {
    public static final String DESCRIPTOR = "android$hardware$wifi$IWifiStaIfaceEventCallback".replace('$', '.');

    String getInterfaceHash() throws android.os.RemoteException;

    int getInterfaceVersion() throws android.os.RemoteException;

    void onBackgroundFullScanResult(int i, int i2, StaScanResult staScanResult) throws android.os.RemoteException;

    void onBackgroundScanFailure(int i) throws android.os.RemoteException;

    void onBackgroundScanResults(int i, StaScanData[] staScanDataArr) throws android.os.RemoteException;

    void onRssiThresholdBreached(int i, byte[] bArr, int i2) throws android.os.RemoteException;

    void onTwtFailure(int i, byte b) throws android.os.RemoteException;

    void onTwtSessionCreate(int i, TwtSession twtSession) throws android.os.RemoteException;

    void onTwtSessionResume(int i, int i2) throws android.os.RemoteException;

    void onTwtSessionStats(int i, int i2, TwtSessionStats twtSessionStats) throws android.os.RemoteException;

    void onTwtSessionSuspend(int i, int i2) throws android.os.RemoteException;

    void onTwtSessionTeardown(int i, int i2, byte b) throws android.os.RemoteException;

    void onTwtSessionUpdate(int i, TwtSession twtSession) throws android.os.RemoteException;

    public abstract class Stub extends Binder implements IWifiStaIfaceEventCallback {
        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        public Stub() {
            markVintfStability();
            attachInterface(this, IWifiStaIfaceEventCallback.DESCRIPTOR);
        }

        @Override // android.os.Binder
        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws android.os.RemoteException {
            if (i >= 1 && i <= 16777215) {
                parcel.enforceInterface(IWifiStaIfaceEventCallback.DESCRIPTOR);
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
            switch (i) {
                case 1:
                    onBackgroundFullScanResult(parcel.readInt(), parcel.readInt(), (StaScanResult) parcel.readTypedObject(StaScanResult.CREATOR));
                    return true;
                case 2:
                    onBackgroundScanFailure(parcel.readInt());
                    return true;
                case 3:
                    onBackgroundScanResults(parcel.readInt(), (StaScanData[]) parcel.createTypedArray(StaScanData.CREATOR));
                    return true;
                case 4:
                    onRssiThresholdBreached(parcel.readInt(), (byte[]) parcel.createFixedArray(byte[].class, 6), parcel.readInt());
                    return true;
                case 5:
                    onTwtFailure(parcel.readInt(), parcel.readByte());
                    return true;
                case 6:
                    onTwtSessionCreate(parcel.readInt(), (TwtSession) parcel.readTypedObject(TwtSession.CREATOR));
                    return true;
                case 7:
                    onTwtSessionUpdate(parcel.readInt(), (TwtSession) parcel.readTypedObject(TwtSession.CREATOR));
                    return true;
                case 8:
                    onTwtSessionTeardown(parcel.readInt(), parcel.readInt(), parcel.readByte());
                    return true;
                case 9:
                    onTwtSessionStats(parcel.readInt(), parcel.readInt(), (TwtSessionStats) parcel.readTypedObject(TwtSessionStats.CREATOR));
                    return true;
                case 10:
                    onTwtSessionSuspend(parcel.readInt(), parcel.readInt());
                    return true;
                case 11:
                    onTwtSessionResume(parcel.readInt(), parcel.readInt());
                    return true;
                default:
                    return super.onTransact(i, parcel, parcel2, i2);
            }
        }
    }
}
