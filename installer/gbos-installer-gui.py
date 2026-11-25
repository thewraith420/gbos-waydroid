#!/usr/bin/env python3
"""gbos-installer-gui: a graphical front-end for installing GBOS, step by step.

    installer/gbos-installer-gui.py          (run it from a checkout of the repository, as your normal user)

It runs exactly the commands docs/INSTALL.md lists, in the same order, and shows their output. Nothing is hidden: every
command it runs is printed in the output pane first. Steps that need root ask for your password through polkit (pkexec),
once per step. The one destructive step (resetting Waydroid) asks you to type "reset" first, as try.sh itself does.

Needs GTK 4 and libadwaita 1.5 or newer with their Python bindings:
    Debian/Ubuntu: sudo apt install python3-gi gir1.2-gtk-4.0 gir1.2-adw-1
    Fedora:        sudo dnf install python3-gobject gtk4 libadwaita
    Arch:          sudo pacman -S python-gobject gtk4 libadwaita
Settings (folders, choices, finished steps) are kept in ~/.config/gbos/installer.json.
"""
import json
import os
import pwd
import re
import shlex
import sys
from pathlib import Path

try:
    import gi
    gi.require_version("Gtk", "4.0")
    gi.require_version("Adw", "1")
    from gi.repository import Adw, Gdk, Gio, GLib, Gtk, Pango
except (ImportError, ValueError) as e:
    sys.exit("gbos-installer-gui needs GTK 4 and libadwaita with Python bindings (%s).\n"
             "Debian/Ubuntu: sudo apt install python3-gi gir1.2-gtk-4.0 gir1.2-adw-1" % e)

if (Adw.get_major_version(), Adw.get_minor_version()) < (1, 5):
    sys.exit("gbos-installer-gui needs libadwaita 1.5 or newer (found %d.%d). The command-line steps in docs/INSTALL.md "
             "work without it." % (Adw.get_major_version(), Adw.get_minor_version()))

REPO = Path(__file__).resolve().parent.parent
INSTALLER = REPO / "installer" / "gbos-install.py"
DOCTOR = REPO / "installer" / "gbos-doctor.py"
VERSION = (REPO / "VERSION").read_text().strip() if (REPO / "VERSION").exists() else "unknown"
RELEASE_URL = "https://github.com/thewraith420/gbos-waydroid/releases/download/v%s" % VERSION
CONFIG = Path.home() / ".config" / "gbos" / "installer.json"
HOME = Path.home()
PHONEHUB = HOME / ".local" / "share" / "gbos" / "phonehub"
WAYDROID_DATA = HOME / ".local" / "share" / "waydroid" / "data"
SESSION_LOG = HOME / ".cache" / "gbos" / "waydroid-session.log"

# Non-interactive install commands: the GUI has no terminal for a [Y/n] question.
PKG_RUN = {"apt": ["apt-get", "install", "-y"], "dnf": ["dnf", "install", "-y"],
           "pacman": ["pacman", "-S", "--needed", "--noconfirm"], "zypper": ["zypper", "--non-interactive", "install"]}

STATUS_ICON = {"ok": ("object-select-symbolic", "success"), "warn": ("dialog-warning-symbolic", "warning"),
               "fail": ("dialog-error-symbolic", "error"), "info": ("dialog-information-symbolic", "dim-label")}

DEFAULTS = {"downloads": str(HOME / "gbos" / "dl"), "work": str(HOME / "gbos" / "work"),
            "backups": str(HOME / "gbos" / "backups"), "source": "prebuilt", "phone_hub": True, "bluetooth": False,
            "phone_accessibility": True, "phone_addr": "", "addons": str(HOME / "gbos" / "addons"), "kernel_tree": "",
            "done": {}}


def load_config():
    cfg = dict(DEFAULTS)
    try:
        cfg.update(json.loads(CONFIG.read_text()))
    except (OSError, ValueError):
        pass
    return cfg


def save_config(cfg):
    try:
        CONFIG.parent.mkdir(parents=True, exist_ok=True)
        CONFIG.write_text(json.dumps(cfg, indent=2))
    except OSError:
        pass


class Cmd:
    """One command of a step. root=True runs `script` (bash) through pkexec with the caller's identity passed on as
    SUDO_USER/SUDO_UID/SUDO_GID, which is what the recipe scripts expect from sudo. capture=True collects stdout
    (for --json) instead of printing it, and hands it to on_done(stdout, exit_status)."""

    def __init__(self, argv=None, script=None, root=False, args=(), capture=False, ok=(0,), on_done=None, title=None):
        self.argv, self.script, self.root, self.args = argv, script, root, list(args)
        self.capture, self.ok, self.on_done, self.title = capture, ok, on_done, title

    def real_argv(self):
        if not self.root:
            return self.argv if self.argv else ["bash", "-c", self.script, "gbos"] + self.args
        env = ["SUDO_USER=%s" % pwd.getpwuid(os.getuid()).pw_name, "SUDO_UID=%d" % os.getuid(), "SUDO_GID=%d" % os.getgid(),
               "GBOS_YES=1", "PYTHONUNBUFFERED=1", "XDG_RUNTIME_DIR=/run/user/%d" % os.getuid(),
               "WAYLAND_DISPLAY=%s" % os.environ.get("WAYLAND_DISPLAY", "wayland-0"),
               "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"]
        body = self.script if self.script else " ".join(shlex.quote(a) for a in self.argv)
        return ["pkexec", "env"] + env + ["bash", "-c", "set -e\n" + body, "gbos"] + self.args

    def shown(self):
        if self.title:
            return self.title
        if self.argv:
            return ("sudo " if self.root else "") + " ".join(shlex.quote(a) for a in self.argv)
        lines = [l.strip() for l in self.script.strip().splitlines() if l.strip() and not l.strip().startswith("#")]
        return ("sudo: " if self.root else "") + " ; ".join(lines)


class LogView(Gtk.ScrolledWindow):
    """A read-only output pane that understands carriage returns (progress lines overwrite themselves)."""

    def __init__(self):
        super().__init__(min_content_height=240, vexpand=True)
        self.view = Gtk.TextView(editable=False, cursor_visible=False, monospace=True, wrap_mode=Gtk.WrapMode.WORD_CHAR,
                                 top_margin=8, bottom_margin=8, left_margin=10, right_margin=10)
        self.view.add_css_class("gbos-log")
        self.set_child(self.view)
        self.buf = self.view.get_buffer()
        self.buf.create_tag("cmd", weight=Pango.Weight.BOLD)
        self.buf.create_tag("note", style=Pango.Style.ITALIC)
        self.buf.create_tag("bad", foreground="#c01c28", weight=Pango.Weight.BOLD)
        self.line_start = self.buf.create_mark(None, self.buf.get_end_iter(), True)
        self.pending_cr = False

    def clear(self):
        self.buf.set_text("")
        self.buf.move_mark(self.line_start, self.buf.get_end_iter())
        self.pending_cr = False

    def _insert(self, text, tag=None):
        end = self.buf.get_end_iter()
        if tag:
            self.buf.insert_with_tags_by_name(end, text, tag)
        else:
            self.buf.insert(end, text)

    def write(self, text, tag=None):
        for part in re.split(r"(\r\n|\n|\r)", text):
            if part in ("\n", "\r\n"):
                self.pending_cr = False
                self._insert("\n")
                self.buf.move_mark(self.line_start, self.buf.get_end_iter())
            elif part == "\r":
                self.pending_cr = True
            elif part:
                if self.pending_cr:            # a progress update: replace the current line
                    self.buf.delete(self.buf.get_iter_at_mark(self.line_start), self.buf.get_end_iter())
                    self.pending_cr = False
                self._insert(part, tag)
        GLib.idle_add(self._scroll)

    def line(self, text, tag=None):
        if self.buf.get_iter_at_mark(self.line_start).get_offset() != self.buf.get_end_iter().get_offset():
            self.write("\n")
        self.write(text + "\n", tag)

    def _scroll(self):
        adj = self.get_vadjustment()
        adj.set_value(adj.get_upper() - adj.get_page_size())
        return False


class Page:
    """One install step: a description, its options, a run button, a results area and the output pane."""

    def __init__(self, win, key, title, blurb, run_label=None, destructive=False):
        self.win, self.key, self.title = win, key, title
        self.box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=18, margin_top=24, margin_bottom=24,
                           margin_start=16, margin_end=16)
        head = Gtk.Label(label=title, xalign=0, wrap=True)
        head.add_css_class("title-1")
        self.box.append(head)
        self.blurb = Gtk.Label(xalign=0, wrap=True, use_markup=True)
        self.blurb.set_markup(blurb)
        self.blurb.connect("activate-link", lambda _l, uri: (Gtk.UriLauncher.new(uri).launch(self.win, None, None, None), True)[1])
        self.box.append(self.blurb)
        self.options = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        self.box.append(self.options)
        self.buttons = Gtk.Box(spacing=8, halign=Gtk.Align.START)
        self.run_button = None
        if run_label:
            self.run_button = Gtk.Button(label=run_label)
            self.run_button.add_css_class("destructive-action" if destructive else "suggested-action")
            self.run_button.add_css_class("pill")
            self.buttons.append(self.run_button)
        self.box.append(self.buttons)
        self.results = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        self.box.append(self.results)
        self.log = LogView()
        frame = Gtk.Frame(child=self.log)
        self.expander = Gtk.Expander(label="Output", child=frame)
        self.box.append(self.expander)
        clamp = Adw.Clamp(maximum_size=860, child=self.box)
        self.widget = Gtk.ScrolledWindow(child=clamp, hscrollbar_policy=Gtk.PolicyType.NEVER)

    def clear_results(self):
        while (c := self.results.get_first_child()) is not None:
            self.results.remove(c)


def status_icon(status):
    icon_name, css = STATUS_ICON.get(status, STATUS_ICON["info"])
    icon = Gtk.Image.new_from_icon_name(icon_name)
    icon.add_css_class(css)
    return icon


def status_row(title, subtitle, status):
    row = Adw.ActionRow(title=GLib.markup_escape_text(title), subtitle=GLib.markup_escape_text(subtitle or ""))
    row.set_subtitle_selectable(True)
    row.add_prefix(status_icon(status))
    return row


def folder_row(win, title, key):
    row = Adw.ActionRow(title=title, subtitle=win.cfg[key])
    row.set_subtitle_selectable(True)
    btn = Gtk.Button(icon_name="folder-open-symbolic", valign=Gtk.Align.CENTER, tooltip_text="Choose a folder")
    btn.add_css_class("flat")

    def chosen(dlg, res):
        try:
            f = dlg.select_folder_finish(res)
        except GLib.Error:
            return
        win.cfg[key] = f.get_path()
        row.set_subtitle(f.get_path())
        save_config(win.cfg)

    def pick(_b):
        dlg = Gtk.FileDialog(title=title)
        p = Path(win.cfg[key])
        while not p.exists() and p != p.parent:
            p = p.parent
        dlg.set_initial_folder(Gio.File.new_for_path(str(p)))
        dlg.select_folder(win, None, chosen)

    btn.connect("clicked", pick)
    row.add_suffix(btn)
    row.set_activatable_widget(btn)
    return row


def switch_row(win, title, subtitle, key):
    row = Adw.SwitchRow(title=title, subtitle=subtitle, active=bool(win.cfg[key]))
    row.connect("notify::active", lambda r, _p: (win.cfg.__setitem__(key, r.get_active()), save_config(win.cfg)))
    return row


class Window(Adw.ApplicationWindow):
    def __init__(self, app):
        super().__init__(application=app, title="GBOS Installer", default_width=1100, default_height=800)
        self.cfg = load_config()
        self.running = None          # the Gio.Subprocess of the command in progress
        self.queue = []
        self.current_page = None
        self.pages = []

        self.toasts = Adw.ToastOverlay()
        split = Adw.NavigationSplitView(min_sidebar_width=230, max_sidebar_width=280)
        self.toasts.set_child(split)
        self.set_content(self.toasts)

        # sidebar: the steps
        self.steps = Gtk.ListBox(selection_mode=Gtk.SelectionMode.SINGLE)
        self.steps.add_css_class("navigation-sidebar")
        side_tb = Adw.ToolbarView()
        side_head = Adw.HeaderBar()
        side_head.set_title_widget(Adw.WindowTitle(title="GBOS", subtitle="version %s" % VERSION))
        side_tb.add_top_bar(side_head)
        side_tb.set_content(Gtk.ScrolledWindow(child=self.steps, hscrollbar_policy=Gtk.PolicyType.NEVER))
        split.set_sidebar(Adw.NavigationPage(title="Steps", child=side_tb))

        # content
        self.stack = Gtk.Stack(transition_type=Gtk.StackTransitionType.CROSSFADE)
        content_tb = Adw.ToolbarView()
        self.content_head = Adw.HeaderBar()
        self.spinner = Gtk.Spinner()
        self.stop_button = Gtk.Button(label="Stop", sensitive=False, tooltip_text="Stop the command that is running")
        self.stop_button.connect("clicked", self.stop)
        self.content_head.pack_end(self.stop_button)
        self.content_head.pack_end(self.spinner)
        content_tb.add_top_bar(self.content_head)
        content_tb.set_content(self.stack)
        self.content_page = Adw.NavigationPage(title="Install", child=content_tb)
        split.set_content(self.content_page)

        css = Gtk.CssProvider()
        css.load_from_string(".gbos-log { font-size: 9pt; } .success { color: @success_color; } "
                             ".warning { color: @warning_color; } .error { color: @error_color; }")
        add = getattr(Gtk, "style_context_add_provider_for_display", None) or Gtk.StyleContext.add_provider_for_display
        add(Gdk.Display.get_default(), css, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)

        for build in (self.page_check, self.page_download, self.page_prepare, self.page_backup, self.page_build,
                      self.page_bake, self.page_host, self.page_mainuser, self.page_signin, self.page_phonesetup, self.page_phone,
                      self.page_doctor):
            self.add_page(build())

        self.steps.connect("row-selected", self.on_select)
        first = next((i for i, p in enumerate(self.pages) if not self.cfg["done"].get(p.key)), 0)
        self.steps.select_row(self.steps.get_row_at_index(first))
        self.connect("close-request", self.on_close)

    # ---- plumbing ------------------------------------------------------------------------------------------------

    def add_page(self, page):
        n = len(self.pages) + 1
        self.pages.append(page)
        self.stack.add_named(page.widget, page.key)
        box = Gtk.Box(spacing=10, margin_top=6, margin_bottom=6)
        page.side_icon = Gtk.Image()
        page.side_icon.set_size_request(16, 16)
        box.append(Gtk.Label(label="%d." % n, width_chars=3, xalign=1))
        box.append(Gtk.Label(label=page.title, xalign=0, hexpand=True, ellipsize=Pango.EllipsizeMode.END))
        box.append(page.side_icon)
        row = Gtk.ListBoxRow(child=box)
        page.row = row
        self.steps.append(row)
        self.mark(page, self.cfg["done"].get(page.key))

    def mark(self, page, state):
        """state: True/'done' finished, 'failed', 'running', or None."""
        icons = {True: ("object-select-symbolic", "success"), "done": ("object-select-symbolic", "success"),
                 "skipped": ("go-next-symbolic", "dim-label"), "failed": ("dialog-error-symbolic", "error"),
                 "running": ("content-loading-symbolic", "accent")}
        for c in ("success", "error", "accent", "dim-label"):
            page.side_icon.remove_css_class(c)
        if state in icons:
            page.side_icon.set_from_icon_name(icons[state][0])
            page.side_icon.add_css_class(icons[state][1])
        else:
            page.side_icon.clear()
        if state in (True, "done", "skipped"):
            self.cfg["done"][page.key] = state if state != True else "done"
        elif state == "failed":
            self.cfg["done"].pop(page.key, None)
        save_config(self.cfg)

    def on_select(self, _lb, row):
        if row is None:
            return
        page = self.pages[row.get_index()]
        self.current_page = page
        self.stack.set_visible_child_name(page.key)
        self.content_page.set_title(page.title)

    def toast(self, text):
        self.toasts.add_toast(Adw.Toast(title=text, timeout=4))

    def next_step(self, page):
        i = self.pages.index(page)
        if i + 1 < len(self.pages):
            self.steps.select_row(self.steps.get_row_at_index(i + 1))

    def set_busy(self, busy):
        self.spinner.set_spinning(busy)
        for p in self.pages:
            if p.run_button:
                p.run_button.set_sensitive(not busy)

    def run(self, page, cmds, then=None, mark=True):
        """Run the commands one after another, printing each first. Stops at the first failure."""
        if self.running is not None:
            self.toast("Another step is still running")
            return
        page.log.clear()
        page.expander.set_expanded(True)
        self.set_busy(True)
        self.mark(page, "running")
        self.queue = list(cmds)
        self.job_page, self.job_then, self.job_mark = page, then, mark
        self._next()

    def _next(self):
        page = self.job_page
        if not self.queue:
            self._finish(True)
            return
        cmd = self.queue.pop(0)
        self.job_cmd = cmd
        page.log.line("$ " + cmd.shown(), "cmd")
        if cmd.root:
            page.log.line("(asking for your password: this part runs as root)", "note")
        launcher = Gio.SubprocessLauncher.new(Gio.SubprocessFlags.STDOUT_PIPE |
                                              (Gio.SubprocessFlags.STDERR_PIPE if cmd.capture else Gio.SubprocessFlags.STDERR_MERGE))
        launcher.setenv("PYTHONUNBUFFERED", "1", True)
        try:
            proc = launcher.spawnv(cmd.real_argv())
        except GLib.Error as e:
            page.log.line("could not start: %s" % e.message, "bad")
            self._finish(False)
            return
        self.running = proc
        self.stop_button.set_sensitive(not cmd.root)    # a root process cannot be signalled from here
        if cmd.capture:
            proc.communicate_utf8_async(None, None, self._captured, cmd)
        else:
            proc.get_stdout_pipe().read_bytes_async(16384, GLib.PRIORITY_DEFAULT, None, self._read, cmd)

    def _read(self, stream, res, cmd):
        try:
            data = stream.read_bytes_finish(res).get_data()
        except GLib.Error:
            data = b""
        if data:
            self.job_page.log.write(data.decode("utf-8", "replace"))
            stream.read_bytes_async(16384, GLib.PRIORITY_DEFAULT, None, self._read, cmd)
        else:
            self.running.wait_async(None, self._exited, cmd)

    def _captured(self, proc, res, cmd):
        try:
            _ok, out, err = proc.communicate_utf8_finish(res)
        except GLib.Error as e:
            out, err = "", e.message
        if err:
            self.job_page.log.write(err)
        self._done(proc, cmd, out or "")

    def _exited(self, proc, res, cmd):
        try:
            proc.wait_finish(res)
        except GLib.Error:
            pass
        self._done(proc, cmd, None)

    def _done(self, proc, cmd, out):
        page = self.job_page
        self.running = None
        self.stop_button.set_sensitive(False)
        rc = proc.get_exit_status() if proc.get_if_exited() else -1
        if cmd.on_done:
            try:
                cmd.on_done(out, rc)
            except Exception as e:                          # a display problem must not wedge the runner
                page.log.line("could not show the result: %s" % e, "bad")
        if rc in cmd.ok:
            self._next()
            return
        if cmd.root and rc in (126, 127):
            page.log.line("not authorised (password dialog closed or refused): nothing was run", "bad")
        else:
            page.log.line("failed (exit status %d). The output above says why; docs/TROUBLESHOOTING.md has the usual causes." % rc, "bad")
        self._finish(False)

    def _finish(self, ok):
        page, then = self.job_page, self.job_then
        self.queue = []
        self.set_busy(False)
        if ok:
            page.log.line("done.", "note")
            if self.job_mark:
                self.mark(page, True)
            elif page.side_icon.has_css_class("accent"):     # still showing "running": back to its saved state
                self.mark(page, self.cfg["done"].get(page.key))
            if then:
                then()
        elif self.job_mark:
            self.mark(page, "failed")
        else:                                   # a check-only run must not change the step's state
            self.mark(page, self.cfg["done"].get(page.key))

    def stop(self, _b):
        if self.running is not None and not self.job_cmd.root:
            self.queue = []
            self.running.send_signal(15)

    def on_close(self, _w):
        if self.running is None:
            return False
        d = Adw.AlertDialog(heading="A step is still running",
                            body="Closing the installer stops it. Downloads resume where they stopped; other steps can be run again.")
        d.add_response("cancel", "Keep running")
        d.add_response("quit", "Quit anyway")
        d.set_response_appearance("quit", Adw.ResponseAppearance.DESTRUCTIVE)

        def answered(dlg, res):
            if dlg.choose_finish(res) == "quit":
                if self.running is not None and not self.job_cmd.root:
                    self.running.send_signal(15)
                self.running = None
                self.destroy()
        d.choose(self, None, answered)
        return True

    # ---- shared command pieces -----------------------------------------------------------------------------------

    @property
    def W(self):
        return self.cfg["work"]

    @property
    def D(self):
        return self.cfg["downloads"]

    def session_stop(self):
        return Cmd(script="waydroid session stop >/dev/null 2>&1 || true", title="waydroid session stop")

    def session_start(self):
        return Cmd(script='mkdir -p "$(dirname "$1")"; setsid -f waydroid session start >"$1" 2>&1 </dev/null; '
                          'echo "Waydroid is starting (its log: $1). The first boot after a change is slow."',
                   args=[str(SESSION_LOG)], title="waydroid session start")

    def need_work(self, page, *files):
        missing = [f for f in files if not (Path(self.W) / f).exists()]
        if missing:
            page.log.clear()
            page.expander.set_expanded(True)
            page.log.line("The work folder %s has no %s. Run the Prepare step first (or choose the right work folder "
                          "on the Check page)." % (self.W, ", ".join(missing)), "bad")
            return False
        return True

    def copy(self, text):
        self.get_clipboard().set_content(Gdk.ContentProvider.new_for_bytes("text/plain;charset=utf-8",
                                                                           GLib.Bytes.new(text.encode())))
        self.toast("Copied")

    # ---- the steps -----------------------------------------------------------------------------------------------

    def page_check(self):
        p = Page(self, "check", "Check this machine",
                 "Looks for the tools, kernel options, CPU features, disk space and session type GBOS needs, and "
                 "tells you exactly what to install. It changes nothing.\n\n"
                 "<b>Waydroid must already be installed and working</b> (binder included): GBOS builds on it and does "
                 "not install it. The two folders below need about 25 GB between them, on disk (not in RAM).",
                 "Run the check")
        g = Adw.PreferencesGroup(title="Folders")
        g.add(folder_row(self, "Download folder", "downloads"))
        g.add(folder_row(self, "Work folder", "work"))
        p.options.append(g)

        def show(out, rc):
            p.clear_results()
            try:
                d = json.loads(out)
            except ValueError:
                p.log.line("the check printed no result (exit status %d)" % rc, "bad")
                return
            ready = d.get("result") == "ready"
            banner = Adw.PreferencesGroup()
            addons = bool(d.get("addons_needed"))
            banner.add(status_row(("Ready, once the kernel add-ons below are in" if addons else "Ready to install") if ready
                                  else "%d problem(s) to fix first" % d.get("problems", 0),
                                  "kernel %s, package manager %s" % (d.get("kernel"), d.get("package_manager") or "unknown"),
                                  ("warn" if addons else "ok") if ready else "fail"))
            p.results.append(banner)
            pkgs = d["missing_packages"]["build"] + d["missing_packages"]["phonehub"]
            if pkgs:
                g = Adw.PreferencesGroup(title="Missing packages",
                                         description="Waydroid itself is never in this list: it is a prerequisite.")
                row = Adw.ActionRow(title=GLib.markup_escape_text(d.get("install_command") or " ".join(pkgs)))
                row.set_title_selectable(True)
                cp = Gtk.Button(label="Copy", valign=Gtk.Align.CENTER)
                cp.connect("clicked", lambda _b: self.copy(d.get("install_command") or " ".join(pkgs)))
                row.add_suffix(cp)
                pm = d.get("package_manager")
                if pm in PKG_RUN:
                    inst = Gtk.Button(label="Install", valign=Gtk.Align.CENTER)
                    inst.add_css_class("suggested-action")
                    inst.connect("clicked", lambda _b: self.run(p, [Cmd(argv=PKG_RUN[pm] + pkgs, root=True)],
                                                               then=lambda: p.run_button.emit("clicked")))
                    row.add_suffix(inst)
                g.add(row)
                p.results.append(g)
            if d.get("addons_needed"):
                p.results.append(self.addons_group(p, d))
            sections = {}
            for c in d["checks"]:
                sections.setdefault(c["section"], []).append(c)
            g = Adw.PreferencesGroup(title="Details")
            for sec, items in sections.items():
                bad = [c for c in items if c["status"] in ("fail", "warn")]
                ex = Adw.ExpanderRow(title=sec.capitalize(),
                                     subtitle=("%d to look at" % len(bad)) if bad else "%d fine" % len(items),
                                     expanded=bool(bad))
                for c in items:
                    ex.add_row(status_row(c["name"], c.get("detail", ""), c["status"]))
                g.add(ex)
            p.results.append(g)
            self.mark(p, True if ready and not addons else "failed")
            p.expander.set_expanded(not ready and not pkgs)

        def go(_b):
            self.run(p, [Cmd(argv=[sys.executable, str(INSTALLER), "check", "--json", "--downloads", self.D,
                                   "--work", self.W], capture=True, ok=(0, 1, 2), on_done=show)],
                     mark=False)
        p.run_button.connect("clicked", go)
        return p

    def addons_group(self, p, d):
        """Kernel add-ons, shown on the Check page only when this machine needs some: install the running kernel's
        headers if they are missing, then build each add-on as you and install it as root. Re-runs the check after."""
        kb = d.get("kernel_build") or {}
        pm = d.get("package_manager")
        needed = d["addons_needed"]
        g = Adw.PreferencesGroup(title="Kernel add-ons needed",
                                 description="Small loadable modules that add what this kernel lacks, with no kernel rebuild. "
                                             "They are built here against your running kernel's build files.")
        for a in needed:
            g.add(status_row(a["key"], a["why"] + (" (cannot be added as a module: %s)" % a["cannot_build"] if a.get("cannot_build") else ""),
                             "fail"))
        if kb.get("load_blocker"):
            g.add(status_row("This kernel would refuse them",
                             "%s. The add-ons are unsigned modules. kernel/README.md, \"Secure Boot\", explains the choices."
                             % (kb["load_blocker"][0].upper() + kb["load_blocker"][1:]), "fail"))
            return g
        own = self.cfg.get("kernel_tree") or ""
        tree = own or kb.get("tree")
        if not tree:
            if kb.get("headers_packages") and pm in PKG_RUN:
                row = Adw.ActionRow(title="Your kernel's build files (headers) are not installed",
                                    subtitle=GLib.markup_escape_text("Installs " + " ".join(kb["headers_packages"]) +
                                                                     ". The second keeps them up to date with kernel updates."
                                                                     if len(kb["headers_packages"]) > 1 else
                                                                     "Installs " + kb["headers_packages"][0] + "."))
                row.add_prefix(status_icon("warn"))
                inst = Gtk.Button(label="Install headers", valign=Gtk.Align.CENTER)
                inst.add_css_class("suggested-action")
                inst.connect("clicked", lambda _b: self.run(p, [Cmd(argv=PKG_RUN[pm] + kb["headers_packages"], root=True)],
                                                           then=lambda: p.run_button.emit("clicked"), mark=False))
                row.add_suffix(inst)
                g.add(row)
                if kb.get("headers_note"):
                    g.add(status_row("Note", kb["headers_note"], "warn"))
                return g
            if kb.get("distro_kernel"):
                g.add(status_row("Your kernel's build files are not installed",
                                 kb.get("headers_note") or "No headers package was found for it.", "fail"))
                return g
            g.add(status_row("No package owns this kernel (self-built?)",
                             "Choose the folder it was built in: it has .config and Module.symvers.", "warn"))
        g.add(self.tree_row(kb.get("tree")))
        if not tree:
            return g
        todo = [a for a in needed if not a.get("cannot_build")]
        if any(a["key"] == "netfilter" for a in todo) and not (kb.get("tree_full") or own):
            g.add(status_row("netfilter needs the full source tree",
                             "It rebuilds x_tables from source, which headers do not include. Choose the folder the kernel "
                             "was built in to add it.", "warn"))
            todo = [a for a in todo if a["key"] != "netfilter"]
        if not todo:
            return g
        out = Path(self.cfg.get("addons") or HOME / "gbos" / "addons")
        names = ", ".join(a["key"] for a in todo)
        row = Adw.ActionRow(title=GLib.markup_escape_text("Build and install " + names),
                            subtitle=GLib.markup_escape_text("Builds as you into %s, checks each module against the running "
                                                             "kernel, then installs it and loads it at every boot (asks for "
                                                             "your password)%s." % (out, "; netfilter needs a reboot afterwards"
                                                             if any(a["key"] == "netfilter" for a in todo) else "")))
        btn = Gtk.Button(label="Build and install", valign=Gtk.Align.CENTER)
        btn.add_css_class("suggested-action")

        def go(_b):
            cmds = [Cmd(argv=[sys.executable, str(REPO / a["script"]), "build", "--tree", tree, "--out", str(out / a["key"])])
                    for a in todo]
            cmds.append(Cmd(root=True, script="\n".join("%s install --out %s" % (shlex.quote(str(REPO / a["script"])),
                                                                                    shlex.quote(str(out / a["key"]))) for a in todo)))
            reboot = any(a["key"] == "netfilter" for a in todo)

            def after():
                if reboot:
                    self.toast("Installed. Reboot before starting Waydroid: netfilter replaces modules in use")
                p.run_button.emit("clicked")
            self.run(p, cmds, then=after, mark=False)
        btn.connect("clicked", go)
        row.add_suffix(btn)
        g.add(row)
        return g

    def tree_row(self, found):
        """The kernel build folder: found automatically (/lib/modules/RELEASE/build), or chosen for a self-built kernel."""
        row = Adw.ActionRow(title="Kernel build folder",
                            subtitle=GLib.markup_escape_text(self.cfg.get("kernel_tree") or
                                                             ("%s (found automatically)" % found if found else "not found")))
        btn = Gtk.Button(icon_name="folder-open-symbolic", valign=Gtk.Align.CENTER, tooltip_text="Choose the folder your kernel was built in")
        btn.add_css_class("flat")

        def chosen(dlg, res):
            try:
                f = dlg.select_folder_finish(res)
            except GLib.Error:
                return
            self.cfg["kernel_tree"] = f.get_path()
            save_config(self.cfg)
            self.pages[0].run_button.emit("clicked")

        btn.connect("clicked", lambda _b: Gtk.FileDialog(title="Kernel build folder").select_folder(self, None, chosen))
        row.add_suffix(btn)
        if self.cfg.get("kernel_tree"):
            reset = Gtk.Button(icon_name="edit-undo-symbolic", valign=Gtk.Align.CENTER, tooltip_text="Find it automatically again")
            reset.add_css_class("flat")
            reset.connect("clicked", lambda _b: (self.cfg.pop("kernel_tree", None), save_config(self.cfg),
                                                 self.pages[0].run_button.emit("clicked")))
            row.add_suffix(reset)
        return row

    def page_download(self):
        p = Page(self, "download", "Download",
                 "Downloads about 10 GB from the original publishers: the Googlebook recovery image from Google, the "
                 "Android 16 donor and vendor images from the WayDroid-ATV project, an upstream Chromium WebView and the "
                 "Android build toolchain. Every file is checked against <tt>sources.lock</tt>; a file that does not match "
                 "is deleted. You can stop and start again: downloads resume.",
                 "Download")
        g = Adw.PreferencesGroup(title="Two small Android programs",
                                 description="input-bridge and vpd-stub run inside Android. Either is the same bytes: the "
                                             "NDK build is reproducible and the downloads are checked against "
                                             "prebuilt.sha256.")
        pre = Adw.ActionRow(title="Download the prebuilt ones from the release", subtitle="small, recommended")
        ndk = Adw.ActionRow(title="Build them yourself with the Android NDK", subtitle="adds a 660 MB download")
        r1 = Gtk.CheckButton(active=self.cfg["source"] == "prebuilt")
        r2 = Gtk.CheckButton(group=r1, active=self.cfg["source"] == "ndk")
        for row, radio, val in ((pre, r1, "prebuilt"), (ndk, r2, "ndk")):
            row.add_prefix(radio)
            row.set_activatable_widget(radio)
            radio.connect("toggled", lambda b, v=val: b.get_active() and (self.cfg.__setitem__("source", v), save_config(self.cfg)))
            g.add(row)
        p.options.append(g)

        def go(_b):
            cmds = [Cmd(argv=[sys.executable, str(INSTALLER), "fetch", "--downloads", self.D] +
                    (["--with-ndk"] if self.cfg["source"] == "ndk" else []))]
            if self.cfg["source"] == "prebuilt":
                cmds.append(Cmd(argv=[sys.executable, str(INSTALLER), "fetch-prebuilt", "--url", RELEASE_URL,
                                      "--dest", str(Path(self.D) / "prebuilt")]))
            self.run(p, cmds, then=lambda: self.next_step(p))
        p.run_button.connect("clicked", go)
        return p

    def page_prepare(self):
        p = Page(self, "prepare", "Prepare",
                 "Extracts the Googlebook partitions, derives the patched libraries, builds the native helpers and the "
                 "Android apps, and assembles the work folder (about a minute once the files are local). Safe to run "
                 "again: finished stages are skipped. It changes nothing outside the two folders.\n\n"
                 "It creates the signing key <tt>svc_stub_apk/stub.p12</tt> in the work folder: <b>keep it</b>, apps you "
                 "rebuild later must be signed with the same key.",
                 "Prepare")
        g = Adw.PreferencesGroup()
        g.add(switch_row(self, "Phone Hub", "Link GBOS to your Android phone: notifications, App Cast, files, and the "
                                            "desktop widget", "phone_hub"))
        p.options.append(g)

        def go(_b):
            argv = [sys.executable, str(INSTALLER), "prepare", "--downloads", self.D, "--work", self.W]
            argv += ["--prebuilt", str(Path(self.D) / "prebuilt")] if self.cfg["source"] == "prebuilt" else ["--ndk", "auto"]
            if self.cfg["phone_hub"]:
                argv.append("--phone-hub")
            self.run(p, [Cmd(argv=argv)], then=lambda: self.next_step(p))
        p.run_button.connect("clicked", go)
        return p

    def page_backup(self):
        p = Page(self, "backup", "Back up (optional)",
                 "The next step <b>resets Waydroid</b>. If Waydroid holds anything you want to keep (accounts, apps, "
                 "your own Android data), save it here first. This stops Waydroid, saves its overlay and data, and then "
                 "verifies the backup. Restore later with <tt>gbos-backup.py restore FOLDER</tt> (Waydroid stopped).\n\n"
                 "The small part (container config, properties, host rules) is saved by the next step automatically, "
                 "and the old Android data is moved aside rather than deleted.",
                 "Back up")
        g = Adw.PreferencesGroup()
        g.add(folder_row(self, "Backup folder (on disk, with room for your Waydroid data)", "backups"))
        p.options.append(g)
        skip = Gtk.Button(label="Nothing to keep: skip")
        skip.add_css_class("pill")
        skip.connect("clicked", lambda _b: (self.mark(p, "skipped"), self.next_step(p)))
        p.buttons.append(skip)

        def go(_b):
            if not self.need_work(p, "gbos-backup.py"):
                return
            self.run(p, [self.session_stop(), Cmd(root=True, args=[self.W, self.cfg["backups"]], script=
                         'waydroid container stop >/dev/null 2>&1 || true\n'
                         'mkdir -p "$2"\n'
                         '"$1/gbos-backup.py" backup --out "$2" --with overlay,data --user "$SUDO_USER"\n'
                         'last=$(ls -dt "$2"/gbos-backup-* | head -1)\n'
                         '"$1/gbos-backup.py" verify "$last"\n'
                         'chown -R "$SUDO_UID:$SUDO_GID" "$2" 2>/dev/null || true',
                         title="sudo gbos-backup.py backup --out %s --with overlay,data, then verify" % self.cfg["backups"])],
                     then=lambda: self.next_step(p))
        p.run_button.connect("clicked", go)
        return p

    def page_build(self):
        p = Page(self, "build", "Build and reset Waydroid",
                 "<b>This is the destructive step.</b> It builds <tt>system.img</tt> from the Googlebook partitions, "
                 "patches a copy of the vendor image and copies both to <tt>/etc/waydroid-extra/images/</tt>, then runs "
                 "<tt>waydroid init -f</tt>, which deletes Waydroid's writable overlay. Your existing Android data is "
                 "<b>moved aside</b> (renamed to <tt>data.before-gbos-TIME</tt>), not deleted.\n\n"
                 "It also edits the container config and properties in <tt>/var/lib/waydroid</tt>, installs a udev rule "
                 "and a modules-load file for the legacy iptables modules, then starts Waydroid and waits about three "
                 "minutes for Android to boot. Everything is logged in the work folder.",
                 "Build and reset Waydroid…", destructive=True)

        def go(_b):
            if not self.need_work(p, "try.sh", "system_a.img"):
                return
            entry = Gtk.Entry(placeholder_text='type "reset"')
            d = Adw.AlertDialog(heading="Reset Waydroid?",
                                body="Waydroid's overlay is deleted and its Android data is moved aside. Accounts and apps "
                                     "in the current Waydroid will be gone from it. Type reset to go on.")
            d.set_extra_child(entry)
            d.add_response("cancel", "Cancel")
            d.add_response("reset", "Reset and build")
            d.set_response_appearance("reset", Adw.ResponseAppearance.DESTRUCTIVE)
            d.set_response_enabled("reset", False)
            d.set_close_response("cancel")
            entry.connect("changed", lambda e: d.set_response_enabled("reset", e.get_text().strip() == "reset"))

            def answered(dlg, res):
                if dlg.choose_finish(res) != "reset":
                    return
                self.run(p, [self.session_stop(),
                             Cmd(root=True, args=[self.W], script='cd "$1"\n./try.sh', title="sudo ./try.sh  (in %s)" % self.W)],
                         then=lambda: self.next_step(p))
            d.choose(self, None, answered)
        p.run_button.connect("clicked", go)
        return p

    def page_bake(self):
        p = Page(self, "bake", "Bake the overlay",
                 "Writes everything that stays (the WebView, the stub services, the boot scripts, the apps, the patched "
                 "libraries and the Phone Hub widget) into Waydroid's lower overlay. It never touches your data, and it "
                 "is also how updates are applied later. Waydroid is stopped for it and started again afterwards.\n\n"
                 "<b>The first boot afterwards is slow</b> (it scans a large WebView); give it several minutes. "
                 "Undo with <tt>sudo ./bake-apps.sh --remove</tt>.",
                 "Bake")

        def go(_b):
            if not self.need_work(p, "bake-apps.sh"):
                return
            self.run(p, [self.session_stop(),
                         Cmd(root=True, args=[self.W], script='waydroid container stop >/dev/null 2>&1 || true\n'
                             'cd "$1"\n./bake-apps.sh', title="sudo ./bake-apps.sh  (in %s)" % self.W),
                         self.session_start()],
                     then=lambda: self.next_step(p))
        p.run_button.connect("clicked", go)
        return p

    def page_host(self):
        p = Page(self, "host", "Host services",
                 "The Wi-Fi and LAN services make Android believe it is on your network (Phone Hub needs both). With "
                 "Phone Hub on, its user services are installed too, from the work folder. Waydroid is restarted "
                 "afterwards. Details, ports and the trust model: <tt>host/README.md</tt>.",
                 "Install host services")
        g = Adw.PreferencesGroup()
        g.add(switch_row(self, "Share the Bluetooth adapter", "Gives Android the host's Bluetooth (real devices, "
                                                               "and Phone Hub's nearby discovery)", "bluetooth"))
        p.options.append(g)

        def go(_b):
            parts = ["system", "lan", "wifi"] + (["bt"] if self.cfg["bluetooth"] else [])
            cmds = [self.session_stop(),
                    Cmd(root=True, args=[str(REPO / "host" / "install-host.sh")] + parts,
                        script='"$1" "${@:2}"\nwaydroid container restart',
                        title="sudo host/install-host.sh %s ; sudo waydroid container restart" % " ".join(parts))]
            if self.cfg["phone_hub"]:
                cmds.append(Cmd(argv=[str(REPO / "host" / "install-host.sh"), "phonehub", "--work", self.W]))
            cmds.append(self.session_start())
            self.run(p, cmds, then=lambda: self.next_step(p))
        p.run_button.connect("clicked", go)
        return p

    def page_mainuser(self):
        p = Page(self, "mainuser", "Main user",
                 "Android creates the real user (user 10) on the first boot after baking. Google services expect that "
                 "user to be the device's <b>main user</b>, and the flag can only be set while Waydroid is stopped. This "
                 "stops Waydroid, sets it (keeping a backup of the file), and starts Waydroid again.\n\n"
                 "Afterwards GBOS shows a <b>user picker with one user, \"Owner\"</b>: click it to log in.",
                 "Set the main user")
        users_xml = WAYDROID_DATA / "system" / "users" / "10.xml"
        state = Gtk.Label(xalign=0, wrap=True)
        state.add_css_class("dim-label")
        p.options.append(state)

        def refresh(*_a):
            state.set_label(("User 10 exists: ready." if users_xml.exists() else
                             "User 10 does not exist yet. Let GBOS finish its first boot after the Bake step "
                             "(the desktop appears), then come back.") + "  (%s)" % users_xml)
        refresh()
        p.widget.connect("map", refresh)

        def go(_b):
            tool = str(REPO / "host" / "phonehub" / "set-main-user.py")
            self.run(p, [self.session_stop(),
                         Cmd(root=True, args=[tool, str(users_xml)], script=
                             'waydroid container stop >/dev/null 2>&1 || true\n'
                             '[ -f "$2" ] || { echo "no $2 yet: let GBOS finish its first boot after baking"; exit 1; }\n'
                             'python3 "$1" "$2"\n'
                             'python3 "$1" "$2" --apply\n'
                             '[ -f "$2.bak-before-main" ] && chown --reference="$2" "$2.bak-before-main" || true\n'
                             'waydroid container start >/dev/null 2>&1 || true',
                             title="sudo set-main-user.py %s --apply" % users_xml),
                         self.session_start()],
                     then=lambda: self.next_step(p))
        p.run_button.connect("clicked", go)
        return p

    def page_signin(self):
        p = Page(self, "signin", "Sign in",
                 "In GBOS, sign in with the Google account <b>you use on your phone</b> (Phone Hub only links a phone "
                 "signed in to the same account). Either sign in in the \"Android Setup\" window that may open, or use "
                 "the <b>\"Finish Googlebook setup\"</b> notification; Settings → Add account works too. Only the sign-in "
                 "part of the setup wizard runs here; the rest is disabled on purpose (it needs a security chip this "
                 "machine does not have).\n\n"
                 "Sign-in pages can be slow: allow up to a minute per page.\n\n"
                 "With Phone Hub on, apply its settings once you are signed in: location for the system user (so apps "
                 "can see the Wi-Fi name), the cross-device app's local-network permission, Play services flags, App "
                 "Cast and Quick Access, and memory-killer tuning. It backs up what it changes to "
                 "<tt>~/.cache/gbos/userfixes/</tt> and can be run again any time.",
                 "Apply Phone Hub settings")
        done = Gtk.Button(label="I've signed in (no Phone Hub)")
        done.add_css_class("pill")
        done.connect("clicked", lambda _b: (self.mark(p, True), self.next_step(p)))
        p.buttons.append(done)

        def go(_b):
            tool = PHONEHUB / "gb-userfixes.sh"
            if not tool.exists():
                p.log.clear()
                p.expander.set_expanded(True)
                p.log.line("%s is not installed: run the Host services step with Phone Hub on first." % tool, "bad")
                return
            self.run(p, [Cmd(argv=[str(tool)])], then=lambda: self.next_step(p))
        p.run_button.connect("clicked", go)
        return p

    def page_phonesetup(self):
        p = Page(self, "phonesetup", "Set up your phone",
                 "Installs the <b>GBOS Cast</b> companion app on your Android phone (Android 14 or newer) and sets it "
                 "up over <b>wireless debugging</b>, in one go.\n\n"
                 "<b>First, on the phone:</b> Settings → About phone → tap <i>Build number</i> seven times to unlock "
                 "Developer options, then Developer options → <b>Wireless debugging</b> on (phone and PC on the same "
                 "Wi-Fi). Copy its <i>IP address &amp; Port</i> below. <b>The first time</b> this PC talks to the phone, "
                 "tap <i>Pair device with pairing code</i> on that screen and copy the pairing IP:port and the 6-digit "
                 "code too (both change every time that dialog opens). A USB cable with USB debugging works as well: "
                 "leave the fields empty.\n\n"
                 "<b>Set up phone</b> then installs the app (built by Prepare, signed with your own key), grants the "
                 "permissions it needs (changing developer settings for the Re-arm button, recording the phone's "
                 "sound, notifications), allows its notification access so a phone notification tapped on GBOS opens on "
                 "the phone, optionally turns on its accessibility service for touch control, and opens it. Every "
                 "change is read back. Nothing else on the phone is touched; uninstalling GBOS Cast undoes all of it. "
                 "<b>Check phone</b> only reports.",
                 "Set up phone")
        g = Adw.PreferencesGroup(title="Phone")
        addr = Adw.EntryRow(title="Wireless debugging IP address &amp; Port (e.g. 192.168.1.20:41234)")
        addr.set_text(self.cfg.get("phone_addr", ""))
        addr.connect("changed", lambda r: (self.cfg.__setitem__("phone_addr", r.get_text().strip()), save_config(self.cfg)))
        g.add(addr)
        pair_addr = Adw.EntryRow(title="First time only: pairing IP address &amp; Port")
        pair_code = Adw.EntryRow(title="First time only: 6-digit pairing code")
        g.add(pair_addr)
        g.add(pair_code)
        g.add(switch_row(self, "Touch control through accessibility",
                         "Optional. The app's touch agent does the same job, and Android's Advanced Protection blocks "
                         "this for apps from outside the Play Store", "phone_accessibility"))
        p.options.append(g)
        tool = REPO / "host" / "phonehub" / "gb-phone-setup.sh"

        def argv(check):
            a = [str(tool)]
            if self.cfg.get("phone_addr"):
                a += ["--connect", self.cfg["phone_addr"]]
            pa, pc = pair_addr.get_text().strip(), pair_code.get_text().strip()
            if pa and pc:
                a += ["--pair", pa, "--code", pc]
            if check:
                return a + ["--check"]
            a += ["--work", self.W]
            if not self.cfg["phone_accessibility"]:
                a.append("--no-accessibility")
            return a
        chk = Gtk.Button(label="Check phone")
        chk.add_css_class("pill")
        chk.connect("clicked", lambda _b: self.run(p, [Cmd(argv=argv(True))], mark=False))
        p.buttons.append(chk)

        def go(_b):
            if not self.need_work(p, "gbcast_app/build/gbcast.apk"):
                return
            self.run(p, [Cmd(argv=argv(False))], then=lambda: self.next_step(p))
        p.run_button.connect("clicked", go)
        return p

    def page_phone(self):
        p = Page(self, "phone", "Pair your phone",
                 "With the phone set up (previous step), finish at the phone and in GBOS:\n\n"
                 "1. In <b>GBOS Cast</b> on the phone, press <b>Re-arm touch forwarding</b> once (it starts the touch "
                 "agent; needed again only after the phone reboots). Wireless debugging can be switched off "
                 "afterwards: Re-arm turns it on by itself for a few seconds when it needs it.\n"
                 "2. On the phone, turn on <b>Cross-device services</b> for the account GBOS is signed in with.\n"
                 "3. In GBOS, open the Cross-device services popup and scan its QR code with the phone.\n"
                 "4. Start casting from the phone app (it finds your PC on its own), or open App Cast in GBOS.\n"
                 "5. Optional: put the <b>Phone Hub widget</b> on the GBOS desktop (right-click the desktop, Widgets, "
                 "\"Phone Hub\"). It shows whether the link is up and has a Reconnect button.\n\n"
                 "The button below shows the state of the Phone Hub services.",
                 "Show Phone Hub status")
        done = Gtk.Button(label="Paired")
        done.add_css_class("pill")
        done.connect("clicked", lambda _b: (self.mark(p, True), self.next_step(p)))
        p.buttons.append(done)

        def go(_b):
            tool = PHONEHUB / "appcast_up.sh"
            if not tool.exists():
                p.log.clear()
                p.expander.set_expanded(True)
                p.log.line("%s is not installed: run the Host services step with Phone Hub on first." % tool, "bad")
                return
            self.run(p, [Cmd(argv=[str(tool), "status"], ok=tuple(range(256)))], mark=False)
        p.run_button.connect("clicked", go)
        return p

    def page_doctor(self):
        p = Page(self, "doctor", "Health check",
                 "A read-only look at the whole install: the host, Waydroid, the baked overlay, the host services and, "
                 "if it is running, Android itself. Everything it flags comes with the command that fixes it. Run it "
                 "any time something seems off, and after updates.",
                 "Run the health check")
        start = Gtk.Button(label="Start GBOS")
        start.add_css_class("pill")
        start.connect("clicked", lambda _b: self.run(p, [self.session_start(), Cmd(
            script="setsid -f waydroid show-full-ui >/dev/null 2>&1 </dev/null", title="waydroid show-full-ui")], mark=False))
        p.buttons.append(start)
        level_map = {"ok": "ok", "warn": "warn", "fail": "fail"}

        def show(out, rc):
            p.clear_results()
            try:
                items = json.loads(out)
            except ValueError:
                p.log.line("the health check printed no result (exit status %d)" % rc, "bad")
                return
            broken = [i for i in items if i["level"] == "fail"]
            warns = [i for i in items if i["level"] == "warn"]
            g = Adw.PreferencesGroup()
            g.add(status_row("Nothing broken" if not broken else "%d broken" % len(broken),
                             "%d warning(s), %d fine" % (len(warns), len(items) - len(broken) - len(warns)),
                             "fail" if broken else "warn" if warns else "ok"))
            p.results.append(g)
            areas = {}
            for i in items:
                areas.setdefault(i["area"], []).append(i)
            g = Adw.PreferencesGroup(title="Details")
            for area, its in areas.items():
                bad = [i for i in its if i["level"] in ("fail", "warn")]
                ex = Adw.ExpanderRow(title=area.capitalize(),
                                     subtitle=("%d to look at" % len(bad)) if bad else "%d fine" % len(its),
                                     expanded=bool(bad))
                for i in its:
                    ex.add_row(status_row(i["text"], i.get("hint", ""), level_map.get(i["level"], "info")))
                g.add(ex)
            p.results.append(g)
            p.expander.set_expanded(False)

        def go(_b):
            self.run(p, [Cmd(argv=[sys.executable, str(DOCTOR), "--json"], capture=True, ok=(0, 1, 2), on_done=show)])
        p.run_button.connect("clicked", go)
        return p


class App(Adw.Application):
    def __init__(self):
        super().__init__(application_id="io.github.thewraith420.GbosInstaller", flags=Gio.ApplicationFlags.DEFAULT_FLAGS)

    def do_activate(self):
        win = self.props.active_window or Window(self)
        win.present()


if __name__ == "__main__":
    if os.geteuid() == 0:
        sys.exit("run the installer as your normal user; it asks for your password for the steps that need root")
    sys.exit(App().run([sys.argv[0]]))
