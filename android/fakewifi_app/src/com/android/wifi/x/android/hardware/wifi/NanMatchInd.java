package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;
import com.android.wifi.x.android.hardware.wifi.common.OuiKeyedData;

/* loaded from: classes.dex */
public class NanMatchInd implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanMatchInd.1
        @Override // android.os.Parcelable.Creator
        public NanMatchInd createFromParcel(Parcel parcel) {
            NanMatchInd nanMatchInd = new NanMatchInd();
            nanMatchInd.readFromParcel(parcel);
            return nanMatchInd;
        }

        @Override // android.os.Parcelable.Creator
        public NanMatchInd[] newArray(int i) {
            return new NanMatchInd[i];
        }
    };
    public byte[] addr = new byte[0];
    public byte[] extendedServiceSpecificInfo = new byte[0];
    public byte[] matchFilter = new byte[0];
    public int peerCipherType;
    public NanIdentityResolutionAttribute peerNira = new NanIdentityResolutionAttribute();
    public NanPairingConfig peerPairingConfig = new NanPairingConfig();
    public byte[] scid = new byte[0];
    public byte[] serviceSpecificInfo = new byte[0];
    public OuiKeyedData[] vendorData = new OuiKeyedData[0];
    public byte discoverySessionId = 0;
    public int peerId = 0;
    public boolean matchOccurredInBeaconFlag = false;
    public boolean outOfResourceFlag = false;
    public byte rssiValue = 0;
    public boolean peerRequiresSecurityEnabledInNdp = false;
    public boolean peerRequiresRanging = false;
    public int rangingMeasurementInMm = 0;
    public int rangingIndicationType = 0;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeByte(this.discoverySessionId);
        parcel.writeInt(this.peerId);
        parcel.writeFixedArray(this.addr, i, 6);
        parcel.writeByteArray(this.serviceSpecificInfo);
        parcel.writeByteArray(this.extendedServiceSpecificInfo);
        parcel.writeByteArray(this.matchFilter);
        parcel.writeBoolean(this.matchOccurredInBeaconFlag);
        parcel.writeBoolean(this.outOfResourceFlag);
        parcel.writeByte(this.rssiValue);
        parcel.writeInt(this.peerCipherType);
        parcel.writeBoolean(this.peerRequiresSecurityEnabledInNdp);
        parcel.writeBoolean(this.peerRequiresRanging);
        parcel.writeInt(this.rangingMeasurementInMm);
        parcel.writeInt(this.rangingIndicationType);
        parcel.writeByteArray(this.scid);
        parcel.writeTypedObject(this.peerPairingConfig, i);
        parcel.writeTypedObject(this.peerNira, i);
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
                this.discoverySessionId = parcel.readByte();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.peerId = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.addr = (byte[]) parcel.createFixedArray(byte[].class, 6);
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.serviceSpecificInfo = parcel.createByteArray();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.extendedServiceSpecificInfo = parcel.createByteArray();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.matchFilter = parcel.createByteArray();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.matchOccurredInBeaconFlag = parcel.readBoolean();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.outOfResourceFlag = parcel.readBoolean();
                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                this.rssiValue = parcel.readByte();
                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                    this.peerCipherType = parcel.readInt();
                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                        this.peerRequiresSecurityEnabledInNdp = parcel.readBoolean();
                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                            this.peerRequiresRanging = parcel.readBoolean();
                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                this.rangingMeasurementInMm = parcel.readInt();
                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                    this.rangingIndicationType = parcel.readInt();
                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                        this.scid = parcel.createByteArray();
                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                            this.peerPairingConfig = (NanPairingConfig) parcel.readTypedObject(NanPairingConfig.CREATOR);
                                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                this.peerNira = (NanIdentityResolutionAttribute) parcel.readTypedObject(NanIdentityResolutionAttribute.CREATOR);
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
        return describeContents(this.vendorData) | describeContents(this.peerPairingConfig) | describeContents(this.peerNira);
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
