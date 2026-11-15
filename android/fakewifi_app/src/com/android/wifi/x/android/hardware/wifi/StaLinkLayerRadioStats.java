package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class StaLinkLayerRadioStats implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.StaLinkLayerRadioStats.1
        @Override // android.os.Parcelable.Creator
        public StaLinkLayerRadioStats createFromParcel(Parcel parcel) {
            StaLinkLayerRadioStats staLinkLayerRadioStats = new StaLinkLayerRadioStats();
            staLinkLayerRadioStats.readFromParcel(parcel);
            return staLinkLayerRadioStats;
        }

        @Override // android.os.Parcelable.Creator
        public StaLinkLayerRadioStats[] newArray(int i) {
            return new StaLinkLayerRadioStats[i];
        }
    };
    public WifiChannelStats[] channelStats = new WifiChannelStats[0];
    public int[] txTimeInMsPerLevel = new int[0];
    public int onTimeInMs = 0;
    public int txTimeInMs = 0;
    public int rxTimeInMs = 0;
    public int onTimeInMsForScan = 0;
    public int onTimeInMsForNanScan = 0;
    public int onTimeInMsForBgScan = 0;
    public int onTimeInMsForRoamScan = 0;
    public int onTimeInMsForPnoScan = 0;
    public int onTimeInMsForHs20Scan = 0;
    public int radioId = 0;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeInt(this.onTimeInMs);
        parcel.writeInt(this.txTimeInMs);
        parcel.writeIntArray(this.txTimeInMsPerLevel);
        parcel.writeInt(this.rxTimeInMs);
        parcel.writeInt(this.onTimeInMsForScan);
        parcel.writeInt(this.onTimeInMsForNanScan);
        parcel.writeInt(this.onTimeInMsForBgScan);
        parcel.writeInt(this.onTimeInMsForRoamScan);
        parcel.writeInt(this.onTimeInMsForPnoScan);
        parcel.writeInt(this.onTimeInMsForHs20Scan);
        parcel.writeTypedArray(this.channelStats, i);
        parcel.writeInt(this.radioId);
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
                this.onTimeInMs = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.txTimeInMs = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.txTimeInMsPerLevel = parcel.createIntArray();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.rxTimeInMs = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.onTimeInMsForScan = parcel.readInt();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.onTimeInMsForNanScan = parcel.readInt();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.onTimeInMsForBgScan = parcel.readInt();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.onTimeInMsForRoamScan = parcel.readInt();
                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                this.onTimeInMsForPnoScan = parcel.readInt();
                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                    this.onTimeInMsForHs20Scan = parcel.readInt();
                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                        this.channelStats = (WifiChannelStats[]) parcel.createTypedArray(WifiChannelStats.CREATOR);
                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                            this.radioId = parcel.readInt();
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
        return describeContents(this.channelStats);
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
