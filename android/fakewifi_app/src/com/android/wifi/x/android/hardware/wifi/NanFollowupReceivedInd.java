package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanFollowupReceivedInd implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanFollowupReceivedInd.1
        @Override // android.os.Parcelable.Creator
        public NanFollowupReceivedInd createFromParcel(Parcel parcel) {
            NanFollowupReceivedInd nanFollowupReceivedInd = new NanFollowupReceivedInd();
            nanFollowupReceivedInd.readFromParcel(parcel);
            return nanFollowupReceivedInd;
        }

        @Override // android.os.Parcelable.Creator
        public NanFollowupReceivedInd[] newArray(int i) {
            return new NanFollowupReceivedInd[i];
        }
    };
    public byte[] addr = new byte[0];
    public byte[] extendedServiceSpecificInfo = new byte[0];
    public byte[] serviceSpecificInfo = new byte[0];
    public byte discoverySessionId = 0;
    public int peerId = 0;
    public boolean receivedInFaw = false;

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
        parcel.writeBoolean(this.receivedInFaw);
        parcel.writeByteArray(this.serviceSpecificInfo);
        parcel.writeByteArray(this.extendedServiceSpecificInfo);
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
                            this.receivedInFaw = parcel.readBoolean();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.serviceSpecificInfo = parcel.createByteArray();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.extendedServiceSpecificInfo = parcel.createByteArray();
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
