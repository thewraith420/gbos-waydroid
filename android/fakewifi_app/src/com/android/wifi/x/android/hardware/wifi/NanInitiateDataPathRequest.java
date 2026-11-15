package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanInitiateDataPathRequest implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanInitiateDataPathRequest.1
        @Override // android.os.Parcelable.Creator
        public NanInitiateDataPathRequest createFromParcel(Parcel parcel) {
            NanInitiateDataPathRequest nanInitiateDataPathRequest = new NanInitiateDataPathRequest();
            nanInitiateDataPathRequest.readFromParcel(parcel);
            return nanInitiateDataPathRequest;
        }

        @Override // android.os.Parcelable.Creator
        public NanInitiateDataPathRequest[] newArray(int i) {
            return new NanInitiateDataPathRequest[i];
        }
    };
    public byte[] appInfo = new byte[0];
    public int channelRequestType;
    public String ifaceName = "";
    public byte[] peerDiscMacAddr = new byte[0];
    public NanDataPathSecurityConfig securityConfig = new NanDataPathSecurityConfig();
    public byte[] serviceNameOutOfBand = new byte[0];
    public int peerId = 0;
    public int channel = 0;
    public byte discoverySessionId = 0;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeInt(this.peerId);
        parcel.writeFixedArray(this.peerDiscMacAddr, i, 6);
        parcel.writeInt(this.channelRequestType);
        parcel.writeInt(this.channel);
        parcel.writeString(this.ifaceName);
        parcel.writeTypedObject(this.securityConfig, i);
        parcel.writeByteArray(this.appInfo);
        parcel.writeByteArray(this.serviceNameOutOfBand);
        parcel.writeByte(this.discoverySessionId);
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
                this.peerId = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.peerDiscMacAddr = (byte[]) parcel.createFixedArray(byte[].class, 6);
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.channelRequestType = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.channel = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.ifaceName = parcel.readString();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.securityConfig = (NanDataPathSecurityConfig) parcel.readTypedObject(NanDataPathSecurityConfig.CREATOR);
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.appInfo = parcel.createByteArray();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.serviceNameOutOfBand = parcel.createByteArray();
                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                this.discoverySessionId = parcel.readByte();
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
        return describeContents(this.securityConfig);
    }

    private int describeContents(Object obj) {
        if (obj != null && (obj instanceof Parcelable)) {
            return ((Parcelable) obj).describeContents();
        }
        return 0;
    }
}
