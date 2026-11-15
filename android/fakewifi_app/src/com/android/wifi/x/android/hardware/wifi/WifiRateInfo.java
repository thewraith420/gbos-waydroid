package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class WifiRateInfo implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.WifiRateInfo.1
        @Override // android.os.Parcelable.Creator
        public WifiRateInfo createFromParcel(Parcel parcel) {
            WifiRateInfo wifiRateInfo = new WifiRateInfo();
            wifiRateInfo.readFromParcel(parcel);
            return wifiRateInfo;
        }

        @Override // android.os.Parcelable.Creator
        public WifiRateInfo[] newArray(int i) {
            return new WifiRateInfo[i];
        }
    };
    public int bw;
    public int nss;
    public int preamble;
    public byte rateMcsIdx = 0;
    public int bitRateInKbps = 0;

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
        parcel.writeInt(this.preamble);
        parcel.writeInt(this.nss);
        parcel.writeInt(this.bw);
        parcel.writeByte(this.rateMcsIdx);
        parcel.writeInt(this.bitRateInKbps);
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
                this.preamble = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.nss = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.bw = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.rateMcsIdx = parcel.readByte();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.bitRateInKbps = parcel.readInt();
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
