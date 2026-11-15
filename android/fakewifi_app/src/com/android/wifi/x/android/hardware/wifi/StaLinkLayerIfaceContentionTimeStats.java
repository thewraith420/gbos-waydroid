package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class StaLinkLayerIfaceContentionTimeStats implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.StaLinkLayerIfaceContentionTimeStats.1
        @Override // android.os.Parcelable.Creator
        public StaLinkLayerIfaceContentionTimeStats createFromParcel(Parcel parcel) {
            StaLinkLayerIfaceContentionTimeStats staLinkLayerIfaceContentionTimeStats = new StaLinkLayerIfaceContentionTimeStats();
            staLinkLayerIfaceContentionTimeStats.readFromParcel(parcel);
            return staLinkLayerIfaceContentionTimeStats;
        }

        @Override // android.os.Parcelable.Creator
        public StaLinkLayerIfaceContentionTimeStats[] newArray(int i) {
            return new StaLinkLayerIfaceContentionTimeStats[i];
        }
    };
    public int contentionTimeMinInUsec = 0;
    public int contentionTimeMaxInUsec = 0;
    public int contentionTimeAvgInUsec = 0;
    public int contentionNumSamples = 0;

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
        parcel.writeInt(this.contentionTimeMinInUsec);
        parcel.writeInt(this.contentionTimeMaxInUsec);
        parcel.writeInt(this.contentionTimeAvgInUsec);
        parcel.writeInt(this.contentionNumSamples);
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
                this.contentionTimeMinInUsec = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.contentionTimeMaxInUsec = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.contentionTimeAvgInUsec = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.contentionNumSamples = parcel.readInt();
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
