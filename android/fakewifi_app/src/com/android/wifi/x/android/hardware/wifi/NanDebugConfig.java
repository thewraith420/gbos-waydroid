package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanDebugConfig implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanDebugConfig.1
        @Override // android.os.Parcelable.Creator
        public NanDebugConfig createFromParcel(Parcel parcel) {
            NanDebugConfig nanDebugConfig = new NanDebugConfig();
            nanDebugConfig.readFromParcel(parcel);
            return nanDebugConfig;
        }

        @Override // android.os.Parcelable.Creator
        public NanDebugConfig[] newArray(int i) {
            return new NanDebugConfig[i];
        }
    };
    public int[] discoveryChannelMhzVal = new int[0];
    public byte[] intfAddrVal = new byte[0];
    public boolean[] useBeaconsInBandVal = new boolean[0];
    public boolean[] useSdfInBandVal = new boolean[0];
    public boolean validClusterIdVals = false;
    public char clusterIdBottomRangeVal = 0;
    public char clusterIdTopRangeVal = 0;
    public boolean validIntfAddrVal = false;
    public boolean validOuiVal = false;
    public int ouiVal = 0;
    public boolean validRandomFactorForceVal = false;
    public byte randomFactorForceVal = 0;
    public boolean validHopCountForceVal = false;
    public byte hopCountForceVal = 0;
    public boolean validDiscoveryChannelVal = false;
    public boolean validUseBeaconsInBandVal = false;
    public boolean validUseSdfInBandVal = false;

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
        parcel.writeBoolean(this.validClusterIdVals);
        parcel.writeInt(this.clusterIdBottomRangeVal);
        parcel.writeInt(this.clusterIdTopRangeVal);
        parcel.writeBoolean(this.validIntfAddrVal);
        parcel.writeFixedArray(this.intfAddrVal, i, 6);
        parcel.writeBoolean(this.validOuiVal);
        parcel.writeInt(this.ouiVal);
        parcel.writeBoolean(this.validRandomFactorForceVal);
        parcel.writeByte(this.randomFactorForceVal);
        parcel.writeBoolean(this.validHopCountForceVal);
        parcel.writeByte(this.hopCountForceVal);
        parcel.writeBoolean(this.validDiscoveryChannelVal);
        parcel.writeFixedArray(this.discoveryChannelMhzVal, i, 3);
        parcel.writeBoolean(this.validUseBeaconsInBandVal);
        parcel.writeFixedArray(this.useBeaconsInBandVal, i, 3);
        parcel.writeBoolean(this.validUseSdfInBandVal);
        parcel.writeFixedArray(this.useSdfInBandVal, i, 3);
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
                this.validClusterIdVals = parcel.readBoolean();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.clusterIdBottomRangeVal = (char) parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.clusterIdTopRangeVal = (char) parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.validIntfAddrVal = parcel.readBoolean();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.intfAddrVal = (byte[]) parcel.createFixedArray(byte[].class, 6);
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.validOuiVal = parcel.readBoolean();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.ouiVal = parcel.readInt();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.validRandomFactorForceVal = parcel.readBoolean();
                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                this.randomFactorForceVal = parcel.readByte();
                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                    this.validHopCountForceVal = parcel.readBoolean();
                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                        this.hopCountForceVal = parcel.readByte();
                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                            this.validDiscoveryChannelVal = parcel.readBoolean();
                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                this.discoveryChannelMhzVal = (int[]) parcel.createFixedArray(int[].class, 3);
                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                    this.validUseBeaconsInBandVal = parcel.readBoolean();
                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                        this.useBeaconsInBandVal = (boolean[]) parcel.createFixedArray(boolean[].class, 3);
                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                            this.validUseSdfInBandVal = parcel.readBoolean();
                                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                this.useSdfInBandVal = (boolean[]) parcel.createFixedArray(boolean[].class, 3);
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
