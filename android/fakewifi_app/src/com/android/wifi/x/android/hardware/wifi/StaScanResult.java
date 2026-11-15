package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class StaScanResult implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.StaScanResult.1
        @Override // android.os.Parcelable.Creator
        public StaScanResult createFromParcel(Parcel parcel) {
            StaScanResult staScanResult = new StaScanResult();
            staScanResult.readFromParcel(parcel);
            return staScanResult;
        }

        @Override // android.os.Parcelable.Creator
        public StaScanResult[] newArray(int i) {
            return new StaScanResult[i];
        }
    };
    public byte[] bssid = new byte[0];
    public WifiInformationElement[] informationElements = new WifiInformationElement[0];
    public byte[] ssid = new byte[0];
    public long timeStampInUs = 0;
    public int rssi = 0;
    public int frequency = 0;
    public char beaconPeriodInMs = 0;
    public char capability = 0;

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
        parcel.writeInt(this.rssi);
        parcel.writeInt(this.frequency);
        parcel.writeInt(this.beaconPeriodInMs);
        parcel.writeInt(this.capability);
        parcel.writeTypedArray(this.informationElements, i);
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
                            this.rssi = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.frequency = parcel.readInt();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.beaconPeriodInMs = (char) parcel.readInt();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.capability = (char) parcel.readInt();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.informationElements = (WifiInformationElement[]) parcel.createTypedArray(WifiInformationElement.CREATOR);
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
        return describeContents(this.informationElements);
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
