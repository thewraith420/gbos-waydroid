package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class WifiChannelInfo implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.WifiChannelInfo.1
        @Override // android.os.Parcelable.Creator
        public WifiChannelInfo createFromParcel(Parcel parcel) {
            WifiChannelInfo wifiChannelInfo = new WifiChannelInfo();
            wifiChannelInfo.readFromParcel(parcel);
            return wifiChannelInfo;
        }

        @Override // android.os.Parcelable.Creator
        public WifiChannelInfo[] newArray(int i) {
            return new WifiChannelInfo[i];
        }
    };
    public int centerFreq = 0;
    public int centerFreq0 = 0;
    public int centerFreq1 = 0;
    public int width;

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
        parcel.writeInt(this.width);
        parcel.writeInt(this.centerFreq);
        parcel.writeInt(this.centerFreq0);
        parcel.writeInt(this.centerFreq1);
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
                this.width = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.centerFreq = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.centerFreq0 = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.centerFreq1 = parcel.readInt();
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
