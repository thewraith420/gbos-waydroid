package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanDataPathScheduleUpdateInd implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanDataPathScheduleUpdateInd.1
        @Override // android.os.Parcelable.Creator
        public NanDataPathScheduleUpdateInd createFromParcel(Parcel parcel) {
            NanDataPathScheduleUpdateInd nanDataPathScheduleUpdateInd = new NanDataPathScheduleUpdateInd();
            nanDataPathScheduleUpdateInd.readFromParcel(parcel);
            return nanDataPathScheduleUpdateInd;
        }

        @Override // android.os.Parcelable.Creator
        public NanDataPathScheduleUpdateInd[] newArray(int i) {
            return new NanDataPathScheduleUpdateInd[i];
        }
    };
    public NanDataPathChannelInfo[] channelInfo = new NanDataPathChannelInfo[0];
    public int[] ndpInstanceIds = new int[0];
    public byte[] peerDiscoveryAddress = new byte[0];

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeFixedArray(this.peerDiscoveryAddress, i, 6);
        parcel.writeTypedArray(this.channelInfo, i);
        parcel.writeIntArray(this.ndpInstanceIds);
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
                this.peerDiscoveryAddress = (byte[]) parcel.createFixedArray(byte[].class, 6);
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.channelInfo = (NanDataPathChannelInfo[]) parcel.createTypedArray(NanDataPathChannelInfo.CREATOR);
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.ndpInstanceIds = parcel.createIntArray();
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
        return describeContents(this.channelInfo);
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
