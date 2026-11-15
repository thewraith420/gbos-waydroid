package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanDataPathConfirmInd implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanDataPathConfirmInd.1
        @Override // android.os.Parcelable.Creator
        public NanDataPathConfirmInd createFromParcel(Parcel parcel) {
            NanDataPathConfirmInd nanDataPathConfirmInd = new NanDataPathConfirmInd();
            nanDataPathConfirmInd.readFromParcel(parcel);
            return nanDataPathConfirmInd;
        }

        @Override // android.os.Parcelable.Creator
        public NanDataPathConfirmInd[] newArray(int i) {
            return new NanDataPathConfirmInd[i];
        }
    };
    public byte[] appInfo = new byte[0];
    public NanDataPathChannelInfo[] channelInfo = new NanDataPathChannelInfo[0];
    public byte[] peerNdiMacAddr = new byte[0];
    public NanStatus status = new NanStatus();
    public int ndpInstanceId = 0;
    public boolean dataPathSetupSuccess = false;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeInt(this.ndpInstanceId);
        parcel.writeBoolean(this.dataPathSetupSuccess);
        parcel.writeFixedArray(this.peerNdiMacAddr, i, 6);
        parcel.writeByteArray(this.appInfo);
        parcel.writeTypedObject(this.status, i);
        parcel.writeTypedArray(this.channelInfo, i);
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
                this.ndpInstanceId = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.dataPathSetupSuccess = parcel.readBoolean();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.peerNdiMacAddr = (byte[]) parcel.createFixedArray(byte[].class, 6);
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.appInfo = parcel.createByteArray();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.status = (NanStatus) parcel.readTypedObject(NanStatus.CREATOR);
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.channelInfo = (NanDataPathChannelInfo[]) parcel.createTypedArray(NanDataPathChannelInfo.CREATOR);
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
        return describeContents(this.channelInfo) | describeContents(this.status);
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
