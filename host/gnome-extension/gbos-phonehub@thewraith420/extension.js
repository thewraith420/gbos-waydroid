import Clutter from 'gi://Clutter';
import Gio from 'gi://Gio';
import GLib from 'gi://GLib';
import GObject from 'gi://GObject';
import Meta from 'gi://Meta';
import Pango from 'gi://Pango';
import St from 'gi://St';

import * as Main from 'resource:///org/gnome/shell/ui/main.js';
import * as PanelMenu from 'resource:///org/gnome/shell/ui/panelMenu.js';
import * as PopupMenu from 'resource:///org/gnome/shell/ui/popupMenu.js';
import {Extension} from 'resource:///org/gnome/shell/extensions/extension.js';

const DEFAULT_BRIDGE_IP = '192.168.240.1';
const DAEMON_PORT = 8092;
const POLL_SECONDS = 5;
const GRID_COLUMNS = 4;
const MAX_RECENT = 4;
const DOT_CLASSES = ['gbos-dot-connected', 'gbos-dot-live', 'gbos-dot-paused', 'gbos-dot-offline'];

const VERTICAL_BOX_PROPS = Clutter.Orientation?.VERTICAL !== undefined
    ? {orientation: Clutter.Orientation.VERTICAL}
    : {vertical: true};

function _readConfigValue(key) {
    const envVal = GLib.getenv(key);
    if (envVal)
        return envVal;

    const candidates = [
        GLib.getenv('GB_CONFIG'),
        GLib.build_filenamev([GLib.get_user_config_dir(), 'gbos', 'gbos.env']),
    ];
    const re = new RegExp(`^\\s*${key}\\s*=\\s*["']?([^\\s"'#]+)["']?`, 'm');
    for (const envPath of candidates) {
        if (!envPath || !GLib.file_test(envPath, GLib.FileTest.EXISTS))
            continue;
        try {
            const [ok, bytes] = GLib.file_get_contents(envPath);
            if (!ok || !bytes)
                continue;
            const text = new TextDecoder().decode(bytes);
            const match = text.match(re);
            if (match && match[1])
                return match[1];
        } catch (_e) {
            // Optional config file unreadable; continue to fallback.
        }
    }
    return null;
}

function _readBridgeIp() {
    return _readConfigValue('GB_BRIDGE_IP') || DEFAULT_BRIDGE_IP;
}

function _resolveAppsDir() {
    const direct = _readConfigValue('GB_APPS_DIR');
    if (direct)
        return direct;
    const state = _readConfigValue('GB_STATE') ||
        GLib.build_filenamev([GLib.get_user_cache_dir(), 'gbos']);
    return GLib.build_filenamev([state, 'apps']);
}

function _recentFilePath() {
    const state = _readConfigValue('GB_STATE') ||
        GLib.build_filenamev([GLib.get_user_cache_dir(), 'gbos']);
    return GLib.build_filenamev([state, 'gnome-phonehub-recent.json']);
}

function _loadRecentPkgs() {
    try {
        const path = _recentFilePath();
        if (!GLib.file_test(path, GLib.FileTest.EXISTS))
            return [];
        const [ok, bytes] = GLib.file_get_contents(path);
        if (!ok || !bytes)
            return [];
        const parsed = JSON.parse(new TextDecoder().decode(bytes));
        if (!Array.isArray(parsed))
            return [];
        return parsed.filter(p => typeof p === 'string' && p.length > 0).slice(0, MAX_RECENT);
    } catch (_e) {
        return [];
    }
}

function _saveRecentPkgs(list) {
    try {
        const path = _recentFilePath();
        const dir = GLib.path_get_dirname(path);
        GLib.mkdir_with_parents(dir, 0o700);
        GLib.file_set_contents(path, JSON.stringify(list.slice(0, MAX_RECENT)));
    } catch (_e) {
        // Non-fatal if cache dir is unwritable.
    }
}

const GbosPhoneHubIndicator = GObject.registerClass(
class GbosPhoneHubIndicator extends PanelMenu.Button {
    _init() {
        super._init(0.0, 'GBOS Phone Hub');

        this._destroyed = false;
        this._polling = false;
        this._pollId = 0;
        this._postOpenId = 0;
        this._cancellable = new Gio.Cancellable();

        this._apps = [];
        this._filteredApps = [];
        this._recentPkgs = _loadRecentPkgs();
        this._appsDir = _resolveAppsDir();
        this._appsMtime = -1;

        // Top bar indicator: phone icon + live status dot
        const panelBox = new St.BoxLayout({
            style_class: 'gbos-panel-box',
            y_align: Clutter.ActorAlign.CENTER,
        });
        const panelIcon = new St.Icon({
            icon_name: 'phone-symbolic',
            style_class: 'system-status-icon',
        });
        this._panelDot = new St.Widget({
            style_class: 'gbos-panel-dot gbos-dot-offline',
            y_align: Clutter.ActorAlign.CENTER,
        });
        panelBox.add_child(panelIcon);
        panelBox.add_child(this._panelDot);
        this.add_child(panelBox);

        // 1) Status card + Open Phone Home button
        const cardItem = new PopupMenu.PopupBaseMenuItem({
            reactive: false,
            can_focus: false,
        });
        const card = new St.BoxLayout({
            style_class: 'gbos-card',
            x_expand: true,
            ...VERTICAL_BOX_PROPS,
        });

        const header = new St.BoxLayout({
            style_class: 'gbos-card-header',
            y_align: Clutter.ActorAlign.CENTER,
        });
        this._cardDot = new St.Widget({
            style_class: 'gbos-panel-dot gbos-dot-offline',
            y_align: Clutter.ActorAlign.CENTER,
        });
        this._titleLabel = new St.Label({
            text: 'Phone \u2022 Checking\u2026',
            style_class: 'gbos-card-title',
            y_align: Clutter.ActorAlign.CENTER,
        });
        header.add_child(this._cardDot);
        header.add_child(this._titleLabel);
        card.add_child(header);

        this._subLabel = new St.Label({
            text: 'Contacting GBOS link daemon\u2026',
            style_class: 'gbos-card-sub',
        });
        this._subLabel.clutter_text.line_wrap = true;
        card.add_child(this._subLabel);

        const openBtnBox = new St.BoxLayout({
            style_class: 'gbos-open-btn-box',
            x_align: Clutter.ActorAlign.CENTER,
            y_align: Clutter.ActorAlign.CENTER,
        });
        openBtnBox.add_child(new St.Icon({
            icon_name: 'phone-symbolic',
            icon_size: 16,
        }));
        openBtnBox.add_child(new St.Label({
            text: 'Open Phone Home',
            y_align: Clutter.ActorAlign.CENTER,
        }));

        this._openBtn = new St.Button({
            style_class: 'gbos-open-btn',
            can_focus: true,
            x_expand: true,
            child: openBtnBox,
        });
        this._openBtn.connect('clicked', () => this._onOpenPhoneHome());
        card.add_child(this._openBtn);

        cardItem.add_child(card);
        this.menu.addMenuItem(cardItem);
        this.menu.addMenuItem(new PopupMenu.PopupSeparatorMenuItem());

        // 2) Phone Apps search + Recent + 4-column scrollable icon grid
        const appsItem = new PopupMenu.PopupBaseMenuItem({
            reactive: false,
            can_focus: false,
        });
        const appsSection = new St.BoxLayout({
            style_class: 'gbos-apps-section',
            x_expand: true,
            ...VERTICAL_BOX_PROPS,
        });

        this._clearIcon = new St.Icon({
            icon_name: 'edit-clear-symbolic',
            icon_size: 14,
            visible: false,
        });
        this._searchEntry = new St.Entry({
            style_class: 'gbos-search-entry',
            hint_text: 'Search phone apps\u2026',
            can_focus: true,
            track_hover: true,
            x_expand: true,
        });
        this._searchEntry.set_primary_icon(new St.Icon({
            icon_name: 'edit-find-symbolic',
            icon_size: 14,
        }));
        this._searchEntry.set_secondary_icon(this._clearIcon);
        this._clearIconId = this._searchEntry.connect('secondary-icon-clicked', () => {
            this._searchEntry.text = '';
        });
        this._searchTextId = this._searchEntry.clutter_text.connect('text-changed', () => {
            const hasText = this._searchEntry.text.length > 0;
            this._clearIcon.visible = hasText;
            this._renderApps();
        });
        this._searchActivateId = this._searchEntry.clutter_text.connect('activate', () => {
            if (this._filteredApps.length > 0)
                this._onOpenPhoneApp(this._filteredApps[0]);
        });
        appsSection.add_child(this._searchEntry);

        this._recentSection = new St.BoxLayout({
            x_expand: true,
            visible: false,
            ...VERTICAL_BOX_PROPS,
        });
        this._recentSection.add_child(new St.Label({
            text: 'RECENT',
            style_class: 'gbos-section-label',
        }));
        this._recentRows = new St.BoxLayout({
            style_class: 'gbos-apps-rows',
            x_expand: true,
            ...VERTICAL_BOX_PROPS,
        });
        this._recentSection.add_child(this._recentRows);
        appsSection.add_child(this._recentSection);

        this._appsHeader = new St.Label({
            text: 'PHONE APPS',
            style_class: 'gbos-section-label',
        });
        appsSection.add_child(this._appsHeader);

        this._appsScroll = new St.ScrollView({
            style_class: 'gbos-apps-scroll',
            hscrollbar_policy: St.PolicyType.NEVER,
            vscrollbar_policy: St.PolicyType.AUTOMATIC,
            overlay_scrollbars: true,
            x_expand: true,
        });
        this._appsRows = new St.BoxLayout({
            style_class: 'gbos-apps-rows',
            x_expand: true,
            ...VERTICAL_BOX_PROPS,
        });
        if (typeof this._appsScroll.set_child === 'function')
            this._appsScroll.set_child(this._appsRows);
        else
            this._appsScroll.add_child(this._appsRows);
        appsSection.add_child(this._appsScroll);

        appsItem.add_child(appsSection);
        this.menu.addMenuItem(appsItem);
        this.menu.addMenuItem(new PopupMenu.PopupSeparatorMenuItem());

        // 3) Footer actions
        const desktopItem = new PopupMenu.PopupImageMenuItem('Open Full GBOS Desktop', 'computer-symbolic');
        desktopItem.connect('activate', () => this._openFullDesktop());
        this.menu.addMenuItem(desktopItem);

        const refreshItem = new PopupMenu.PopupImageMenuItem('Refresh Status', 'view-refresh-symbolic');
        refreshItem.connect('activate', () => {
            this._refreshStatus();
            this._loadPhoneApps(true);
        });
        this.menu.addMenuItem(refreshItem);

        this._menuSignalId = this.menu.connect('open-state-changed', (_menu, isOpen) => {
            if (this._destroyed)
                return;
            if (isOpen) {
                this._refreshStatus();
                this._loadPhoneApps(false);
            } else if (this._searchEntry.text.length > 0) {
                this._searchEntry.text = '';
            }
        });

        this._loadPhoneApps(true);
        this._refreshStatus();
        this._pollId = GLib.timeout_add_seconds(GLib.PRIORITY_DEFAULT, POLL_SECONDS, () => {
            if (this._destroyed) {
                this._pollId = 0;
                return GLib.SOURCE_REMOVE;
            }
            this._refreshStatus();
            return GLib.SOURCE_CONTINUE;
        });
    }

    _setDotClass(cssClass) {
        for (const dot of [this._panelDot, this._cardDot]) {
            for (const c of DOT_CLASSES)
                dot.remove_style_class_name(c);
            dot.add_style_class_name(cssClass);
        }
    }

    _loadPhoneApps(force = false) {
        if (this._destroyed)
            return;

        this._appsDir = _resolveAppsDir();
        const appsJson = GLib.build_filenamev([this._appsDir, 'apps.json']);
        if (!GLib.file_test(appsJson, GLib.FileTest.EXISTS)) {
            if (force || this._appsMtime !== 0) {
                this._appsMtime = 0;
                this._apps = [];
                this._renderApps();
            }
            return;
        }

        let mtime = 0;
        try {
            const info = Gio.File.new_for_path(appsJson).query_info(
                'time::modified',
                Gio.FileQueryInfoFlags.NONE,
                null
            );
            mtime = info.get_modification_date_time()?.to_unix() ?? 0;
        } catch (_e) {
            mtime = 0;
        }

        if (!force && mtime > 0 && mtime === this._appsMtime && this._apps.length > 0)
            return;

        try {
            const [ok, bytes] = GLib.file_get_contents(appsJson);
            if (!ok || !bytes)
                return;
            const parsed = JSON.parse(new TextDecoder().decode(bytes));
            if (!Array.isArray(parsed))
                return;

            this._apps = parsed
                .filter(item => item && typeof item.pkg === 'string' && typeof item.label === 'string')
                .map(item => ({
                    pkg: item.pkg.trim(),
                    label: item.label.trim() || item.pkg.trim(),
                }))
                .filter(item => item.pkg.length > 0)
                .sort((a, b) => a.label.localeCompare(b.label, undefined, {sensitivity: 'base'}));
            this._appsMtime = mtime;
        } catch (_e) {
            this._apps = [];
        }
        this._renderApps();
    }

    _createAppTile(app) {
        const tileBox = new St.BoxLayout({
            style_class: 'gbos-app-tile-box',
            x_align: Clutter.ActorAlign.CENTER,
            y_align: Clutter.ActorAlign.START,
            ...VERTICAL_BOX_PROPS,
        });

        const iconPath = GLib.build_filenamev([this._appsDir, 'icons', `${app.pkg}.png`]);
        let gicon = null;
        if (GLib.file_test(iconPath, GLib.FileTest.EXISTS))
            gicon = Gio.FileIcon.new(Gio.File.new_for_path(iconPath));

        const icon = new St.Icon({
            icon_size: 36,
            x_align: Clutter.ActorAlign.CENTER,
            ...(gicon ? {gicon} : {icon_name: 'application-x-executable-symbolic'}),
        });
        tileBox.add_child(icon);

        const label = new St.Label({
            text: app.label,
            style_class: 'gbos-app-label',
            x_align: Clutter.ActorAlign.CENTER,
        });
        label.clutter_text.ellipsize = Pango.EllipsizeMode.END;
        tileBox.add_child(label);

        const btn = new St.Button({
            style_class: 'gbos-app-tile',
            can_focus: true,
            x_expand: true,
            child: tileBox,
        });
        btn.connect('clicked', () => this._onOpenPhoneApp(app));
        return btn;
    }

    _populateGridRows(container, apps) {
        container.destroy_all_children();
        for (let i = 0; i < apps.length; i += GRID_COLUMNS) {
            const row = new St.BoxLayout({
                style_class: 'gbos-app-row',
                x_expand: true,
            });
            // St.BoxLayout has no 'homogeneous' property; it lives on the Clutter layout manager.
            row.layout_manager.homogeneous = true;
            for (let col = 0; col < GRID_COLUMNS; col++) {
                const idx = i + col;
                if (idx < apps.length)
                    row.add_child(this._createAppTile(apps[idx]));
                else
                    row.add_child(new St.Widget({x_expand: true}));
            }
            container.add_child(row);
        }
    }

    _renderApps() {
        if (this._destroyed)
            return;

        const q = this._searchEntry.text.trim().toLowerCase();
        const byPkg = new Map();
        for (const app of this._apps)
            byPkg.set(app.pkg, app);

        const recentApps = [];
        if (!q) {
            for (const pkg of this._recentPkgs) {
                const app = byPkg.get(pkg);
                if (app)
                    recentApps.push(app);
            }
        }

        if (recentApps.length > 0) {
            this._recentSection.visible = true;
            this._populateGridRows(this._recentRows, recentApps);
        } else {
            this._recentRows.destroy_all_children();
            this._recentSection.visible = false;
        }

        this._filteredApps = q
            ? this._apps.filter(a => a.label.toLowerCase().includes(q) || a.pkg.toLowerCase().includes(q))
            : this._apps;

        if (this._apps.length === 0) {
            this._appsHeader.text = 'PHONE APPS';
            this._appsRows.destroy_all_children();
            const msg = new St.Label({
                text: 'No synced phone apps yet.\nOpen GBOS Cast on your phone to sync.',
                style_class: 'gbos-empty-label',
            });
            msg.clutter_text.line_wrap = true;
            this._appsRows.add_child(msg);
            return;
        }

        if (this._filteredApps.length === 0) {
            this._appsHeader.text = 'PHONE APPS (0)';
            this._appsRows.destroy_all_children();
            this._appsRows.add_child(new St.Label({
                text: 'No matching apps',
                style_class: 'gbos-empty-label',
            }));
            return;
        }

        this._appsHeader.text = q
            ? `MATCHING APPS (${this._filteredApps.length})`
            : `ALL APPS (${this._apps.length})`;
        this._populateGridRows(this._appsRows, this._filteredApps);
    }

    _recordRecentPkg(pkg) {
        const next = [pkg, ...this._recentPkgs.filter(p => p !== pkg)].slice(0, MAX_RECENT);
        this._recentPkgs = next;
        _saveRecentPkgs(next);
    }

    _sendDaemonCommand(cmd, callback) {
        if (this._destroyed)
            return;

        const host = _readBridgeIp();
        const client = new Gio.SocketClient();
        client.set_timeout(3);

        client.connect_to_host_async(`${host}:${DAEMON_PORT}`, DAEMON_PORT, this._cancellable, (_c, res) => {
            if (this._destroyed)
                return;
            let conn;
            try {
                conn = client.connect_to_host_finish(res);
            } catch (_e) {
                callback(null);
                return;
            }

            const finish = reply => {
                try {
                    conn.close_async(GLib.PRIORITY_DEFAULT, null, null);
                } catch (_e) {
                    // Best-effort close.
                }
                if (!this._destroyed)
                    callback(reply);
            };

            try {
                const payload = new TextEncoder().encode(`${cmd}\n`);
                const out = conn.get_output_stream();
                out.write_all_async(payload, GLib.PRIORITY_DEFAULT, this._cancellable, (_s, wres) => {
                    if (this._destroyed) {
                        finish(null);
                        return;
                    }
                    try {
                        const [ok] = out.write_all_finish(wres);
                        if (!ok) {
                            finish(null);
                            return;
                        }
                        const dataIn = Gio.DataInputStream.new(conn.get_input_stream());
                        dataIn.read_line_async(GLib.PRIORITY_DEFAULT, this._cancellable, (_d, rres) => {
                            if (this._destroyed) {
                                finish(null);
                                return;
                            }
                            try {
                                const [line] = dataIn.read_line_finish_utf8(rres);
                                finish(line ?? null);
                            } catch (_e) {
                                finish(null);
                            }
                        });
                    } catch (_e) {
                        finish(null);
                    }
                });
            } catch (_e) {
                finish(null);
            }
        });
    }

    _refreshStatus() {
        if (this._destroyed || this._polling)
            return;
        this._polling = true;

        this._sendDaemonCommand('STATUS', reply => {
            this._polling = false;
            if (this._destroyed)
                return;

            if (!reply) {
                this._setDotClass('gbos-dot-offline');
                this._titleLabel.text = 'GBOS \u2022 Offline';
                this._subLabel.text = 'Link daemon unreachable (is Waydroid running?)';
                return;
            }

            let data;
            try {
                data = JSON.parse(reply);
            } catch (_e) {
                this._setDotClass('gbos-dot-offline');
                this._titleLabel.text = 'GBOS \u2022 Error';
                this._subLabel.text = 'Unexpected response from link daemon';
                return;
            }

            const cds = String(data.cds ?? 'disconnected').toLowerCase();
            const appcast = String(data.appcast ?? 'inactive').toLowerCase();
            const cast = String(data.cast ?? (appcast === 'off' ? 'off' : 'on')).toLowerCase();
            const rawPhone = String(data.phone ?? '').trim();
            const phoneSuffix = rawPhone && rawPhone !== 'none' ? ` (${rawPhone})` : '';

            if (cds === 'connected') {
                if (cast === 'off' || appcast === 'off') {
                    this._setDotClass('gbos-dot-paused');
                    this._titleLabel.text = 'Phone \u2022 Cast Off';
                    this._subLabel.text = 'GBOS Cast is turned off on phone';
                } else if (appcast === 'live') {
                    this._setDotClass('gbos-dot-live');
                    this._titleLabel.text = 'Phone \u2022 Connected';
                    this._subLabel.text = `AppCast: Live${phoneSuffix}`;
                } else {
                    this._setDotClass('gbos-dot-connected');
                    this._titleLabel.text = 'Phone \u2022 Connected';
                    this._subLabel.text = `AppCast: Ready${phoneSuffix}`;
                }
            } else {
                this._setDotClass('gbos-dot-offline');
                this._titleLabel.text = 'Phone \u2022 Disconnected';
                this._subLabel.text = 'CDS offline \u2022 Open GBOS to check';
            }
        });
    }

    _schedulePostOpenRefresh() {
        if (this._postOpenId) {
            GLib.Source.remove(this._postOpenId);
            this._postOpenId = 0;
        }
        this._postOpenId = GLib.timeout_add_seconds(GLib.PRIORITY_DEFAULT, 4, () => {
            this._postOpenId = 0;
            if (!this._destroyed)
                this._refreshStatus();
            return GLib.SOURCE_REMOVE;
        });
    }

    _hasOffWorkspaceWaydroidWindow() {
        try {
            const activeWs = global.workspace_manager.get_active_workspace();
            for (const actor of global.get_window_actors()) {
                const win = actor.meta_window;
                if (!win || win.window_type !== Meta.WindowType.NORMAL)
                    continue;
                const id = `${win.get_wm_class() ?? ''} ${win.get_wm_class_instance() ?? ''} ${win.get_title() ?? ''}`.toLowerCase();
                if (!id.includes('waydroid'))
                    continue;
                const ws = win.get_workspace();
                if (ws && activeWs && ws !== activeWs)
                    return true;
            }
        } catch (e) {
            logError(e, 'gbos-phonehub: failed checking off-workspace Waydroid windows');
        }
        return false;
    }

    _sendAmbientLaunch(command, onReply) {
        const wireCmd = this._hasOffWorkspaceWaydroidWindow()
            ? `STANDALONE ${command}`
            : command;
        this._sendDaemonCommand(wireCmd, onReply);
    }

    _onOpenPhoneHome() {
        this._subLabel.text = 'Opening phone home screen\u2026';
        this._sendAmbientLaunch('OPEN_PHONE_HOME', reply => {
            if (this._destroyed)
                return;
            if (!reply) {
                this._subLabel.text = 'Link daemon unreachable (is Waydroid running?)';
                return;
            }
            this.menu.close();
            this._schedulePostOpenRefresh();
        });
    }

    _onOpenPhoneApp(app) {
        this._recordRecentPkg(app.pkg);
        this._subLabel.text = `Opening ${app.label}\u2026`;
        this._sendAmbientLaunch(`OPEN_APP ${app.pkg}`, reply => {
            if (this._destroyed)
                return;
            if (!reply) {
                this._subLabel.text = 'Link daemon unreachable (is Waydroid running?)';
                return;
            }
            try {
                const parsed = JSON.parse(reply);
                if (parsed.error) {
                    this._subLabel.text = `Failed to open ${app.label}: ${parsed.error}`;
                    return;
                }
            } catch (_e) {
                // Ignore parse error if daemon replied.
            }
            this.menu.close();
            this._schedulePostOpenRefresh();
        });
    }

    _openFullDesktop() {
        this.menu.close();
        try {
            const launcher = new Gio.SubprocessLauncher({
                flags: Gio.SubprocessFlags.NONE,
            });
            launcher.spawnv(['waydroid', 'show-full-ui']);
        } catch (e) {
            logError(e, 'gbos-phonehub: failed to launch waydroid show-full-ui');
        }
    }

    destroy() {
        this._destroyed = true;
        if (this._cancellable) {
            this._cancellable.cancel();
            this._cancellable = null;
        }
        if (this._pollId) {
            GLib.Source.remove(this._pollId);
            this._pollId = 0;
        }
        if (this._postOpenId) {
            GLib.Source.remove(this._postOpenId);
            this._postOpenId = 0;
        }
        if (this._menuSignalId && this.menu) {
            this.menu.disconnect(this._menuSignalId);
            this._menuSignalId = 0;
        }
        if (this._clearIconId && this._searchEntry) {
            this._searchEntry.disconnect(this._clearIconId);
            this._clearIconId = 0;
        }
        if (this._searchTextId && this._searchEntry) {
            this._searchEntry.clutter_text.disconnect(this._searchTextId);
            this._searchTextId = 0;
        }
        if (this._searchActivateId && this._searchEntry) {
            this._searchEntry.clutter_text.disconnect(this._searchActivateId);
            this._searchActivateId = 0;
        }
        super.destroy();
    }
});

export default class GbosPhoneHubExtension extends Extension {
    enable() {
        this._indicator = new GbosPhoneHubIndicator();
        Main.panel.addToStatusArea(this.uuid, this._indicator);
    }

    disable() {
        this._indicator?.destroy();
        this._indicator = null;
    }
}
