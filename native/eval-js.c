#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>

int find_socket_name(char *out_name, size_t max_len) {
    FILE *f = fopen("/proc/net/unix", "r");
    if (!f) return -1;
    char line[512];
    while (fgets(line, sizeof(line), f)) {
        char *p = strstr(line, "@webview_devtools_remote_");
        if (p) {
            p++;
            char *end = p;
            while (*end && *end != ' ' && *end != '\n' && *end != '\r') end++;
            size_t len = end - p;
            if (len < max_len) {
                strncpy(out_name, p, len);
                out_name[len] = 0;
                fclose(f);
                return 0;
            }
        }
    }
    fclose(f);
    return -1;
}

int connect_abstract(const char *sock_name) {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) return -1;
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

int read_exact(int fd, void *buf, size_t count) {
    size_t done = 0;
    while (done < count) {
        ssize_t n = read(fd, (char *)buf + done, count - done);
        if (n <= 0) return -1;
        done += n;
    }
    return 0;
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
    if (write(fd, req, strlen(req)) < 0) return -1;

    char resp[4096];
    int n = read(fd, resp, sizeof(resp) - 1);
    if (n <= 0) return -1;
    resp[n] = 0;
    if (!strstr(resp, "101 ")) {
        printf("[-] Handshake failed: %s\n", resp);
        return -1;
    }

    // Escape js_code
    char *escaped = (char *)malloc(131072);
    if (!escaped) return -1;
    size_t e = 0;
    for (size_t i = 0; js_code[i] && e < 131072 - 4; i++) {
        if (js_code[i] == '"') { escaped[e++] = '\\'; escaped[e++] = '"'; }
        else if (js_code[i] == '\\') { escaped[e++] = '\\'; escaped[e++] = '\\'; }
        else if (js_code[i] == '\n') { escaped[e++] = '\\'; escaped[e++] = 'n'; }
        else if (js_code[i] == '\r') { escaped[e++] = '\\'; escaped[e++] = 'r'; }
        else { escaped[e++] = js_code[i]; }
    }
    escaped[e] = 0;

    char *json_payload = (char *)malloc(131072);
    if (!json_payload) { free(escaped); return -1; }
    snprintf(json_payload, 131072,
        "{\"id\":1,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":\"%s\",\"returnByValue\":true}}",
        escaped);
    free(escaped);

    size_t plen = strlen(json_payload);
    unsigned char *frame = (unsigned char *)malloc(plen + 16);
    if (!frame) { free(json_payload); return -1; }
    size_t fidx = 0;
    frame[fidx++] = 0x81;
    if (plen < 126) {
        frame[fidx++] = 0x80 | (unsigned char)plen;
    } else {
        frame[fidx++] = 0x80 | 126;
        frame[fidx++] = (plen >> 8) & 0xff;
        frame[fidx++] = plen & 0xff;
    }
    unsigned char mask[4] = {0x12, 0x34, 0x56, 0x78};
    memcpy(frame + fidx, mask, 4);
    fidx += 4;
    for (size_t i = 0; i < plen; i++) {
        frame[fidx++] = ((unsigned char)json_payload[i]) ^ mask[i % 4];
    }
    free(json_payload);

    if (write(fd, frame, fidx) < 0) { free(frame); return -1; }
    free(frame);

    // Read WS frame response
    unsigned char ws_hdr[2];
    if (read_exact(fd, ws_hdr, 2) < 0) return -1;

    size_t ws_payload_len = ws_hdr[1] & 0x7f;
    if (ws_payload_len == 126) {
        unsigned char ext_len[2];
        if (read_exact(fd, ext_len, 2) < 0) return -1;
        ws_payload_len = (ext_len[0] << 8) | ext_len[1];
    } else if (ws_payload_len == 127) {
        unsigned char ext_len[8];
        if (read_exact(fd, ext_len, 8) < 0) return -1;
        ws_payload_len = 0;
        for (int i = 0; i < 8; i++) ws_payload_len = (ws_payload_len << 8) | ext_len[i];
    }

    size_t to_read = ws_payload_len;
    if (to_read >= max_resp) to_read = max_resp - 1;

    if (read_exact(fd, out_resp, to_read) < 0) return -1;
    out_resp[to_read] = 0;

    // Drain any remaining bytes if truncated
    if (ws_payload_len > to_read) {
        size_t rem = ws_payload_len - to_read;
        char junk[1024];
        while (rem > 0) {
            size_t chk = rem > sizeof(junk) ? sizeof(junk) : rem;
            read_exact(fd, junk, chk);
            rem -= chk;
        }
    }

    return 0;
}

int main(int argc, char **argv) {
    if (argc < 2) {
        printf("Usage: %s <js_code_or_file> [--submit]\n", argv[0]);
        return 1;
    }

    int do_enter = 0;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--submit") == 0 || strcmp(argv[i], "-s") == 0) {
            do_enter = 1;
        }
    }

    char *file_buf = NULL;
    const char *js_code = argv[1];

    FILE *fjs = fopen(argv[1], "rb");
    if (fjs) {
        fseek(fjs, 0, SEEK_END);
        long sz = ftell(fjs);
        fseek(fjs, 0, SEEK_SET);
        if (sz > 0 && sz < 65536) {
            file_buf = (char *)malloc(sz + 1);
            if (file_buf) {
                size_t nread = fread(file_buf, 1, sz, fjs);
                file_buf[nread] = 0;
                js_code = file_buf;
                printf("[*] Loaded %zu bytes from file: %s\n", nread, argv[1]);
            }
        }
        fclose(fjs);
    }

    char sock_name[256];
    if (find_socket_name(sock_name, sizeof(sock_name)) != 0) {
        printf("[-] No webview socket found\n");
        if (file_buf) free(file_buf);
        return 1;
    }
    int fd = connect_abstract(sock_name);
    if (fd < 0) {
        printf("[-] Failed to connect to socket @%s\n", sock_name);
        if (file_buf) free(file_buf);
        return 1;
    }
    char req[] = "GET /json HTTP/1.1\r\nHost: localhost\r\n\r\n";
    write(fd, req, sizeof(req) - 1);
    char resp[8192];
    int n = read(fd, resp, sizeof(resp) - 1);
    close(fd);
    if (n <= 0) {
        if (file_buf) free(file_buf);
        return 1;
    }
    resp[n] = 0;
    char *ws_ptr = strstr(resp, "\"webSocketDebuggerUrl\":");
    if (!ws_ptr) {
        printf("[-] No debugger url\n");
        if (file_buf) free(file_buf);
        return 1;
    }
    char *path_start = strstr(ws_ptr, "/devtools/page/");
    if (!path_start) {
        if (file_buf) free(file_buf);
        return 1;
    }
    char path[256];
    char *path_end = strchr(path_start, '"');
    if (!path_end) {
        if (file_buf) free(file_buf);
        return 1;
    }
    size_t plen = path_end - path_start;
    strncpy(path, path_start, plen);
    path[plen] = 0;

    int ws_fd = connect_abstract(sock_name);
    if (ws_fd < 0) {
        if (file_buf) free(file_buf);
        return 1;
    }
    char cdp_res[32768] = {0};
    if (send_ws_eval(ws_fd, path, js_code, cdp_res, sizeof(cdp_res)) == 0) {
        printf("%s\n", cdp_res);
    } else {
        printf("[-] Eval failed\n");
    }
    close(ws_fd);
    if (file_buf) free(file_buf);
    if (do_enter) {
        usleep(250000); // 250ms
        printf("[*] Dispatching native KEYCODE_ENTER (66)...\n");
        system("input keyevent 66");
    }
    return 0;
}
