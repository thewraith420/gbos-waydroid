package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class TwtCapabilities implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.TwtCapabilities.1
        @Override // android.os.Parcelable.Creator
        public TwtCapabilities createFromParcel(Parcel parcel) {
            TwtCapabilities twtCapabilities = new TwtCapabilities();
            twtCapabilities.readFromParcel(parcel);
            return twtCapabilities;
        }

        @Override // android.os.Parcelable.Creator
        public TwtCapabilities[] newArray(int i) {
            return new TwtCapabilities[i];
        }
    };
    public boolean isTwtRequesterSupported = false;
    public boolean isTwtResponderSupported = false;
    public boolean isBroadcastTwtSupported = false;
    public boolean isFlexibleTwtScheduleSupported = false;
    public int minWakeDurationUs = 0;
    public int maxWakeDurationUs = 0;
    public long minWakeIntervalUs = 0;
    public long maxWakeIntervalUs = 0;

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
        parcel.writeBoolean(this.isTwtRequesterSupported);
        parcel.writeBoolean(this.isTwtResponderSupported);
        parcel.writeBoolean(this.isBroadcastTwtSupported);
        parcel.writeBoolean(this.isFlexibleTwtScheduleSupported);
        parcel.writeInt(this.minWakeDurationUs);
        parcel.writeInt(this.maxWakeDurationUs);
        parcel.writeLong(this.minWakeIntervalUs);
        parcel.writeLong(this.maxWakeIntervalUs);
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
                this.isTwtRequesterSupported = parcel.readBoolean();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.isTwtResponderSupported = parcel.readBoolean();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.isBroadcastTwtSupported = parcel.readBoolean();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.isFlexibleTwtScheduleSupported = parcel.readBoolean();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.minWakeDurationUs = parcel.readInt();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.maxWakeDurationUs = parcel.readInt();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.minWakeIntervalUs = parcel.readLong();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.maxWakeIntervalUs = parcel.readLong();
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
