package com.android.wifi.x.android.hardware.wifi;

import android.os.BadParcelableException;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: classes.dex */
public interface IWifiChipEventCallback extends IInterface {
    public static final String DESCRIPTOR = "android$hardware$wifi$IWifiChipEventCallback".replace('$', '.');

    String getInterfaceHash() throws android.os.RemoteException;

    int getInterfaceVersion() throws android.os.RemoteException;

    void onChipReconfigureFailure(int i) throws android.os.RemoteException;

    void onChipReconfigured(int i) throws android.os.RemoteException;

    void onDebugErrorAlert(int i, byte[] bArr) throws android.os.RemoteException;

    void onDebugRingBufferDataAvailable(WifiDebugRingBufferStatus wifiDebugRingBufferStatus, byte[] bArr) throws android.os.RemoteException;

    void onIfaceAdded(int i, String str) throws android.os.RemoteException;

    void onIfaceRemoved(int i, String str) throws android.os.RemoteException;

    void onRadioModeChange(RadioModeInfo[] radioModeInfoArr) throws android.os.RemoteException;

    public abstract class Stub extends Binder implements IWifiChipEventCallback {
        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        public Stub() {
            markVintfStability();
            attachInterface(this, IWifiChipEventCallback.DESCRIPTOR);
        }

        @Override // android.os.Binder
        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws android.os.RemoteException {
            if (i >= 1 && i <= 16777215) {
                parcel.enforceInterface(IWifiChipEventCallback.DESCRIPTOR);
            }
            if (i == 16777215) {
                parcel2.writeNoException();
                parcel2.writeInt(getInterfaceVersion());
                return true;
            }
            if (i == 16777214) {
                parcel2.writeNoException();
                parcel2.writeString(getInterfaceHash());
                return true;
            }
            switch (i) {
                case 1:
                    onChipReconfigureFailure(parcel.readInt());
                    return true;
                case 2:
                    onChipReconfigured(parcel.readInt());
                    return true;
                case 3:
                    onDebugErrorAlert(parcel.readInt(), parcel.createByteArray());
                    return true;
                case 4:
                    onDebugRingBufferDataAvailable((WifiDebugRingBufferStatus) parcel.readTypedObject(WifiDebugRingBufferStatus.CREATOR), parcel.createByteArray());
                    return true;
                case 5:
                    onIfaceAdded(parcel.readInt(), parcel.readString());
                    return true;
                case 6:
                    onIfaceRemoved(parcel.readInt(), parcel.readString());
                    return true;
                case 7:
                    onRadioModeChange((RadioModeInfo[]) parcel.createTypedArray(RadioModeInfo.CREATOR));
                    return true;
                default:
                    return super.onTransact(i, parcel, parcel2, i2);
            }
        }
    }

    public class IfaceInfo implements Parcelable {
        public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.IWifiChipEventCallback.IfaceInfo.1
            @Override // android.os.Parcelable.Creator
            public IfaceInfo createFromParcel(Parcel parcel) {
                IfaceInfo ifaceInfo = new IfaceInfo();
                ifaceInfo.readFromParcel(parcel);
                return ifaceInfo;
            }

            @Override // android.os.Parcelable.Creator
            public IfaceInfo[] newArray(int i) {
                return new IfaceInfo[i];
            }
        };
        public int channel = 0;
        public String name;

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
            parcel.writeString(this.name);
            parcel.writeInt(this.channel);
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
                    this.name = parcel.readString();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.channel = parcel.readInt();
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

    public class RadioModeInfo implements Parcelable {
        public static final Parcelable.Creator CREATOR = new Parcelable.Creator() { // from class: com.android.wifi.x.android.hardware.wifi.IWifiChipEventCallback.RadioModeInfo.1
            @Override // android.os.Parcelable.Creator
            public RadioModeInfo createFromParcel(Parcel parcel) {
                RadioModeInfo radioModeInfo = new RadioModeInfo();
                radioModeInfo.readFromParcel(parcel);
                return radioModeInfo;
            }

            @Override // android.os.Parcelable.Creator
            public RadioModeInfo[] newArray(int i) {
                return new RadioModeInfo[i];
            }
        };
        public int bandInfo;
        public IfaceInfo[] ifaceInfos;
        public int radioId = 0;

        public final int getStability() {
            return 1;
        }

        @Override // android.os.Parcelable
        public final void writeToParcel(Parcel parcel, int i) {
            int dataPosition = parcel.dataPosition();
            parcel.writeInt(0);
            parcel.writeInt(this.radioId);
            parcel.writeInt(this.bandInfo);
            parcel.writeTypedArray(this.ifaceInfos, i);
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
                    this.radioId = parcel.readInt();
                    if (parcel.dataPosition() - dataPosition < readInt) {
                        this.bandInfo = parcel.readInt();
                        if (parcel.dataPosition() - dataPosition < readInt) {
                            this.ifaceInfos = (IfaceInfo[]) parcel.createTypedArray(IfaceInfo.CREATOR);
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
            return describeContents(this.ifaceInfos);
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
}
