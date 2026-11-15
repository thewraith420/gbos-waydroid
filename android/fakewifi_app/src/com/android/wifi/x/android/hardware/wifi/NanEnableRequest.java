package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanEnableRequest implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanEnableRequest.1
        @Override // android.os.Parcelable.Creator
        public NanEnableRequest createFromParcel(Parcel parcel) {
            NanEnableRequest nanEnableRequest = new NanEnableRequest();
            nanEnableRequest.readFromParcel(parcel);
            return nanEnableRequest;
        }

        @Override // android.os.Parcelable.Creator
        public NanEnableRequest[] newArray(int i) {
            return new NanEnableRequest[i];
        }
    };
    public NanConfigRequest configParams = new NanConfigRequest();
    public NanDebugConfig debugConfigs = new NanDebugConfig();
    public byte hopCountMax = 0;
    public boolean[] operateInBand = new boolean[0];

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeFixedArray(this.operateInBand, i, 3);
        parcel.writeByte(this.hopCountMax);
        parcel.writeTypedObject(this.configParams, i);
        parcel.writeTypedObject(this.debugConfigs, i);
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
                this.operateInBand = (boolean[]) parcel.createFixedArray(boolean[].class, 3);
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.hopCountMax = parcel.readByte();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.configParams = (NanConfigRequest) parcel.readTypedObject(NanConfigRequest.CREATOR);
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.debugConfigs = (NanDebugConfig) parcel.readTypedObject(NanDebugConfig.CREATOR);
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
        return describeContents(this.debugConfigs) | describeContents(this.configParams);
    }

    private int describeContents(Object obj) {
        if (obj != null && (obj instanceof Parcelable)) {
            return ((Parcelable) obj).describeContents();
        }
        return 0;
    }
}
