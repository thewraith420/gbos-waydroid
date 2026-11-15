package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanDataPathRequestInd implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanDataPathRequestInd.1
        @Override // android.os.Parcelable.Creator
        public NanDataPathRequestInd createFromParcel(Parcel parcel) {
            NanDataPathRequestInd nanDataPathRequestInd = new NanDataPathRequestInd();
            nanDataPathRequestInd.readFromParcel(parcel);
            return nanDataPathRequestInd;
        }

        @Override // android.os.Parcelable.Creator
        public NanDataPathRequestInd[] newArray(int i) {
            return new NanDataPathRequestInd[i];
        }
    };
    public byte[] appInfo = new byte[0];
    public byte[] peerDiscMacAddr = new byte[0];
    public byte discoverySessionId = 0;
    public int ndpInstanceId = 0;
    public boolean securityRequired = false;

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
        parcel.writeFixedArray(this.peerDiscMacAddr, i, 6);
        parcel.writeInt(this.ndpInstanceId);
        parcel.writeBoolean(this.securityRequired);
        parcel.writeByteArray(this.appInfo);
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
                    this.peerDiscMacAddr = (byte[]) parcel.createFixedArray(byte[].class, 6);
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.ndpInstanceId = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.securityRequired = parcel.readBoolean();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.appInfo = parcel.createByteArray();
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
