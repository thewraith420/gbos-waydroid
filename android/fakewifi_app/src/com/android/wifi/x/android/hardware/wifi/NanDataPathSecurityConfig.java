package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanDataPathSecurityConfig implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanDataPathSecurityConfig.1
        @Override // android.os.Parcelable.Creator
        public NanDataPathSecurityConfig createFromParcel(Parcel parcel) {
            NanDataPathSecurityConfig nanDataPathSecurityConfig = new NanDataPathSecurityConfig();
            nanDataPathSecurityConfig.readFromParcel(parcel);
            return nanDataPathSecurityConfig;
        }

        @Override // android.os.Parcelable.Creator
        public NanDataPathSecurityConfig[] newArray(int i) {
            return new NanDataPathSecurityConfig[i];
        }
    };
    public int cipherType;
    public byte[] passphrase = new byte[0];
    public byte[] pmk = new byte[0];
    public byte[] scid = new byte[0];
    public int securityType;
    public boolean enable16ReplyCountersForTksa = false;
    public boolean enable16ReplyCountersForGtksa = false;
    public boolean supportGtkAndIgtk = false;
    public boolean supportBigtksa = false;
    public boolean enableNcsBip256 = false;
    public boolean requiresEnhancedFrameProtection = false;

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
        parcel.writeInt(this.securityType);
        parcel.writeInt(this.cipherType);
        parcel.writeFixedArray(this.pmk, i, 32);
        parcel.writeByteArray(this.passphrase);
        parcel.writeFixedArray(this.scid, i, 16);
        parcel.writeBoolean(this.enable16ReplyCountersForTksa);
        parcel.writeBoolean(this.enable16ReplyCountersForGtksa);
        parcel.writeBoolean(this.supportGtkAndIgtk);
        parcel.writeBoolean(this.supportBigtksa);
        parcel.writeBoolean(this.enableNcsBip256);
        parcel.writeBoolean(this.requiresEnhancedFrameProtection);
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
                this.securityType = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.cipherType = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.pmk = (byte[]) parcel.createFixedArray(byte[].class, 32);
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.passphrase = parcel.createByteArray();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.scid = (byte[]) parcel.createFixedArray(byte[].class, 16);
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.enable16ReplyCountersForTksa = parcel.readBoolean();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.enable16ReplyCountersForGtksa = parcel.readBoolean();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.supportGtkAndIgtk = parcel.readBoolean();
                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                this.supportBigtksa = parcel.readBoolean();
                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                    this.enableNcsBip256 = parcel.readBoolean();
                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                        this.requiresEnhancedFrameProtection = parcel.readBoolean();
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
