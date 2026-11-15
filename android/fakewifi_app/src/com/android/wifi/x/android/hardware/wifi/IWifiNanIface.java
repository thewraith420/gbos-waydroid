package com.android.wifi.x.android.hardware.wifi;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* loaded from: classes.dex */
public interface IWifiNanIface extends IInterface {
    public static final String DESCRIPTOR = "android$hardware$wifi$IWifiNanIface".replace('$', '.');

    void configRequest(char c, NanConfigRequest nanConfigRequest, NanConfigRequestSupplemental nanConfigRequestSupplemental) throws android.os.RemoteException;

    void createDataInterfaceRequest(char c, String str) throws android.os.RemoteException;

    void deleteDataInterfaceRequest(char c, String str) throws android.os.RemoteException;

    void disableRequest(char c) throws android.os.RemoteException;

    void enableRequest(char c, NanEnableRequest nanEnableRequest, NanConfigRequestSupplemental nanConfigRequestSupplemental) throws android.os.RemoteException;

    void getCapabilitiesRequest(char c) throws android.os.RemoteException;

    String getName() throws android.os.RemoteException;

    void initiateBootstrappingRequest(char c, NanBootstrappingRequest nanBootstrappingRequest) throws android.os.RemoteException;

    void initiateDataPathRequest(char c, NanInitiateDataPathRequest nanInitiateDataPathRequest) throws android.os.RemoteException;

    void initiatePairingRequest(char c, NanPairingRequest nanPairingRequest) throws android.os.RemoteException;

    void registerEventCallback(IWifiNanIfaceEventCallback iWifiNanIfaceEventCallback) throws android.os.RemoteException;

    void respondToBootstrappingIndicationRequest(char c, NanBootstrappingResponse nanBootstrappingResponse) throws android.os.RemoteException;

    void respondToDataPathIndicationRequest(char c, NanRespondToDataPathIndicationRequest nanRespondToDataPathIndicationRequest) throws android.os.RemoteException;

    void respondToPairingIndicationRequest(char c, NanRespondToPairingIndicationRequest nanRespondToPairingIndicationRequest) throws android.os.RemoteException;

    void resumeRequest(char c, byte b) throws android.os.RemoteException;

    void startPublishRequest(char c, NanPublishRequest nanPublishRequest) throws android.os.RemoteException;

    void startSubscribeRequest(char c, NanSubscribeRequest nanSubscribeRequest) throws android.os.RemoteException;

    void stopPublishRequest(char c, byte b) throws android.os.RemoteException;

    void stopSubscribeRequest(char c, byte b) throws android.os.RemoteException;

    void suspendRequest(char c, byte b) throws android.os.RemoteException;

    void terminateDataPathRequest(char c, int i) throws android.os.RemoteException;

    void terminatePairingRequest(char c, int i) throws android.os.RemoteException;

    void transmitFollowupRequest(char c, NanTransmitFollowupRequest nanTransmitFollowupRequest) throws android.os.RemoteException;

    public abstract class Stub extends Binder implements IWifiNanIface {
        public static IWifiNanIface asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(IWifiNanIface.DESCRIPTOR);
            if (queryLocalInterface != null && (queryLocalInterface instanceof IWifiNanIface)) {
                return (IWifiNanIface) queryLocalInterface;
            }
            return new Proxy(iBinder);
        }

        static final class Proxy implements IWifiNanIface {
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

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public String getName() throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
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

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void configRequest(char c, NanConfigRequest nanConfigRequest, NanConfigRequestSupplemental nanConfigRequestSupplemental) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeTypedObject(nanConfigRequest, 0);
                    obtain.writeTypedObject(nanConfigRequestSupplemental, 0);
                    if (!this.mRemote.transact(2, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void createDataInterfaceRequest(char c, String str) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(3, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void deleteDataInterfaceRequest(char c, String str) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(4, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void disableRequest(char c) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    if (!this.mRemote.transact(5, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void enableRequest(char c, NanEnableRequest nanEnableRequest, NanConfigRequestSupplemental nanConfigRequestSupplemental) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeTypedObject(nanEnableRequest, 0);
                    obtain.writeTypedObject(nanConfigRequestSupplemental, 0);
                    if (!this.mRemote.transact(6, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void getCapabilitiesRequest(char c) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    if (!this.mRemote.transact(7, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void initiateDataPathRequest(char c, NanInitiateDataPathRequest nanInitiateDataPathRequest) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeTypedObject(nanInitiateDataPathRequest, 0);
                    if (!this.mRemote.transact(8, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void registerEventCallback(IWifiNanIfaceEventCallback iWifiNanIfaceEventCallback) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeStrongInterface(iWifiNanIfaceEventCallback);
                    if (!this.mRemote.transact(9, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void respondToDataPathIndicationRequest(char c, NanRespondToDataPathIndicationRequest nanRespondToDataPathIndicationRequest) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeTypedObject(nanRespondToDataPathIndicationRequest, 0);
                    if (!this.mRemote.transact(10, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void startPublishRequest(char c, NanPublishRequest nanPublishRequest) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeTypedObject(nanPublishRequest, 0);
                    if (!this.mRemote.transact(11, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void startSubscribeRequest(char c, NanSubscribeRequest nanSubscribeRequest) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeTypedObject(nanSubscribeRequest, 0);
                    if (!this.mRemote.transact(12, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void stopPublishRequest(char c, byte b) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeByte(b);
                    if (!this.mRemote.transact(13, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void stopSubscribeRequest(char c, byte b) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeByte(b);
                    if (!this.mRemote.transact(14, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void terminateDataPathRequest(char c, int i) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(15, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void suspendRequest(char c, byte b) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeByte(b);
                    if (!this.mRemote.transact(16, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void resumeRequest(char c, byte b) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeByte(b);
                    if (!this.mRemote.transact(17, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void transmitFollowupRequest(char c, NanTransmitFollowupRequest nanTransmitFollowupRequest) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeTypedObject(nanTransmitFollowupRequest, 0);
                    if (!this.mRemote.transact(18, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void initiatePairingRequest(char c, NanPairingRequest nanPairingRequest) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeTypedObject(nanPairingRequest, 0);
                    if (!this.mRemote.transact(19, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void respondToPairingIndicationRequest(char c, NanRespondToPairingIndicationRequest nanRespondToPairingIndicationRequest) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeTypedObject(nanRespondToPairingIndicationRequest, 0);
                    if (!this.mRemote.transact(20, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void initiateBootstrappingRequest(char c, NanBootstrappingRequest nanBootstrappingRequest) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeTypedObject(nanBootstrappingRequest, 0);
                    if (!this.mRemote.transact(21, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void respondToBootstrappingIndicationRequest(char c, NanBootstrappingResponse nanBootstrappingResponse) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeTypedObject(nanBootstrappingResponse, 0);
                    if (!this.mRemote.transact(22, obtain, obtain2, 0)) {
                        throw new RemoteException("Unimplemented");
                    }
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.android.wifi.x.android.hardware.wifi.IWifiNanIface
            public void terminatePairingRequest(char c, int i) throws android.os.RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IWifiNanIface.DESCRIPTOR);
                    obtain.writeInt(c);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(23, obtain, obtain2, 0)) {
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
