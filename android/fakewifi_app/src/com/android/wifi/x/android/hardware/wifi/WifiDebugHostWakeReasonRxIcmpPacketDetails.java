package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class WifiDebugHostWakeReasonRxIcmpPacketDetails implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.WifiDebugHostWakeReasonRxIcmpPacketDetails.1
        @Override // android.os.Parcelable.Creator
        public WifiDebugHostWakeReasonRxIcmpPacketDetails createFromParcel(Parcel parcel) {
            WifiDebugHostWakeReasonRxIcmpPacketDetails wifiDebugHostWakeReasonRxIcmpPacketDetails = new WifiDebugHostWakeReasonRxIcmpPacketDetails();
            wifiDebugHostWakeReasonRxIcmpPacketDetails.readFromParcel(parcel);
            return wifiDebugHostWakeReasonRxIcmpPacketDetails;
        }

        @Override // android.os.Parcelable.Creator
        public WifiDebugHostWakeReasonRxIcmpPacketDetails[] newArray(int i) {
            return new WifiDebugHostWakeReasonRxIcmpPacketDetails[i];
        }
    };
    public int icmpPkt = 0;
    public int icmp6Pkt = 0;
    public int icmp6Ra = 0;
    public int icmp6Na = 0;
    public int icmp6Ns = 0;

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
        parcel.writeInt(this.icmpPkt);
        parcel.writeInt(this.icmp6Pkt);
        parcel.writeInt(this.icmp6Ra);
        parcel.writeInt(this.icmp6Na);
        parcel.writeInt(this.icmp6Ns);
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
                this.icmpPkt = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.icmp6Pkt = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.icmp6Ra = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.icmp6Na = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.icmp6Ns = parcel.readInt();
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
