package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanPairingSecurityConfig implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanPairingSecurityConfig.1
        @Override // android.os.Parcelable.Creator
        public NanPairingSecurityConfig createFromParcel(Parcel parcel) {
            NanPairingSecurityConfig nanPairingSecurityConfig = new NanPairingSecurityConfig();
            nanPairingSecurityConfig.readFromParcel(parcel);
            return nanPairingSecurityConfig;
        }

        @Override // android.os.Parcelable.Creator
        public NanPairingSecurityConfig[] newArray(int i) {
            return new NanPairingSecurityConfig[i];
        }
    };
    public int akm;
    public int cipherType;
    public byte[] passphrase = new byte[0];
    public byte[] pmk = new byte[0];
    public int securityType;

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
        parcel.writeInt(this.securityType);
        parcel.writeFixedArray(this.pmk, i, 32);
        parcel.writeByteArray(this.passphrase);
        parcel.writeInt(this.akm);
        parcel.writeInt(this.cipherType);
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
                this.securityType = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.pmk = (byte[]) parcel.createFixedArray(byte[].class, 32);
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.passphrase = parcel.createByteArray();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.akm = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.cipherType = parcel.readInt();
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
}
