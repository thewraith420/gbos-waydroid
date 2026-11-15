package com.android.wifi.x.android.hardware.wifi.common;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import com.android.wifi.x.android.hardware.wifi.Ssid$$ExternalSyntheticBUOutline0;

/* loaded from: classes.dex */
public class OuiKeyedData implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.common.OuiKeyedData.1
        @Override // android.os.Parcelable.Creator
        public OuiKeyedData createFromParcel(Parcel parcel) {
            OuiKeyedData ouiKeyedData = new OuiKeyedData();
            ouiKeyedData.readFromParcel(parcel);
            return ouiKeyedData;
        }

        @Override // android.os.Parcelable.Creator
        public OuiKeyedData[] newArray(int i) {
            return new OuiKeyedData[i];
        }
    };
    public int oui = 0;
    public PersistableBundle vendorData;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeInt(this.oui);
        parcel.writeTypedObject(this.vendorData, i);
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
                this.oui = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.vendorData = (PersistableBundle) parcel.readTypedObject(PersistableBundle.CREATOR);
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
        return describeContents(this.vendorData);
    }

    private int describeContents(Object obj) {
        if (obj != null && (obj instanceof Parcelable)) {
            return ((Parcelable) obj).describeContents();
        }
        return 0;
    }
}
