package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class PasnConfig implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.PasnConfig.1
        @Override // android.os.Parcelable.Creator
        public PasnConfig createFromParcel(Parcel parcel) {
            PasnConfig pasnConfig = new PasnConfig();
            pasnConfig.readFromParcel(parcel);
            return pasnConfig;
        }

        @Override // android.os.Parcelable.Creator
        public PasnConfig[] newArray(int i) {
            return new PasnConfig[i];
        }
    };
    public long baseAkm = 0;
    public long cipherSuite = 0;
    public byte[] passphrase = new byte[0];
    public byte[] pmkid = new byte[0];

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
        parcel.writeLong(this.baseAkm);
        parcel.writeLong(this.cipherSuite);
        parcel.writeByteArray(this.passphrase);
        parcel.writeByteArray(this.pmkid);
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
                this.baseAkm = parcel.readLong();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.cipherSuite = parcel.readLong();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.passphrase = parcel.createByteArray();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.pmkid = parcel.createByteArray();
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
