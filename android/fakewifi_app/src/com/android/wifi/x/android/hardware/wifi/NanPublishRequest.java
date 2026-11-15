package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;
import com.android.wifi.x.android.hardware.wifi.common.OuiKeyedData;

/* loaded from: classes.dex */
public class NanPublishRequest implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanPublishRequest.1
        @Override // android.os.Parcelable.Creator
        public NanPublishRequest createFromParcel(Parcel parcel) {
            NanPublishRequest nanPublishRequest = new NanPublishRequest();
            nanPublishRequest.readFromParcel(parcel);
            return nanPublishRequest;
        }

        @Override // android.os.Parcelable.Creator
        public NanPublishRequest[] newArray(int i) {
            return new NanPublishRequest[i];
        }
    };
    public NanDiscoveryCommonConfig baseConfigs = new NanDiscoveryCommonConfig();
    public byte[] identityKey = new byte[0];
    public NanPairingConfig pairingConfig = new NanPairingConfig();
    public int publishType;
    public int txType;
    public OuiKeyedData[] vendorData = new OuiKeyedData[0];
    public boolean autoAcceptDataPathRequests = false;
    public boolean rangingResultsRequired = false;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeTypedObject(this.baseConfigs, i);
        parcel.writeInt(this.publishType);
        parcel.writeInt(this.txType);
        parcel.writeBoolean(this.autoAcceptDataPathRequests);
        parcel.writeTypedObject(this.pairingConfig, i);
        parcel.writeFixedArray(this.identityKey, i, 16);
        parcel.writeTypedArray(this.vendorData, i);
        parcel.writeBoolean(this.rangingResultsRequired);
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
                this.baseConfigs = (NanDiscoveryCommonConfig) parcel.readTypedObject(NanDiscoveryCommonConfig.CREATOR);
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.publishType = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.txType = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.autoAcceptDataPathRequests = parcel.readBoolean();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.pairingConfig = (NanPairingConfig) parcel.readTypedObject(NanPairingConfig.CREATOR);
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.identityKey = (byte[]) parcel.createFixedArray(byte[].class, 16);
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.vendorData = (OuiKeyedData[]) parcel.createTypedArray(OuiKeyedData.CREATOR);
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.rangingResultsRequired = parcel.readBoolean();
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
        return describeContents(this.vendorData) | describeContents(this.baseConfigs) | describeContents(this.pairingConfig);
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
