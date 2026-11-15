package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanBootstrappingConfirmInd implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanBootstrappingConfirmInd.1
        @Override // android.os.Parcelable.Creator
        public NanBootstrappingConfirmInd createFromParcel(Parcel parcel) {
            NanBootstrappingConfirmInd nanBootstrappingConfirmInd = new NanBootstrappingConfirmInd();
            nanBootstrappingConfirmInd.readFromParcel(parcel);
            return nanBootstrappingConfirmInd;
        }

        @Override // android.os.Parcelable.Creator
        public NanBootstrappingConfirmInd[] newArray(int i) {
            return new NanBootstrappingConfirmInd[i];
        }
    };
    public int bootstrappingInstanceId = 0;
    public int comeBackDelay = 0;
    public byte[] cookie = new byte[0];
    public NanStatus reasonCode = new NanStatus();
    public int responseCode;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeInt(this.bootstrappingInstanceId);
        parcel.writeInt(this.responseCode);
        parcel.writeTypedObject(this.reasonCode, i);
        parcel.writeInt(this.comeBackDelay);
        parcel.writeByteArray(this.cookie);
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
                this.bootstrappingInstanceId = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.responseCode = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.reasonCode = (NanStatus) parcel.readTypedObject(NanStatus.CREATOR);
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.comeBackDelay = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.cookie = parcel.createByteArray();
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

    @Override // android.os.Parcelable
    public int describeContents() {
        return describeContents(this.reasonCode);
    }

    private int describeContents(Object obj) {
        if (obj != null && (obj instanceof Parcelable)) {
            return ((Parcelable) obj).describeContents();
        }
        return 0;
    }
}
