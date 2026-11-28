#!/usr/bin/env python3
"""appcast_session.py — own the AppCast rig so teardown cannot kill ambient (and CDS with it).

WHY THIS EXISTS
---------------
The rig used to be "start frida CLI, then `kill $FRIDA_PID`". Killing the client makes
frida-server unload the agent *underneath a live app*, and on this build (Android 16 / ART 17)
that unload faults inside the agent's own static destructors:

    tombstone_42: signal 11 (SIGSEGV) in /memfd:frida-agent-64.so (deleted)
      #04 frida-agent-64.so
      #07 __dl_soinfo::call_destructors()
      #08 __dl_soinfo_unload_impl()
      #12 __loader_dlclose+28

ambient dies -> the Cross Device Services link dies with it. Killing the client is not a
teardown. This supervisor is: on window close (feed_live reports the App Cast surface being
released) or on SIGINT/SIGTERM it runs each script's `shutdown` export in reverse load order
(every hook reverted, every timer stopped, every retained reference dropped), unloads the
scripts, then detaches. It never touches the app process.

MEASURED (see APPCAST_HANDOFF.md §6.1):
  bare attach + detach, no scripts .......... ambient survives
  scripts loaded, native hooks only, detach . ambient survives
  full stack + `kill <frida cli pid>` ....... ambient dies, agent destructor SIGSEGV
  revert+unload+detach after a stream ....... 3/5 survived -> NOT RELIABLE (tombstone_47, _48;
                                              a 0 s and a 6 s settle both failed)
  window close -> stop feed, keep session ... no unload happens, so nothing can fault

The failures cluster just after the app itself tears the media plane down (it logs "[jys] Decoder
error, scheduling recreation" ~0.4 s after the surface is released), so an unload keeps racing that
recovery. That is why the default no longer unloads on window close.

USAGE
  ./appcast_session.py                     # fresh ambient, relays, launch window, stay resident
  ./appcast_session.py --no-restart        # attach to whatever ambient is running
  ./appcast_session.py --no-relay          # relays already running, don't start them
  ./appcast_session.py --duration 600      # full stop after N seconds
  ./appcast_session.py --on-close detach   # old behaviour: revert+unload+detach on close (flaky)
  ./appcast_session.py --no-safe-stop      # detach while ambient still runs (flaky; experiments)
  ./appcast_session.py --abrupt            # BASELINE ONLY: detach with hooks still installed
  ./appcast_session.py --keep-attached     # revert hooks, keep the session, never detach

Ctrl-C = full stop: force-stop ambient, detach, restart ambient (CDS reconnects). No tombstone.
"""
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), "..", "lib"))
import gbos_env  # noqa: E402  shared runtime config: private run dir, container/LAN discovery
import argparse
import base64
import json
import os
import re
import shutil
import signal
import secrets
import socket
import subprocess
import sys
import threading
import time
from pathlib import Path

import frida
import frida_tools
import phone_rearm  # agent-port probe shared with the re-arm server (script dir is on sys.path)
import resolve_cds_names  # pure-Python DEX resolver for com.google.ambient.streaming R8 names

APPDIR = Path(__file__).resolve().parent
BRIDGES = Path(frida_tools.__file__).parent / "bridges"
SCRIPTS = ["gc_guard.js", "mediacodec_inject_info.js", "appcast_loopback.js", "native_decoder.js", "feed_live.js"]
LOCK = gbos_env.run_path("gb-appcast.lock")
PKG = "com.google.ambient.streaming"
FGS = f"{PKG}/.multidevicesync.devicelink.dtdi.foreground.ForegroundDiscoveryService"
ACT = f"{PKG}/.multidevicesync.appcast.receiver.AppCastActivity"
GRID = f"{PKG}/.multidevicesync.appcast.receiver.launcher.RemoteAppsLauncherActivity"
DEVICE_ID = "b1c2d3e4-0000-4a4a-b4b4-c5c5c5c5c5c5"
# Must match CaptureService.INPUT_TOKEN in waydroid/gbcast_app.
APP_INPUT_TOKEN = "gbos-cast-v1"

stop_requested = False

# frida 17 keeps the language bridges out of the agent; the client installs them. frida-tools'
# repl agent does it with this exact handshake (see repl_agent.js: `o("Java")`), so a session
# driven through the Python API must reproduce it or every `Java.` in our sources is a
# ReferenceError. `Java` lands on the script's own global, so this is prepended per script.
BRIDGE_BOOT = '''
send({type: "frida:load-bridge", name: "java"});
recv("frida:bridge-loaded", function (m) {
  Script.evaluate("/frida/bridges/" + m.filename,
    "(function () {" + m.source +
    "Object.defineProperty(globalThis, 'Java', { value: bridge });" +
    "return bridge;})();");
}).wait();
'''


# All four files are loaded into ONE script context, exactly as `frida -l a -l b -l c -l d` does
# (frida-tools compiles the -l files into a single repl agent and evaluates them in one global
# scope). MEASURED: four separate Script objects with the same files and timings make ambient
# abort (SIGABRT, heap-verification message naming [anon:dalvik-main space]) at media-plane
# spin-up; the single-context path survives a 10-minute soak. Do not "clean this up" into one
# create_script() per file.
POSTLUDE = '''
// ---- host-facing exports (one script, so each file registers its teardown on globalThis) ----
rpc.exports = {
  shutdown: function(){
    var out = {};
    try{ out.feed = (typeof shutdown === 'function') ? shutdown() : 'n/a'; }catch(e){ out.feedErr = '' + e; }
    try{ out.loopback = globalThis.__GB_LOOPBACK_SHUTDOWN ? globalThis.__GB_LOOPBACK_SHUTDOWN() : 'n/a'; }catch(e){ out.loopbackErr = '' + e; }
    try{ out.media = globalThis.__GB_MEDIA_SHUTDOWN ? globalThis.__GB_MEDIA_SHUTDOWN() : 'n/a'; }catch(e){ out.mediaErr = '' + e; }
    try{ out.gc = globalThis.__GB_GC_SHUTDOWN ? globalThis.__GB_GC_SHUTDOWN() : 'n/a'; }catch(e){ out.gcErr = '' + e; }
    return out;
  },
  armCast: function(ms){
    var f = globalThis.__GB_LOOPBACK_ARM;
    if(!f) return 'n/a';
    return f(ms || 20000);
  },
  reprime: function(){
    var f = globalThis.__GB_LOOPBACK_REPRIME;
    if(!f) return 'n/a';
    return f();
  }
};
'''


def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}][sup] {msg}", flush=True)


APPS_DIR = Path(os.environ.get("GB_APPS_DIR", str(Path(gbos_env.STATE) / "apps")))
AGENT_TOKEN_FILE = Path(gbos_env.STATE) / "agent-token"
APPS_BUNDLE_MAX = 3_000_000      # bytes of embedded JS; ~100 apps at 96 px is ~0.6 MB, this is a runaway guard


def load_apps_bundle():
    """JSON for globalThis.GB_APPS from the phone's last app-list push, or None.

    {"apps": [[pkg, label], ...], "icons": {pkg: base64 png}} — see AppSync.java / host_relay.py.
    ensure_ascii keeps it valid JS source. Anything wrong with the cache just means "use the
    built-in list": it must never be able to stop a session from starting.
    """
    # Kill switch: `touch ~/.cache/gbos/apps.off` to ignore the phone's list without deleting it.
    # MEASURED 2026-11-01: with the bundle on, GBOS's App Cast window never established the stream
    # (4 sessions in a row: window opens, "Connection attempt timed out" after 25 s, supervisor's
    # 0-frame recovery restarts ambient, CDS drops, repeat). With it off: Establish within ~1 s.
    # Cause not yet isolated (187-app list vs. the 43 answered icons), so it ships OFF.
    if (APPS_DIR.parent / "apps.off").exists():
        log("phone app bundle: DISABLED by ~/.cache/gbos/apps.off (using the built-in app list)")
        return None
    try:
        listing = json.loads((APPS_DIR / "apps.json").read_text())
        apps, icons, size = [], {}, 0
        for ent in listing:
            pkg, label = ent["pkg"], ent["label"]
            png = (APPS_DIR / "icons" / f"{pkg}.png").read_bytes()
            b64 = base64.b64encode(png).decode("ascii")
            size += len(b64) + len(pkg) + len(label)
            if size > APPS_BUNDLE_MAX:
                log(f"WARN: app bundle over {APPS_BUNDLE_MAX} bytes — truncating at {len(apps)} apps")
                break
            apps.append([pkg, label])
            icons[pkg] = b64
        if not apps:
            return None
        age = int(time.time() - (APPS_DIR / "apps.json").stat().st_mtime)
        log(f"phone app bundle: {len(apps)} apps, ~{size // 1024} KB embedded (pushed {age}s ago)")
        return json.dumps({"apps": apps, "icons": icons}, ensure_ascii=True)
    except FileNotFoundError:
        log("phone app bundle: none yet (using the built-in app list) — it arrives when the phone app next starts a cast")
    except Exception as e:
        log(f"WARN: phone app bundle unusable ({type(e).__name__}: {e}) — using the built-in app list")
    return None


def adb(serial, *args, timeout=25):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, timeout=timeout)


def ambient_pid(serial):
    out = adb(serial, "shell", "pidof", PKG).stdout.strip()
    return int(out.split()[0]) if out else None


def newest_tombstone(serial):
    # The directory saturates at 100 entries, so a count tells you nothing. Identify the newest
    # entry by name+mtime instead.
    name = adb(serial, "shell", "ls -t /data/tombstones 2>/dev/null | head -1").stdout.strip()
    if not name:
        return None
    ts = adb(serial, "shell", f"stat -c %Y /data/tombstones/{name} 2>/dev/null").stdout.strip()
    return f"{name}@{ts}"


TOUCH_JAR = Path(os.environ.get("GB_TOUCH_JAR") or APPDIR.parent / "touch" / "gb-touch.jar")
TOUCH_PORT = None          # set by start_phone_input_agent / ensure_touch_injector


def _push_touch_files(pixel):
    """The live touch injector is a jar run through app_process; ship it with the agent script."""
    if TOUCH_JAR.exists():
        adb(pixel, "push", str(TOUCH_JAR), "/data/local/tmp/gb-touch.jar")
        return True
    log(f"WARN: {TOUCH_JAR} missing — build it with touch_injector/build.sh (gestures use tap/swipe)")
    return False


def _parse_touch_port(out):
    for line in out.splitlines():
        if "TOUCH_PORT=" in line and "TOUCH_READY" in line:
            try:
                return int(line.split("TOUCH_PORT=")[1].split()[0])
            except (IndexError, ValueError):
                return None
    return None


def probe_touch_injector(phone_ip, token):
    """Port of a live touch injector already running on the phone, or None. It answers "pong"."""
    return phone_rearm.probe_touch_injector(phone_ip, token)


def ensure_touch_injector(pixel, phone_ip, token, allow_ip):
    """Find a running injector, or (with adb) start/upgrade one without touching the gesture agent."""
    global TOUCH_PORT
    port, caps = phone_rearm.probe_touch_caps(phone_ip, token) if phone_ip else (None, "")
    if (port is None or "key" not in caps or "fg" not in caps or "cast" not in caps) and pixel and _push_touch_files(pixel):
        adb(pixel, "push", str(APPDIR / "phone_agent.sh"), "/data/local/tmp/gb-phone-agent.sh")
        r = adb(pixel, "shell", "sh", "/data/local/tmp/gb-phone-agent.sh", "touch", token,
                allow_ip or "", timeout=60)
        new_port = _parse_touch_port(r.stdout or "")
        if new_port is not None:
            port = new_port
    TOUCH_PORT = port
    log(f"live touch injector: {'port ' + str(port) if port else 'not available — tap/swipe replay'}")
    return port


def start_phone_input_agent(pixel, token, want_port):
    """Push and start phone_agent.sh (input only) on the Pixel; return the port it really got.

    This is the ONLY part of the gesture path that needs the Pixel's adb, and it lasts about two
    seconds per phone boot: the agent detaches with setsid, survives adbd going away, and then
    speaks plain LAN TCP as uid `shell`. With --drop-adb we cut adb straight after and prove the
    rest of the session never touches it.

    Chosen over the in-app AccessibilityService because that route is unimplementable on a phone
    with Advanced Protection on (it refuses the accessibility role to a sideloaded app).

    The agent self-probes and reports AGENT_PORT: on this phone something already owns 8097 (and
    8103-8109) and never accepts, so a fixed port silently swallowed every gesture. Trust the
    reported port, not the requested one.
    """
    global TOUCH_PORT
    adb(pixel, "push", str(APPDIR / "phone_agent.sh"), "/data/local/tmp/gb-phone-agent.sh")
    adb(pixel, "shell", "chmod", "755", "/data/local/tmp/gb-phone-agent.sh")
    _push_touch_files(pixel)
    r = adb(pixel, "shell", "sh", "/data/local/tmp/gb-phone-agent.sh",
            "start", token, str(want_port), host_lan_ip() or "", timeout=90)
    out = (r.stdout or "").strip()
    TOUCH_PORT = _parse_touch_port(out)
    for line in out.splitlines():
        log(f"phone input agent: {line}")
    if r.stderr.strip():
        log(f"phone input agent stderr: {r.stderr.strip()}")
    port = None
    for line in out.splitlines():
        if "AGENT_PORT=" in line and "AGENT_READY" in line:
            try:
                port = int(line.split("AGENT_PORT=")[1].split()[0])
            except (IndexError, ValueError):
                port = None
    if port is None:
        log("WARN: phone input agent did not confirm a working port; gestures fall back to adb")
        return None
    if port != want_port:
        log(f"phone input agent: {want_port} was unusable on the phone, using {port}")
    return port


def host_lan_ip():
    """This laptop's LAN address, as the GBOS container sees it.

    MEASURED 2026-10-30: the container can reach the host directly on its LAN address, which is immune
    to the `adb reverse` failure mode where the tunnel accepts the container's connection and then
    forwards zero bytes — video keeps rendering while every click in the window evaporates.
    """
    return gbos_env.lan_ip()


def pick_pixel(serial):
    out = subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout
    serials = [l.split()[0] for l in out.splitlines()[1:] if l.strip() and "\tdevice" in l]
    serials = [s for s in serials if s != serial]
    lan = host_lan_ip() or ""
    prefix = ".".join(lan.split(".")[:3]) + "." if lan else None      # prefer a phone on this host's own subnet
    for s in serials:
        if prefix and s.startswith(prefix):
            return s
    return serials[0] if serials else None


def start_ambient(serial):
    adb(serial, "shell", "am force-stop", PKG)
    # `am force-stop` RETURNS before AMS has finished killing the process: under load the kill
    # landed ~8 s later, the old pid was still pidof-able, we attached to it, and its replacement
    # (from the FGS start below) looked exactly like "ambient died" at t=0. Wait for the kill to
    # actually land, then require a pid we have not seen before.
    old = ambient_pid(serial)
    for _ in range(40):
        cur = ambient_pid(serial)
        if not cur or cur != old:
            break
        time.sleep(0.25)
    time.sleep(0.5)
    adb(serial, "shell", "am start-foreground-service --user 10 -n", FGS)
    # ...and ambient itself sometimes restarts once more right after the FGS start (measured
    # 517579 -> 519210 -> 519392 within seconds), so a *new* pid is not enough either: return
    # only a pid that has survived STABLE_FOR seconds, or we attach to a process that is about
    # to be replaced and the run aborts at t=0 with a bogus "AMBIENT DIED".
    STABLE_FOR = 3.0
    pid = None
    stable_since = 0.0
    for _ in range(80):
        time.sleep(0.5)
        cur = ambient_pid(serial)
        if not cur or cur == old:
            continue
        if cur != pid:
            pid, stable_since = cur, time.time()
        elif time.time() - stable_since >= STABLE_FOR:
            return pid
    return pid


class Rig:
    def __init__(self, serial, name, runtime="v8"):
        self.serial = serial
        self.name = name
        self.runtime = runtime
        self.dev = frida.get_device_manager().get_device(serial)
        self.session = None
        self.scripts = []          # [(name, Script)] in load order
        self.frames = 0
        # Last agent heartbeat (feed_live sends one every 5 s from a plain JS timer). The stall
        # watchdog needs it: a closed window or a static screen moves no frame counter for minutes.
        self.last_hb = time.time()
        self.ready = False
        self.stream_end = None
        self.errors = []
        # Agent liveness. If the agent dies while ambient is still alive there is no reason to
        # force-stop the app (which drops CDS and needs a keeper repair) — re-attach instead.
        self.detached_reason = None
        self.script_dead = False
        # Set when the agent reports that a reopened App Cast window cannot be fed: the app released
        # the old decoder with the old surface, and only a fresh agent (which re-arms llz.a) can get
        # the app to build a new one.
        self.recast = None

    def _on_detached(self, *a):
        self.detached_reason = str(a[0]) if a else "unknown"
        log(f"AGENT detached: {self.detached_reason}")

    def _on_script_destroyed(self, *a):
        self.script_dead = True
        log("AGENT script destroyed (unloaded or crashed)")

    # ---- message plumbing: log lines, gb events, and the Java-bridge handshake -------------
    def _on_message(self, script_name, script, message, data):
        if message.get("type") == "error":
            desc = message.get("description")
            self.errors.append((script_name, desc))
            log(f"{script_name} ERROR {desc}")
            return
        payload = message.get("payload")
        if isinstance(payload, dict):
            # frida 17 loads language bridges on demand from the client; frida-tools answers
            # this handshake, so an API-driven session must answer it too or `Java` is undefined.
            if payload.get("type") == "frida:load-bridge":
                path = BRIDGES / f"{payload['name'].lower()}.js"
                script.post({"type": "frida:bridge-loaded",
                             "filename": path.name,
                             "source": path.read_text(encoding="utf-8")})
                return
            gb = payload.get("gb")
            if gb == "stream-end":
                self.stream_end = payload
                return
            if gb == "recast":
                self.recast = payload.get("pkg") or ""
                return
            if gb == "ready":
                self.ready = True
                return
            if gb == "frame":
                self.frames = int(payload.get("n", self.frames))
                return
            if gb == "hb":
                self.last_hb = time.time()
                return
            if gb == "resize":
                tid = payload.get("taskId")
                if tid:
                    adb(self.serial, "shell", "am", "task", "resize", str(tid), "772", "80", "1148", "960")
                    log(f"auto-resized task {tid} to 376x880 portrait")
                return
            return
        if isinstance(payload, str):
            # console.log output arrives on frida's default log handler, not here; keep this branch
            # for scripts that use send("...").
            print(f"  {payload}", flush=True)

    def attach(self, pid, opts=None):
        self.session = self.dev.attach(pid)
        try:
            self.session.on("detached", self._on_detached)
        except Exception as e:
            log(f"WARN: session detach callback: {type(e).__name__} {e}")
        parts = []
        # FEED_OPTS is read by feed_live's own top level ("globalThis.FEED_OPTS || {}"), so it has
        # to be set before anything else runs in the shared scope.
        if opts:
            parts.append(f"globalThis.FEED_OPTS={opts};")
        # The phone's real app list + icons (pushed by the gbcast app, stored by host_relay.py).
        # appcast_loopback.js reads globalThis.GB_APPS; absent => it falls back to its hand-typed list.
        bundle = load_apps_bundle()
        if bundle:
            parts.append(f"globalThis.GB_APPS={bundle};")
        # Resolve R8-obfuscated Cross-Device Services class/method names for the installed APK version;
        # inject into the shared scope so scripts survive app updates without manual name hunting.
        try:
            cds_names = resolve_cds_names.resolve_for_container(getattr(self, "serial", None))
            parts.append(f"globalThis.GB_CDS_NAMES={json.dumps(cds_names)};")
            log(f"CDS symbols resolved for versionCode={cds_names.get('versionCode', '?')}: gate={cds_names.get('gate')} display={cds_names.get('display')}")
        except Exception as e:
            log(f"WARN: CDS name resolution failed ({e}); using built-in 1.0.1392 defaults")
        for name in SCRIPTS:
            parts.append(f"// ====================== {name} ======================")
            parts.append((APPDIR / name).read_text())
        parts.append(POSTLUDE)
        source = BRIDGE_BOOT + "\n" + "\n".join(parts)
        script = self.session.create_script(source, name="appcast", runtime=self.runtime)
        script.on("message", lambda m, d: self._on_message("appcast", script, m, d))
        try:
            script.on("destroyed", self._on_script_destroyed)
        except Exception as e:
            log(f"WARN: script destroyed callback: {type(e).__name__} {e}")
        script.load()
        self.scripts.append(("appcast", script))

    def call(self, name, method):
        for n, s in self.scripts:
            if n == name:
                return getattr(s.exports_sync, method)()
        return None

    def shutdown(self, keep_attached=False, abrupt=False):
        if abrupt:
            log("ABRUPT detach (baseline): hooks still installed, no shutdown calls")
            self.session.detach()
            return
        for name, script in reversed(self.scripts):
            t = time.time()
            try:
                r = script.exports_sync.shutdown()
                log(f"{name}.shutdown -> {r}  ({(time.time()-t)*1000:.0f} ms)")
            except Exception as e:
                log(f"{name}.shutdown FAILED: {type(e).__name__} {e}")
        if keep_attached:
            log("--keep-attached: hooks reverted, session left idle (no unload, no detach)")
            return
        for name, script in reversed(self.scripts):
            try:
                script.unload()
                log(f"{name} unloaded")
            except Exception as e:
                log(f"{name}.unload FAILED: {e}")
        log("detaching")
        self.session.detach()
        log("detached")


def _sig(_signo, _frame):
    global stop_requested
    stop_requested = True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--container", default=gbos_env.container())
    ap.add_argument("--pixel", default=None)
    ap.add_argument("--target", default="com.android.settings", help="package to mirror")
    ap.add_argument("--agent-stall", type=int, default=120,
                    help="re-attach in place when no frame arrives for this many seconds while the app "
                         "is alive (0 disables). Catches a wedged agent that reports nothing")
    ap.add_argument("--max-agent-reattach", type=int, default=5,
                    help="give up and end the session after this many successful in-place re-attaches "
                         "in one session (guards a crash loop)")
    ap.add_argument("--agent-reattach-window", type=int, default=90,
                    help="how many seconds to keep retrying while frida-server comes back")
    ap.add_argument("--test-kill-agent", type=int, metavar="SECONDS",
                    default=int(os.environ.get("GB_TEST_KILL_AGENT", "0")),
                    help="TEST ONLY: destroy the injected agent this many seconds after it is ready, "
                         "leaving ambient alive, to exercise the in-place re-attach path")
    ap.add_argument("--duration", type=int, default=0)
    ap.add_argument("--no-restart", action="store_true", help="attach to the running ambient")
    ap.add_argument("--no-relay", action="store_true")
    ap.add_argument("--phone-app", action="store_true",
                    help="video comes from the GBOS Cast app on the phone (MediaProjection -> H.264 "
                         "-> TCP): no screenrecord, no adb at all in the video path")
    ap.add_argument("--input-agent", action="store_true",
                    help="with --phone-app: gestures go to the shell-uid agent (phone_agent.sh) over "
                         "plain LAN. NEEDS ADB FOR ~2 s PER PHONE BOOT to start the agent, and nothing "
                         "after that — wireless debugging can be switched off.")
    ap.add_argument("--input-app", action="store_true",
                    help="with --phone-app: gestures go to the app's own listener "
                         "(AccessibilityService). Needs GBOS Cast enabled under Settings > "
                         "Accessibility, which Advanced Protection refuses for a sideloaded app")
    ap.add_argument("--drop-adb", action="store_true",
                    help="with --input-agent: disconnect the Pixel's adb once the agent is running "
                         "(proves the session no longer needs it)")
    ap.add_argument("--phone-video-port", type=int, default=8098)
    ap.add_argument("--host-ip", default=None,
                    help="this laptop's LAN address for the container to reach the relays on "
                         "(default: auto-detected; the loopback/adb-reverse path is kept as a "
                         "fallback in the injected script)")
    ap.add_argument("--phone-ip", default=None,
                    help="the Pixel's LAN address. --phone-app does not need the phone in `adb "
                         "devices` at all (it dials us), so pass this when wireless debugging is "
                         "off and the gesture relay/re-arm server still need to know where the "
                         "phone is.")
    ap.add_argument("--phone-input-port", type=int, default=8099)
    ap.add_argument("--no-launch", action="store_true")
    ap.add_argument("--keep-attached", action="store_true")
    ap.add_argument("--settle", type=float, default=6.0,
                    help="seconds to wait after the stream ends before reverting/unloading "
                         "(0 removes the wait; measured to crash ambient after a long stream)")
    ap.add_argument("--abrupt", action="store_true", help="baseline: detach without shutdown")
    ap.add_argument("--on-close", choices=["idle", "detach"], default="idle",
                    help="window close: 'idle' stops the feed but keeps the session (default, "
                         "the only reliably safe option); 'detach' reverts+unloads+detaches")
    ap.add_argument("--safe-stop", dest="safe_stop", action="store_true", default=True,
                    help="on full stop, force-stop ambient before detaching (no tombstone), then restart it (default)")
    ap.add_argument("--no-safe-stop", dest="safe_stop", action="store_false",
                    help="detach while ambient is still running (measured flaky: 3/5 survived)")
    ap.add_argument("--arm-before-launch", action="store_true",
                    help="re-arm llz.a right before am start (measured to abort in establish; prefer the 45 s startup window)")
    ap.add_argument("--opts", default=None, help='FEED_OPTS json, e.g. {\'keepU\':1}')
    ap.add_argument("--runtime", choices=["v8", "qjs"], default="v8",
                    help="script runtime (default v8; qjs measured to use less anon memory)")
    args = ap.parse_args()

    signal.signal(signal.SIGINT, _sig)
    signal.signal(signal.SIGTERM, _sig)

    serial = args.container
    os.environ["GB_CONTAINER"] = serial
    pixel = args.pixel or pick_pixel(serial)
    # --phone-app means the phone connects out to us, so its adb serial is optional. Keep the LAN
    # address anyway: the gesture relay and the re-arm server both need it, and it must survive
    # wireless debugging being off (the normal state between re-arms).
    phone_ip = args.phone_ip or (pixel.split(":")[0] if pixel else None)
    lan_ip = args.host_ip or host_lan_ip()
    Path(LOCK).touch()

    pid = None if args.no_restart else start_ambient(serial)
    if pid is None:
        pid = ambient_pid(serial)
    if not pid:
        log("FATAL: ambient is not running and would not start")
        Path(LOCK).unlink(missing_ok=True)
        return 1
    tomb0 = newest_tombstone(serial)
    log(f"ambient pid={pid} newest_tombstone={tomb0} pixel={pixel}")

    relays = []
    # Relays that must survive a crash WITHOUT costing the frida session. Killing and recreating the
    # whole session for a dead relay was expensive: it force-stops ambient, drops CDS, and needs a
    # keeper repair. The container reconnects to a fresh relay by itself (see feed_live.js
    # scheduleReconnect + host_relay's cached SPS/PPS/IDR), so restarting the process in place is
    # enough. Measured 2026-10-31.
    relay_specs = []

    def spawn_relay(name, argv, env, logpath):
        f = open(logpath, "a")
        p = subprocess.Popen(argv, stdout=f, stderr=subprocess.STDOUT, env=env)
        relays.append(p)
        relay_specs.append({"name": name, "argv": argv, "env": env, "logpath": logpath,
                            "proc": p, "file": f})
        return p

    agent_token, agent_port = None, None
    if args.input_agent and args.input_app:
        log("FATAL: --input-agent and --input-app are two different gesture paths; pick one")
        Path(LOCK).unlink(missing_ok=True)
        return 1
    if (args.input_agent or args.input_app) and not args.phone_app:
        log("FATAL: --input-agent/--input-app need --phone-app (video from the app)")
        Path(LOCK).unlink(missing_ok=True)
        return 1

    if not args.no_relay:
        if not pixel and not args.phone_app:
            log("WARN: no Pixel found; starting without a media source")
        else:
            adb(serial, "reverse", "tcp:8099", "tcp:8099")
            adb(serial, "reverse", "tcp:8100", "tcp:8100")
            # Clean up any leftover relays from an earlier session before binding
            subprocess.run(["pkill", "-f", "host_relay\\.py"], stderr=subprocess.DEVNULL)
            subprocess.run(["pkill", "-f", "phone_rearm\\.py"], stderr=subprocess.DEVNULL)
            subprocess.run(["pkill", "-f", "input_relay\\.py"], stderr=subprocess.DEVNULL)
            time.sleep(0.2)
            ilog = open(gbos_env.run_path("appcast_input_relay.log"), "w")
            # Relays listen on the LAN address when one was found, so the container does not depend
            # on `adb reverse` at all; the reverse tunnels are still set up as the fallback.
            relay_env = dict(os.environ)
            if lan_ip:
                relay_env["GB_RELAY_BIND"] = lan_ip
                # Only these peers may inject input: this host, the container's two addresses.
                relay_env["GB_INPUT_ALLOW"] = gbos_env.input_allow()
                log(f"relays bind {lan_ip}; container may drive them on the LAN (fallback 127.0.0.1)")
            if args.phone_app:
                # The app dials us; there is no video bootstrap and nothing to tear down on the
                # phone. Gestures take one of two routes:
                #   --input-agent  shell uid over LAN (needs ~2 s of adb once per phone boot)
                #   --input-app    the app's AccessibilityService (no adb ever; blocked by
                #                  Advanced Protection on this Pixel)
                # Nothing here needs the phone's serial: it dials us. host_relay's PIXEL argument
                # is only used by the screenrecord (pull) mode, which this path never runs.
                spawn_relay("video relay",
                            ["python3", str(APPDIR / "host_relay.py"), "--listen",
                             str(args.phone_video_port)],
                            relay_env, gbos_env.run_path("appcast_host_relay.log"))
                # Lets the phone app replace the hand-typed IP: it broadcasts a probe and this
                # answers with the port to push to, from whatever address DHCP has given us today.
                spawn_relay("discovery responder",
                            ["python3", str(APPDIR / "discover_host.py")],
                            dict(relay_env, GB_PHONE_VIDEO_PORT=str(args.phone_video_port),
                                 GB_PHONE_INPUT_PORT=str(args.phone_input_port)),
                            gbos_env.run_path("appcast_discover.log"))
                log("discovery responder on udp:8096 — the app can find this laptop by itself")
                if args.input_agent:
                    # The token must outlive a laptop reboot: the phone's agent and touch injector keep running
                    # with the token they were started with. MEASURED 2026-11-03: /tmp was wiped by a reboot, the
                    # session minted a new token, and every gesture was silently ignored by the phone.
                    token_file = AGENT_TOKEN_FILE
                    if token_file.exists():
                        agent_token = token_file.read_text().strip()
                    else:
                        agent_token = secrets.token_hex(8)
                    token_file.parent.mkdir(parents=True, exist_ok=True)
                    token_file.write_text(agent_token)
                    os.chmod(token_file, 0o600)             # the token authorises input on the phone

                    # Probe if the agent is already alive on the phone across known ports
                    if phone_ip:
                        # Not a bare connect test: the live touch injector (8110+) also accepts TCP.
                        found = phone_rearm.check_alive_agent(phone_ip, agent_token, args.phone_input_port)
                        if found:
                            agent_port = found
                            log(f"phone input agent already alive on {phone_ip}:{agent_port}")

                    if agent_port:
                        # Agent survived from an earlier run: its touch injector may not exist yet.
                        ensure_touch_injector(pixel, phone_ip, agent_token, host_lan_ip())
                    if not agent_port:
                        if pixel:
                            agent_port = start_phone_input_agent(pixel, agent_token,
                                                                 args.phone_input_port)
                        else:
                            log("no Pixel in adb devices: skipping the gesture bootstrap — the "
                                "app's Re-arm button starts the agent over the LAN")
                if args.input_app:
                    token, port = APP_INPUT_TOKEN, args.phone_input_port
                    desc = f"the app on {phone_ip}:{port} (no adb at all)"
                elif agent_port:
                    token, port = agent_token, agent_port
                    desc = f"the shell agent on {phone_ip}:{port} (no adb after bootstrap)"
                else:
                    token = port = None
                    desc = "nothing yet — press 'Re-arm touch forwarding' on the phone"
                if token:
                    p = subprocess.Popen(
                        ["python3", str(APPDIR / "input_relay.py"), pixel or "lan",
                         "--phone-tcp", phone_ip, str(port), token]
                        + (["--phone-touch", phone_ip, str(TOUCH_PORT), token]
                           if TOUCH_PORT and token == agent_token else []),
                        stdout=ilog, stderr=subprocess.STDOUT, env=relay_env)
                    relays.append(p)
                    # phone_rearm.py restarts this exact relay when a re-arm lands the agent on a
                    # different port, so it needs to find it.
                    Path(gbos_env.run_path("gb-input-relay.pid")).write_text(str(p.pid))
                elif pixel:
                    relays.append(subprocess.Popen(
                        ["python3", str(APPDIR / "input_relay.py"), pixel],
                        stdout=ilog, stderr=subprocess.STDOUT, env=relay_env))
                if args.input_agent and phone_ip:
                    # Started even when no agent exists yet: the app's "Re-arm touch forwarding"
                    # button is exactly how touch comes back after a phone reboot — or when this
                    # session was started with wireless debugging already off. The server starts
                    # the agent and then starts/repoints the input relay itself.
                    rlog = open(gbos_env.run_path("appcast_rearm.log"), "w")
                    relays.append(subprocess.Popen(
                        ["python3", str(APPDIR / "phone_rearm.py"),
                         "--phone", phone_ip, "--token", agent_token,
                         "--agent-port", str(agent_port or args.phone_input_port)],
                        stdout=rlog, stderr=subprocess.STDOUT))
                    log("re-arm server on :8091 (the phone app's button can start the agent with "
                        "no adb left on)")
                elif args.input_agent:
                    log("WARN: no phone address, so the Re-arm button cannot work; pass "
                        "--phone-ip <pixel lan ip>")
                log(f"relays up — GBOS Cast app pushes video to this host:{args.phone_video_port};"
                    f" gestures via {desc}")

                active_phone_ip = phone_ip
                active_input_port = agent_port

                def bind_phone_gestures(new_ip):
                    nonlocal active_phone_ip, active_input_port
                    if not new_ip or not args.input_agent:
                        return
                    agent_alive = phone_rearm.check_alive_agent(new_ip, agent_token, args.phone_input_port)
                    detected_port = agent_alive or 8099

                    pidfile = Path(gbos_env.run_path("gb-input-relay.pid"))
                    need_spawn = True
                    if pidfile.exists() and active_phone_ip == new_ip and active_input_port == detected_port:
                        try:
                            pid = int(pidfile.read_text().strip())
                            os.kill(pid, 0)
                            need_spawn = False
                        except (OSError, ValueError):
                            pass

                    if need_spawn:
                        if pidfile.exists():
                            try:
                                old = int(pidfile.read_text().strip())
                                os.kill(old, signal.SIGTERM)
                            except OSError:
                                pass
                        ilog_append = open(gbos_env.run_path("appcast_input_relay.log"), "a")
                        # The phone often shows up AFTER the session started (cast switched on later), so
                        # the start-up path never looked for the live touch injector. Look now: finds a
                        # running one with no adb, starts one if adb is available. Never lets a hung adb
                        # call stall this loop.
                        try:
                            tport = ensure_touch_injector(pixel, new_ip, agent_token, host_lan_ip())
                        except Exception as e:
                            log(f"WARN: touch injector lookup failed ({type(e).__name__}: {e}) — tap/swipe replay")
                            tport = None
                        p_rel = subprocess.Popen(
                            ["python3", str(APPDIR / "input_relay.py"), pixel or "lan",
                             "--phone-tcp", new_ip, str(detected_port), agent_token]
                            + (["--phone-touch", new_ip, str(tport), agent_token]
                               if tport else []),
                            stdout=ilog_append, stderr=subprocess.STDOUT, env=relay_env)
                        relays.append(p_rel)
                        pidfile.write_text(str(p_rel.pid))
                        active_phone_ip = new_ip
                        active_input_port = detected_port
                        if agent_alive:
                            log(f"dynamically bound gestures to phone {new_ip}:{detected_port}")
                        else:
                            log(f"phone gesture agent not reachable on {new_ip} yet — tap 'Re-arm touch forwarding' in GBOS Cast on the phone (relay listening on :8100 -> {new_ip}:{detected_port})")

                    # Ensure re-arm server is running
                    rearm_running = False
                    for r in relays:
                        if r.poll() is None and any("phone_rearm.py" in str(x) for x in getattr(r, 'args', [])):
                            rearm_running = True
                            break
                    if not rearm_running:
                        rlog_append = open(gbos_env.run_path("appcast_rearm.log"), "a")
                        p_rearm = subprocess.Popen(
                            ["python3", str(APPDIR / "phone_rearm.py"),
                             "--phone", new_ip, "--token", agent_token,
                             "--agent-port", str(detected_port)],
                            stdout=rlog_append, stderr=subprocess.STDOUT)
                        relays.append(p_rearm)
                        log(f"re-arm server running on :8091 for phone {new_ip}")

                def phone_watcher():
                    phone_file = Path(gbos_env.run_path("gb-appcast-phone-ip"))
                    while True:
                        time.sleep(2)
                        try:
                            if phone_file.exists():
                                cur = phone_file.read_text().strip()
                                if cur and (cur != active_phone_ip or active_input_port is None):
                                    bind_phone_gestures(cur)
                        except Exception:
                            pass

                if args.input_agent:
                    tw = threading.Thread(target=phone_watcher, daemon=True)
                    tw.start()
            else:
                spawn_relay("video relay",
                            ["python3", str(APPDIR / "host_relay.py"), pixel, "1376x736",
                             "6000000"], None, gbos_env.run_path("appcast_host_relay.log"))
                relays.append(subprocess.Popen(["python3", str(APPDIR / "input_relay.py"), pixel],
                                               stdout=ilog, stderr=subprocess.STDOUT))
                log(f"relays up (host_relay -> {pixel})")

    if args.drop_adb and args.input_agent and agent_port:
        # Everything the phone needs is running detached; nothing in this session uses the
        # Pixel's adb any more, so cut it. Switch Wireless debugging off and the cast and the
        # touches both keep working.
        adb(serial, "disconnect", pixel)
        log(f"dropped adb to {pixel} — capture and input now ride the LAN only")
        log("you can switch Wireless debugging off on the phone now; this session will not notice")

    # Tell the injected script where the relays are. FEED_OPTS is evaluated as JS before the
    # scripts run; the LAN address is tried first and loopback second, inside connectHost().
    opts = args.opts
    if lan_ip:
        lan_opts = '{"videoHost":"%s","inputHost":"%s"}' % (lan_ip, lan_ip)
        opts = f"Object.assign({lan_opts},{opts})" if opts else lan_opts

    rig = Rig(serial, "ambient", args.runtime)
    try:
        rig.attach(pid, opts)
        log(f"attached pid={pid} scripts={len(rig.scripts)}")
    except Exception as e:
        log(f"FATAL: attach failed: {type(e).__name__} {e}")
        for r in relays:
            r.terminate()
        Path(LOCK).unlink(missing_ok=True)
        return 1

    for _ in range(40):
        if rig.ready:
            break
        time.sleep(0.5)

    def cds_connected():
        """The Cross-Device link state, read the way the keeper does: the app's own ongoing notification."""
        try:
            out = subprocess.run(["adb", "-s", serial, "shell", "dumpsys notification --noredact"],
                                 capture_output=True, text=True, timeout=20).stdout
        except Exception:
            return None
        lines = out.splitlines()
        for i, ln in enumerate(lines):
            if f"pkg={PKG} " in ln and "id=1 tag=null" in ln:
                return any("android.title=String (Connected)" in x for x in lines[i:i + 30])
        return False

    def link_watcher():
        """Replays the app-list handshake when the link comes back after a drop.

        MEASURED 2026-11-03 (16:18, 16:52): a Dtdi drop cancels GBOS's per-device setup flows and the Phone Hub
        drawer reverts to "Get started"; the handshake only ever ran at session start. UNVERIFIED that the
        replay repairs it. Needs >= 20 s down so a notification re-post blip never triggers it."""
        down_since = None
        manual = Path(gbos_env.run_path("gb-reprime-now"))     # `touch` it to replay the handshake by hand (testing / drawer stuck)
        while not stop_requested:
            time.sleep(10)
            if manual.exists():
                manual.unlink(missing_ok=True)
                try:
                    log(f"re-prime (manual trigger): {rig.call('appcast', 'reprime')}")
                except Exception as e:
                    log(f"WARN: manual re-prime failed: {type(e).__name__}: {e}")
            up = cds_connected()
            if up is None:
                continue
            if not up:
                down_since = down_since or time.time()
            elif down_since is not None:
                outage = time.time() - down_since
                down_since = None
                if outage >= 20:
                    log(f"link back after {int(outage)} s down — replaying the app-list handshake")
                    time.sleep(10)
                    try:
                        log(f"re-prime: {rig.call('appcast', 'reprime')}")
                    except Exception as e:
                        log(f"WARN: re-prime failed: {type(e).__name__}: {e}")

    threading.Thread(target=link_watcher, daemon=True).start()
    if not args.no_launch:
        # Arm llz.a BEFORE the window exists: the receiver only emits EstablishOpenScreenConnection
        # once, and an unarmed hook misses it (that is a 0-frame window, not an error).
        if args.arm_before_launch:
            try:
                rig.call("appcast", "armCast")
                log("armed llz.a for the cast request (20 s), now launching")
            except Exception as e:
                log(f"armCast failed: {type(e).__name__} {e}")
        # FLAG_ACTIVITY_NEW_TASK|CLEAR_TASK (0x10008000): Android restores the ambient task with the
        # intent it was FIRST launched with, so a plain `am start` for a different package leaves the
        # activity's packageName extra pointing at the old app (MEASURED 2026-11-01: asked for
        # calculator, the window carried com.android.settings and the Pixel launched Settings again).
        # Clearing the task forces onCreate with OUR intent.
        adb(serial, "shell", "am start --user 10 -f 0x10008000 -n", ACT, "--es", "deviceId", DEVICE_ID,
            "--es", "packageName", args.target, "--el", "sessionId", "12345")
        log(f"launched App Cast window for {args.target}")

    # A dead relay no longer ends the session. Before this, the only recovery was the supervisor
    # tearing everything down (force-stop ambient -> CDS drops -> keeper repair -> full re-attach)
    # for what is usually one crashed listener. The container's reader already reconnects by itself
    # and host_relay re-sends cached SPS/PPS + IDR, so restarting the process in place is enough.
    # Set once this session starts taking the relays down for good. Without it the watchdog could
    # not tell "the relay crashed" from "we just stopped the relay", and resurrected one during
    # teardown — an orphan that kept :8098 bound and made the next session's relay lose the port.
    relays_retired = threading.Event()

    def relay_watchdog():
        while not stop_requested and not relays_retired.is_set():
            time.sleep(3)
            for spec in relay_specs:
                if relays_retired.is_set() or stop_requested:
                    return
                p = spec["proc"]
                if p.poll() is None:
                    continue
                log(f"WARN: {spec['name']} exited (rc={p.returncode}) — restarting it in place; "
                    f"the container reconnects on its own (no re-attach, ambient untouched)")
                try:
                    f = open(spec["logpath"], "a")
                    np = subprocess.Popen(spec["argv"], stdout=f, stderr=subprocess.STDOUT,
                                          env=spec["env"])
                    try:
                        spec["file"].close()
                    except Exception:
                        pass
                    spec["proc"], spec["file"] = np, f
                    relays.append(np)
                    log(f"{spec['name']} restarted pid={np.pid}")
                except Exception as e:
                    log(f"WARN: could not restart {spec['name']}: {type(e).__name__} {e}")

    if relay_specs:
        threading.Thread(target=relay_watchdog, daemon=True).start()

    if args.test_kill_agent:
        # Reproducing "the agent died but ambient did not" needs a death that does NOT take
        # frida-server with it: killing the server is an abrupt detach and ambient goes down with it
        # (measured: pid replaced ~3 s later). Unloading the script is the same client-side event
        # frida reports as "script destroyed", minus the server death.
        def kill_agent_for_test():
            # Wait for FRAMES, not just `ready`: a cold start can take a while (and the window can
            # be armed without ever establishing), and killing the agent before anything flows
            # tests nothing.
            deadline = time.time() + 300
            while not stop_requested and rig.frames == 0 and time.time() < deadline:
                time.sleep(0.5)
            if stop_requested or rig.frames == 0:
                log("TEST: no frames — not killing the agent")
                return
            time.sleep(args.test_kill_agent)
            log(f"TEST: destroying the agent {args.test_kill_agent}s after ready "
                f"(ambient left running) to exercise the in-place re-attach")
            for _name, s in list(rig.scripts):
                try:
                    s.unload()
                except Exception as e:
                    log(f"TEST: unload raised: {type(e).__name__} {e}")

        threading.Thread(target=kill_agent_for_test, daemon=True).start()

    started = time.time()
    reason = "duration"
    last_report = 0.0
    last_frames, last_frame_t = rig.frames, time.time()
    agent_reattaches = 0
    reattach_deadline = None    # set while trying; bounds how long we keep asking
    while True:
        time.sleep(0.5)
        now = time.time()
        if stop_requested:
            reason = "signal"
            break
        if rig.stream_end:
            reason = f"stream-end ({rig.stream_end.get('reason')})"
            break
        cur = ambient_pid(serial)
        if cur != pid:
            reason = f"AMBIENT DIED (pid {pid} -> {cur})"
            break
        if args.duration and now - started >= args.duration:
            break
        if rig.frames != last_frames:
            last_frames, last_frame_t = rig.frames, now

        # ---- Phone Hub click on a closed window: recycle the agent in place -------------------
        # MEASURED 2026-11-01: after the window closes, the retained receiver's decoder is dead
        # (jys.k=undefined) and the reopened window renders nothing while frames are "fed" into it.
        # The app only builds a new receiver when its cast request is bridged, and llz.a cannot be
        # re-armed in a live agent (that aborts the process). A FRESH agent re-arms it at load, so
        # recycle the agent instead of the whole session: ambient and CDS are never touched, and if
        # ambient does die during the unload the supervisor's cycle takes over exactly as before.
        if rig.recast is not None:
            new_pkg = rig.recast or args.target
            log(f"Phone Hub click -> {new_pkg}: recycling the agent in place "
                f"(the retained receiver has no decoder left)")
            try:
                try:
                    rig.session.detach()
                except Exception:
                    pass
                fresh = Rig(serial, "ambient", args.runtime)
                fresh.attach(pid, opts)
                for _ in range(60):
                    if fresh.ready:
                        break
                    time.sleep(0.5)
                try:
                    fresh.call("appcast", "armCast")
                except Exception as e:
                    log(f"armCast failed: {type(e).__name__} {e}")
                adb(serial, "shell", "am start --user 10 -f 0x10008000 -n", ACT, "--es", "deviceId",
                    DEVICE_ID, "--es", "packageName", new_pkg, "--el", "sessionId", "12345")
                rig = fresh
                last_frames, last_frame_t = rig.frames, time.time()
                log(f"agent recycled for {new_pkg} (ready={rig.ready}); App Cast window relaunched")
            except Exception as e:
                # Leave the package where the supervisor can find it, so its full cycle casts the
                # app the user actually clicked instead of the session default.
                log(f"recycle failed: {type(e).__name__} {e} — ending the session for a full cycle")
                try:
                    Path(gbos_env.run_path("gb-appcast-recast-target")).write_text(new_pkg)
                except OSError:
                    pass
                reason = f"recast ({new_pkg})"
                break
            continue

        # ---- agent died but the app did not: re-attach in place -------------------------------
        # Ending the session here is what made the supervisor run a full cycle (force-stop ambient,
        # wait for CDS, keeper repair, re-launch). None of that is necessary while the process we
        # were attached to is still alive: a fresh agent finds the live JniOpenScreenReceiver in
        # pollForRecv(), builds a SurfaceView in the existing window and reconnects to the relay.
        # The stall branch catches a wedged agent that never reports anything.
        cause = None
        if rig.detached_reason:
            cause = f"detached ({rig.detached_reason})"
        elif rig.script_dead:
            cause = "script destroyed"
        elif (args.agent_stall and last_frames > 0 and now - last_frame_t >= args.agent_stall
              and now - rig.last_hb >= args.agent_stall):
            # Frames AND heartbeats silent = a wedged agent. Frames alone are not evidence: a closed
            # window or a static phone screen produces none, and re-attaching an agent that is fine
            # unloads frida-agent, which crashed ambient in most of the cases we measured.
            cause = f"no frames or heartbeat for {int(now - max(last_frame_t, rig.last_hb))}s"
        if cause:
            if reattach_deadline is None:
                reattach_deadline = now + args.agent_reattach_window
            if agent_reattaches >= args.max_agent_reattach or now > reattach_deadline:
                log(f"WARN: agent {cause} and re-attaching did not bring it back "
                    f"({agent_reattaches} successes, {int(now - (reattach_deadline or now))}s spent); "
                    f"ending the session so the supervisor can do a full cycle")
                reason = f"agent-dead ({cause})"
                break
            log(f"agent {cause} while ambient pid={pid} is alive — re-attaching INTO THE SAME "
                f"PROCESS (no force-stop, CDS untouched)")
            try:
                try:
                    rig.session.detach()
                except Exception:
                    pass
                new_rig = Rig(serial, "ambient", args.runtime)
                new_rig.attach(pid, opts)
                for _ in range(60):                    # give the scripts time to find the receiver
                    if new_rig.ready:
                        break
                    time.sleep(0.5)
                rig = new_rig
                agent_reattaches += 1
                reattach_deadline = None
                last_frames, last_frame_t = rig.frames, time.time()
                log(f"re-attached to pid={pid} (ready={rig.ready}); window and relays untouched")
            except Exception as e:
                # frida-server itself is usually the thing restarting here (gb-fridaserver is an
                # init service), so wait for it rather than burning the attempt budget.
                # Retry fast: gb-fridaserver respawns in about a second, and the App Cast window
                # tolerates only a couple of seconds without frames before it tears its decoder
                # down. A 4 s backoff here was long enough to lose that race every time.
                log(f"re-attach failed: {type(e).__name__} {e} — retrying (frida-server restarting?)")
                time.sleep(0.5)
            continue

        if now - last_report >= 15:
            last_report = now
            log(f"t={int(now-started)}s frames={rig.frames} pid={cur}")

    log(f"stream ended: {reason} after {int(time.time()-started)}s, {rig.frames} frames fed")

    # Closing the window must not cost ambient (and CDS with it) anything. The only reliable way
    # to guarantee that is to NOT unmap the agent while the app runs: "revert+unload+detach" after
    # a stream measured 3/5 survivals (tombstone_47 and _48 in the failures, with and without a
    # 6 s settle; the app releases its decoder on surface release and logs "[jys] Decoder error,
    # scheduling recreation" ~0.4 s later, so the unload keeps racing its media-plane recovery).
    # So a closed window means: stop feeding, keep the session. That is also exactly the state
    # that survived the 10-minute soak.
    idled = False
    if reason.startswith("stream-end") and args.on_close == "idle":
        idled = True
        log("window closed -> feed stopped; KEEPING the frida session (no unload, no detach)")
        log("ambient + CDS stay as they are. Ctrl-C here for a full stop.")
        while not stop_requested:
            time.sleep(0.5)
            cur = ambient_pid(serial)
            if cur != pid:
                log(f"ambient changed while idle (pid {pid} -> {cur})")
                break

    relays_retired.set()
    if args.safe_stop and ambient_pid(serial) == pid:
        # Force-stop first: with the process gone an agent unload cannot fault, so this path
        # never leaves a tombstone. Ambient is restarted below and CDS reconnects on its own.
        log("full stop: force-stopping ambient so the agent unload cannot take a live app down")
        adb(serial, "shell", "am force-stop", PKG)
        time.sleep(1.5)
        try:
            rig.session.detach()
            log("detached (app already stopped)")
        except Exception as e:
            log(f"detach: {type(e).__name__} {e}")
    else:
        if args.settle > 0:
            log(f"settling {args.settle}s before revert/unload")
            time.sleep(args.settle)
        try:
            rig.shutdown(keep_attached=args.keep_attached, abrupt=args.abrupt)
        except Exception as e:
            log(f"shutdown raised: {type(e).__name__} {e}")

    for r in relays:
        r.terminate()
    # phone_rearm.py starts its OWN relay (detached) when it re-arms an agent, so it is not in
    # `relays` and would otherwise keep holding :8100 into the next session.
    try:
        stray = int(Path(gbos_env.run_path("gb-input-relay.pid")).read_text().strip())
        if all(r.pid != stray for r in relays):
            os.kill(stray, signal.SIGTERM)
            log(f"stopped the re-arm's detached input_relay pid={stray}")
    except (OSError, ValueError):
        pass
    # Do not kill the phone agent on session stop — it runs with setsid as uid shell
    # specifically to survive across sessions so touch forwarding never needs re-arming
    # after normal service restarts.
    Path(LOCK).unlink(missing_ok=True)

    restarted = None
    if args.safe_stop:
        restarted = start_ambient(serial)
        if restarted:
            log(f"ambient restarted pid={restarted} (CDS reconnects on its own)")

    time.sleep(2)
    after = restarted or ambient_pid(serial)
    tomb1 = newest_tombstone(serial)
    new_tomb = tomb1 != tomb0
    survived = (after == pid) and not new_tomb
    restarted_clean = (restarted is not None) and not new_tomb
    ok = survived or restarted_clean
    if survived:
        verdict = "SURVIVED (same pid)"
    elif restarted_clean:
        verdict = "CLEAN STOP + RESTART (no tombstone)"
    else:
        verdict = "DIED"
    print()
    print("=" * 74)
    print(f"RESULT  ambient: {pid} -> {after}   {verdict}")
    print(f"        newest tombstone: {tomb0} -> {tomb1}{'   (NEW TOMBSTONE)' if new_tomb else ''}")
    print(f"        frames fed: {rig.frames}   end reason: {reason}")
    print(f"        session: {'kept idle' if idled else ('detached after force-stop' if args.safe_stop else 'detached live')}")
    if rig.errors:
        print(f"        script errors: {rig.errors[:3]}")
    print("=" * 74)
    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())
