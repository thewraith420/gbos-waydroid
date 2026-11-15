package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;
import com.android.wifi.x.android.hardware.wifi.common.OuiKeyedData;

/* loaded from: classes.dex */
public class NanConfigRequest implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanConfigRequest.1
        @Override // android.os.Parcelable.Creator
        public NanConfigRequest createFromParcel(Parcel parcel) {
            NanConfigRequest nanConfigRequest = new NanConfigRequest();
            nanConfigRequest.readFromParcel(parcel);
            return nanConfigRequest;
        }

        @Override // android.os.Parcelable.Creator
        public NanConfigRequest[] newArray(int i) {
            return new NanConfigRequest[i];
        }
    };
    public NanBandSpecificConfig[] bandSpecificConfig = new NanBandSpecificConfig[0];
    public OuiKeyedData[] vendorData = new OuiKeyedData[0];
    public byte masterPref = 0;
    public boolean disableDiscoveryAddressChangeIndication = false;
    public boolean disableStartedClusterIndication = false;
    public boolean disableJoinedClusterIndication = false;
    public boolean includePublishServiceIdsInBeacon = false;
    public byte numberOfPublishServiceIdsInBeacon = 0;
    public boolean includeSubscribeServiceIdsInBeacon = false;
    public byte numberOfSubscribeServiceIdsInBeacon = 0;
    public char rssiWindowSize = 0;
    public int macAddressRandomizationIntervalSec = 0;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeByte(this.masterPref);
        parcel.writeBoolean(this.disableDiscoveryAddressChangeIndication);
        parcel.writeBoolean(this.disableStartedClusterIndication);
        parcel.writeBoolean(this.disableJoinedClusterIndication);
        parcel.writeBoolean(this.includePublishServiceIdsInBeacon);
        parcel.writeByte(this.numberOfPublishServiceIdsInBeacon);
        parcel.writeBoolean(this.includeSubscribeServiceIdsInBeacon);
        parcel.writeByte(this.numberOfSubscribeServiceIdsInBeacon);
        parcel.writeInt(this.rssiWindowSize);
        parcel.writeInt(this.macAddressRandomizationIntervalSec);
        parcel.writeFixedArray(this.bandSpecificConfig, i, 3);
        parcel.writeTypedArray(this.vendorData, i);
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
                this.masterPref = parcel.readByte();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.disableDiscoveryAddressChangeIndication = parcel.readBoolean();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.disableStartedClusterIndication = parcel.readBoolean();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.disableJoinedClusterIndication = parcel.readBoolean();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.includePublishServiceIdsInBeacon = parcel.readBoolean();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.numberOfPublishServiceIdsInBeacon = parcel.readByte();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.includeSubscribeServiceIdsInBeacon = parcel.readBoolean();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.numberOfSubscribeServiceIdsInBeacon = parcel.readByte();
                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                this.rssiWindowSize = (char) parcel.readInt();
                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                    this.macAddressRandomizationIntervalSec = parcel.readInt();
                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                        this.bandSpecificConfig = (NanBandSpecificConfig[]) parcel.createFixedArray(NanBandSpecificConfig[].class, NanBandSpecificConfig.CREATOR, 3);
                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                            this.vendorData = (OuiKeyedData[]) parcel.createTypedArray(OuiKeyedData.CREATOR);
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
        return describeContents(this.vendorData) | describeContents(this.bandSpecificConfig);
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
