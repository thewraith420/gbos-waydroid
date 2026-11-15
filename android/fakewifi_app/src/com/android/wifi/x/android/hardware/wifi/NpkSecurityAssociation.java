package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NpkSecurityAssociation implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NpkSecurityAssociation.1
        @Override // android.os.Parcelable.Creator
        public NpkSecurityAssociation createFromParcel(Parcel parcel) {
            NpkSecurityAssociation npkSecurityAssociation = new NpkSecurityAssociation();
            npkSecurityAssociation.readFromParcel(parcel);
            return npkSecurityAssociation;
        }

        @Override // android.os.Parcelable.Creator
        public NpkSecurityAssociation[] newArray(int i) {
            return new NpkSecurityAssociation[i];
        }
    };
    public int akm;
    public int cipherType;
    public byte[] localNanIdentityKey = new byte[0];
    public byte[] npk = new byte[0];
    public byte[] peerNanIdentityKey = new byte[0];

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
        parcel.writeFixedArray(this.peerNanIdentityKey, i, 16);
        parcel.writeFixedArray(this.localNanIdentityKey, i, 16);
        parcel.writeFixedArray(this.npk, i, 32);
        parcel.writeInt(this.akm);
        parcel.writeInt(this.cipherType);
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
                this.peerNanIdentityKey = (byte[]) parcel.createFixedArray(byte[].class, 16);
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.localNanIdentityKey = (byte[]) parcel.createFixedArray(byte[].class, 16);
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.npk = (byte[]) parcel.createFixedArray(byte[].class, 32);
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.akm = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.cipherType = parcel.readInt();
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
