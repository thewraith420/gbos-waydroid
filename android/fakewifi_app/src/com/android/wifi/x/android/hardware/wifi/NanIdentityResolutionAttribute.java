package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanIdentityResolutionAttribute implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanIdentityResolutionAttribute.1
        @Override // android.os.Parcelable.Creator
        public NanIdentityResolutionAttribute createFromParcel(Parcel parcel) {
            NanIdentityResolutionAttribute nanIdentityResolutionAttribute = new NanIdentityResolutionAttribute();
            nanIdentityResolutionAttribute.readFromParcel(parcel);
            return nanIdentityResolutionAttribute;
        }

        @Override // android.os.Parcelable.Creator
        public NanIdentityResolutionAttribute[] newArray(int i) {
            return new NanIdentityResolutionAttribute[i];
        }
    };
    public byte[] nonce = new byte[0];
    public byte[] tag = new byte[0];

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
        parcel.writeFixedArray(this.nonce, i, 8);
        parcel.writeFixedArray(this.tag, i, 8);
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
                this.nonce = (byte[]) parcel.createFixedArray(byte[].class, 8);
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.tag = (byte[]) parcel.createFixedArray(byte[].class, 8);
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
