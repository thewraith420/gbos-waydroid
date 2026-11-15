// OS bringup: keyboard/mouse/touch for Googlebook-on-Waydroid.
//
// Waydroid's hwcomposer feeds host Wayland input into Android by writing struct input_event streams to
// /dev/input/wl_{keyboard,pointer,touch,tablet}_events. Googlebook's stock EventHub can't read those
// (evdev ioctl -> ENOTTY -> skipped). Swapping in ATV's libinputflinger is ruled out (A16/A17 ABI).
// This daemon copies those streams into real kernel uinput devices (which stock EventHub reads) so
// keyboard/mouse/touch work, without any framework patch. Logs to logcat (tag "input-bridge") AND
// stderr so a normal boot's logcat shows exactly what it forwards.
#include <linux/uinput.h>
#include <linux/input.h>
#include <fcntl.h>
#include <poll.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <dirent.h>
#include <android/log.h>
#include <sys/system_properties.h>

#define LOGI(...) do { __android_log_print(ANDROID_LOG_INFO, "input-bridge", __VA_ARGS__); \
                       fprintf(stderr, "input-bridge: " __VA_ARGS__); fprintf(stderr, "\n"); } while (0)

#define ABSMAX 32767
// Absolute-pointer coordinate space = the Android display's logical size. hwcomposer writes the host
// cursor position in these pixels, so the uinput device advertises the same range and the cursor tracks
// 1:1. 1920x1080 covers the fullscreen case (the windowed logicalFrame is 1920x1045, and the pointer is
// clamped to this range). TODO: follow the live display size.
// Default 1920x1080; a different screen sets the system property persist.gbos.display to "WxH" (the output of
// `wm size`, e.g. 1280x800) and restarts this service: setprop ctl.restart gb-inputbridge. Out-of-range or
// malformed values are ignored. gbos-doctor.py warns when the property and the real display disagree.
static int g_ptr_w = 1920, g_ptr_h = 1080;
#define PTR_W g_ptr_w
#define PTR_H g_ptr_h

// BEGIN parse_display (unit-tested on the host: see tools/test-input-bridge-parse.sh)
static int parse_display(const char *s, int *w, int *h) {
    int a = 0, b = 0, n = 0;
    if (!s || sscanf(s, "%dx%d%n", &a, &b, &n) != 2 || s[n] != '\0') return 0;
    if (a < 320 || a > 8192 || b < 320 || b > 8192) return 0;
    *w = a; *h = b;
    return 1;
}
// END parse_display

static void load_display_size(void) {
    char v[PROP_VALUE_MAX] = {0};
    if (__system_property_get("persist.gbos.display", v) > 0) {
        int w, h;
        if (parse_display(v, &w, &h)) { g_ptr_w = w; g_ptr_h = h; }
        else LOGI("ignoring persist.gbos.display=%s (want WxH, 320..8192)", v);
    }
}

static void ev(int fd, int req, int val) { ioctl(fd, req, val); }

static int read_majmin(const char *sysdev, unsigned *maj, unsigned *min) {
    FILE *f = fopen(sysdev, "r"); if (!f) return -1;
    int ok = (fscanf(f, "%u:%u", maj, min) == 2); fclose(f); return ok ? 0 : -1;
}
static void ensure_uinput(void) {
    if (access("/dev/uinput", F_OK) == 0) return;
    unsigned maj = 10, min = 223;
    read_majmin("/sys/class/misc/uinput/dev", &maj, &min);
    mknod("/dev/uinput", S_IFCHR | 0666, makedev(maj, min));
    chmod("/dev/uinput", 0666);
    LOGI("created /dev/uinput %u:%u", maj, min);
}
static void mknod_evnode(int ufd) {
    char sysname[64] = {0};
    if (ioctl(ufd, UI_GET_SYSNAME(sizeof sysname), sysname) < 0) return;
    char dir[128]; snprintf(dir, sizeof dir, "/sys/devices/virtual/input/%s", sysname);
    DIR *d = opendir(dir); if (!d) return;
    struct dirent *e;
    while ((e = readdir(d))) {
        if (strncmp(e->d_name, "event", 5) != 0) continue;
        char dp[256]; snprintf(dp, sizeof dp, "%s/%s/dev", dir, e->d_name);
        unsigned maj, min;
        if (read_majmin(dp, &maj, &min) == 0) {
            char node[80]; snprintf(node, sizeof node, "/dev/input/%s", e->d_name);
            // Create the node world-rw (0666) ATOMICALLY. system_server's EventHub watches /dev/input
            // with inotify and opens each new node O_RDWR the instant IN_CREATE fires (at mknod time).
            // If the node is created root:root 0660 (system_server is uid system, not root), that open
            // gets EACCES and EventHub gives up with no retry -> the device is never registered and
            // InputReader shows only the Virtual device (no cursor, no keys). It must already be
            // world-rw at creation; a chmod/chown after mknod loses the race. (main() sets umask(0) so
            // the 0666 isn't masked.) We also chown root:input as belt-and-suspenders.
            // A previous instance of this daemon (service restart) leaves its event nodes behind in /dev, and the new
            // uinput devices usually get the same numbers. mknod() then fails with EEXIST, no IN_CREATE reaches
            // EventHub, and the devices never register (InputReader shows only "Virtual"). Remove the stale node first.
            unlink(node);
            mknod(node, S_IFCHR | 0666, makedev(maj, min));
            chmod(node, 0666);
            chown(node, 0, 1004 /* AID_INPUT */);
            LOGI("mknod %s %u:%u (0666)", node, maj, min);
        }
        break;
    }
    closedir(d);
}

// type: 0=keyboard, 1=mouse (relative), 2=touchscreen (abs MT)
static int make_dev(const char *name, int type) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { LOGI("open /dev/uinput: %s", strerror(errno)); return -1; }
    ev(fd, UI_SET_EVBIT, EV_SYN);
    ev(fd, UI_SET_EVBIT, EV_KEY);
    if (type == 0) {
        for (int k = 1; k < 256; k++) ev(fd, UI_SET_KEYBIT, k);
    } else if (type == 1) {                // absolute pointer, modelled as an indirect stylus/tablet so
        // Android draws a cursor at the EXACT absolute position (a relative mouse drifts out of
        // calibration under pointer acceleration; a plain ABS+POINTER device is treated as a relative
        // touchpad). ABS_X/ABS_Y place the cursor; BTN_TOOL_PEN keeps it "in range" (visible); the mouse
        // buttons are mapped to pen contact / stylus buttons in the forward loop.
        ev(fd, UI_SET_EVBIT, EV_ABS);
        ev(fd, UI_SET_ABSBIT, ABS_X); ev(fd, UI_SET_ABSBIT, ABS_Y);
        ev(fd, UI_SET_KEYBIT, BTN_TOOL_PEN);   // tool present -> cursor shown
        ev(fd, UI_SET_KEYBIT, BTN_TOUCH);      // pen contact  -> left click/tap
        ev(fd, UI_SET_KEYBIT, BTN_STYLUS);     // stylus button -> right click (stylus-aware apps)
        ev(fd, UI_SET_KEYBIT, BTN_STYLUS2);    // stylus button2 -> middle click
        ev(fd, UI_SET_KEYBIT, BTN_RIGHT);      // mouse secondary -> right click (desktop launcher)
        ev(fd, UI_SET_KEYBIT, BTN_MIDDLE);     // mouse tertiary -> middle click
        ev(fd, UI_SET_EVBIT, EV_REL);
        ev(fd, UI_SET_RELBIT, REL_WHEEL); ev(fd, UI_SET_RELBIT, REL_HWHEEL);  // scroll wheel
        ev(fd, UI_SET_PROPBIT, INPUT_PROP_POINTER);  // indirect -> cursor (not direct touchscreen)
    } else {                               // multitouch touchscreen (also carries hwcomposer two-finger scroll)
        ev(fd, UI_SET_EVBIT, EV_ABS);
        ev(fd, UI_SET_ABSBIT, ABS_MT_SLOT); ev(fd, UI_SET_ABSBIT, ABS_MT_TRACKING_ID);
        ev(fd, UI_SET_ABSBIT, ABS_MT_POSITION_X); ev(fd, UI_SET_ABSBIT, ABS_MT_POSITION_Y);
        ev(fd, UI_SET_ABSBIT, ABS_MT_PRESSURE);
        ev(fd, UI_SET_ABSBIT, ABS_X); ev(fd, UI_SET_ABSBIT, ABS_Y);
        ev(fd, UI_SET_KEYBIT, BTN_TOUCH);
        ev(fd, UI_SET_PROPBIT, INPUT_PROP_DIRECT);
    }
    struct uinput_user_dev u;
    memset(&u, 0, sizeof u);
    snprintf(u.name, sizeof u.name, "%s", name);
    // UNIQUE product id per device. Android InputReader groups EventHub devices into InputDevices by a
    // descriptor that, when vendor+product are set, is hash(vendor,product,version) and IGNORES the name.
    // With an identical product all three devices collide into one merged InputDevice (keyboard absorbs
    // the mouse -> no cursor). A distinct product per device keeps them separate.
    u.id.bustype = BUS_VIRTUAL; u.id.vendor = 0x1209; u.id.product = 0xab00 + type; u.id.version = 1;
    if (type == 1) {                       // absolute pointer range = Android display size
        u.absmin[ABS_X] = 0; u.absmax[ABS_X] = PTR_W - 1;
        u.absmin[ABS_Y] = 0; u.absmax[ABS_Y] = PTR_H - 1;
    }
    if (type == 2) {
        // hwcomposer writes display pixel coordinates (0..PTR_W-1, 0..PTR_H-1) and ABS_MT_PRESSURE=50
        // to /dev/input/wl_touch_events (including for two-finger touchpad scroll). Using 32767 scaled
        // all touch/scroll coordinates into a tiny 112x61 box in the top-left corner over the clock.
        u.absmin[ABS_MT_SLOT] = 0;         u.absmax[ABS_MT_SLOT] = 9;
        u.absmin[ABS_MT_TRACKING_ID] = 0;  u.absmax[ABS_MT_TRACKING_ID] = 65535;
        u.absmin[ABS_MT_POSITION_X] = 0;   u.absmax[ABS_MT_POSITION_X] = PTR_W - 1;
        u.absmin[ABS_MT_POSITION_Y] = 0;   u.absmax[ABS_MT_POSITION_Y] = PTR_H - 1;
        u.absmin[ABS_MT_PRESSURE] = 0;     u.absmax[ABS_MT_PRESSURE] = 255;
        u.absmin[ABS_X] = 0;               u.absmax[ABS_X] = PTR_W - 1;
        u.absmin[ABS_Y] = 0;               u.absmax[ABS_Y] = PTR_H - 1;
    }
    if (write(fd, &u, sizeof u) != (ssize_t)sizeof u) { LOGI("write dev '%s': %s", name, strerror(errno)); close(fd); return -1; }
    if (ioctl(fd, UI_DEV_CREATE) < 0)              { LOGI("UI_DEV_CREATE '%s': %s", name, strerror(errno)); close(fd); return -1; }
    mknod_evnode(fd);
    LOGI("created uinput device '%s'", name);
    return fd;
}

// O_RDWR, not O_RDONLY: these are FIFOs and Waydroid's hwcomposer is only a writer while the host is
// actually producing input. A FIFO read end with no connected writer makes poll() return POLLHUP forever;
// since we only act on POLLIN, that spun a whole CPU core and read nothing. Opening O_RDWR makes us a
// writer too (we never write to the source), so the pipe always has >=1 writer and poll() blocks on
// POLLIN as intended. (Recreation of the node is handled separately by the inode recheck in the loop,
// since O_RDWR means we never see the POLLHUP that would otherwise hint the node was replaced.)
static int open_once(const char *path) { return open(path, O_RDWR | O_NONBLOCK); }

static long cur_ino(const char *path) {
    struct stat st;
    return stat(path, &st) == 0 ? (long)st.st_ino : -1;
}

static int open_src(const char *path) {               // initial open: wait up to 600s for the node
    for (int i = 0; i < 600; i++) {
        int fd = open_once(path);
        if (fd >= 0) { LOGI("opened %s", path); return fd; }
        sleep(1);
    }
    LOGI("giving up on %s: %s", path, strerror(errno));
    return -1;
}

#define SCROLL_MULT 10

int main(void) {
    load_display_size();
    LOGI("pointer/touch range %dx%d", PTR_W, PTR_H);
    // cx/cy = latest ABS position this frame; px/py = previous frame's; haveabs bitmask (1=X,2=Y);
    // haveprev set once we have a baseline to diff against (pointer derived-delta calibration).
    struct dev { const char *src, *name; int type, sfd, ufd, count; long ino;
                 int cx, cy, px, py, haveabs, haveprev; } m[] = {
        { "/dev/input/wl_keyboard_events", "waydroid_keyboard",    0, -1, -1, 0, -1, 0,0,0,0,0,0 },
        { "/dev/input/wl_pointer_events",  "waydroid_pointer",     1, -1, -1, 0, -1, 0,0,0,0,0,0 },
        { "/dev/input/wl_touch_events",    "waydroid_touchscreen", 2, -1, -1, 0, -1, 0,0,0,0,0,0 },
    };
    int n = sizeof(m) / sizeof(m[0]);
    int touch_active = 0, touch_has_ox = 0, touch_has_oy = 0, touch_ox = 0, touch_oy = 0;
    umask(0);               // so mknod() below creates nodes 0666, not 0666 & ~umask (EventHub needs rw)
    ensure_uinput();
    for (int i = 0; i < n; i++) {
        m[i].ufd = make_dev(m[i].name, m[i].type);
        m[i].sfd = open_src(m[i].src);
        if (m[i].sfd >= 0) m[i].ino = cur_ino(m[i].src);
    }

    struct pollfd pfd[8];
    for (;;) {
        // Waydroid's hwcomposer deletes and recreates /dev/input/wl_*_events (e.g. across a session
        // restart). Whoever opened the old node keeps writing to / reading from a now-orphaned pipe:
        // seen live as hwcomposer's fd pointing at "wl_pointer_events (deleted)" while real input went
        // nowhere. Detect it by comparing the path's current inode to the one we opened; if it changed,
        // the node was replaced, so close the stale fd and reopen the live node. Also (re)open any
        // source that isn't open yet.
        for (int i = 0; i < n; i++) {
            if (m[i].ufd < 0) continue;
            long now = cur_ino(m[i].src);
            if (m[i].sfd >= 0 && now >= 0 && now != m[i].ino) {
                LOGI("%s recreated (inode %ld -> %ld), reopening", m[i].src, m[i].ino, now);
                close(m[i].sfd); m[i].sfd = -1;
            }
            if (m[i].sfd < 0) {
                int fd = open_once(m[i].src);
                if (fd >= 0) { m[i].sfd = fd; m[i].ino = cur_ino(m[i].src); LOGI("opened %s (inode %ld)", m[i].src, m[i].ino); }
            }
        }
        int np = 0, idx[8];
        for (int i = 0; i < n; i++) {
            if (m[i].sfd < 0 || m[i].ufd < 0) continue;
            pfd[np].fd = m[i].sfd; pfd[np].events = POLLIN; pfd[np].revents = 0; idx[np] = i; np++;
        }
        if (np == 0) { sleep(1); continue; }
        // 1s timeout (not -1) so the inode recheck above keeps running even when no input arrives.
        if (poll(pfd, np, 1000) < 0) { if (errno == EINTR) continue; break; }
        // Belt and suspenders against a POLLHUP/POLLERR busy-spin: if poll woke us but nothing is
        // actually readable (e.g. a writer-less FIFO signalling POLLHUP), don't loop straight back into
        // poll() and peg a core. O_RDWR should keep this from happening; a timeout wake is also normal.
        int readable = 0;
        for (int p = 0; p < np; p++) if (pfd[p].revents & POLLIN) readable = 1;
        if (!readable) { continue; }
        for (int p = 0; p < np; p++) {
            if (!(pfd[p].revents & POLLIN)) continue;
            int i = idx[p];
            struct input_event e; ssize_t r;
            while ((r = read(m[i].sfd, &e, sizeof e)) == (ssize_t)sizeof e) {
                if (m[i].type == 1) {
                    // Absolute stylus pointer: forward hwcomposer's accurate ABS position straight through
                    // (cursor sits exactly there); drop its down-scaled REL deltas; translate the mouse
                    // buttons to pen contact / stylus + mouse buttons.
                    // Do not assert BTN_TOOL_PEN while a touchscreen scroll/touch gesture is active, or
                    // InputState::trackMotion's stylus palm rejection will drop the touchscreen events.
                    if (!m[i].haveprev && !touch_active) {
                        struct input_event te; memset(&te, 0, sizeof te);
                        te.type = EV_KEY; te.code = BTN_TOOL_PEN; te.value = 1;
                        write(m[i].ufd, &te, sizeof te);
                        m[i].haveprev = 1;
                    }
                    if (e.type == EV_REL && (e.code == REL_X || e.code == REL_Y)) continue;  // drop rel
                    if (e.type == EV_KEY) {
                        if (e.code == BTN_LEFT) {
                            e.code = BTN_TOUCH;                 // left click = pen contact; fall through
                        } else if (e.code == BTN_RIGHT || e.code == BTN_MIDDLE) {
                            // Emit both BTN_RIGHT/BTN_MIDDLE (for TouchInputMapper's CursorButtonAccumulator ->
                            // BUTTON_SECONDARY/BUTTON_TERTIARY, which the desktop launcher checks) and
                            // BTN_STYLUS/BTN_STYLUS2 + BTN_TOUCH (for stylus-aware apps like Chrome).
                            int sbtn = (e.code == BTN_RIGHT) ? BTN_STYLUS : BTN_STYLUS2;
                            int mbtn = e.code;
                            struct input_event me, se, te;
                            memset(&me, 0, sizeof me); me.type = EV_KEY; me.code = mbtn;
                            memset(&se, 0, sizeof se); se.type = EV_KEY; se.code = sbtn;
                            memset(&te, 0, sizeof te); te.type = EV_KEY; te.code = BTN_TOUCH;
                            if (e.value) { me.value = 1; write(m[i].ufd, &me, sizeof me);
                                           se.value = 1; write(m[i].ufd, &se, sizeof se);
                                           te.value = 1; write(m[i].ufd, &te, sizeof te); }
                            else         { te.value = 0; write(m[i].ufd, &te, sizeof te);
                                           se.value = 0; write(m[i].ufd, &se, sizeof se);
                                           me.value = 0; write(m[i].ufd, &me, sizeof me); }
                            continue;                           // full sequence emitted; don't write orig
                        } else {
                            continue;                           // drop side/extra buttons
                        }
                    }
                    // ABS_X/ABS_Y, REL_WHEEL/HWHEEL, SYN fall through to the write below.
                } else if (m[i].type == 2) {
                    // hwcomposer writes multitouch events (and two-finger touchpad scroll) to
                    // /dev/input/wl_touch_events without BTN_TOUCH.
                    // 1) Drop BTN_TOOL_PEN on waydroid_pointer before touch-down so Android's
                    //    InputState::trackMotion clears the hovering stylus MotionMemento (otherwise
                    //    stylus palm rejection drops all TOUCHSCREEN events as "inconsistent event").
                    // 2) Assert/release BTN_TOUCH alongside ABS_MT_TRACKING_ID.
                    // 3) Scale deltas from the gesture origin by SCROLL_MULT because hwcomposer's
                    //    axis_value120 scaling shrinks each scroll step to ~1px (below touchSlop).
                    if (e.type == EV_ABS && e.code == ABS_MT_TRACKING_ID) {
                        if (e.value >= 0) {
                            if (!touch_active) {
                                if (m[1].ufd >= 0 && m[1].haveprev) {
                                    struct input_event pe, ps;
                                    memset(&pe, 0, sizeof pe); pe.type = EV_KEY; pe.code = BTN_TOOL_PEN; pe.value = 0;
                                    memset(&ps, 0, sizeof ps); ps.type = EV_SYN; ps.code = SYN_REPORT;   ps.value = 0;
                                    write(m[1].ufd, &pe, sizeof pe);
                                    write(m[1].ufd, &ps, sizeof ps);
                                    m[1].haveprev = 0;
                                    usleep(5000); // let InputReader dispatch ACTION_HOVER_EXIT first
                                }
                                struct input_event te; memset(&te, 0, sizeof te);
                                te.type = EV_KEY; te.code = BTN_TOUCH; te.value = 1;
                                write(m[i].ufd, &te, sizeof te);
                                touch_active = 1;
                                touch_has_ox = 0;
                                touch_has_oy = 0;
                            }
                        } else {
                            struct input_event te; memset(&te, 0, sizeof te);
                            te.type = EV_KEY; te.code = BTN_TOUCH; te.value = 0;
                            write(m[i].ufd, &te, sizeof te);
                            touch_active = 0;
                            touch_has_ox = 0;
                            touch_has_oy = 0;
                        }
                    } else if (e.type == EV_ABS && e.code == ABS_MT_POSITION_X && touch_active) {
                        if (!touch_has_ox) {
                            touch_ox = e.value;
                            touch_has_ox = 1;
                        } else {
                            int v = touch_ox + (e.value - touch_ox) * SCROLL_MULT;
                            if (v < 0) v = 0;
                            if (v >= PTR_W) v = PTR_W - 1;
                            e.value = v;
                        }
                    } else if (e.type == EV_ABS && e.code == ABS_MT_POSITION_Y && touch_active) {
                        if (!touch_has_oy) {
                            touch_oy = e.value;
                            touch_has_oy = 1;
                        } else {
                            int v = touch_oy + (e.value - touch_oy) * SCROLL_MULT;
                            if (v < 0) v = 0;
                            if (v >= PTR_H) v = PTR_H - 1;
                            e.value = v;
                        }
                    }
                }
                ssize_t w = write(m[i].ufd, &e, sizeof e);
                if (w != (ssize_t)sizeof e) LOGI("write %s failed: %s", m[i].name, strerror(errno));
                // hwcomposer writes keyboard key events with NO EV_SYN terminator (unlike pointer, which
                // it does sync). Android's EventHub only commits an input frame on SYN_REPORT, so bare key
                // events never get processed -> nothing types. Inject a SYN_REPORT after each key event so
                // the uinput keyboard frame is committed.
                if (m[i].type == 0 && e.type == EV_KEY) {
                    struct input_event syn; memset(&syn, 0, sizeof syn);
                    syn.type = EV_SYN; syn.code = SYN_REPORT; syn.value = 0;
                    write(m[i].ufd, &syn, sizeof syn);
                }
                if (m[i].count < 12) LOGI("%s fwd#%d type=%u code=%u val=%d", m[i].name, m[i].count, e.type, e.code, e.value);
                if (++m[i].count % 300 == 0) LOGI("%s forwarded %d events", m[i].name, m[i].count);
            }
        }
    }
    return 0;
}
