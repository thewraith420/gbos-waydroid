package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class WifiChipCapabilities implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.WifiChipCapabilities.1
        @Override // android.os.Parcelable.Creator
        public WifiChipCapabilities createFromParcel(Parcel parcel) {
            WifiChipCapabilities wifiChipCapabilities = new WifiChipCapabilities();
            wifiChipCapabilities.readFromParcel(parcel);
            return wifiChipCapabilities;
        }

        @Override // android.os.Parcelable.Creator
        public WifiChipCapabilities[] newArray(int i) {
            return new WifiChipCapabilities[i];
        }
    };
    public int maxMloAssociationLinkCount = 0;
    public int maxMloStrLinkCount = 0;
    public int maxConcurrentTdlsSessionCount = 0;

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
        parcel.writeInt(this.maxMloAssociationLinkCount);
        parcel.writeInt(this.maxMloStrLinkCount);
        parcel.writeInt(this.maxConcurrentTdlsSessionCount);
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
                this.maxMloAssociationLinkCount = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.maxMloStrLinkCount = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.maxConcurrentTdlsSessionCount = parcel.readInt();
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
