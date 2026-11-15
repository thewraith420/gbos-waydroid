package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class WifiDebugHostWakeReasonRxMulticastPacketDetails implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.WifiDebugHostWakeReasonRxMulticastPacketDetails.1
        @Override // android.os.Parcelable.Creator
        public WifiDebugHostWakeReasonRxMulticastPacketDetails createFromParcel(Parcel parcel) {
            WifiDebugHostWakeReasonRxMulticastPacketDetails wifiDebugHostWakeReasonRxMulticastPacketDetails = new WifiDebugHostWakeReasonRxMulticastPacketDetails();
            wifiDebugHostWakeReasonRxMulticastPacketDetails.readFromParcel(parcel);
            return wifiDebugHostWakeReasonRxMulticastPacketDetails;
        }

        @Override // android.os.Parcelable.Creator
        public WifiDebugHostWakeReasonRxMulticastPacketDetails[] newArray(int i) {
            return new WifiDebugHostWakeReasonRxMulticastPacketDetails[i];
        }
    };
    public int ipv4RxMulticastAddrCnt = 0;
    public int ipv6RxMulticastAddrCnt = 0;
    public int otherRxMulticastAddrCnt = 0;

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
        parcel.writeInt(this.ipv4RxMulticastAddrCnt);
        parcel.writeInt(this.ipv6RxMulticastAddrCnt);
        parcel.writeInt(this.otherRxMulticastAddrCnt);
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
                this.ipv4RxMulticastAddrCnt = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.ipv6RxMulticastAddrCnt = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.otherRxMulticastAddrCnt = parcel.readInt();
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
}
