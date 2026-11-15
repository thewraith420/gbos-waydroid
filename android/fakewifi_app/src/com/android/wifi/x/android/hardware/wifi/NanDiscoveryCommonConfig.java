package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class NanDiscoveryCommonConfig implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.NanDiscoveryCommonConfig.1
        @Override // android.os.Parcelable.Creator
        public NanDiscoveryCommonConfig createFromParcel(Parcel parcel) {
            NanDiscoveryCommonConfig nanDiscoveryCommonConfig = new NanDiscoveryCommonConfig();
            nanDiscoveryCommonConfig.readFromParcel(parcel);
            return nanDiscoveryCommonConfig;
        }

        @Override // android.os.Parcelable.Creator
        public NanDiscoveryCommonConfig[] newArray(int i) {
            return new NanDiscoveryCommonConfig[i];
        }
    };
    public WifiChannelInfo channelInfo = new WifiChannelInfo();
    public int discoveryMatchIndicator;
    public byte[] extendedServiceSpecificInfo = new byte[0];
    public int preamble;
    public byte[] rxMatchFilter = new byte[0];
    public NanDataPathSecurityConfig securityConfig = new NanDataPathSecurityConfig();
    public byte[] serviceName = new byte[0];
    public byte[] serviceSpecificInfo = new byte[0];
    public byte[] txMatchFilter = new byte[0];
    public byte sessionId = 0;
    public char ttlSec = 0;
    public char discoveryWindowPeriod = 0;
    public byte discoveryCount = 0;
    public boolean useRssiThreshold = false;
    public boolean disableDiscoveryTerminationIndication = false;
    public boolean disableMatchExpirationIndication = false;
    public boolean disableFollowupReceivedIndication = false;
    public boolean rangingRequired = false;
    public int rangingIntervalMs = 0;
    public int configRangingIndications = 0;
    public char distanceIngressCm = 0;
    public char distanceEgressCm = 0;
    public boolean enableSessionSuspendability = false;
    public int rttBurstSize = 0;

    public final int getStability() {
        return 1;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        int dataPosition = parcel.dataPosition();
        parcel.writeInt(0);
        parcel.writeByte(this.sessionId);
        parcel.writeInt(this.ttlSec);
        parcel.writeInt(this.discoveryWindowPeriod);
        parcel.writeByte(this.discoveryCount);
        parcel.writeByteArray(this.serviceName);
        parcel.writeInt(this.discoveryMatchIndicator);
        parcel.writeByteArray(this.serviceSpecificInfo);
        parcel.writeByteArray(this.extendedServiceSpecificInfo);
        parcel.writeByteArray(this.rxMatchFilter);
        parcel.writeByteArray(this.txMatchFilter);
        parcel.writeBoolean(this.useRssiThreshold);
        parcel.writeBoolean(this.disableDiscoveryTerminationIndication);
        parcel.writeBoolean(this.disableMatchExpirationIndication);
        parcel.writeBoolean(this.disableFollowupReceivedIndication);
        parcel.writeTypedObject(this.securityConfig, i);
        parcel.writeBoolean(this.rangingRequired);
        parcel.writeInt(this.rangingIntervalMs);
        parcel.writeInt(this.configRangingIndications);
        parcel.writeInt(this.distanceIngressCm);
        parcel.writeInt(this.distanceEgressCm);
        parcel.writeBoolean(this.enableSessionSuspendability);
        parcel.writeInt(this.rttBurstSize);
        parcel.writeInt(this.preamble);
        parcel.writeTypedObject(this.channelInfo, i);
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
                this.sessionId = parcel.readByte();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.ttlSec = (char) parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.discoveryWindowPeriod = (char) parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.discoveryCount = parcel.readByte();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.serviceName = parcel.createByteArray();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.discoveryMatchIndicator = parcel.readInt();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.serviceSpecificInfo = parcel.createByteArray();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.extendedServiceSpecificInfo = parcel.createByteArray();
                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                this.rxMatchFilter = parcel.createByteArray();
                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                    this.txMatchFilter = parcel.createByteArray();
                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                        this.useRssiThreshold = parcel.readBoolean();
                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                            this.disableDiscoveryTerminationIndication = parcel.readBoolean();
                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                this.disableMatchExpirationIndication = parcel.readBoolean();
                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                    this.disableFollowupReceivedIndication = parcel.readBoolean();
                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                        this.securityConfig = (NanDataPathSecurityConfig) parcel.readTypedObject(NanDataPathSecurityConfig.CREATOR);
                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                            this.rangingRequired = parcel.readBoolean();
                                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                this.rangingIntervalMs = parcel.readInt();
                                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                    this.configRangingIndications = parcel.readInt();
                                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                        this.distanceIngressCm = (char) parcel.readInt();
                                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                            this.distanceEgressCm = (char) parcel.readInt();
                                                                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                this.enableSessionSuspendability = parcel.readBoolean();
                                                                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                    this.rttBurstSize = parcel.readInt();
                                                                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                        this.preamble = parcel.readInt();
                                                                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                                                                            this.channelInfo = (WifiChannelInfo) parcel.readTypedObject(WifiChannelInfo.CREATOR);
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

    @Override // android.os.Parcelable
    public int describeContents() {
        return describeContents(this.channelInfo) | describeContents(this.securityConfig);
    }

    private int describeContents(Object obj) {
        if (obj != null && (obj instanceof Parcelable)) {
            return ((Parcelable) obj).describeContents();
        }
        return 0;
    }
}
