package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanRespondToDataPathIndicationRequest implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanRespondToDataPathIndicationRequest.1
        @Override // android.os.Parcelable.Creator
        public NanRespondToDataPathIndicationRequest createFromParcel(Parcel parcel) {
            NanRespondToDataPathIndicationRequest nanRespondToDataPathIndicationRequest = new NanRespondToDataPathIndicationRequest();
            nanRespondToDataPathIndicationRequest.readFromParcel(parcel);
            return nanRespondToDataPathIndicationRequest;
        }

        @Override // android.os.Parcelable.Creator
        public NanRespondToDataPathIndicationRequest[] newArray(int i) {
            return new NanRespondToDataPathIndicationRequest[i];
        }
    };
    public byte[] appInfo = new byte[0];
    public String ifaceName = "";
    public NanDataPathSecurityConfig securityConfig = new NanDataPathSecurityConfig();
    public byte[] serviceNameOutOfBand = new byte[0];
    public boolean acceptRequest = false;
    public int ndpInstanceId = 0;
    public byte discoverySessionId = 0;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeBoolean(this.acceptRequest);
        parcel.writeInt(this.ndpInstanceId);
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
                this.acceptRequest = parcel.readBoolean();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.ndpInstanceId = parcel.readInt();
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
