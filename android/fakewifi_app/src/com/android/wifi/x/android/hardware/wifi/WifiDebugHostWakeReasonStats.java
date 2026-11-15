package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class WifiDebugHostWakeReasonStats implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.WifiDebugHostWakeReasonStats.1
        @Override // android.os.Parcelable.Creator
        public WifiDebugHostWakeReasonStats createFromParcel(Parcel parcel) {
            WifiDebugHostWakeReasonStats wifiDebugHostWakeReasonStats = new WifiDebugHostWakeReasonStats();
            wifiDebugHostWakeReasonStats.readFromParcel(parcel);
            return wifiDebugHostWakeReasonStats;
        }

        @Override // android.os.Parcelable.Creator
        public WifiDebugHostWakeReasonStats[] newArray(int i) {
            return new WifiDebugHostWakeReasonStats[i];
        }
    };
    public int[] cmdEventWakeCntPerType = new int[0];
    public int[] driverFwLocalWakeCntPerType = new int[0];
    public WifiDebugHostWakeReasonRxIcmpPacketDetails rxIcmpPkWakeDetails = new WifiDebugHostWakeReasonRxIcmpPacketDetails();
    public WifiDebugHostWakeReasonRxMulticastPacketDetails rxMulticastPkWakeDetails = new WifiDebugHostWakeReasonRxMulticastPacketDetails();
    public WifiDebugHostWakeReasonRxPacketDetails rxPktWakeDetails = new WifiDebugHostWakeReasonRxPacketDetails();
    public int totalCmdEventWakeCnt = 0;
    public int totalDriverFwLocalWakeCnt = 0;
    public int totalRxPacketWakeCnt = 0;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeInt(this.totalCmdEventWakeCnt);
        parcel.writeIntArray(this.cmdEventWakeCntPerType);
        parcel.writeInt(this.totalDriverFwLocalWakeCnt);
        parcel.writeIntArray(this.driverFwLocalWakeCntPerType);
        parcel.writeInt(this.totalRxPacketWakeCnt);
        parcel.writeTypedObject(this.rxPktWakeDetails, i);
        parcel.writeTypedObject(this.rxMulticastPkWakeDetails, i);
        parcel.writeTypedObject(this.rxIcmpPkWakeDetails, i);
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
                this.totalCmdEventWakeCnt = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.cmdEventWakeCntPerType = parcel.createIntArray();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.totalDriverFwLocalWakeCnt = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.driverFwLocalWakeCntPerType = parcel.createIntArray();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.totalRxPacketWakeCnt = parcel.readInt();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.rxPktWakeDetails = (WifiDebugHostWakeReasonRxPacketDetails) parcel.readTypedObject(WifiDebugHostWakeReasonRxPacketDetails.CREATOR);
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.rxMulticastPkWakeDetails = (WifiDebugHostWakeReasonRxMulticastPacketDetails) parcel.readTypedObject(WifiDebugHostWakeReasonRxMulticastPacketDetails.CREATOR);
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.rxIcmpPkWakeDetails = (WifiDebugHostWakeReasonRxIcmpPacketDetails) parcel.readTypedObject(WifiDebugHostWakeReasonRxIcmpPacketDetails.CREATOR);
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
        return describeContents(this.rxIcmpPkWakeDetails) | describeContents(this.rxPktWakeDetails) | describeContents(this.rxMulticastPkWakeDetails);
    }

    private int describeContents(Object obj) {
        if (obj != null && (obj instanceof Parcelable)) {
            return ((Parcelable) obj).describeContents();
        }
        return 0;
    }
}
