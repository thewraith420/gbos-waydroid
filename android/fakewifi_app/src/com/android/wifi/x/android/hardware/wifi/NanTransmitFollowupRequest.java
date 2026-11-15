package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanTransmitFollowupRequest implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanTransmitFollowupRequest.1
        @Override // android.os.Parcelable.Creator
        public NanTransmitFollowupRequest createFromParcel(Parcel parcel) {
            NanTransmitFollowupRequest nanTransmitFollowupRequest = new NanTransmitFollowupRequest();
            nanTransmitFollowupRequest.readFromParcel(parcel);
            return nanTransmitFollowupRequest;
        }

        @Override // android.os.Parcelable.Creator
        public NanTransmitFollowupRequest[] newArray(int i) {
            return new NanTransmitFollowupRequest[i];
        }
    };
    public byte[] addr = new byte[0];
    public byte[] extendedServiceSpecificInfo = new byte[0];
    public byte[] serviceSpecificInfo = new byte[0];
    public byte discoverySessionId = 0;
    public int peerId = 0;
    public boolean isHighPriority = false;
    public boolean shouldUseDiscoveryWindow = false;
    public boolean disableFollowupResultIndication = false;

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
        parcel.writeByte(this.discoverySessionId);
        parcel.writeInt(this.peerId);
        parcel.writeFixedArray(this.addr, i, 6);
        parcel.writeBoolean(this.isHighPriority);
        parcel.writeBoolean(this.shouldUseDiscoveryWindow);
        parcel.writeByteArray(this.serviceSpecificInfo);
        parcel.writeByteArray(this.extendedServiceSpecificInfo);
        parcel.writeBoolean(this.disableFollowupResultIndication);
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
                this.discoverySessionId = parcel.readByte();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.peerId = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.addr = (byte[]) parcel.createFixedArray(byte[].class, 6);
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.isHighPriority = parcel.readBoolean();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.shouldUseDiscoveryWindow = parcel.readBoolean();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.serviceSpecificInfo = parcel.createByteArray();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.extendedServiceSpecificInfo = parcel.createByteArray();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.disableFollowupResultIndication = parcel.readBoolean();
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
