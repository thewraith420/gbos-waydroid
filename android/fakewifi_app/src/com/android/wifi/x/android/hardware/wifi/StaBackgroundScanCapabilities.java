package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class StaBackgroundScanCapabilities implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.StaBackgroundScanCapabilities.1
        @Override // android.os.Parcelable.Creator
        public StaBackgroundScanCapabilities createFromParcel(Parcel parcel) {
            StaBackgroundScanCapabilities staBackgroundScanCapabilities = new StaBackgroundScanCapabilities();
            staBackgroundScanCapabilities.readFromParcel(parcel);
            return staBackgroundScanCapabilities;
        }

        @Override // android.os.Parcelable.Creator
        public StaBackgroundScanCapabilities[] newArray(int i) {
            return new StaBackgroundScanCapabilities[i];
        }
    };
    public int maxCacheSize = 0;
    public int maxBuckets = 0;
    public int maxApCachePerScan = 0;
    public int maxReportingThreshold = 0;

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
        parcel.writeInt(this.maxCacheSize);
        parcel.writeInt(this.maxBuckets);
        parcel.writeInt(this.maxApCachePerScan);
        parcel.writeInt(this.maxReportingThreshold);
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
                this.maxCacheSize = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.maxBuckets = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.maxApCachePerScan = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.maxReportingThreshold = parcel.readInt();
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
