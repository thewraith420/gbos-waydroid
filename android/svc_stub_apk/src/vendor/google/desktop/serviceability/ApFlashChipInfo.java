package vendor.google.desktop.serviceability;

import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;
import vendor.google.libraries.desktop.serviceability.DesktopBuild;

/* loaded from: classes.dex */
public class ApFlashChipInfo implements Parcelable {
    public static final Parcelable.Creator<ApFlashChipInfo> CREATOR = new Parcelable.Creator<ApFlashChipInfo>() { // from class: vendor.google.desktop.serviceability.ApFlashChipInfo.1
        @Override // android.os.Parcelable.Creator
        public ApFlashChipInfo createFromParcel(Parcel _aidl_source) {
            ApFlashChipInfo _aidl_out = new ApFlashChipInfo();
            _aidl_out.readFromParcel(_aidl_source);
            return _aidl_out;
        }

        @Override // android.os.Parcelable.Creator
        public ApFlashChipInfo[] newArray(int _aidl_size) {
            return new ApFlashChipInfo[_aidl_size];
        }
    };
    public String name;

    /* renamed from: vendor, reason: collision with root package name */
    public String f0vendor;

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel _aidl_parcel, int _aidl_flag) {
        int _aidl_start_pos = _aidl_parcel.dataPosition();
        _aidl_parcel.writeInt(0);
        _aidl_parcel.writeString(this.f0vendor);
        _aidl_parcel.writeString(this.name);
        int _aidl_end_pos = _aidl_parcel.dataPosition();
        _aidl_parcel.setDataPosition(_aidl_start_pos);
        _aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);
        _aidl_parcel.setDataPosition(_aidl_end_pos);
    }

    public final void readFromParcel(Parcel _aidl_parcel) {
        int _aidl_start_pos = _aidl_parcel.dataPosition();
        int _aidl_parcelable_size = _aidl_parcel.readInt();
        try {
            if (_aidl_parcelable_size < 4) {
                throw new BadParcelableException("Parcelable too small");
            }
            if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) {
                if (_aidl_start_pos > DesktopBuild.VERSION_MAIN - _aidl_parcelable_size) {
                    throw new BadParcelableException("Overflow in the size of parcelable");
                }
                _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
                return;
            }
            this.f0vendor = _aidl_parcel.readString();
            if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) {
                if (_aidl_start_pos > DesktopBuild.VERSION_MAIN - _aidl_parcelable_size) {
                    throw new BadParcelableException("Overflow in the size of parcelable");
                }
                _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
            } else {
                this.name = _aidl_parcel.readString();
                if (_aidl_start_pos > DesktopBuild.VERSION_MAIN - _aidl_parcelable_size) {
                    throw new BadParcelableException("Overflow in the size of parcelable");
                }
                _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
            }
        } catch (Throwable th) {
            if (_aidl_start_pos > DesktopBuild.VERSION_MAIN - _aidl_parcelable_size) {
                throw new BadParcelableException("Overflow in the size of parcelable");
            }
            _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
            throw th;
        }
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }
}
