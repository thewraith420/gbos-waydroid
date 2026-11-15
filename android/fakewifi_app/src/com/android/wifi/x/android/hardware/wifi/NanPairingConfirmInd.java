package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;
import com.android.wifi.x.android.hardware.wifi.common.OuiKeyedData;

/* loaded from: classes.dex */
public class NanPairingConfirmInd implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanPairingConfirmInd.1
        @Override // android.os.Parcelable.Creator
        public NanPairingConfirmInd createFromParcel(Parcel parcel) {
            NanPairingConfirmInd nanPairingConfirmInd = new NanPairingConfirmInd();
            nanPairingConfirmInd.readFromParcel(parcel);
            return nanPairingConfirmInd;
        }

        @Override // android.os.Parcelable.Creator
        public NanPairingConfirmInd[] newArray(int i) {
            return new NanPairingConfirmInd[i];
        }
    };
    public NpkSecurityAssociation npksa = new NpkSecurityAssociation();
    public int requestType;
    public NanStatus status = new NanStatus();
    public OuiKeyedData[] vendorData = new OuiKeyedData[0];
    public int pairingInstanceId = 0;
    public boolean pairingSuccess = false;
    public boolean enablePairingCache = false;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeInt(this.pairingInstanceId);
        parcel.writeBoolean(this.pairingSuccess);
        parcel.writeTypedObject(this.status, i);
        parcel.writeInt(this.requestType);
        parcel.writeBoolean(this.enablePairingCache);
        parcel.writeTypedObject(this.npksa, i);
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
                this.pairingInstanceId = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.pairingSuccess = parcel.readBoolean();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.status = (NanStatus) parcel.readTypedObject(NanStatus.CREATOR);
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.requestType = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.enablePairingCache = parcel.readBoolean();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.npksa = (NpkSecurityAssociation) parcel.readTypedObject(NpkSecurityAssociation.CREATOR);
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
        return describeContents(this.vendorData) | describeContents(this.status) | describeContents(this.npksa);
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
