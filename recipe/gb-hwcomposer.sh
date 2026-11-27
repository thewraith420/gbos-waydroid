#!/system/bin/sh
if [ -f /vendor/lib64/libgb-hwc-waydroid.so ]; then
    LD_PRELOAD=/vendor/lib64/libgb-hwc-waydroid.so exec /vendor/bin/hw/android.hardware.graphics.composer@2.1-service "$@"
fi
exec /vendor/bin/hw/android.hardware.graphics.composer@2.1-service "$@"
