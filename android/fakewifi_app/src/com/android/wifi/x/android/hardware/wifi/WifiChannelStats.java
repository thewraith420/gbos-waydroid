package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class WifiChannelStats implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.WifiChannelStats.1
        @Override // android.os.Parcelable.Creator
        public WifiChannelStats createFromParcel(Parcel parcel) {
            WifiChannelStats wifiChannelStats = new WifiChannelStats();
            wifiChannelStats.readFromParcel(parcel);
            return wifiChannelStats;
        }

        @Override // android.os.Parcelable.Creator
        public WifiChannelStats[] newArray(int i) {
            return new WifiChannelStats[i];
        }
    };
    public WifiChannelInfo channel = new WifiChannelInfo();
    public int onTimeInMs = 0;
    public int ccaBusyTimeInMs = 0;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeTypedObject(this.channel, i);
        parcel.writeInt(this.onTimeInMs);
        parcel.writeInt(this.ccaBusyTimeInMs);
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
                this.channel = (WifiChannelInfo) parcel.readTypedObject(WifiChannelInfo.CREATOR);
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.onTimeInMs = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.ccaBusyTimeInMs = parcel.readInt();
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

    @Override // android.os.Parcelable
    public int describeContents() {
        return describeContents(this.channel);
    }

    private int describeContents(Object obj) {
        if (obj != null && (obj instanceof Parcelable)) {
            return ((Parcelable) obj).describeContents();
        }
        return 0;
    }
}
