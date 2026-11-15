package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class StaBackgroundScanParameters implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.StaBackgroundScanParameters.1
        @Override // android.os.Parcelable.Creator
        public StaBackgroundScanParameters createFromParcel(Parcel parcel) {
            StaBackgroundScanParameters staBackgroundScanParameters = new StaBackgroundScanParameters();
            staBackgroundScanParameters.readFromParcel(parcel);
            return staBackgroundScanParameters;
        }

        @Override // android.os.Parcelable.Creator
        public StaBackgroundScanParameters[] newArray(int i) {
            return new StaBackgroundScanParameters[i];
        }
    };
    public StaBackgroundScanBucketParameters[] buckets = new StaBackgroundScanBucketParameters[0];
    public int basePeriodInMs = 0;
    public int maxApPerScan = 0;
    public int reportThresholdPercent = 0;
    public int reportThresholdNumScans = 0;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeInt(this.basePeriodInMs);
        parcel.writeInt(this.maxApPerScan);
        parcel.writeInt(this.reportThresholdPercent);
        parcel.writeInt(this.reportThresholdNumScans);
        parcel.writeTypedArray(this.buckets, i);
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
                this.basePeriodInMs = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.maxApPerScan = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.reportThresholdPercent = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.reportThresholdNumScans = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.buckets = (StaBackgroundScanBucketParameters[]) parcel.createTypedArray(StaBackgroundScanBucketParameters.CREATOR);
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

    @Override // android.os.Parcelable
    public int describeContents() {
        return describeContents(this.buckets);
    }

    private int describeContents(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Object[]) {
            int i = 0;
            for (Object obj2 : (Object[]) obj) {
                i |= describeContents(obj2);
            }
            return i;
        }
        if (obj instanceof Parcelable) {
            return ((Parcelable) obj).describeContents();
        }
        return 0;
    }
}
