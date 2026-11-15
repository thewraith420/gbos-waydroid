#!/bin/bash
# Regenerate src/com/gbos/fakewifi/Defaults.java (needs the HAL AIDL classes compiled to /tmp/fwtest first, see build.sh)
set -e
cd "$(dirname "$0")/.."
AA=../storprobe/android-all-16-robolectric-13921718.jar
H=com.android.wifi.x.android.hardware.wifi
N=android.net.wifi.nl80211
rm -rf /tmp/fwtest && mkdir /tmp/fwtest
javac -nowarn -proc:none -cp $AA -d /tmp/fwtest $(find src/com/android -name "*.java")
python3 -I tools/gen_dispatch.py src/com/android/wifi/x/android/hardware/wifi src/com/gbos/fakewifi/Dispatch.java com.gbos.fakewifi IWifi IWifiChip IWifiStaIface
python3 -I tools/gen_defaults.py /tmp/fwtest:$AA src/com/gbos/fakewifi/Defaults.java com.gbos.fakewifi \
  $H.IWifi+ctor $H.IWifiChip+ctor $H.IWifiStaIface+ctor $H.IWifiEventCallback $H.IWifiChipEventCallback $H.IWifiStaIfaceEventCallback \
  $N.IWificond $N.IClientInterface $N.IWifiScannerImpl $N.IApInterface $N.IWificondEventCallback $N.IInterfaceEventCallback $N.IScanEvent $N.IPnoScanEvent
