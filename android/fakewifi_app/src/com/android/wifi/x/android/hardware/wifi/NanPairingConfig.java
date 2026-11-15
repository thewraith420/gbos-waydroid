package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanPairingConfig implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanPairingConfig.1
        @Override // android.os.Parcelable.Creator
        public NanPairingConfig createFromParcel(Parcel parcel) {
            NanPairingConfig nanPairingConfig = new NanPairingConfig();
            nanPairingConfig.readFromParcel(parcel);
            return nanPairingConfig;
        }

        @Override // android.os.Parcelable.Creator
        public NanPairingConfig[] newArray(int i) {
            return new NanPairingConfig[i];
        }
    };
    public boolean enablePairingSetup = false;
    public boolean enablePairingCache = false;
    public boolean enablePairingVerification = false;
    public int supportedBootstrappingMethods = 0;

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
        parcel.writeBoolean(this.enablePairingSetup);
        parcel.writeBoolean(this.enablePairingCache);
        parcel.writeBoolean(this.enablePairingVerification);
        parcel.writeInt(this.supportedBootstrappingMethods);
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
                this.enablePairingSetup = parcel.readBoolean();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.enablePairingCache = parcel.readBoolean();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.enablePairingVerification = parcel.readBoolean();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.supportedBootstrappingMethods = parcel.readInt();
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
