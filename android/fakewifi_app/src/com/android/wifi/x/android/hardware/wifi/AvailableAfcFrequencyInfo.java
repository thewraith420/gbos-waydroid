package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class AvailableAfcFrequencyInfo implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.AvailableAfcFrequencyInfo.1
        @Override // android.os.Parcelable.Creator
        public AvailableAfcFrequencyInfo createFromParcel(Parcel parcel) {
            AvailableAfcFrequencyInfo availableAfcFrequencyInfo = new AvailableAfcFrequencyInfo();
            availableAfcFrequencyInfo.readFromParcel(parcel);
            return availableAfcFrequencyInfo;
        }

        @Override // android.os.Parcelable.Creator
        public AvailableAfcFrequencyInfo[] newArray(int i) {
            return new AvailableAfcFrequencyInfo[i];
        }
    };
    public int startFrequencyMhz = 0;
    public int endFrequencyMhz = 0;
    public int maxPsd = 0;

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
        parcel.writeInt(this.startFrequencyMhz);
        parcel.writeInt(this.endFrequencyMhz);
        parcel.writeInt(this.maxPsd);
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
                this.startFrequencyMhz = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.endFrequencyMhz = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.maxPsd = parcel.readInt();
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
