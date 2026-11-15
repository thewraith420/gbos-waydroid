# adb: the one prerequisite you set up yourself

The host services talk to the GBOS container, and (once per phone boot) to your phone, through `adb`.
Nothing else on this page is required reading.

## 1. Install adb on the PC

Debian / Ubuntu: `sudo apt install adb` (older releases call it `android-tools-adb`). Check with `adb version`.

## 2. The container

The recipe makes Android's `adbd` listen on TCP port 5555 and run as root on every boot. Waydroid hands the
container its address by DHCP; find it with `waydroid status` (the `IP address:` line). Then:

```sh
adb connect <that address>:5555
adb devices
```

You want to see `<address>:5555    device`.

* `unauthorized`: the container has not accepted this PC's key yet. Look at the GBOS screen for an
  "Allow USB debugging?" prompt and accept it (tick "always allow"), then run `adb devices` again.
  *(Written from how adb behaves in general; the first-authorization step has not been walked through on a
  clean install yet.)*
* `offline` or no entry: Android has not finished booting, or Waydroid is not running. Wait, or
  `waydroid session start`.
* The address changes after a Waydroid restart. The services follow it automatically; for your own commands run
  `adb connect` again with the new address.

If you use a separate key for the container, point the services at it with `GB_ADB_KEY` in
`~/.config/gbos/gbos.env`.

## 3. The phone

The cast itself needs **no** adb on the phone: the GBOS Cast app on the phone dials your PC. adb on the phone is
used for exactly one thing, to start the small "gesture agent" that lets you click inside the cast window:

1. On the phone: Settings → About phone → tap *Build number* 7 times → Developer options → turn on
   *Wireless debugging* and pair it with the PC (`adb pair`, then `adb connect`).
2. The session starts the agent and then disconnects adb by itself. Touch keeps working until the phone reboots.
   After a phone reboot, repeat the pairing step (or press *Re-arm* in the phone app while wireless debugging is on).

You can switch wireless debugging off again as soon as the agent is running.

## Security notes

* Whoever can reach port 5555 on the container *and* holds an authorized key has root in it. The container's
  `adbd` listens on all of its interfaces and requires key authorization; `gb-lan.sh` also drops port 5555 on the
  LAN-facing NIC so it is not reachable from your network. Keep it that way.
* Whoever holds an authorized adb key for your phone can control it. Turn wireless debugging off when you are
  not using it.
