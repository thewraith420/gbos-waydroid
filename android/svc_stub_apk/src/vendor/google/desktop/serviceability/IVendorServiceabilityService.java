package vendor.google.desktop.serviceability;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.List;

/* loaded from: classes.dex */
public interface IVendorServiceabilityService extends IInterface {
    public static final String DESCRIPTOR = "vendor.google.desktop.serviceability.IVendorServiceabilityService";

    boolean checkAudioDevice(String str) throws RemoteException;

    String generateUfsc() throws RemoteException;

    ApFlashChipInfo getApFlashChipInfo() throws RemoteException;

    BatteryInfo getBatteryInfo() throws RemoteException;

    List<BlockDeviceInfo> getBlockDevicesInfo() throws RemoteException;

    List<CardReaderInfo> getCardReaderInfo() throws RemoteException;

    String getCpuName() throws RemoteException;

    CpuUsageStats getCpuUsageInfo() throws RemoteException;

    CpuUsageStats getCpuUsageStats() throws RemoteException;

    DiskIo getDiskIo() throws RemoteException;

    MemoryPerformanceStats getMemoryPerformanceStats() throws RemoteException;

    List<PhysicalCpuInfo> getPhysicalCpusInfo() throws RemoteException;

    String getSoundCardName() throws RemoteException;

    List<StorageInfo> getStorageInfo() throws RemoteException;

    List<ThermalInfo> getThermalInfo() throws RemoteException;

    WirelessInfo getWirelessInfo() throws RemoteException;

    boolean hasUfscCapability() throws RemoteException;

    void validateHardwareDescriptor(String str) throws RemoteException;

    public static class Default implements IVendorServiceabilityService {
        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public BatteryInfo getBatteryInfo() throws RemoteException {
            return null;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public CpuUsageStats getCpuUsageInfo() throws RemoteException {
            return null;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public CpuUsageStats getCpuUsageStats() throws RemoteException {
            return null;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public List<ThermalInfo> getThermalInfo() throws RemoteException {
            return null;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public List<StorageInfo> getStorageInfo() throws RemoteException {
            return null;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public String getCpuName() throws RemoteException {
            return null;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public ApFlashChipInfo getApFlashChipInfo() throws RemoteException {
            return null;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public WirelessInfo getWirelessInfo() throws RemoteException {
            return null;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public List<PhysicalCpuInfo> getPhysicalCpusInfo() throws RemoteException {
            return null;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public String getSoundCardName() throws RemoteException {
            return null;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public boolean checkAudioDevice(String soundCard) throws RemoteException {
            return false;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public List<CardReaderInfo> getCardReaderInfo() throws RemoteException {
            return null;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public List<BlockDeviceInfo> getBlockDevicesInfo() throws RemoteException {
            return null;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public String generateUfsc() throws RemoteException {
            return null;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public boolean hasUfscCapability() throws RemoteException {
            return false;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public void validateHardwareDescriptor(String hardwareDescriptor) throws RemoteException {
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public DiskIo getDiskIo() throws RemoteException {
            return null;
        }

        @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
        public MemoryPerformanceStats getMemoryPerformanceStats() throws RemoteException {
            return null;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }
    }

    public static abstract class Stub extends Binder implements IVendorServiceabilityService {
        static final int TRANSACTION_checkAudioDevice = 11;
        static final int TRANSACTION_generateUfsc = 14;
        static final int TRANSACTION_getApFlashChipInfo = 7;
        static final int TRANSACTION_getBatteryInfo = 1;
        static final int TRANSACTION_getBlockDevicesInfo = 13;
        static final int TRANSACTION_getCardReaderInfo = 12;
        static final int TRANSACTION_getCpuName = 6;
        static final int TRANSACTION_getCpuUsageInfo = 2;
        static final int TRANSACTION_getCpuUsageStats = 3;
        static final int TRANSACTION_getDiskIo = 17;
        static final int TRANSACTION_getMemoryPerformanceStats = 18;
        static final int TRANSACTION_getPhysicalCpusInfo = 9;
        static final int TRANSACTION_getSoundCardName = 10;
        static final int TRANSACTION_getStorageInfo = 5;
        static final int TRANSACTION_getThermalInfo = 4;
        static final int TRANSACTION_getWirelessInfo = 8;
        static final int TRANSACTION_hasUfscCapability = 15;
        static final int TRANSACTION_validateHardwareDescriptor = 16;

        public Stub() {
            attachInterface(this, IVendorServiceabilityService.DESCRIPTOR);
        }

        public static IVendorServiceabilityService asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(IVendorServiceabilityService.DESCRIPTOR);
            if (iin != null && (iin instanceof IVendorServiceabilityService)) {
                return (IVendorServiceabilityService) iin;
            }
            return new Proxy(obj);
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code >= 1 && code <= 16777215) {
                data.enforceInterface(IVendorServiceabilityService.DESCRIPTOR);
            }
            switch (code) {
                case 1:
                    BatteryInfo _result = getBatteryInfo();
                    reply.writeNoException();
                    reply.writeTypedObject(_result, 1);
                    return true;
                case 2:
                    CpuUsageStats _result2 = getCpuUsageInfo();
                    reply.writeNoException();
                    reply.writeTypedObject(_result2, 1);
                    return true;
                case 3:
                    CpuUsageStats _result3 = getCpuUsageStats();
                    reply.writeNoException();
                    reply.writeTypedObject(_result3, 1);
                    return true;
                case 4:
                    List<ThermalInfo> _result4 = getThermalInfo();
                    reply.writeNoException();
                    reply.writeTypedList(_result4, 1);
                    return true;
                case TRANSACTION_getStorageInfo /* 5 */:
                    List<StorageInfo> _result5 = getStorageInfo();
                    reply.writeNoException();
                    reply.writeTypedList(_result5, 1);
                    return true;
                case TRANSACTION_getCpuName /* 6 */:
                    String _result6 = getCpuName();
                    reply.writeNoException();
                    reply.writeString(_result6);
                    return true;
                case TRANSACTION_getApFlashChipInfo /* 7 */:
                    ApFlashChipInfo _result7 = getApFlashChipInfo();
                    reply.writeNoException();
                    reply.writeTypedObject(_result7, 1);
                    return true;
                case 8:
                    WirelessInfo _result8 = getWirelessInfo();
                    reply.writeNoException();
                    reply.writeTypedObject(_result8, 1);
                    return true;
                case TRANSACTION_getPhysicalCpusInfo /* 9 */:
                    List<PhysicalCpuInfo> _result9 = getPhysicalCpusInfo();
                    reply.writeNoException();
                    reply.writeTypedList(_result9, 1);
                    return true;
                case TRANSACTION_getSoundCardName /* 10 */:
                    String _result10 = getSoundCardName();
                    reply.writeNoException();
                    reply.writeString(_result10);
                    return true;
                case TRANSACTION_checkAudioDevice /* 11 */:
                    String _arg0 = data.readString();
                    data.enforceNoDataAvail();
                    boolean _result11 = checkAudioDevice(_arg0);
                    reply.writeNoException();
                    reply.writeBoolean(_result11);
                    return true;
                case TRANSACTION_getCardReaderInfo /* 12 */:
                    List<CardReaderInfo> _result12 = getCardReaderInfo();
                    reply.writeNoException();
                    reply.writeTypedList(_result12, 1);
                    return true;
                case TRANSACTION_getBlockDevicesInfo /* 13 */:
                    List<BlockDeviceInfo> _result13 = getBlockDevicesInfo();
                    reply.writeNoException();
                    reply.writeTypedList(_result13, 1);
                    return true;
                case TRANSACTION_generateUfsc /* 14 */:
                    String _result14 = generateUfsc();
                    reply.writeNoException();
                    reply.writeString(_result14);
                    return true;
                case TRANSACTION_hasUfscCapability /* 15 */:
                    boolean _result15 = hasUfscCapability();
                    reply.writeNoException();
                    reply.writeBoolean(_result15);
                    return true;
                case 16:
                    String _arg02 = data.readString();
                    data.enforceNoDataAvail();
                    validateHardwareDescriptor(_arg02);
                    reply.writeNoException();
                    return true;
                case TRANSACTION_getDiskIo /* 17 */:
                    DiskIo _result16 = getDiskIo();
                    reply.writeNoException();
                    reply.writeTypedObject(_result16, 1);
                    return true;
                case TRANSACTION_getMemoryPerformanceStats /* 18 */:
                    MemoryPerformanceStats _result17 = getMemoryPerformanceStats();
                    reply.writeNoException();
                    reply.writeTypedObject(_result17, 1);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static final class Proxy implements IVendorServiceabilityService {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            public final String getInterfaceDescriptor() {
                return IVendorServiceabilityService.DESCRIPTOR;
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public BatteryInfo getBatteryInfo() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                    BatteryInfo _result = (BatteryInfo) _reply.readTypedObject(BatteryInfo.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public CpuUsageStats getCpuUsageInfo() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                    CpuUsageStats _result = (CpuUsageStats) _reply.readTypedObject(CpuUsageStats.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public CpuUsageStats getCpuUsageStats() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                    CpuUsageStats _result = (CpuUsageStats) _reply.readTypedObject(CpuUsageStats.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public List<ThermalInfo> getThermalInfo() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(4, _data, _reply, 0);
                    _reply.readException();
                    List<ThermalInfo> _result = _reply.createTypedArrayList(ThermalInfo.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public List<StorageInfo> getStorageInfo() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getStorageInfo, _data, _reply, 0);
                    _reply.readException();
                    List<StorageInfo> _result = _reply.createTypedArrayList(StorageInfo.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public String getCpuName() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getCpuName, _data, _reply, 0);
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public ApFlashChipInfo getApFlashChipInfo() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getApFlashChipInfo, _data, _reply, 0);
                    _reply.readException();
                    ApFlashChipInfo _result = (ApFlashChipInfo) _reply.readTypedObject(ApFlashChipInfo.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public WirelessInfo getWirelessInfo() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(8, _data, _reply, 0);
                    _reply.readException();
                    WirelessInfo _result = (WirelessInfo) _reply.readTypedObject(WirelessInfo.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public List<PhysicalCpuInfo> getPhysicalCpusInfo() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getPhysicalCpusInfo, _data, _reply, 0);
                    _reply.readException();
                    List<PhysicalCpuInfo> _result = _reply.createTypedArrayList(PhysicalCpuInfo.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public String getSoundCardName() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getSoundCardName, _data, _reply, 0);
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public boolean checkAudioDevice(String soundCard) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    _data.writeString(soundCard);
                    this.mRemote.transact(Stub.TRANSACTION_checkAudioDevice, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readBoolean();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public List<CardReaderInfo> getCardReaderInfo() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getCardReaderInfo, _data, _reply, 0);
                    _reply.readException();
                    List<CardReaderInfo> _result = _reply.createTypedArrayList(CardReaderInfo.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public List<BlockDeviceInfo> getBlockDevicesInfo() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getBlockDevicesInfo, _data, _reply, 0);
                    _reply.readException();
                    List<BlockDeviceInfo> _result = _reply.createTypedArrayList(BlockDeviceInfo.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public String generateUfsc() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_generateUfsc, _data, _reply, 0);
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public boolean hasUfscCapability() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_hasUfscCapability, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readBoolean();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public void validateHardwareDescriptor(String hardwareDescriptor) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    _data.writeString(hardwareDescriptor);
                    this.mRemote.transact(16, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public DiskIo getDiskIo() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getDiskIo, _data, _reply, 0);
                    _reply.readException();
                    DiskIo _result = (DiskIo) _reply.readTypedObject(DiskIo.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // vendor.google.desktop.serviceability.IVendorServiceabilityService
            public MemoryPerformanceStats getMemoryPerformanceStats() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(IVendorServiceabilityService.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getMemoryPerformanceStats, _data, _reply, 0);
                    _reply.readException();
                    MemoryPerformanceStats _result = (MemoryPerformanceStats) _reply.readTypedObject(MemoryPerformanceStats.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}
