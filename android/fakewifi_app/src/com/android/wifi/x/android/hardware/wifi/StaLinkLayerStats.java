package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class StaLinkLayerStats implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.StaLinkLayerStats.1
        @Override // android.os.Parcelable.Creator
        public StaLinkLayerStats createFromParcel(Parcel parcel) {
            StaLinkLayerStats staLinkLayerStats = new StaLinkLayerStats();
            staLinkLayerStats.readFromParcel(parcel);
            return staLinkLayerStats;
        }

        @Override // android.os.Parcelable.Creator
        public StaLinkLayerStats[] newArray(int i) {
            return new StaLinkLayerStats[i];
        }
    };
    public StaLinkLayerIfaceStats iface = new StaLinkLayerIfaceStats();
    public StaLinkLayerRadioStats[] radios = new StaLinkLayerRadioStats[0];
    public long timeStampInMs = 0;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeTypedObject(this.iface, i);
        parcel.writeTypedArray(this.radios, i);
        parcel.writeLong(this.timeStampInMs);
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
                this.iface = (StaLinkLayerIfaceStats) parcel.readTypedObject(StaLinkLayerIfaceStats.CREATOR);
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.radios = (StaLinkLayerRadioStats[]) parcel.createTypedArray(StaLinkLayerRadioStats.CREATOR);
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.timeStampInMs = parcel.readLong();
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
        return describeContents(this.radios) | describeContents(this.iface);
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
