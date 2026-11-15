package com.android.wifi.x.android.hardware.wifi;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;

/* loaded from: classes.dex */
public interface IWifiNanIfaceEventCallback extends IInterface {
    public static final String DESCRIPTOR = "android$hardware$wifi$IWifiNanIfaceEventCallback".replace('$', '.');

    void eventBootstrappingConfirm(NanBootstrappingConfirmInd nanBootstrappingConfirmInd) throws android.os.RemoteException;

    void eventBootstrappingRequest(NanBootstrappingRequestInd nanBootstrappingRequestInd) throws android.os.RemoteException;

    void eventClusterEvent(NanClusterEventInd nanClusterEventInd) throws android.os.RemoteException;

    void eventDataPathConfirm(NanDataPathConfirmInd nanDataPathConfirmInd) throws android.os.RemoteException;

    void eventDataPathRequest(NanDataPathRequestInd nanDataPathRequestInd) throws android.os.RemoteException;

    void eventDataPathScheduleUpdate(NanDataPathScheduleUpdateInd nanDataPathScheduleUpdateInd) throws android.os.RemoteException;

    void eventDataPathTerminated(int i) throws android.os.RemoteException;

    void eventDisabled(NanStatus nanStatus) throws android.os.RemoteException;

    void eventFollowupReceived(NanFollowupReceivedInd nanFollowupReceivedInd) throws android.os.RemoteException;

    void eventMatch(NanMatchInd nanMatchInd) throws android.os.RemoteException;

    void eventMatchExpired(byte b, int i) throws android.os.RemoteException;

    void eventPairingConfirm(NanPairingConfirmInd nanPairingConfirmInd) throws android.os.RemoteException;

    void eventPairingRequest(NanPairingRequestInd nanPairingRequestInd) throws android.os.RemoteException;

    void eventPublishTerminated(byte b, NanStatus nanStatus) throws android.os.RemoteException;

    void eventSubscribeTerminated(byte b, NanStatus nanStatus) throws android.os.RemoteException;

    void eventSuspensionModeChanged(NanSuspensionModeChangeInd nanSuspensionModeChangeInd) throws android.os.RemoteException;

    void eventTransmitFollowup(char c, NanStatus nanStatus) throws android.os.RemoteException;

    String getInterfaceHash() throws android.os.RemoteException;

    int getInterfaceVersion() throws android.os.RemoteException;

    void notifyCapabilitiesResponse(char c, NanStatus nanStatus, NanCapabilities nanCapabilities) throws android.os.RemoteException;

    void notifyConfigResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    void notifyCreateDataInterfaceResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    void notifyDeleteDataInterfaceResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    void notifyDisableResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    void notifyEnableResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    void notifyInitiateBootstrappingResponse(char c, NanStatus nanStatus, int i) throws android.os.RemoteException;

    void notifyInitiateDataPathResponse(char c, NanStatus nanStatus, int i) throws android.os.RemoteException;

    void notifyInitiatePairingResponse(char c, NanStatus nanStatus, int i) throws android.os.RemoteException;

    void notifyRangingResults(RttResult[] rttResultArr, byte b) throws android.os.RemoteException;

    void notifyRespondToBootstrappingIndicationResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    void notifyRespondToDataPathIndicationResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    void notifyRespondToPairingIndicationResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    void notifyResumeResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    void notifyStartPublishResponse(char c, NanStatus nanStatus, byte b) throws android.os.RemoteException;

    void notifyStartSubscribeResponse(char c, NanStatus nanStatus, byte b) throws android.os.RemoteException;

    void notifyStopPublishResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    void notifyStopSubscribeResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    void notifySuspendResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    void notifyTerminateDataPathResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    void notifyTerminatePairingResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    void notifyTransmitFollowupResponse(char c, NanStatus nanStatus) throws android.os.RemoteException;

    public abstract class Stub extends Binder implements IWifiNanIfaceEventCallback {
        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        public Stub() {
            markVintfStability();
            attachInterface(this, IWifiNanIfaceEventCallback.DESCRIPTOR);
        }

        @Override // android.os.Binder
        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws android.os.RemoteException {
            if (i >= 1 && i <= 16777215) {
                parcel.enforceInterface(IWifiNanIfaceEventCallback.DESCRIPTOR);
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
                    eventClusterEvent((NanClusterEventInd) parcel.readTypedObject(NanClusterEventInd.CREATOR));
                    return true;
                case 2:
                    eventDataPathConfirm((NanDataPathConfirmInd) parcel.readTypedObject(NanDataPathConfirmInd.CREATOR));
                    return true;
                case 3:
                    eventDataPathRequest((NanDataPathRequestInd) parcel.readTypedObject(NanDataPathRequestInd.CREATOR));
                    return true;
                case 4:
                    eventDataPathScheduleUpdate((NanDataPathScheduleUpdateInd) parcel.readTypedObject(NanDataPathScheduleUpdateInd.CREATOR));
                    return true;
                case 5:
                    eventDataPathTerminated(parcel.readInt());
                    return true;
                case 6:
                    eventDisabled((NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 7:
                    eventFollowupReceived((NanFollowupReceivedInd) parcel.readTypedObject(NanFollowupReceivedInd.CREATOR));
                    return true;
                case 8:
                    eventMatch((NanMatchInd) parcel.readTypedObject(NanMatchInd.CREATOR));
                    return true;
                case 9:
                    eventMatchExpired(parcel.readByte(), parcel.readInt());
                    return true;
                case 10:
                    eventPublishTerminated(parcel.readByte(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 11:
                    eventSubscribeTerminated(parcel.readByte(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 12:
                    eventTransmitFollowup((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 13:
                    eventSuspensionModeChanged((NanSuspensionModeChangeInd) parcel.readTypedObject(NanSuspensionModeChangeInd.CREATOR));
                    return true;
                case 14:
                    notifyCapabilitiesResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR), (NanCapabilities) parcel.readTypedObject(NanCapabilities.CREATOR));
                    return true;
                case 15:
                    notifyConfigResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 16:
                    notifyCreateDataInterfaceResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 17:
                    notifyDeleteDataInterfaceResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 18:
                    notifyDisableResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 19:
                    notifyEnableResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 20:
                    notifyInitiateDataPathResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR), parcel.readInt());
                    return true;
                case 21:
                    notifyRespondToDataPathIndicationResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 22:
                    notifyStartPublishResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR), parcel.readByte());
                    return true;
                case 23:
                    notifyStartSubscribeResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR), parcel.readByte());
                    return true;
                case 24:
                    notifyStopPublishResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 25:
                    notifyStopSubscribeResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 26:
                    notifyTerminateDataPathResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 27:
                    notifySuspendResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 28:
                    notifyResumeResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 29:
                    notifyTransmitFollowupResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 30:
                    eventPairingRequest((NanPairingRequestInd) parcel.readTypedObject(NanPairingRequestInd.CREATOR));
                    return true;
                case 31:
                    eventPairingConfirm((NanPairingConfirmInd) parcel.readTypedObject(NanPairingConfirmInd.CREATOR));
                    return true;
                case 32:
                    notifyInitiatePairingResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR), parcel.readInt());
                    return true;
                case 33:
                    notifyRespondToPairingIndicationResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 34:
                    eventBootstrappingRequest((NanBootstrappingRequestInd) parcel.readTypedObject(NanBootstrappingRequestInd.CREATOR));
                    return true;
                case 35:
                    eventBootstrappingConfirm((NanBootstrappingConfirmInd) parcel.readTypedObject(NanBootstrappingConfirmInd.CREATOR));
                    return true;
                case 36:
                    notifyInitiateBootstrappingResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR), parcel.readInt());
                    return true;
                case 37:
                    notifyRespondToBootstrappingIndicationResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 38:
                    notifyTerminatePairingResponse((char) parcel.readInt(), (NanStatus) parcel.readTypedObject(NanStatus.CREATOR));
                    return true;
                case 39:
                    notifyRangingResults((RttResult[]) parcel.createTypedArray(RttResult.CREATOR), parcel.readByte());
                    return true;
                default:
                    return super.onTransact(i, parcel, parcel2, i2);
            }
        }
    }
}
