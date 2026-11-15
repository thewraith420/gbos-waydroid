package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class CachedScanResult implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.CachedScanResult.1
        @Override // android.os.Parcelable.Creator
        public CachedScanResult createFromParcel(Parcel parcel) {
            CachedScanResult cachedScanResult = new CachedScanResult();
            cachedScanResult.readFromParcel(parcel);
            return cachedScanResult;
        }

        @Override // android.os.Parcelable.Creator
        public CachedScanResult[] newArray(int i) {
            return new CachedScanResult[i];
        }
    };
    public byte[] bssid = new byte[0];
    public int channelWidthMhz;
    public int preambleType;
    public byte[] ssid = new byte[0];
    public long timeStampInUs = 0;
    public int rssiDbm = 0;
    public int frequencyMhz = 0;

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
        parcel.writeLong(this.timeStampInUs);
        parcel.writeByteArray(this.ssid);
        parcel.writeFixedArray(this.bssid, i, 6);
        parcel.writeInt(this.rssiDbm);
        parcel.writeInt(this.frequencyMhz);
        parcel.writeInt(this.channelWidthMhz);
        parcel.writeInt(this.preambleType);
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
                this.timeStampInUs = parcel.readLong();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.ssid = parcel.createByteArray();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.bssid = (byte[]) parcel.createFixedArray(byte[].class, 6);
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.rssiDbm = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.frequencyMhz = parcel.readInt();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.channelWidthMhz = parcel.readInt();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.preambleType = parcel.readInt();
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
