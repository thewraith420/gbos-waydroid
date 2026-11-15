package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanBandSpecificConfig implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanBandSpecificConfig.1
        @Override // android.os.Parcelable.Creator
        public NanBandSpecificConfig createFromParcel(Parcel parcel) {
            NanBandSpecificConfig nanBandSpecificConfig = new NanBandSpecificConfig();
            nanBandSpecificConfig.readFromParcel(parcel);
            return nanBandSpecificConfig;
        }

        @Override // android.os.Parcelable.Creator
        public NanBandSpecificConfig[] newArray(int i) {
            return new NanBandSpecificConfig[i];
        }
    };
    public byte rssiClose = 0;
    public byte rssiMiddle = 0;
    public byte rssiCloseProximity = 0;
    public char dwellTimeMs = 0;
    public char scanPeriodSec = 0;
    public boolean validDiscoveryWindowIntervalVal = false;
    public byte discoveryWindowIntervalVal = 0;

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
        parcel.writeByte(this.rssiClose);
        parcel.writeByte(this.rssiMiddle);
        parcel.writeByte(this.rssiCloseProximity);
        parcel.writeInt(this.dwellTimeMs);
        parcel.writeInt(this.scanPeriodSec);
        parcel.writeBoolean(this.validDiscoveryWindowIntervalVal);
        parcel.writeByte(this.discoveryWindowIntervalVal);
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
                this.rssiClose = parcel.readByte();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.rssiMiddle = parcel.readByte();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.rssiCloseProximity = parcel.readByte();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.dwellTimeMs = (char) parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.scanPeriodSec = (char) parcel.readInt();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.validDiscoveryWindowIntervalVal = parcel.readBoolean();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.discoveryWindowIntervalVal = parcel.readByte();
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
