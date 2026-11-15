package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class RttSecureConfig implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.RttSecureConfig.1
        @Override // android.os.Parcelable.Creator
        public RttSecureConfig createFromParcel(Parcel parcel) {
            RttSecureConfig rttSecureConfig = new RttSecureConfig();
            rttSecureConfig.readFromParcel(parcel);
            return rttSecureConfig;
        }

        @Override // android.os.Parcelable.Creator
        public RttSecureConfig[] newArray(int i) {
            return new RttSecureConfig[i];
        }
    };
    public byte[] pasnComebackCookie = new byte[0];
    public PasnConfig pasnConfig = new PasnConfig();
    public boolean enableSecureHeLtf = false;
    public boolean enableRangingFrameProtection = false;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeTypedObject(this.pasnConfig, i);
        parcel.writeBoolean(this.enableSecureHeLtf);
        parcel.writeBoolean(this.enableRangingFrameProtection);
        parcel.writeByteArray(this.pasnComebackCookie);
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
                this.pasnConfig = (PasnConfig) parcel.readTypedObject(PasnConfig.CREATOR);
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.enableSecureHeLtf = parcel.readBoolean();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.enableRangingFrameProtection = parcel.readBoolean();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.pasnComebackCookie = parcel.createByteArray();
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

    @Override // android.os.Parcelable
    public int describeContents() {
        return describeContents(this.pasnConfig);
    }

    private int describeContents(Object obj) {
        if (obj != null && (obj instanceof Parcelable)) {
            return ((Parcelable) obj).describeContents();
        }
        return 0;
    }
}
