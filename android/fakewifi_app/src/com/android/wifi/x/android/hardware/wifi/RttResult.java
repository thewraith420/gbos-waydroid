package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;
import com.android.wifi.x.android.hardware.wifi.common.OuiKeyedData;

/* loaded from: classes.dex */
public class RttResult implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.RttResult.1
        @Override // android.os.Parcelable.Creator
        public RttResult createFromParcel(Parcel parcel) {
            RttResult rttResult = new RttResult();
            rttResult.readFromParcel(parcel);
            return rttResult;
        }

        @Override // android.os.Parcelable.Creator
        public RttResult[] newArray(int i) {
            return new RttResult[i];
        }
    };
    public byte[] addr = new byte[0];
    public WifiInformationElement lci = new WifiInformationElement();
    public WifiInformationElement lcr = new WifiInformationElement();
    public int packetBw;
    public byte[] pasnComebackCookie = new byte[0];
    public WifiRateInfo rxRate = new WifiRateInfo();
    public int status;
    public WifiRateInfo txRate = new WifiRateInfo();
    public int type;
    public OuiKeyedData[] vendorData = new OuiKeyedData[0];
    public int burstNum = 0;
    public int measurementNumber = 0;
    public int successNumber = 0;
    public byte numberPerBurstPeer = 0;
    public byte retryAfterDuration = 0;
    public int rssi = 0;
    public int rssiSpread = 0;
    public long rtt = 0;
    public long rttSd = 0;
    public long rttSpread = 0;
    public int distanceInMm = 0;
    public int distanceSdInMm = 0;
    public int distanceSpreadInMm = 0;
    public long timeStampInUs = 0;
    public int burstDurationInMs = 0;
    public int negotiatedBurstNum = 0;
    public int channelFreqMHz = 0;
    public byte i2rTxLtfRepetitionCount = 0;
    public byte r2iTxLtfRepetitionCount = 0;
    public long ntbMinMeasurementTime = 0;
    public long ntbMaxMeasurementTime = 0;
    public byte numTxSpatialStreams = 0;
    public byte numRxSpatialStreams = 0;
    public boolean isRangingFrameProtectionEnabled = false;
    public boolean isSecureLtfEnabled = false;
    public long baseAkm = 0;
    public long cipherSuite = 0;
    public int secureHeLtfProtocolVersion = 0;
    public long pasnComebackAfterMillis = 0;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeFixedArray(this.addr, i, 6);
        parcel.writeInt(this.burstNum);
        parcel.writeInt(this.measurementNumber);
        parcel.writeInt(this.successNumber);
        parcel.writeByte(this.numberPerBurstPeer);
        parcel.writeInt(this.status);
        parcel.writeByte(this.retryAfterDuration);
        parcel.writeInt(this.type);
        parcel.writeInt(this.rssi);
        parcel.writeInt(this.rssiSpread);
        parcel.writeTypedObject(this.txRate, i);
        parcel.writeTypedObject(this.rxRate, i);
        parcel.writeLong(this.rtt);
        parcel.writeLong(this.rttSd);
        parcel.writeLong(this.rttSpread);
        parcel.writeInt(this.distanceInMm);
        parcel.writeInt(this.distanceSdInMm);
        parcel.writeInt(this.distanceSpreadInMm);
        parcel.writeLong(this.timeStampInUs);
        parcel.writeInt(this.burstDurationInMs);
        parcel.writeInt(this.negotiatedBurstNum);
        parcel.writeTypedObject(this.lci, i);
        parcel.writeTypedObject(this.lcr, i);
        parcel.writeInt(this.channelFreqMHz);
        parcel.writeInt(this.packetBw);
        parcel.writeByte(this.i2rTxLtfRepetitionCount);
        parcel.writeByte(this.r2iTxLtfRepetitionCount);
        parcel.writeLong(this.ntbMinMeasurementTime);
        parcel.writeLong(this.ntbMaxMeasurementTime);
        parcel.writeByte(this.numTxSpatialStreams);
        parcel.writeByte(this.numRxSpatialStreams);
        parcel.writeTypedArray(this.vendorData, i);
        parcel.writeBoolean(this.isRangingFrameProtectionEnabled);
        parcel.writeBoolean(this.isSecureLtfEnabled);
        parcel.writeLong(this.baseAkm);
        parcel.writeLong(this.cipherSuite);
        parcel.writeInt(this.secureHeLtfProtocolVersion);
        parcel.writeLong(this.pasnComebackAfterMillis);
        parcel.writeByteArray(this.pasnComebackCookie);
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
                    this.burstNum = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.measurementNumber = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.successNumber = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.numberPerBurstPeer = parcel.readByte();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.status = parcel.readInt();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.retryAfterDuration = parcel.readByte();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.type = parcel.readInt();
                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                this.rssi = parcel.readInt();
                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                    this.rssiSpread = parcel.readInt();
                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                        Parcelable.Creator creator = WifiRateInfo.CREATOR;
                                                        this.txRate = (WifiRateInfo) parcel.readTypedObject(creator);
                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                            this.rxRate = (WifiRateInfo) parcel.readTypedObject(creator);
                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                this.rtt = parcel.readLong();
                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                    this.rttSd = parcel.readLong();
                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                        this.rttSpread = parcel.readLong();
                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                            this.distanceInMm = parcel.readInt();
                                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                this.distanceSdInMm = parcel.readInt();
                                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                    this.distanceSpreadInMm = parcel.readInt();
                                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                        this.timeStampInUs = parcel.readLong();
                                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                            this.burstDurationInMs = parcel.readInt();
                                                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                this.negotiatedBurstNum = parcel.readInt();
                                                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                    Parcelable.Creator creator2 = WifiInformationElement.CREATOR;
                                                                                                    this.lci = (WifiInformationElement) parcel.readTypedObject(creator2);
                                                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                        this.lcr = (WifiInformationElement) parcel.readTypedObject(creator2);
                                                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                            this.channelFreqMHz = parcel.readInt();
                                                                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                this.packetBw = parcel.readInt();
                                                                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                    this.i2rTxLtfRepetitionCount = parcel.readByte();
                                                                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                        this.r2iTxLtfRepetitionCount = parcel.readByte();
                                                                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                            this.ntbMinMeasurementTime = parcel.readLong();
                                                                                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                                this.ntbMaxMeasurementTime = parcel.readLong();
                                                                                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                                    this.numTxSpatialStreams = parcel.readByte();
                                                                                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                                        this.numRxSpatialStreams = parcel.readByte();
                                                                                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                                            this.vendorData = (OuiKeyedData[]) parcel.createTypedArray(OuiKeyedData.CREATOR);
                                                                                                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                                                this.isRangingFrameProtectionEnabled = parcel.readBoolean();
                                                                                                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                                                    this.isSecureLtfEnabled = parcel.readBoolean();
                                                                                                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                                                        this.baseAkm = parcel.readLong();
                                                                                                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                                                            this.cipherSuite = parcel.readLong();
                                                                                                                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                                                                this.secureHeLtfProtocolVersion = parcel.readInt();
                                                                                                                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                                                                    this.pasnComebackAfterMillis = parcel.readLong();
                                                                                                                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                                                                                        this.pasnComebackCookie = parcel.createByteArray();
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
        return describeContents(this.vendorData) | describeContents(this.txRate) | describeContents(this.rxRate) | describeContents(this.lci) | describeContents(this.lcr);
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
