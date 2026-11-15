package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class WifiDebugRingBufferStatus implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.WifiDebugRingBufferStatus.1
        @Override // android.os.Parcelable.Creator
        public WifiDebugRingBufferStatus createFromParcel(Parcel parcel) {
            WifiDebugRingBufferStatus wifiDebugRingBufferStatus = new WifiDebugRingBufferStatus();
            wifiDebugRingBufferStatus.readFromParcel(parcel);
            return wifiDebugRingBufferStatus;
        }

        @Override // android.os.Parcelable.Creator
        public WifiDebugRingBufferStatus[] newArray(int i) {
            return new WifiDebugRingBufferStatus[i];
        }
    };
    public String ringName = "";
    public int flags = 0;
    public int ringId = 0;
    public int sizeInBytes = 0;
    public int freeSizeInBytes = 0;
    public int verboseLevel = 0;

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
        parcel.writeString(this.ringName);
        parcel.writeInt(this.flags);
        parcel.writeInt(this.ringId);
        parcel.writeInt(this.sizeInBytes);
        parcel.writeInt(this.freeSizeInBytes);
        parcel.writeInt(this.verboseLevel);
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
                this.ringName = parcel.readString();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.flags = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.ringId = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.sizeInBytes = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.freeSizeInBytes = parcel.readInt();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.verboseLevel = parcel.readInt();
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
