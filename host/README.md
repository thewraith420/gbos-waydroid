# Host services

Everything GBOS needs on the **host** (the PC that runs Waydroid), as opposed to inside the container.
Installed by `install-host.sh`; see its header for all options.

| Part | Runs as | What it does |
|---|---|---|
| `system/gb-lan.py` | root service `gb-lan` | Puts the container on your LAN (an ipvlan NIC, `eth1`) so a phone on the same network can see it. Phone Hub needs this. |
| `system/wifi-hwsim.sh` | root service `gb-wifi-host` | Creates a virtual Wi-Fi radio (`mac80211_hwsim`) and moves it into the container so Android believes it has Wi-Fi. Phone Hub needs this. |
| `system/gb-bt-bridge.py` | root service `gb-bt-bridge` | Optional. Shares the host's Bluetooth adapter with the container. |
| `phonehub/gb-hub-link.sh` | user service `gb-hub-link` | The Cross-Device link keeper: notices when the link to the phone is down and repairs it. |
| `phonehub/appcast_up.sh` + `injector/` | user service `gb-appcast` | The App Cast supervisor: waits for the container, frida-server and the link, then attaches the cast session and keeps it attached. |
| `injector/audio_relay.py` | user service `gb-audio` | Plays the phone's cast audio on this PC (port 8101). |
| `injector/discover_host.py` | user service `gb-discover` | Lets the phone app find this PC without typing an IP (UDP 8096). |
| `phonehub/gb-link-daemon.py` + `gb_continuity.py` | user service `gb-link-daemon` | Backend for the GBOS home-screen widget, the GNOME Phone Hub extension (port 8092), and Android 17 Task Continuity ("Continue On", bridge port 8095). |
| `gnome-extension/gbos-phonehub@thewraith420` | GNOME Shell user extension | Top-bar link/cast status indicator, searchable phone app launcher grid, and standalone App Cast launch (`install-host.sh gnome-extension`). |
| `phonehub/gb-autostop.sh` | user service `gb-autostop` | Finishes a shutdown: stops the Waydroid session when Android powers itself off. |
| `phonehub/gb-userfixes.sh`, `set-main-user.py` | run by hand, once | First-run provisioning: the settings inside Android's data that Phone Hub needs (see [INSTALL](../docs/INSTALL.md#6-first-boot-the-main-user-then-sign-in)). |

## Requirements

`waydroid` (already set up by the recipe), `adb` ([how to set it up](../docs/TUTORIAL-adb.md)), a JDK for `jdb`
(`default-jdk-headless`), `python3-venv`, `curl`, `xz-utils`. The system part needs root; the Phone Hub part must
**not** be run as root.

## Install

```sh
host/install-host.sh phonehub --work WORKDIR                                            # as your user
host/install-host.sh gnome-extension                                                    # optional: GNOME Shell panel indicator
sudo host/install-host.sh system lan wifi                                              # root; add `bt` if wanted
```

The Phone Hub part copies itself to `~/.local/share/gbos/phonehub`, builds a Python environment with the pinned
`frida` (`requirements.txt`), downloads the pinned `frida-server` and refuses to use it unless its sha256 matches
`sources.lock`, installs the units into `~/.config/systemd/user` and starts them. `--no-start` only enables them.
Remove with `--remove`.

## Configuration

Nothing needs configuring: the container address comes from `waydroid status`, the LAN interface from the default
route, the allowed input peers from the running container. To override, copy `config/gbos.env.example` to
`~/.config/gbos/gbos.env` (the installer writes it with everything commented out).

## State and files

* Locks, pid files, logs and the phone-agent token: `$XDG_RUNTIME_DIR/gbos` (private, mode 700, cleared at logout).
  Nothing is kept in `/tmp`.
* Persistent: `~/.cache/gbos` (last phone address, the phone-agent token with mode 600, the phone's app list,
  the downloaded frida-server).

Handy commands:

```sh
~/.local/share/gbos/phonehub/appcast_up.sh status     # one-screen state of container, frida, link, session
~/.local/share/gbos/phonehub/gb-reconnect.sh          # force a repair of the link and the cast
journalctl --user -u gb-hub-link -u gb-appcast -f     # the services' own logs
```

## Ports

| Port | Where | Purpose |
|---|---|---|
| 8098 | host, LAN | video pushed by the phone app |
| 8099 / 8100 | host, LAN | video / input relays towards the container |
| 8101 | host, LAN | cast audio from the phone |
| 8091 | host, LAN | "re-arm" server the phone app's button talks to |
| 8096/udp | host, LAN | discovery |
| 8092 | Waydroid bridge / host | widget + GNOME extension link daemon |
| 8095 | Waydroid bridge only | Android 17 Task Continuity (`gb_continuity.py`) |

## Trust model (read this before exposing the PC on an untrusted network)

This is built for a home or office LAN, not for a hostile one.

* The video, audio and discovery listeners accept any RFC1918 peer. The audio channel uses a fixed shared token.
* The input relay (the one that can drive your phone) only accepts connections from this PC and the container,
  and the phone agent additionally checks a random per-install token.
* `gb-link-daemon` is unauthenticated and listens on the Waydroid bridge, so **any app inside GBOS** can ask it to
  reconnect or open a cast window. It cannot do anything else.
* `frida-server` runs as root inside the container and is controlled over adb. The container's `adbd` listens on
  every interface (`*:5555`) and requires an authorized key (`ro.adb.secure=1`). `gb-lan.sh` additionally drops
  port 5555 on the LAN-facing NIC (`eth1`), so the phone and other LAN machines cannot reach it. If you stop
  using `gb-lan` or change its interface, keep that rule (or an equivalent firewall rule) in place.

## Status

Checked: all scripts parse; units pass `systemd-analyze verify`; the staged install renders correctly; `status`,
the link daemon and the usage guards were run against a live container. **Not yet run from a clean machine:**
the full `install-host.sh` (venv, pip, frida-server download, `systemctl enable`), because it replaces the services
of a working setup.
