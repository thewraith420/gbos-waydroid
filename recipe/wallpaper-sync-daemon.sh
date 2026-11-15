#!/system/bin/sh
# wallpaper-sync-daemon.sh: Sync wallpaper, theme, Monet colors, and clock from User 10 to User 0 (Keyguard)

mirror_now() {
    # Check if User 10 has a separate lock wallpaper or shared wallpaper
    SRC_IMG=""
    SRC_ORIG=""
    if [ -f /data/system/users/10/wallpaper_lock ] && [ -s /data/system/users/10/wallpaper_lock ]; then
        SRC_IMG="/data/system/users/10/wallpaper_lock"
        SRC_ORIG="/data/system/users/10/wallpaper_lock_orig"
    elif [ -f /data/system/users/10/wallpaper ] && [ -s /data/system/users/10/wallpaper ]; then
        SRC_IMG="/data/system/users/10/wallpaper"
        SRC_ORIG="/data/system/users/10/wallpaper_orig"
    fi

    if [ -n "$SRC_IMG" ]; then
        cp "$SRC_IMG" /data/system/users/0/wallpaper_lock
        cp "${SRC_ORIG:-$SRC_IMG}" /data/system/users/0/wallpaper_lock_orig
        cp "$SRC_IMG" /data/system/users/0/wallpaper
        cp "${SRC_ORIG:-$SRC_IMG}" /data/system/users/0/wallpaper_orig

        chown system:system /data/system/users/0/wallpaper*
        chmod 600 /data/system/users/0/wallpaper*
        restorecon /data/system/users/0/wallpaper* 2>/dev/null || true
    fi

    # Mirror wallpaper_info.xml if present
    if [ -f /data/system/users/10/wallpaper_info.xml ]; then
        cp /data/system/users/10/wallpaper_info.xml /data/system/users/0/wallpaper_info.xml
        chown system:system /data/system/users/0/wallpaper_info.xml
        chmod 600 /data/system/users/0/wallpaper_info.xml
        restorecon /data/system/users/0/wallpaper_info.xml 2>/dev/null || true
    fi

    # Copy dynamic Monet palette, clock style, and dark mode from User 10 to User 0
    for key in theme_customization_overlay_packages ui_night_mode lockscreen_use_double_line_clock lock_screen_custom_clock_face; do
        VAL=$(settings --user 10 get secure "$key" 2>/dev/null)
        if [ -n "$VAL" ] && [ "$VAL" != "null" ]; then
            settings --user 0 put secure "$key" "$VAL"
        fi
    done

    # Trigger WallpaperObserver file watch
    touch /data/system/users/0/wallpaper_lock_orig 2>/dev/null
    # If user 0 live wallpaper is lingering, stop it cleanly without killing active SystemUI
    am force-stop --user 0 com.google.android.desktop.adaptivewallpaper 2>/dev/null || true
}

TARGET_DIR="/data/system/users/10"
LAST_MOD=0

while true; do
    CURRENT_MOD=$(stat -c %Y "$TARGET_DIR/wallpaper" "$TARGET_DIR/wallpaper_lock" "$TARGET_DIR/wallpaper_info.xml" 2>/dev/null | sort -n | tail -1)
    if [ -n "$CURRENT_MOD" ] && [ "$CURRENT_MOD" -ne "$LAST_MOD" ]; then
        if [ "$LAST_MOD" -ne 0 ]; then
            sleep 2
            mirror_now
        fi
        LAST_MOD=$CURRENT_MOD
    fi
    sleep 3
done

