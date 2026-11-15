# touch_injector — live touch for the GBOS cast

A tiny program that runs ON THE PHONE (adb-shell user via `app_process`, no root — the scrcpy technique) and
turns a stream of finger events from GBOS into real MotionEvents, so the phone follows your finger live.

Before this, a gesture was only sent when the finger lifted and was replayed as ONE swipe of 160–350 ms
(`input swipe`): no live scrolling, no hold, no long-press, no pinch.

Pieces
- `src/com/gbos/touch/Main.java` — the injector. Listens on TCP (default 8110+), token-protected, only the
  laptop (and loopback) may connect. Lines: `<token> D|M|U <id> <x> <y>`, `<token> C`, `<token> ping`.
  Uses `InputManagerGlobal.injectInputEvent` (verified present for uid 2000 on Android 17 / SDK 37).
  Cancels any finger left down if the laptop disconnects or goes quiet for 4 s.
- `build.sh` -> `build/gb-touch.jar` (same local toolchain as gbcast_app).
- `tools/appcast_injector/phone_agent.sh` — `start` also launches it (reports `TOUCH_PORT=`); `touch <token> [ip]`
  starts only the injector, leaving the running gesture agent alone.
- `tools/appcast_injector/appcast_session.py` — pushes the jar, finds/starts the injector
  (`ensure_touch_injector`), passes `--phone-touch IP PORT TOKEN` to the relay.
- `tools/appcast_injector/input_relay.py` — keeps ONE persistent connection to the injector; if it is
  unreachable (or dies mid-gesture) the finished gesture is replayed as tap/swipe exactly as before.
- `tools/appcast_injector/feed_live.js` — `handleNativeTouch` streams `touch D|M|U` live. Gestures that START
  in the phone's real system-gesture strips (L/R 90 px, top 100 px, bottom 110 px) keep the old release-time
  Back/Home/Recents/notification mapping. `FEED_OPTS {noLiveTouch:1}` disables it.

Measured 2026-11-02: laptop<->injector RTT over Wi-Fi median 6 ms (p90 7.6, max 16); a live 35-step drag
scrolled the phone's app drawer ~8 rows with natural fling. `input tap` itself was only 60–90 ms; the real
lag was acting on release.
