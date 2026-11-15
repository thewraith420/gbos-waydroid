package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class StaLinkLayerLinkStats implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.StaLinkLayerLinkStats.1
        @Override // android.os.Parcelable.Creator
        public StaLinkLayerLinkStats createFromParcel(Parcel parcel) {
            StaLinkLayerLinkStats staLinkLayerLinkStats = new StaLinkLayerLinkStats();
            staLinkLayerLinkStats.readFromParcel(parcel);
            return staLinkLayerLinkStats;
        }

        @Override // android.os.Parcelable.Creator
        public StaLinkLayerLinkStats[] newArray(int i) {
            return new StaLinkLayerLinkStats[i];
        }
    };
    public StaPeerInfo[] peers = new StaPeerInfo[0];
    public int state;
    public StaLinkLayerIfaceContentionTimeStats wmeBeContentionTimeStats = new StaLinkLayerIfaceContentionTimeStats();
    public StaLinkLayerIfacePacketStats wmeBePktStats = new StaLinkLayerIfacePacketStats();
    public StaLinkLayerIfaceContentionTimeStats wmeBkContentionTimeStats = new StaLinkLayerIfaceContentionTimeStats();
    public StaLinkLayerIfacePacketStats wmeBkPktStats = new StaLinkLayerIfacePacketStats();
    public StaLinkLayerIfaceContentionTimeStats wmeViContentionTimeStats = new StaLinkLayerIfaceContentionTimeStats();
    public StaLinkLayerIfacePacketStats wmeViPktStats = new StaLinkLayerIfacePacketStats();
    public StaLinkLayerIfaceContentionTimeStats wmeVoContentionTimeStats = new StaLinkLayerIfaceContentionTimeStats();
    public StaLinkLayerIfacePacketStats wmeVoPktStats = new StaLinkLayerIfacePacketStats();
    public int linkId = 0;
    public int radioId = 0;
    public int frequencyMhz = 0;
    public int beaconRx = 0;
    public int avgRssiMgmt = 0;
    public byte timeSliceDutyCycleInPercent = 0;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeInt(this.linkId);
        parcel.writeInt(this.radioId);
        parcel.writeInt(this.frequencyMhz);
        parcel.writeInt(this.beaconRx);
        parcel.writeInt(this.avgRssiMgmt);
        parcel.writeTypedObject(this.wmeBePktStats, i);
        parcel.writeTypedObject(this.wmeBkPktStats, i);
        parcel.writeTypedObject(this.wmeViPktStats, i);
        parcel.writeTypedObject(this.wmeVoPktStats, i);
        parcel.writeByte(this.timeSliceDutyCycleInPercent);
        parcel.writeTypedObject(this.wmeBeContentionTimeStats, i);
        parcel.writeTypedObject(this.wmeBkContentionTimeStats, i);
        parcel.writeTypedObject(this.wmeViContentionTimeStats, i);
        parcel.writeTypedObject(this.wmeVoContentionTimeStats, i);
        parcel.writeTypedArray(this.peers, i);
        parcel.writeInt(this.state);
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
                this.linkId = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.radioId = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.frequencyMhz = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.beaconRx = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.avgRssiMgmt = parcel.readInt();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    Parcelable.Creator creator = StaLinkLayerIfacePacketStats.CREATOR;
                                    this.wmeBePktStats = (StaLinkLayerIfacePacketStats) parcel.readTypedObject(creator);
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.wmeBkPktStats = (StaLinkLayerIfacePacketStats) parcel.readTypedObject(creator);
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.wmeViPktStats = (StaLinkLayerIfacePacketStats) parcel.readTypedObject(creator);
                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                this.wmeVoPktStats = (StaLinkLayerIfacePacketStats) parcel.readTypedObject(creator);
                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                    this.timeSliceDutyCycleInPercent = parcel.readByte();
                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                        Parcelable.Creator creator2 = StaLinkLayerIfaceContentionTimeStats.CREATOR;
                                                        this.wmeBeContentionTimeStats = (StaLinkLayerIfaceContentionTimeStats) parcel.readTypedObject(creator2);
                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                            this.wmeBkContentionTimeStats = (StaLinkLayerIfaceContentionTimeStats) parcel.readTypedObject(creator2);
                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                this.wmeViContentionTimeStats = (StaLinkLayerIfaceContentionTimeStats) parcel.readTypedObject(creator2);
                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                    this.wmeVoContentionTimeStats = (StaLinkLayerIfaceContentionTimeStats) parcel.readTypedObject(creator2);
                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                        this.peers = (StaPeerInfo[]) parcel.createTypedArray(StaPeerInfo.CREATOR);
                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                            this.state = parcel.readInt();
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
        return describeContents(this.peers) | describeContents(this.wmeBePktStats) | describeContents(this.wmeBkPktStats) | describeContents(this.wmeViPktStats) | describeContents(this.wmeVoPktStats) | describeContents(this.wmeBeContentionTimeStats) | describeContents(this.wmeBkContentionTimeStats) | describeContents(this.wmeViContentionTimeStats) | describeContents(this.wmeVoContentionTimeStats);
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
