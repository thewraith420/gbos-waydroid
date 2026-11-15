package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;
import com.android.wifi.x.android.hardware.wifi.common.OuiKeyedData;

/* loaded from: classes.dex */
public class RttCapabilities implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.RttCapabilities.1
        @Override // android.os.Parcelable.Creator
        public RttCapabilities createFromParcel(Parcel parcel) {
            RttCapabilities rttCapabilities = new RttCapabilities();
            rttCapabilities.readFromParcel(parcel);
            return rttCapabilities;
        }

        @Override // android.os.Parcelable.Creator
        public RttCapabilities[] newArray(int i) {
            return new RttCapabilities[i];
        }
    };
    public int bwSupport;
    public int preambleSupport;
    public OuiKeyedData[] vendorData = new OuiKeyedData[0];
    public boolean rttOneSidedSupported = false;
    public boolean rttFtmSupported = false;
    public boolean lciSupported = false;
    public boolean lcrSupported = false;
    public boolean responderSupported = false;
    public byte mcVersion = 0;
    public int azPreambleSupport = 0;
    public int azBwSupport = 0;
    public boolean ntbInitiatorSupported = false;
    public boolean ntbResponderSupported = false;
    public long akmsSupported = 0;
    public long cipherSuitesSupported = 0;
    public boolean secureHeLtfSupported = false;
    public boolean rangingFrameProtectionSupported = false;
    public int maxSupportedSecureHeLtfProtocolVersion = 0;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeBoolean(this.rttOneSidedSupported);
        parcel.writeBoolean(this.rttFtmSupported);
        parcel.writeBoolean(this.lciSupported);
        parcel.writeBoolean(this.lcrSupported);
        parcel.writeBoolean(this.responderSupported);
        parcel.writeInt(this.preambleSupport);
        parcel.writeInt(this.bwSupport);
        parcel.writeByte(this.mcVersion);
        parcel.writeInt(this.azPreambleSupport);
        parcel.writeInt(this.azBwSupport);
        parcel.writeBoolean(this.ntbInitiatorSupported);
        parcel.writeBoolean(this.ntbResponderSupported);
        parcel.writeTypedArray(this.vendorData, i);
        parcel.writeLong(this.akmsSupported);
        parcel.writeLong(this.cipherSuitesSupported);
        parcel.writeBoolean(this.secureHeLtfSupported);
        parcel.writeBoolean(this.rangingFrameProtectionSupported);
        parcel.writeInt(this.maxSupportedSecureHeLtfProtocolVersion);
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
                this.rttOneSidedSupported = parcel.readBoolean();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.rttFtmSupported = parcel.readBoolean();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.lciSupported = parcel.readBoolean();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.lcrSupported = parcel.readBoolean();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.responderSupported = parcel.readBoolean();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.preambleSupport = parcel.readInt();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.bwSupport = parcel.readInt();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.mcVersion = parcel.readByte();
                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                this.azPreambleSupport = parcel.readInt();
                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                    this.azBwSupport = parcel.readInt();
                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                        this.ntbInitiatorSupported = parcel.readBoolean();
                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                            this.ntbResponderSupported = parcel.readBoolean();
                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                this.vendorData = (OuiKeyedData[]) parcel.createTypedArray(OuiKeyedData.CREATOR);
                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                    this.akmsSupported = parcel.readLong();
                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                        this.cipherSuitesSupported = parcel.readLong();
                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                            this.secureHeLtfSupported = parcel.readBoolean();
                                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                this.rangingFrameProtectionSupported = parcel.readBoolean();
                                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                    this.maxSupportedSecureHeLtfProtocolVersion = parcel.readInt();
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
        return describeContents(this.vendorData);
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
