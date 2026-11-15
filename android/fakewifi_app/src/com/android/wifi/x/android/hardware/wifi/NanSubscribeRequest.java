package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;
import com.android.wifi.x.android.hardware.wifi.common.OuiKeyedData;

/* loaded from: classes.dex */
public class NanSubscribeRequest implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanSubscribeRequest.1
        @Override // android.os.Parcelable.Creator
        public NanSubscribeRequest createFromParcel(Parcel parcel) {
            NanSubscribeRequest nanSubscribeRequest = new NanSubscribeRequest();
            nanSubscribeRequest.readFromParcel(parcel);
            return nanSubscribeRequest;
        }

        @Override // android.os.Parcelable.Creator
        public NanSubscribeRequest[] newArray(int i) {
            return new NanSubscribeRequest[i];
        }
    };
    public NanDiscoveryCommonConfig baseConfigs = new NanDiscoveryCommonConfig();
    public byte[] identityKey = new byte[0];
    public MacAddress[] intfAddr = new MacAddress[0];
    public NanPairingConfig pairingConfig = new NanPairingConfig();
    public int srfType;
    public int subscribeType;
    public OuiKeyedData[] vendorData = new OuiKeyedData[0];
    public boolean srfRespondIfInAddressSet = false;
    public boolean shouldUseSrf = false;
    public boolean isSsiRequiredForMatch = false;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeTypedObject(this.baseConfigs, i);
        parcel.writeInt(this.subscribeType);
        parcel.writeInt(this.srfType);
        parcel.writeBoolean(this.srfRespondIfInAddressSet);
        parcel.writeBoolean(this.shouldUseSrf);
        parcel.writeBoolean(this.isSsiRequiredForMatch);
        parcel.writeTypedArray(this.intfAddr, i);
        parcel.writeTypedObject(this.pairingConfig, i);
        parcel.writeFixedArray(this.identityKey, i, 16);
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
                this.baseConfigs = (NanDiscoveryCommonConfig) parcel.readTypedObject(NanDiscoveryCommonConfig.CREATOR);
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.subscribeType = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.srfType = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.srfRespondIfInAddressSet = parcel.readBoolean();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.shouldUseSrf = parcel.readBoolean();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.isSsiRequiredForMatch = parcel.readBoolean();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.intfAddr = (MacAddress[]) parcel.createTypedArray(MacAddress.CREATOR);
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.pairingConfig = (NanPairingConfig) parcel.readTypedObject(NanPairingConfig.CREATOR);
                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                this.identityKey = (byte[]) parcel.createFixedArray(byte[].class, 16);
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
        return describeContents(this.vendorData) | describeContents(this.baseConfigs) | describeContents(this.intfAddr) | describeContents(this.pairingConfig);
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
