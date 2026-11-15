package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanBootstrappingRequest implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanBootstrappingRequest.1
        @Override // android.os.Parcelable.Creator
        public NanBootstrappingRequest createFromParcel(Parcel parcel) {
            NanBootstrappingRequest nanBootstrappingRequest = new NanBootstrappingRequest();
            nanBootstrappingRequest.readFromParcel(parcel);
            return nanBootstrappingRequest;
        }

        @Override // android.os.Parcelable.Creator
        public NanBootstrappingRequest[] newArray(int i) {
            return new NanBootstrappingRequest[i];
        }
    };
    public byte[] cookie = new byte[0];
    public byte[] peerDiscMacAddr = new byte[0];
    public int requestBootstrappingMethod;
    public byte[] serviceSpecificInfo = new byte[0];
    public int peerId = 0;
    public boolean isComeback = false;
    public byte discoverySessionId = 0;

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
        parcel.writeInt(this.peerId);
        parcel.writeFixedArray(this.peerDiscMacAddr, i, 6);
        parcel.writeInt(this.requestBootstrappingMethod);
        parcel.writeByteArray(this.cookie);
        parcel.writeBoolean(this.isComeback);
        parcel.writeByte(this.discoverySessionId);
        parcel.writeByteArray(this.serviceSpecificInfo);
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
                this.peerId = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.peerDiscMacAddr = (byte[]) parcel.createFixedArray(byte[].class, 6);
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.requestBootstrappingMethod = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.cookie = parcel.createByteArray();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.isComeback = parcel.readBoolean();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.discoverySessionId = parcel.readByte();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.serviceSpecificInfo = parcel.createByteArray();
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
