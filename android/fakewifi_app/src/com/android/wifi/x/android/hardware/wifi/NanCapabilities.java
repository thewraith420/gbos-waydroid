package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanCapabilities implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanCapabilities.1
        @Override // android.os.Parcelable.Creator
        public NanCapabilities createFromParcel(Parcel parcel) {
            NanCapabilities nanCapabilities = new NanCapabilities();
            nanCapabilities.readFromParcel(parcel);
            return nanCapabilities;
        }

        @Override // android.os.Parcelable.Creator
        public NanCapabilities[] newArray(int i) {
            return new NanCapabilities[i];
        }
    };
    public int maxSupportedBandwidth;
    public int maxConcurrentClusters = 0;
    public int maxPublishes = 0;
    public int maxSubscribes = 0;
    public int maxServiceNameLen = 0;
    public int maxMatchFilterLen = 0;
    public int maxTotalMatchFilterLen = 0;
    public int maxServiceSpecificInfoLen = 0;
    public int maxExtendedServiceSpecificInfoLen = 0;
    public int maxNdiInterfaces = 0;
    public int maxNdpSessions = 0;
    public int maxAppInfoLen = 0;
    public int maxQueuedTransmitFollowupMsgs = 0;
    public int maxSubscribeInterfaceAddresses = 0;
    public int supportedCipherSuites = 0;
    public boolean instantCommunicationModeSupportFlag = false;
    public boolean supports6g = false;
    public boolean supportsHe = false;
    public boolean supportsPairing = false;
    public boolean supportsSetClusterId = false;
    public boolean supportsSuspension = false;
    public boolean supportsPeriodicRanging = false;
    public int maxNumRxChainsSupported = 0;
    public int supportedPeriodicRangingIntervals = 0;

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
        parcel.writeInt(this.maxConcurrentClusters);
        parcel.writeInt(this.maxPublishes);
        parcel.writeInt(this.maxSubscribes);
        parcel.writeInt(this.maxServiceNameLen);
        parcel.writeInt(this.maxMatchFilterLen);
        parcel.writeInt(this.maxTotalMatchFilterLen);
        parcel.writeInt(this.maxServiceSpecificInfoLen);
        parcel.writeInt(this.maxExtendedServiceSpecificInfoLen);
        parcel.writeInt(this.maxNdiInterfaces);
        parcel.writeInt(this.maxNdpSessions);
        parcel.writeInt(this.maxAppInfoLen);
        parcel.writeInt(this.maxQueuedTransmitFollowupMsgs);
        parcel.writeInt(this.maxSubscribeInterfaceAddresses);
        parcel.writeInt(this.supportedCipherSuites);
        parcel.writeBoolean(this.instantCommunicationModeSupportFlag);
        parcel.writeBoolean(this.supports6g);
        parcel.writeBoolean(this.supportsHe);
        parcel.writeBoolean(this.supportsPairing);
        parcel.writeBoolean(this.supportsSetClusterId);
        parcel.writeBoolean(this.supportsSuspension);
        parcel.writeBoolean(this.supportsPeriodicRanging);
        parcel.writeInt(this.maxSupportedBandwidth);
        parcel.writeInt(this.maxNumRxChainsSupported);
        parcel.writeInt(this.supportedPeriodicRangingIntervals);
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
                this.maxConcurrentClusters = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.maxPublishes = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.maxSubscribes = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.maxServiceNameLen = parcel.readInt();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.maxMatchFilterLen = parcel.readInt();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.maxTotalMatchFilterLen = parcel.readInt();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.maxServiceSpecificInfoLen = parcel.readInt();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.maxExtendedServiceSpecificInfoLen = parcel.readInt();
                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                this.maxNdiInterfaces = parcel.readInt();
                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                    this.maxNdpSessions = parcel.readInt();
                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                        this.maxAppInfoLen = parcel.readInt();
                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                            this.maxQueuedTransmitFollowupMsgs = parcel.readInt();
                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                this.maxSubscribeInterfaceAddresses = parcel.readInt();
                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                    this.supportedCipherSuites = parcel.readInt();
                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                        this.instantCommunicationModeSupportFlag = parcel.readBoolean();
                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                            this.supports6g = parcel.readBoolean();
                                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                this.supportsHe = parcel.readBoolean();
                                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                    this.supportsPairing = parcel.readBoolean();
                                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                        this.supportsSetClusterId = parcel.readBoolean();
                                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                            this.supportsSuspension = parcel.readBoolean();
                                                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                this.supportsPeriodicRanging = parcel.readBoolean();
                                                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                    this.maxSupportedBandwidth = parcel.readInt();
                                                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                        this.maxNumRxChainsSupported = parcel.readInt();
                                                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                            this.supportedPeriodicRangingIntervals = parcel.readInt();
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
