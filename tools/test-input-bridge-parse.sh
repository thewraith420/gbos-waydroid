#!/bin/bash
# Host-side unit test of input-bridge's persist.gbos.display parser. The function is cut out of native/input-bridge.c
# between its BEGIN/END markers, so what is tested is exactly what ships. Needs only gcc.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
T="$(mktemp -d "${TMPDIR:-/var/tmp}/ibtest.XXXXXX")"; trap 'rm -r "$T"' EXIT
{ echo '#include <stdio.h>'; sed -n '/BEGIN parse_display/,/END parse_display/p' "$ROOT/native/input-bridge.c"; cat <<'C'
static int fails;
static void t(const char *in, int want, int ww, int wh) {
    int w = -1, h = -1, r = parse_display(in, &w, &h);
    int ok = (r == want) && (!want || (w == ww && h == wh)) && (want || (w == -1 && h == -1));
    printf("%-4s %-18s -> %d %dx%d\n", ok ? "ok" : "FAIL", in ? in : "(null)", r, w, h);
    if (!ok) fails++;
}
int main(void) {
    t("1920x1080", 1, 1920, 1080);  t("1280x800", 1, 1280, 800);   t("2160x1440", 1, 2160, 1440);
    t("320x320", 1, 320, 320);      t("8192x8192", 1, 8192, 8192);
    t("319x800", 0, 0, 0);          t("1280x319", 0, 0, 0);        t("8193x800", 0, 0, 0);   t("100x100", 0, 0, 0);
    t("1280x", 0, 0, 0);            t("x800", 0, 0, 0);            t("abc", 0, 0, 0);        t("", 0, 0, 0);
    t("-1280x800", 0, 0, 0);        t("1280X800", 0, 0, 0);        t("1280x800junk", 0, 0, 0);
    t("1280x800\n", 0, 0, 0);       t("1280 x 800", 0, 0, 0);      t(NULL, 0, 0, 0);
    printf("%s\n", fails ? "FAILED" : "all passed");
    return fails != 0;
}
C
} > "$T/t.c"
gcc -Wall -Werror -o "$T/t" "$T/t.c" && "$T/t"
