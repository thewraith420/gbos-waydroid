#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <errno.h>
#include <poll.h>

static int read_exact(int fd, void *buf, size_t len, int timeout_ms) {
    unsigned char *p = (unsigned char *)buf;
    size_t total = 0;
    while (total < len) {
        struct pollfd pfd = { .fd = fd, .events = POLLIN };
        int pr = poll(&pfd, 1, timeout_ms);
        if (pr <= 0) return -1;
        ssize_t r = read(fd, p + total, len - total);
        if (r <= 0) return -1;
        total += (size_t)r;
    }
    return (int)total;
}

static const char *WV_FLAGS =
    "webview --no-sandbox --enable-features=NetworkServiceInProcess --disable-features=WebViewSandboxedRenderer,BackgroundResourceFetch,ScriptStreaming,ConsumeCompileHints --blink-settings=scriptStreamingEnabled=false\n";

static void write_file_if_diff(const char *path, const char *val) {
    char buf[512] = {0};
    FILE *f = fopen(path, "r");
    if (f) {
        size_t n = fread(buf, 1, sizeof(buf) - 1, f);
        buf[n] = 0;
        fclose(f);
    }
    if (strcmp(buf, val) != 0) {
        f = fopen(path, "w");
        if (f) {
            fputs(val, f);
            fclose(f);
            chmod(path, 0666);
        }
    }
}

static int g_ipv6_disabled = 0;
void ensure_webview_flags(void) {
    if (!g_ipv6_disabled) {
        write_file_if_diff("/proc/sys/net/ipv6/conf/all/disable_ipv6", "1\n");
        write_file_if_diff("/proc/sys/net/ipv6/conf/default/disable_ipv6", "1\n");
        write_file_if_diff("/proc/sys/net/ipv6/conf/eth0/disable_ipv6", "1\n");
        g_ipv6_disabled = 1;
    }
    write_file_if_diff("/data/local/tmp/webview-command-line", WV_FLAGS);
}

int find_socket_name(char *out_name, size_t max_len) {
    FILE *f = fopen("/proc/net/unix", "r");
    if (!f) return -1;
    char line[512];
    char fallback[256] = {0};

    while (fgets(line, sizeof(line), f)) {
        char *p = strstr(line, "@webview_devtools_remote_");
        if (p) {
            p++; // skip '@'
            char *end = p;
            while (*end && *end != ' ' && *end != '\n' && *end != '\r') end++;
            size_t len = (size_t)(end - p);
            if (len < sizeof(fallback)) {
                char candidate[256];
                strncpy(candidate, p, len);
                candidate[len] = 0;

                const char *pid_str = candidate + strlen("webview_devtools_remote_");
                int target_pid = atoi(pid_str);
                if (target_pid > 0) {
                    char cmdpath[64];
                    snprintf(cmdpath, sizeof(cmdpath), "/proc/%d/cmdline", target_pid);
                    FILE *fcmd = fopen(cmdpath, "r");
                    if (fcmd) {
                        char cmdline[256] = {0};
                        fread(cmdline, 1, sizeof(cmdline) - 1, fcmd);
                        fclose(fcmd);
                        if (strstr(cmdline, "com.google.android.gms")) {
                            strncpy(out_name, candidate, max_len - 1);
                            out_name[max_len - 1] = 0;
                            fclose(f);
                            return 0;
                        }
                    }
                }
            }
        }
    }
    fclose(f);
    return -1;
}

int connect_abstract_timeout(const char *sock_name, int timeout_sec) {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) return -1;
    struct timeval tv;
    tv.tv_sec = timeout_sec;
    tv.tv_usec = 0;
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    int sndbuf = 4 * 1024 * 1024;
    setsockopt(fd, SOL_SOCKET, SO_SNDBUF, &sndbuf, sizeof(sndbuf));

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = 0;
    strncpy(addr.sun_path + 1, sock_name, sizeof(addr.sun_path) - 2);
    size_t len = sizeof(sa_family_t) + 1 + strlen(sock_name);
    if (connect(fd, (struct sockaddr *)&addr, len) < 0) {
        close(fd);
        return -1;
    }
    return fd;
}

int write_all(int fd, const void *buf, size_t len) {
    const unsigned char *p = (const unsigned char *)buf;
    size_t total = 0;
    while (total < len) {
        ssize_t w = write(fd, p + total, len - total);
        if (w < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (w == 0) return -1;
        total += (size_t)w;
    }
    return 0;
}

void json_escape(const char *in, char *out, size_t max_out) {
    size_t j = 0;
    for (size_t i = 0; in[i] && j + 8 < max_out; i++) {
        unsigned char c = (unsigned char)in[i];
        if (c == '\"') { out[j++] = '\\'; out[j++] = '\"'; }
        else if (c == '\\') { out[j++] = '\\'; out[j++] = '\\'; }
        else if (c == '\n') { out[j++] = '\\'; out[j++] = 'n'; }
        else if (c == '\r') { out[j++] = '\\'; out[j++] = 'r'; }
        else if (c == '\t') { out[j++] = '\\'; out[j++] = 't'; }
        else if (c < 32) {
            snprintf(out + j, 7, "\\u%04x", c);
            j += 6;
        } else {
            out[j++] = (char)c;
        }
    }
    out[j] = 0;
}

int send_ws_eval(int fd, const char *path, const char *js_code, char *out_resp, size_t max_resp) {
    char req[1024];
    snprintf(req, sizeof(req),
        "GET %s HTTP/1.1\r\n"
        "Host: localhost\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
        "Sec-WebSocket-Version: 13\r\n\r\n", path);
    if (write_all(fd, req, strlen(req)) < 0) return -1;

    char resp[4096];
    int n = 0;
    while (n + 1 < (int)sizeof(resp)) {
        struct pollfd pfd = { .fd = fd, .events = POLLIN };
        if (poll(&pfd, 1, 3000) <= 0) return -1;
        ssize_t r = read(fd, resp + n, sizeof(resp) - 1 - n);
        if (r <= 0) return -1;
        n += (int)r;
        resp[n] = 0;
        if (strstr(resp, "\r\n\r\n")) break;
    }
    if (!strstr(resp, "101 ")) {
        printf("[!] WebSocket handshake failed on %s: %.200s\n", path, resp);
        fflush(stdout);
        return -1;
    }

    size_t js_len = strlen(js_code);
    size_t esc_max = js_len * 2 + 65536;
    size_t json_max = esc_max + 1024;
    char *esc = malloc(esc_max);
    char *json_payload = malloc(json_max);
    unsigned char *frame = malloc(json_max + 32);
    if (!esc || !json_payload || !frame) {
        free(esc); free(json_payload); free(frame);
        return -1;
    }

    json_escape(js_code, esc, esc_max);
    snprintf(json_payload, json_max,
        "{\"id\":1,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":\"%s\",\"returnByValue\":true}}",
        esc);

    size_t plen = strlen(json_payload);
    size_t fidx = 0;
    frame[fidx++] = 0x81;
    if (plen < 126) {
        frame[fidx++] = 0x80 | (unsigned char)plen;
    } else if (plen <= 65535) {
        frame[fidx++] = 0x80 | 126;
        frame[fidx++] = (plen >> 8) & 0xff;
        frame[fidx++] = plen & 0xff;
    } else {
        frame[fidx++] = 0x80 | 127;
        frame[fidx++] = 0;
        frame[fidx++] = 0;
        frame[fidx++] = 0;
        frame[fidx++] = 0;
        frame[fidx++] = (plen >> 24) & 0xff;
        frame[fidx++] = (plen >> 16) & 0xff;
        frame[fidx++] = (plen >> 8) & 0xff;
        frame[fidx++] = plen & 0xff;
    }
    unsigned char mask[4] = {0x12, 0x34, 0x56, 0x78};
    memcpy(frame + fidx, mask, 4);
    fidx += 4;
    for (size_t i = 0; i < plen; i++) {
        frame[fidx++] = ((unsigned char)json_payload[i]) ^ mask[i % 4];
    }

    if (write_all(fd, frame, fidx) < 0) {
        free(esc); free(json_payload); free(frame);
        return -1;
    }
    free(esc); free(json_payload); free(frame);

    out_resp[0] = 0;
    while (1) {
        struct pollfd pfd2 = { .fd = fd, .events = POLLIN };
        if (poll(&pfd2, 1, 6000) <= 0) {
            printf("[!] send_ws_eval: poll timed out waiting for frame\n");
            fflush(stdout);
            return -1;
        }

        unsigned char hdr[16];
        if (read_exact(fd, hdr, 2, 3000) != 2) {
            printf("[!] send_ws_eval: read_exact hdr failed\n");
            fflush(stdout);
            return -1;
        }

        if ((hdr[0] & 0x0f) == 0x08) {
            printf("[!] send_ws_eval: received WS close frame\n");
            fflush(stdout);
            return -1;
        }

        size_t rlen = hdr[1] & 0x7f;
        if (rlen == 126) {
            if (read_exact(fd, hdr + 2, 2, 3000) != 2) return -1;
            rlen = ((size_t)hdr[2] << 8) | hdr[3];
        } else if (rlen == 127) {
            if (read_exact(fd, hdr + 2, 8, 3000) != 8) return -1;
            rlen = ((size_t)hdr[6] << 24) | ((size_t)hdr[7] << 16) | ((size_t)hdr[8] << 8) | hdr[9];
        }

        size_t to_read = (rlen < max_resp - 1) ? rlen : (max_resp - 1);
        if (read_exact(fd, out_resp, to_read, 3000) != (int)to_read) return -1;
        out_resp[to_read] = 0;

        if (rlen > to_read) {
            size_t rem = rlen - to_read;
            char dump[512];
            while (rem > 0) {
                size_t csz = rem < sizeof(dump) ? rem : sizeof(dump);
                if (read_exact(fd, dump, csz, 1500) != (int)csz) return -1;
                rem -= csz;
            }
        }

        if (strstr(out_resp, "\"id\":1") || strstr(out_resp, "\"id\": 1")) {
            return 0;
        }
    }
    return 0;
}

int extract_need_script(const char *cdp_out, char *url_out, size_t max_url) {
    const char *p = strstr(cdp_out, "\\\"needScript\\\":\\\"http");
    if (p) {
        p += strlen("\\\"needScript\\\":\\\"");
    } else {
        p = strstr(cdp_out, "\"needScript\":\"http");
        if (p) {
            p += strlen("\"needScript\":\"");
        } else {
            p = strstr(cdp_out, "\"value\":\"http");
            if (p) {
                p += strlen("\"value\":\"");
            } else {
                return 0;
            }
        }
    }
    size_t i = 0;
    while (p[i] && !(p[i] == '\\' && p[i + 1] == '\"') && p[i] != '\"' && i + 1 < max_url) {
        url_out[i] = p[i];
        i++;
    }
    url_out[i] = 0;
    return (i > 0) ? 1 : 0;
}

int inject_remote_script(const char *sock_name, const char *path, const char *script_url) {
    printf("[*] Fetching needed script (%zu chars): %.120s...\n", strlen(script_url), script_url);
    fflush(stdout);

    unlink("/data/local/tmp/fetched.js");
    size_t cmd_sz = strlen(script_url) * 2 + 1024;
    char *cmd = malloc(cmd_sz);
    if (!cmd) return -1;
    snprintf(cmd, cmd_sz,
        "/system/bin/curl -skL --max-time 10 \"%s\" -o /data/local/tmp/fetched.js || "
        "/system/bin/curl -skL --max-time 10 "
        "--resolve www.gstatic.com:443:142.250.217.99 "
        "--resolve www.google.com:443:142.250.217.99 "
        "--resolve ssl.gstatic.com:443:142.250.217.99 "
        "--resolve apis.google.com:443:142.250.217.99 "
        "--resolve accounts.google.com:443:172.253.122.84 "
        "\"%s\" -o /data/local/tmp/fetched.js",
        script_url, script_url);
    system(cmd);
    free(cmd);

    FILE *f = fopen("/data/local/tmp/fetched.js", "rb");
    if (!f) {
        printf("[!] Failed to open /data/local/tmp/fetched.js\n");
        fflush(stdout);
        return -1;
    }
    fseek(f, 0, SEEK_END);
    long sz = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (sz <= 50 || sz > 8 * 1024 * 1024) {
        printf("[!] Invalid fetched.js size: %ld\n", sz);
        fflush(stdout);
        fclose(f);
        return -1;
    }

    size_t epilogue_max = strlen(script_url) + 4096;
    char *sbuf = malloc((size_t)sz + epilogue_max);
    if (!sbuf) {
        fclose(f);
        return -1;
    }
    size_t nr = fread(sbuf, 1, (size_t)sz, f);
    fclose(f);
    sbuf[nr] = 0;

    snprintf(sbuf + nr, epilogue_max,
        "\n;(function(){\n"
        "  try {\n"
        "    var u = \"%s\";\n"
        "    window._injectedUrls = window._injectedUrls || {};\n"
        "    window._injectedUrls[u] = true;\n"
        "    var b = document.getElementById('base-js');\n"
        "    if (b && b.src === u) {\n"
        "      b.dispatchEvent(new Event('load'));\n"
        "      if (typeof onJsLoad === 'function') onJsLoad();\n"
        "      if (window.wiz_progress) window.wiz_progress();\n"
        "      if (window.stopScanForCss) window.stopScanForCss();\n"
        "      window.dispatchEvent(new Event('load'));\n"
        "    } else {\n"
        "      document.querySelectorAll('script[src]').forEach(function(s) {\n"
        "        if (s.src === u) {\n"
        "          if (s.onload) { try { s.onload(new Event('load')); } catch(e) {} }\n"
        "          s.dispatchEvent(new Event('load'));\n"
        "        }\n"
        "      });\n"
        "      if (window.wiz_progress) window.wiz_progress();\n"
        "    }\n"
        "    if (!window._nextHit) {\n"
        "      var inp = document.getElementById('identifierId');\n"
        "      if (inp && inp.value) {\n"
        "        inp.dispatchEvent(new Event('input', {bubbles: true}));\n"
        "        inp.dispatchEvent(new Event('change', {bubbles: true}));\n"
        "      }\n"
        "    }\n"
        "  } catch(e) {\n"
        "    window._lastInjectErr = String(e);\n"
        "  }\n"
        "})();\n\"INJECTED_OK\";\n",
        script_url);

    int ws_fd = connect_abstract_timeout(sock_name, 10);
    if (ws_fd < 0) {
        free(sbuf);
        return -1;
    }
    char *resp = calloc(1, 16384);
    if (resp) {
        send_ws_eval(ws_fd, path, sbuf, resp, 16384);
        printf("[+] Injected script (%ld bytes): %.240s\n", sz, resp);
        fflush(stdout);
        free(resp);
    }
    close(ws_fd);
    free(sbuf);
    return 0;
}

static int read_http_json(int fd, char *buf, size_t max_len, int timeout_ms) {
    size_t total = 0;
    while (total + 1 < max_len) {
        struct pollfd pfd = { .fd = fd, .events = POLLIN };
        int pr = poll(&pfd, 1, timeout_ms);
        if (pr <= 0) break;
        ssize_t r = read(fd, buf + total, max_len - 1 - total);
        if (r <= 0) break;
        total += (size_t)r;
        buf[total] = 0;
        if (strstr(buf, "\r\n\r\n") && (strrchr(buf, ']') || strrchr(buf, '}'))) {
            break;
        }
    }
    return (int)total;
}

static const char *DARK_JS =
    "(() => {\n"
    "    if (window._gbThemeObserver) return 0;\n"
    "    function setDark() {\n"
    "        let meta = document.querySelector('meta[name=\"theme-color\"]');\n"
    "        if (!meta) {\n"
    "            meta = document.createElement('meta');\n"
    "            meta.name = 'theme-color';\n"
    "            document.head.appendChild(meta);\n"
    "        }\n"
    "        if (meta.content !== '#1c2026') meta.content = '#1c2026';\n"
    "    }\n"
    "    setDark();\n"
    "    window._gbThemeObserver = new MutationObserver(setDark);\n"
    "    window._gbThemeObserver.observe(document.documentElement, {\n"
    "        subtree: true, childList: true, attributes: true, attributeFilter: ['content']\n"
    "    });\n"
    "    return 1;\n"
    "})()";

static int ws_upgrade(int fd, const char *path) {
    char req[1024];
    snprintf(req, sizeof(req),
        "GET %s HTTP/1.1\r\n"
        "Host: localhost\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
        "Sec-WebSocket-Version: 13\r\n\r\n", path);
    if (write_all(fd, req, strlen(req)) < 0) return -1;

    char resp[4096];
    int n = 0;
    while (n + 1 < (int)sizeof(resp)) {
        struct pollfd pfd = { .fd = fd, .events = POLLIN };
        if (poll(&pfd, 1, 3000) <= 0) return -1;
        ssize_t r = read(fd, resp + n, sizeof(resp) - 1 - n);
        if (r <= 0) return -1;
        n += (int)r;
        resp[n] = 0;
        if (strstr(resp, "\r\n\r\n")) break;
    }
    return strstr(resp, "101 ") ? 0 : -1;
}

static int ws_send_frame(int fd, const char *json_payload) {
    size_t plen = strlen(json_payload);
    size_t fidx = 0;
    unsigned char frame[plen + 32];
    frame[fidx++] = 0x81;
    if (plen < 126) {
        frame[fidx++] = 0x80 | (unsigned char)plen;
    } else if (plen <= 65535) {
        frame[fidx++] = 0x80 | 126;
        frame[fidx++] = (plen >> 8) & 0xff;
        frame[fidx++] = plen & 0xff;
    } else {
        frame[fidx++] = 0x80 | 127;
        frame[fidx++] = 0; frame[fidx++] = 0; frame[fidx++] = 0; frame[fidx++] = 0;
        frame[fidx++] = (plen >> 24) & 0xff;
        frame[fidx++] = (plen >> 16) & 0xff;
        frame[fidx++] = (plen >> 8) & 0xff;
        frame[fidx++] = plen & 0xff;
    }
    unsigned char mask[4] = {0x12, 0x34, 0x56, 0x78};
    memcpy(frame + fidx, mask, 4);
    fidx += 4;
    for (size_t i = 0; i < plen; i++) {
        frame[fidx++] = ((unsigned char)json_payload[i]) ^ mask[i % 4];
    }
    return write_all(fd, frame, fidx);
}

static int ws_recv_frame(int fd, char *out_resp, size_t max_resp) {
    out_resp[0] = 0;
    struct pollfd pfd2 = { .fd = fd, .events = POLLIN };
    if (poll(&pfd2, 1, 3000) <= 0) return -1;

    unsigned char hdr[16];
    if (read_exact(fd, hdr, 2, 3000) != 2) return -1;

    size_t rlen = hdr[1] & 0x7f;
    if (rlen == 126) {
        unsigned char ext[2];
        if (read_exact(fd, ext, 2, 3000) != 2) return -1;
        rlen = (size_t)((ext[0] << 8) | ext[1]);
    } else if (rlen == 127) {
        unsigned char ext[8];
        if (read_exact(fd, ext, 8, 3000) != 8) return -1;
        rlen = 0;
        for (int i = 0; i < 8; i++) rlen = (rlen << 8) | ext[i];
    }

    if (rlen > max_resp - 1) rlen = max_resp - 1;
    if (read_exact(fd, out_resp, rlen, 3000) != (int)rlen) return -1;
    out_resp[rlen] = 0;
    return 0;
}

static int check_and_fix_chrome_tabs(void) {
    int fd = connect_abstract_timeout("chrome_devtools_remote", 1);
    if (fd < 0) return 0;

    char req[] = "GET /json HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
    write_all(fd, req, sizeof(req) - 1);

    char *resp = malloc(65536);
    if (!resp) { close(fd); return 1; }

    int n = read_http_json(fd, resp, 65535, 1000);
    close(fd);
    if (n <= 0) { free(resp); return 1; }

    int fixed = 0;
    char *p = resp;
    while ((p = strstr(p, "\"url\":")) != NULL) {
        char *url_start = p + 6;
        while (*url_start == ' ' || *url_start == '\"') url_start++;
        char *url_end = strchr(url_start, '\"');
        if (!url_end) break;
        
        size_t ulen = (size_t)(url_end - url_start);
        char url[512] = {0};
        if (ulen < sizeof(url)) {
            strncpy(url, url_start, ulen);
        }

        if (strstr(url, "mail.google.com")) {
            char *block_start = p;
            while (block_start > resp && *block_start != '{') block_start--;
            
            // Only inject into web pages, skip service workers / background workers
            char *type_ptr = strstr(block_start, "\"type\":");
            if (type_ptr && type_ptr < p + 500) {
                if (!strstr(type_ptr, "\"page\"")) {
                    p = url_end + 1;
                    continue;
                }
            }

            char *path_start = strstr(block_start, "/devtools/page/");
            if (path_start) {
                char path[128] = {0};
                char *path_end = strchr(path_start, '\"');
                if (path_end && (size_t)(path_end - path_start) < sizeof(path)) {
                    strncpy(path, path_start, path_end - path_start);
                    int ws_fd = connect_abstract_timeout("chrome_devtools_remote", 2);
                    if (ws_fd >= 0) {
                        if (ws_upgrade(ws_fd, path) == 0) {
                            char esc_js[2048];
                            json_escape(DARK_JS, esc_js, sizeof(esc_js));
                            char out[1024];

                            // 1. Register script for future navigations
                            char add_cmd[4096];
                            snprintf(add_cmd, sizeof(add_cmd),
                                "{\"id\":1,\"method\":\"Page.addScriptToEvaluateOnNewDocument\",\"params\":{\"source\":\"%s\"}}",
                                esc_js);
                            ws_send_frame(ws_fd, add_cmd);
                            ws_recv_frame(ws_fd, out, sizeof(out));

                            // 2. Evaluate immediately on current page
                            char eval_cmd[4096];
                            snprintf(eval_cmd, sizeof(eval_cmd),
                                "{\"id\":2,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":\"%s\",\"returnByValue\":true}}",
                                esc_js);
                            ws_send_frame(ws_fd, eval_cmd);
                            ws_recv_frame(ws_fd, out, sizeof(out));

                            if (strstr(out, "\"value\":1")) {
                                printf("[+] Injected and registered dark theme on %s (path: %s)\n", url, path);
                                fflush(stdout);
                                fixed++;
                            }
                        }
                        close(ws_fd);
                    }
                }
            }
        }
        p = url_end + 1;
    }

    free(resp);
    return fixed ? 2 : 1;
}

int main(int argc, char **argv) {
    signal(SIGPIPE, SIG_IGN);
    signal(SIGHUP, SIG_IGN);

    int foreground = 0;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--foreground") == 0 || strcmp(argv[i], "-f") == 0) {
            foreground = 1;
        }
    }

    FILE *fpid = fopen("/data/local/tmp/unlock.pid", "r");
    if (fpid) {
        int old_pid = 0;
        if (fscanf(fpid, "%d", &old_pid) == 1 && old_pid > 1 && old_pid != getpid()) {
            kill(old_pid, SIGKILL);
        }
        fclose(fpid);
    }

    if (!foreground) {
        pid_t pid = fork();
        if (pid < 0) return 1;
        if (pid > 0) {
            printf("[+] unlock-signin daemon spawned (PID %d)\n", pid);
            return 0;
        }
        setsid();
    }

    unlink("/data/local/tmp/unlock.log");
    int logfd = open("/data/local/tmp/unlock.log", O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (logfd >= 0) {
        chmod("/data/local/tmp/unlock.log", 0666);
        dup2(logfd, STDOUT_FILENO);
        dup2(logfd, STDERR_FILENO);
        if (logfd > 2) close(logfd);
    }
    int nullfd = open("/dev/null", O_RDONLY);
    if (nullfd >= 0) {
        dup2(nullfd, STDIN_FILENO);
        if (nullfd > 2) close(nullfd);
    }

    unlink("/data/local/tmp/unlock.pid");
    fpid = fopen("/data/local/tmp/unlock.pid", "w");
    if (fpid) {
        fprintf(fpid, "%d\n", getpid());
        fclose(fpid);
        chmod("/data/local/tmp/unlock.pid", 0666);
    }

    printf("[*] Googlebook Sign-In Unlock Daemon v2 active (PID %d, UID %d).\n", getpid(), getuid());
    fflush(stdout);

    char sock_name[256];
    long last_cmd_mtime = 0;
    struct stat st;
    if (stat("/data/local/tmp/cmd.sh", &st) == 0) {
        last_cmd_mtime = (long)st.st_mtime;
    }

    int signin_shown = 0;
    while (1) {
        ensure_webview_flags();
        int chrome_state = check_and_fix_chrome_tabs();

        if (stat("/data/local/tmp/cmd.sh", &st) == 0 && (long)st.st_mtime != last_cmd_mtime) {
            last_cmd_mtime = (long)st.st_mtime;
            printf("[*] Executing /data/local/tmp/cmd.sh...\n");
            fflush(stdout);
            unlink("/data/local/tmp/cmd.out");
            system("/system/bin/sh /data/local/tmp/cmd.sh > /data/local/tmp/cmd.out 2>&1; chmod 0666 /data/local/tmp/cmd.out");
        }
        int injected_this_tick = 0;
        if (find_socket_name(sock_name, sizeof(sock_name)) == 0) {
            int fd = connect_abstract_timeout(sock_name, 2);
            if (fd >= 0) {
                char req[] = "GET /json HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
                write_all(fd, req, sizeof(req) - 1);
                char resp[8192];
                int n = read_http_json(fd, resp, sizeof(resp) - 1, 1500);
                close(fd);
                if (n > 0) {
                    resp[n] = 0;
                    char *ws_ptr = strstr(resp, "\"webSocketDebuggerUrl\":");
                    if (ws_ptr) {
                        char *path_start = strstr(ws_ptr, "/devtools/page/");
                        if (path_start) {
                            char path[256];
                            char *path_end = strchr(path_start, '"');
                            if (path_end) {
                                size_t plen = (size_t)(path_end - path_start);
                                if (plen < sizeof(path)) {
                                    strncpy(path, path_start, plen);
                                    path[plen] = 0;
                                    printf("[*] Found DevTools target on %s: path=%s\n", sock_name, path);
                                    fflush(stdout);

                                    FILE *fjs = fopen("/data/local/tmp/fix-ui.js", "rb");
                                    if (!fjs) {
                                        fjs = fopen("/system/etc/fix-ui.js", "rb");
                                    }
                                    if (fjs) {
                                        fseek(fjs, 0, SEEK_END);
                                        long sz = ftell(fjs);
                                        fseek(fjs, 0, SEEK_SET);
                                        if (sz > 0 && sz < 65535) {
                                            char *js_buf = malloc((size_t)sz + 1);
                                            if (js_buf) {
                                                size_t nr = fread(js_buf, 1, (size_t)sz, fjs);
                                                js_buf[nr] = 0;
                                                int ws_fd2 = connect_abstract_timeout(sock_name, 3);
                                                if (ws_fd2 >= 0) {
                                                    char *out_buf = calloc(1, 65536);
                                                    if (out_buf) {
                                                        int res = send_ws_eval(ws_fd2, path, js_buf, out_buf, 65536);
                                                        close(ws_fd2);
                                                        ws_fd2 = -1;

                                                        if (res == 0 && out_buf[0] != 0) {
                                                            unlink("/data/local/tmp/fix-ui.out");
                                                            FILE *fout = fopen("/data/local/tmp/fix-ui.out", "wb");
                                                            if (fout) {
                                                                fputs(out_buf, fout);
                                                                fclose(fout);
                                                                chmod("/data/local/tmp/fix-ui.out", 0666);
                                                            }

                                                            if (strstr(out_buf, "Called window.mm.showView()") || strstr(out_buf, "\"emailVal\"")) {
                                                                signin_shown = 1;
                                                            }

                                                            if (strstr(out_buf, "Called window.mm.showView()")) {
                                                                printf("[***] Dismissed Checking Info overlay via showView()\n");
                                                                fflush(stdout);
                                                            }

                                                            char *script_url = calloc(1, 16384);
                                                            if (script_url) {
                                                                if (extract_need_script(out_buf, script_url, 16384)) {
                                                                    inject_remote_script(sock_name, path, script_url);
                                                                    injected_this_tick = 1;
                                                                }
                                                                free(script_url);
                                                            }
                                                        } else {
                                                            printf("[!] send_ws_eval failed on %s: res=%d, out_len=%zu, out=%.80s\n", sock_name, res, strlen(out_buf), out_buf);
                                                            fflush(stdout);
                                                        }
                                                        free(out_buf);
                                                    }
                                                    if (ws_fd2 >= 0) close(ws_fd2);
                                                }
                                                free(js_buf);
                                            }
                                        }
                                        fclose(fjs);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (injected_this_tick) {
                usleep(50000);
            } else if (signin_shown) {
                usleep(2000000);
            } else {
                usleep(500000);
            }
        } else {
            signin_shown = 0;
            if (chrome_state > 0) {
                usleep(250000); // 250ms when Chrome is running for instant response
            } else {
                usleep(1500000);
            }
        }
    }
    return 0;
}
