#!/bin/bash
# Functional test of the legacy iptables tables, in a throw-away network namespace (touches no real host rules).
# Exercises the rule shapes Android's netd uses: all four tables, IPv4 and IPv6, owner/mark/conntrack matches,
# MARK/CONNMARK/REJECT targets, and the iptables-restore round trip. Run as root:  sudo kernel/test-legacy-tables.sh
set -u
[ "$(id -u)" = 0 ] || { echo "run as root"; exit 1; }
NS=gbos-nftest-$$
ip netns add "$NS" || { echo "cannot create a network namespace"; exit 1; }
ERR=$(mktemp); SAVE=$(mktemp)
trap 'ip netns del "$NS" 2>/dev/null; rm -f "$ERR" "$SAVE"' EXIT
fail=0; pass=0
t() {   # t "description" command...   (runs inside the namespace)
    local what=$1; shift
    if ip netns exec "$NS" "$@" >/dev/null 2>"$ERR"; then pass=$((pass+1)); printf 'ok    %s\n' "$what"
    else fail=$((fail+1)); printf 'FAIL  %s\n        %s\n' "$what" "$(head -c 200 "$ERR")"; fi
}
for v in 4 6; do
    if [ $v = 4 ]; then I=iptables-legacy; else I=ip6tables-legacy; fi
    t "IPv$v filter   DROP tcp/5555 (the adb rule gb-lan.sh adds)"  $I -t filter -A INPUT -p tcp --dport 5555 -j DROP
    t "IPv$v filter   owner match + REJECT"                          $I -t filter -A OUTPUT -m owner --uid-owner 10100 -j REJECT
    t "IPv$v filter   conntrack match"                               $I -t filter -A FORWARD -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
    t "IPv$v nat      MASQUERADE"                                    $I -t nat -A POSTROUTING -o lo -j MASQUERADE
    t "IPv$v mangle   MARK"                                          $I -t mangle -A PREROUTING -j MARK --set-mark 0x10064
    t "IPv$v mangle   mark match + CONNMARK"                         $I -t mangle -A POSTROUTING -m mark --mark 0x10064 -j CONNMARK --save-mark
    t "IPv$v raw      PREROUTING rule"                               $I -t raw -A PREROUTING -p tcp --dport 9 -j ACCEPT
    t "IPv$v user chain create + jump"                               sh -c "$I -t filter -N gbtest && $I -t filter -A INPUT -j gbtest"
done
# netd loads rules with iptables-restore: save what we built, flush, restore, compare
ip netns exec "$NS" iptables-legacy-save > "$SAVE" 2>"$ERR"
t "IPv4 save wrote rules for all four tables" test "$(grep -c '^\*' "$SAVE")" -ge 4
t "IPv4 flush"                                  sh -c "iptables-legacy -t filter -F && iptables-legacy -t nat -F && iptables-legacy -t mangle -F && iptables-legacy -t raw -F"
t "IPv4 iptables-restore of the saved rules"   sh -c "iptables-legacy-restore < $SAVE"
t "IPv4 restored rules are present"            sh -c "iptables-legacy -S INPUT | grep -q 'dport 5555 -j DROP'"
echo
echo "passed: $pass   failed: $fail"
[ $fail = 0 ] && echo "RESULT: the legacy iptables tables work" || echo "RESULT: FAILED"
exit $fail
