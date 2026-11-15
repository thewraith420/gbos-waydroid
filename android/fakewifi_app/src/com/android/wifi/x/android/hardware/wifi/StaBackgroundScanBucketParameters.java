package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class StaBackgroundScanBucketParameters implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.StaBackgroundScanBucketParameters.1
        @Override // android.os.Parcelable.Creator
        public StaBackgroundScanBucketParameters createFromParcel(Parcel parcel) {
            StaBackgroundScanBucketParameters staBackgroundScanBucketParameters = new StaBackgroundScanBucketParameters();
            staBackgroundScanBucketParameters.readFromParcel(parcel);
            return staBackgroundScanBucketParameters;
        }

        @Override // android.os.Parcelable.Creator
        public StaBackgroundScanBucketParameters[] newArray(int i) {
            return new StaBackgroundScanBucketParameters[i];
        }
    };
    public int band;
    public int[] frequencies = new int[0];
    public int bucketIdx = 0;
    public int periodInMs = 0;
    public int eventReportScheme = 0;
    public int exponentialMaxPeriodInMs = 0;
    public int exponentialBase = 0;
    public int exponentialStepCount = 0;

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
        parcel.writeInt(this.bucketIdx);
        parcel.writeInt(this.band);
        parcel.writeIntArray(this.frequencies);
        parcel.writeInt(this.periodInMs);
        parcel.writeInt(this.eventReportScheme);
        parcel.writeInt(this.exponentialMaxPeriodInMs);
        parcel.writeInt(this.exponentialBase);
        parcel.writeInt(this.exponentialStepCount);
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
                this.bucketIdx = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.band = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.frequencies = parcel.createIntArray();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.periodInMs = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.eventReportScheme = parcel.readInt();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.exponentialMaxPeriodInMs = parcel.readInt();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.exponentialBase = parcel.readInt();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.exponentialStepCount = parcel.readInt();
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
