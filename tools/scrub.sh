#!/bin/bash
# Fails (exit 1) if the tree contains anything that must never be published:
# private identifiers, credentials, keys, captures, or third-party binaries.
# Usage: tools/scrub.sh [DIR]      (default: the repo root). Run before every commit.
# Add your own identifiers, one regex per line, to ~/.config/gbos-scrub-extra (kept out of the repo).
set -u
ROOT="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
fail=0
note() { echo "SCRUB: $*"; fail=1; }

# 1) forbidden file names / types
while IFS= read -r f; do note "forbidden file: $f"; done < <(
  find . -path ./.git -prune -o -type f \( \
    -name 'adbkey*' -o -name '*.keystore' -o -name '*.jks' -o -name '*.p12' -o -name '*.pem' -o -name '*.key' \
    -o -name 'debug.keystore' -o -name '*.img' -o -name '*.apk' -o -name '*.apkm' -o -name '*.apks' \
    -o -name '*.zst' -o -name '*.db' -o -name 'logcat*' -o -name 'dmesg*' -o -name '*.png' -o -name '*.raw' \
    -o -name '*.pb' -o -name '.env' \) -print | grep -v '^./assets/' )
# directories that must not exist
for d in .venv venv backups build; do
  while IFS= read -r x; do note "forbidden dir: $x"; done < <(find . -path ./.git -prune -o -type d -name "$d" -print)
done

# 2) forbidden content (text files only)
PATTERNS=(
  '/home/[a-z][a-z0-9_-]*/'          # any home path
  'P:192\.168\.240\.(?!(0|1|112)\b)[0-9]+'   # any other container address (.1 = Waydroid's bridge, .112 = the address its docs use)
  '\b10\.0\.0\.[0-9]+'               # home LAN
  '\b10\.1\.10\.[0-9]+'              # other LAN seen in logs
  'wlp8s0'                          # author's Wi-Fi interface name
  'claudetest'                      # old package namespace
  '8cd17a28e47ef68f'                 # old agent token
  'ya29\.[A-Za-z0-9_-]{20,}'         # google oauth token
  'AIza[0-9A-Za-z_-]{30,}'           # google api key
  'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY'
  '[A-Za-z0-9._%+-]+@gmail\.com'
  '\b[Bb]ob(z|\b)'                  # author handle / kernel name
  '\bLOQ\b'                         # author's hardware
)
[ -f "$HOME/.config/gbos-scrub-extra" ] && while IFS= read -r l; do [ -n "$l" ] && PATTERNS+=("$l"); done < "$HOME/.config/gbos-scrub-extra"
for p in "${PATTERNS[@]}"; do
  mode=-E; pat=$p
  case "$p" in P:*) mode=-P; pat=${p#P:} ;; esac       # "P:" prefix = PCRE (needed for the lookahead)
  hits=$(grep -rIl $mode --exclude-dir=.git --exclude=scrub.sh -e "$pat" . 2>/dev/null)
  [ -n "$hits" ] && while IFS= read -r h; do note "pattern /$p/ in $h"; done <<<"$hits"
done

# 3) oversize files (probably a dump)
while IFS= read -r f; do note "file over 5 MiB: $f"; done < <(find . -path ./.git -prune -o -type f -size +5M -print)

if [ $fail -eq 0 ]; then echo "SCRUB: clean"; else echo "SCRUB: FAILED"; fi
exit $fail
