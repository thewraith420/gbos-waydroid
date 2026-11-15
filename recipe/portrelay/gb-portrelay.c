/* gb-portrelay <listen-port> <dest-ip|auto> <dest-port>: listen on 127.0.0.1:<listen-port>, relay each connection to dest.
   dest-ip 'auto' = the Terminal app's VM, looked up per connection in /proc/net/arp (device avf_tap*). Used to reach the Terminal app's VM sshd (Antigravity dials 127.0.0.1:2222). */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <poll.h>
#include <signal.h>
#include <arpa/inet.h>
#include <sys/socket.h>
static int vm_ip(struct in_addr *out) {
    FILE *f = fopen("/proc/net/arp", "r"); char l[256], ip[64], dev[64]; int found = 0;
    if (!f) return 0;
    while (fgets(l, sizeof l, f)) {
        unsigned flags; if (sscanf(l, "%63s %*s 0x%*x 0x%x %*s %*s %63s", ip, &flags, dev) < 3 && sscanf(l, "%63s %*s %*s %*s %*s %63s", ip, dev) < 2) continue;
        if (!strncmp(dev, "avf_tap", 7) && inet_pton(AF_INET, ip, out) == 1) { found = 1; if (flags & 2) break; }
    }
    fclose(f); return found;
}
static void pump(int a, int b) {
    char buf[16384]; struct pollfd p[2] = {{a, POLLIN, 0}, {b, POLLIN, 0}};
    for (;;) {
        if (poll(p, 2, -1) < 0) break;
        for (int i = 0; i < 2; i++) if (p[i].revents & (POLLIN | POLLHUP | POLLERR)) {
            ssize_t n = read(p[i].fd, buf, sizeof buf); if (n <= 0) return;
            for (ssize_t o = 0; o < n;) { ssize_t w = write(p[1 - i].fd, buf + o, n - o); if (w <= 0) return; o += w; }
        }
    }
}
int main(int c, char **v) {
    if (c != 4) { fprintf(stderr, "usage: %s listen_port dest_ip dest_port\n", v[0]); return 2; }
    signal(SIGCHLD, SIG_IGN); signal(SIGPIPE, SIG_IGN);
    struct sockaddr_in la = {.sin_family = AF_INET, .sin_port = htons(atoi(v[1]))}, da = {.sin_family = AF_INET, .sin_port = htons(atoi(v[3]))};
    inet_pton(AF_INET, "127.0.0.1", &la.sin_addr); int autodest = !strcmp(v[2], "auto"); if (!autodest) inet_pton(AF_INET, v[2], &da.sin_addr);
    int s = socket(AF_INET, SOCK_STREAM, 0), one = 1; setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &one, sizeof one);
    if (bind(s, (void *)&la, sizeof la) || listen(s, 16)) { perror("bind/listen"); return 1; }
    for (;;) {
        int a = accept(s, 0, 0); if (a < 0) continue;
        if (fork() == 0) {
            close(s); int b = socket(AF_INET, SOCK_STREAM, 0);
            if ((!autodest || vm_ip(&da.sin_addr)) && connect(b, (void *)&da, sizeof da) == 0) pump(a, b);
            _exit(0);
        }
        close(a);
    }
}
