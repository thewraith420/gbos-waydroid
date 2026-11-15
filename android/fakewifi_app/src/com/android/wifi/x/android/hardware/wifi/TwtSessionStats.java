package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class TwtSessionStats implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.TwtSessionStats.1
        @Override // android.os.Parcelable.Creator
        public TwtSessionStats createFromParcel(Parcel parcel) {
            TwtSessionStats twtSessionStats = new TwtSessionStats();
            twtSessionStats.readFromParcel(parcel);
            return twtSessionStats;
        }

        @Override // android.os.Parcelable.Creator
        public TwtSessionStats[] newArray(int i) {
            return new TwtSessionStats[i];
        }
    };
    public int avgTxPktCount = 0;
    public int avgRxPktCount = 0;
    public int avgTxPktSize = 0;
    public int avgRxPktSize = 0;
    public int avgEospDurationUs = 0;
    public int eospCount = 0;

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
        parcel.writeInt(this.avgTxPktCount);
        parcel.writeInt(this.avgRxPktCount);
        parcel.writeInt(this.avgTxPktSize);
        parcel.writeInt(this.avgRxPktSize);
        parcel.writeInt(this.avgEospDurationUs);
        parcel.writeInt(this.eospCount);
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
                this.avgTxPktCount = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.avgRxPktCount = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.avgTxPktSize = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.avgRxPktSize = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.avgEospDurationUs = parcel.readInt();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.eospCount = parcel.readInt();
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
