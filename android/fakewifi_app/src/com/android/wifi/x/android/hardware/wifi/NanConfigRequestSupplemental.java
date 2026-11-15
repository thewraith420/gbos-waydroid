package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanConfigRequestSupplemental implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanConfigRequestSupplemental.1
        @Override // android.os.Parcelable.Creator
        public NanConfigRequestSupplemental createFromParcel(Parcel parcel) {
            NanConfigRequestSupplemental nanConfigRequestSupplemental = new NanConfigRequestSupplemental();
            nanConfigRequestSupplemental.readFromParcel(parcel);
            return nanConfigRequestSupplemental;
        }

        @Override // android.os.Parcelable.Creator
        public NanConfigRequestSupplemental[] newArray(int i) {
            return new NanConfigRequestSupplemental[i];
        }
    };
    public int discoveryBeaconIntervalMs = 0;
    public int numberOfSpatialStreamsInDiscovery = 0;
    public boolean enableDiscoveryWindowEarlyTermination = false;
    public boolean enableRanging = false;
    public boolean enableInstantCommunicationMode = false;
    public int instantModeChannel = 0;
    public int clusterId = 0;

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
        parcel.writeInt(this.discoveryBeaconIntervalMs);
        parcel.writeInt(this.numberOfSpatialStreamsInDiscovery);
        parcel.writeBoolean(this.enableDiscoveryWindowEarlyTermination);
        parcel.writeBoolean(this.enableRanging);
        parcel.writeBoolean(this.enableInstantCommunicationMode);
        parcel.writeInt(this.instantModeChannel);
        parcel.writeInt(this.clusterId);
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
                this.discoveryBeaconIntervalMs = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.numberOfSpatialStreamsInDiscovery = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.enableDiscoveryWindowEarlyTermination = parcel.readBoolean();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.enableRanging = parcel.readBoolean();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.enableInstantCommunicationMode = parcel.readBoolean();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.instantModeChannel = parcel.readInt();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.clusterId = parcel.readInt();
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
