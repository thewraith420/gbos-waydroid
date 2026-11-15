package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;
import com.android.wifi.x.android.hardware.wifi.common.OuiKeyedData;

/* loaded from: classes.dex */
public class NanPairingRequestInd implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanPairingRequestInd.1
        @Override // android.os.Parcelable.Creator
        public NanPairingRequestInd createFromParcel(Parcel parcel) {
            NanPairingRequestInd nanPairingRequestInd = new NanPairingRequestInd();
            nanPairingRequestInd.readFromParcel(parcel);
            return nanPairingRequestInd;
        }

        @Override // android.os.Parcelable.Creator
        public NanPairingRequestInd[] newArray(int i) {
            return new NanPairingRequestInd[i];
        }
    };
    public byte[] peerDiscMacAddr = new byte[0];
    public NanIdentityResolutionAttribute peerNira = new NanIdentityResolutionAttribute();
    public int requestType;
    public OuiKeyedData[] vendorData = new OuiKeyedData[0];
    public byte discoverySessionId = 0;
    public int peerId = 0;
    public int pairingInstanceId = 0;
    public boolean enablePairingCache = false;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeByte(this.discoverySessionId);
        parcel.writeInt(this.peerId);
        parcel.writeFixedArray(this.peerDiscMacAddr, i, 6);
        parcel.writeInt(this.pairingInstanceId);
        parcel.writeInt(this.requestType);
        parcel.writeBoolean(this.enablePairingCache);
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
                        this.peerDiscMacAddr = (byte[]) parcel.createFixedArray(byte[].class, 6);
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.pairingInstanceId = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.requestType = parcel.readInt();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.enablePairingCache = parcel.readBoolean();
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
        return describeContents(this.vendorData) | describeContents(this.peerNira);
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
