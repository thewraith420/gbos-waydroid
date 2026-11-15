package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public class TwtSession implements Parcelable {
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.TwtSession.1
        @Override // android.os.Parcelable.Creator
        public TwtSession createFromParcel(Parcel parcel) {
            TwtSession twtSession = new TwtSession();
            twtSession.readFromParcel(parcel);
            return twtSession;
        }

        @Override // android.os.Parcelable.Creator
        public TwtSession[] newArray(int i) {
            return new TwtSession[i];
        }
    };
    public byte negotiationType;
    public int sessionId = 0;
    public int mloLinkId = 0;
    public int wakeDurationUs = 0;
    public long wakeIntervalUs = 0;
    public boolean isTriggerEnabled = false;
    public boolean isAnnounced = false;
    public boolean isImplicit = false;
    public boolean isProtected = false;
    public boolean isUpdatable = false;
    public boolean isSuspendable = false;
    public boolean isResponderPmModeEnabled = false;

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
        parcel.writeInt(this.sessionId);
        parcel.writeInt(this.mloLinkId);
        parcel.writeInt(this.wakeDurationUs);
        parcel.writeLong(this.wakeIntervalUs);
        parcel.writeByte(this.negotiationType);
        parcel.writeBoolean(this.isTriggerEnabled);
        parcel.writeBoolean(this.isAnnounced);
        parcel.writeBoolean(this.isImplicit);
        parcel.writeBoolean(this.isProtected);
        parcel.writeBoolean(this.isUpdatable);
        parcel.writeBoolean(this.isSuspendable);
        parcel.writeBoolean(this.isResponderPmModeEnabled);
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
                this.sessionId = parcel.readInt();
                if (parcel.dataPosition() - dataPosition < readInt) {
                    this.mloLinkId = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.wakeDurationUs = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.wakeIntervalUs = parcel.readLong();
                            if (parcel.dataPosition() - dataPosition < readInt) {
                                this.negotiationType = parcel.readByte();
                                if (parcel.dataPosition() - dataPosition < readInt) {
                                    this.isTriggerEnabled = parcel.readBoolean();
                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                        this.isAnnounced = parcel.readBoolean();
                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                            this.isImplicit = parcel.readBoolean();
                                            if (parcel.dataPosition() - dataPosition < readInt) {
                                                this.isProtected = parcel.readBoolean();
                                                if (parcel.dataPosition() - dataPosition < readInt) {
                                                    this.isUpdatable = parcel.readBoolean();
                                                    if (parcel.dataPosition() - dataPosition < readInt) {
                                                        this.isSuspendable = parcel.readBoolean();
                                                        if (parcel.dataPosition() - dataPosition < readInt) {
                                                            this.isResponderPmModeEnabled = parcel.readBoolean();
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
}
