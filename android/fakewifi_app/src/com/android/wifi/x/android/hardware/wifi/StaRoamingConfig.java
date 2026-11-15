package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class StaRoamingConfig implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.StaRoamingConfig.1
        @Override // android.os.Parcelable.Creator
        public StaRoamingConfig createFromParcel(Parcel parcel) {
            StaRoamingConfig staRoamingConfig = new StaRoamingConfig();
            staRoamingConfig.readFromParcel(parcel);
            return staRoamingConfig;
        }

        @Override // android.os.Parcelable.Creator
        public StaRoamingConfig[] newArray(int i) {
            return new StaRoamingConfig[i];
        }
    };
    public MacAddress[] bssidBlocklist = new MacAddress[0];
    public Ssid[] ssidAllowlist = new Ssid[0];

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeTypedArray(this.bssidBlocklist, i);
        parcel.writeTypedArray(this.ssidAllowlist, i);
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
                this.bssidBlocklist = (MacAddress[]) parcel.createTypedArray(MacAddress.CREATOR);
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.ssidAllowlist = (Ssid[]) parcel.createTypedArray(Ssid.CREATOR);
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
        return describeContents(this.ssidAllowlist) | describeContents(this.bssidBlocklist);
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
