/* gb-dhcpd: minimal single-lease DHCP server for the fake Wi-Fi access point (GBOS_WAYDROID_FIXES.md, section 3.20).
 * usage: gb-dhcpd <ifname> <server-ip> <client-ip> <mask> <dns-ip>
 * Answers DISCOVER with OFFER and REQUEST with ACK (always the same client-ip), broadcasting the replies. Not a general DHCP server. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>

struct dhcp { unsigned char op, htype, hlen, hops; unsigned xid; unsigned short secs, flags; unsigned ciaddr, yiaddr, siaddr, giaddr; unsigned char chaddr[16], sname[64], file[128]; unsigned magic; unsigned char opts[308]; } __attribute__((packed));

static unsigned char *put(unsigned char *p, int code, int len, const void *v) { *p++ = code; *p++ = len; memcpy(p, v, len); return p + len; }

int main(int argc, char **argv) {
    if (argc < 6) { fprintf(stderr, "usage: %s ifname server-ip client-ip mask dns-ip\n", argv[0]); return 1; }
    unsigned srv = inet_addr(argv[2]), cli = inet_addr(argv[3]), mask = inet_addr(argv[4]), dns = inet_addr(argv[5]);
    int s = socket(AF_INET, SOCK_DGRAM, 0), one = 1;
    setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &one, sizeof one);
    setsockopt(s, SOL_SOCKET, SO_BROADCAST, &one, sizeof one);
    if (setsockopt(s, SOL_SOCKET, SO_BINDTODEVICE, argv[1], strlen(argv[1])) < 0) perror("SO_BINDTODEVICE");
    struct sockaddr_in a = { .sin_family = AF_INET, .sin_port = htons(67), .sin_addr.s_addr = INADDR_ANY };
    if (bind(s, (struct sockaddr *)&a, sizeof a) < 0) { perror("bind"); return 1; }
    for (;;) {
        struct dhcp q; ssize_t n = recv(s, &q, sizeof q, 0);
        if (n < 240 || q.op != 1 || q.magic != htonl(0x63825363)) continue;
        int type = 0; for (unsigned char *p = q.opts; p < (unsigned char *)&q + n && *p != 255;) { if (*p == 0) { p++; continue; } if (*p == 53) type = p[2]; p += 2 + p[1]; }
        if (type != 1 && type != 3) continue;
        struct dhcp r; memset(&r, 0, sizeof r);
        r.op = 2; r.htype = q.htype; r.hlen = q.hlen; r.xid = q.xid; r.flags = q.flags; r.yiaddr = cli; r.siaddr = srv; memcpy(r.chaddr, q.chaddr, 16); r.magic = htonl(0x63825363);
        unsigned char *o = r.opts, t = (type == 1) ? 2 : 5; unsigned lease = htonl(3600), half = htonl(1800), seven = htonl(3150);
        o = put(o, 53, 1, &t); o = put(o, 54, 4, &srv); o = put(o, 51, 4, &lease); o = put(o, 58, 4, &half); o = put(o, 59, 4, &seven);
        o = put(o, 1, 4, &mask); o = put(o, 3, 4, &srv); o = put(o, 6, 4, &dns); *o++ = 255;
        struct sockaddr_in to = { .sin_family = AF_INET, .sin_port = htons(68), .sin_addr.s_addr = INADDR_BROADCAST };
        sendto(s, &r, (char *)o - (char *)&r, 0, (struct sockaddr *)&to, sizeof to);
        fprintf(stderr, "dhcp %s -> %s\n", type == 1 ? "DISCOVER/OFFER" : "REQUEST/ACK", argv[3]);
    }
}
