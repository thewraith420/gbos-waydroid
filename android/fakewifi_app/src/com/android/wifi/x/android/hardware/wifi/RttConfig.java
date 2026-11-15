package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;
import com.android.wifi.x.android.hardware.wifi.common.OuiKeyedData;

/* loaded from: classes.dex */
public class RttConfig implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.RttConfig.1
        @Override // android.os.Parcelable.Creator
        public RttConfig createFromParcel(Parcel parcel) {
            RttConfig rttConfig = new RttConfig();
            rttConfig.readFromParcel(parcel);
            return rttConfig;
        }

        @Override // android.os.Parcelable.Creator
        public RttConfig[] newArray(int i) {
            return new RttConfig[i];
        }
    };
    public byte[] addr = new byte[0];
    public int bw;
    public WifiChannelInfo channel = new WifiChannelInfo();
    public int peer;
    public int preamble;
    public RttSecureConfig secureConfig = new RttSecureConfig();
    public int type;
    public OuiKeyedData[] vendorData = new OuiKeyedData[0];
    public int burstPeriod = 0;
    public int numBurst = 0;
    public int numFramesPerBurst = 0;
    public int numRetriesPerRttFrame = 0;
    public int numRetriesPerFtmr = 0;
    public boolean mustRequestLci = false;
    public boolean mustRequestLcr = false;
    public int burstDuration = 0;
    public long ntbMinMeasurementTime = 0;
    public long ntbMaxMeasurementTime = 0;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeFixedArray(this.addr, i, 6);
        parcel.writeInt(this.type);
        parcel.writeInt(this.peer);
        parcel.writeTypedObject(this.channel, i);
        parcel.writeInt(this.burstPeriod);
        parcel.writeInt(this.numBurst);
        parcel.writeInt(this.numFramesPerBurst);
        parcel.writeInt(this.numRetriesPerRttFrame);
        parcel.writeInt(this.numRetriesPerFtmr);
        parcel.writeBoolean(this.mustRequestLci);
        parcel.writeBoolean(this.mustRequestLcr);
        parcel.writeInt(this.burstDuration);
        parcel.writeInt(this.preamble);
        parcel.writeInt(this.bw);
        parcel.writeLong(this.ntbMinMeasurementTime);
        parcel.writeLong(this.ntbMaxMeasurementTime);
        parcel.writeTypedArray(this.vendorData, i);
        parcel.writeTypedObject(this.secureConfig, i);
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
                this.addr = (byte[]) parcel.createFixedArray(byte[].class, 6);
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.type = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.peer = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.channel = (WifiChannelInfo) parcel.readTypedObject(WifiChannelInfo.CREATOR);
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.burstPeriod = parcel.readInt();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.numBurst = parcel.readInt();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.numFramesPerBurst = parcel.readInt();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.numRetriesPerRttFrame = parcel.readInt();
                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                this.numRetriesPerFtmr = parcel.readInt();
                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                    this.mustRequestLci = parcel.readBoolean();
                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                        this.mustRequestLcr = parcel.readBoolean();
                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                            this.burstDuration = parcel.readInt();
                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                this.preamble = parcel.readInt();
                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                    this.bw = parcel.readInt();
                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                        this.ntbMinMeasurementTime = parcel.readLong();
                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                            this.ntbMaxMeasurementTime = parcel.readLong();
                                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                this.vendorData = (OuiKeyedData[]) parcel.createTypedArray(OuiKeyedData.CREATOR);
                                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                    this.secureConfig = (RttSecureConfig) parcel.readTypedObject(RttSecureConfig.CREATOR);
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
        return describeContents(this.secureConfig) | describeContents(this.channel) | describeContents(this.vendorData);
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
