#!/system/bin/sh
mw="$(getprop persist.waydroid.multi_windows)"
ln="$(getprop persist.gbos.sf_layer_names)"
if [ -f /system/lib64/libgb-sf-waydroid.so ] && { [ "$mw" = "true" ] || [ "$mw" = "1" ] || [ "$ln" = "1" ]; }; then
    export LD_PRELOAD=/system/lib64/libgb-sf-waydroid.so
fi
exec /system/bin/surfaceflinger "$@"
