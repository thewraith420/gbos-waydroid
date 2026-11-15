package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class AvailableAfcChannelInfo implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.AvailableAfcChannelInfo.1
        @Override // android.os.Parcelable.Creator
        public AvailableAfcChannelInfo createFromParcel(Parcel parcel) {
            AvailableAfcChannelInfo availableAfcChannelInfo = new AvailableAfcChannelInfo();
            availableAfcChannelInfo.readFromParcel(parcel);
            return availableAfcChannelInfo;
        }

        @Override // android.os.Parcelable.Creator
        public AvailableAfcChannelInfo[] newArray(int i) {
            return new AvailableAfcChannelInfo[i];
        }
    };
    public int globalOperatingClass = 0;
    public int channelCfi = 0;
    public int maxEirpDbm = 0;

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
        parcel.writeInt(this.globalOperatingClass);
        parcel.writeInt(this.channelCfi);
        parcel.writeInt(this.maxEirpDbm);
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
                this.globalOperatingClass = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.channelCfi = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.maxEirpDbm = parcel.readInt();
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
