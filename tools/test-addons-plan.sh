#!/bin/bash
# Host-side test of the installer's add-on detection (gbos-install.py addons_plan): simulated CPUs, security modules and kernel configs.
# Needs no root, no kernel and no network. Exit status 0 = all cases pass.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 -I - "$ROOT" <<'PY'
import importlib.util, sys
root = sys.argv[1]
spec = importlib.util.spec_from_file_location("gi", root + "/installer/gbos-install.py")
gi = importlib.util.module_from_spec(spec); spec.loader.exec_module(gi)

FULL = {"gfni", "vaes", "vpclmulqdq", "avx_vnni", "sha_ni", "avx2"}
OLD = {"avx2", "aes"}
GOOD_OPTS = {"MODULES": "y", "PREEMPT_RCU": "y", "KPROBES": "y", **{n: "y" for n in gi.LEGACY_NET}}
passed = failed = 0

def case(name, flags, lsm, fixed, loaded, opts, want, probs=(), avail=()):
    global passed, failed
    gi.cpu_flags = lambda: flags
    gi.lsm_list = lambda: lsm
    gi.kallsyms_has = lambda n: fixed
    gi.module_loaded = lambda n: n in loaded
    gi.module_available = lambda n: n in loaded or n in avail     # never the real machine's modules
    got = gi.addons_plan(opts)
    keys = sorted(g[0] for g in got)
    pr = sorted(g[0] for g in got if g[3])
    ok = keys == sorted(want) and pr == sorted(probs)
    passed += ok; failed += not ok
    print("%s  %s%s" % ("ok  " if ok else "FAIL", name, "" if ok else "   got %s (problems %s), wanted %s (problems %s)" % (keys, pr, sorted(want), sorted(probs))))

APP = ["lockdown", "capability", "apparmor"]
case("modern Intel, SELinux or not needed -> nothing", FULL, ["selinux"], None, set(), GOOD_OPTS, [])
case("modern Intel, AppArmor with the fix -> nothing", FULL, APP, True, set(), GOOD_OPTS, [])
case("old CPU -> vexemu", OLD, ["selinux"], None, set(), GOOD_OPTS, ["vexemu"])
case("old CPU, module already loaded -> nothing", OLD, ["selinux"], None, {"gbos_vexemu"}, GOOD_OPTS, [])
case("old CPU, kernel without PREEMPT_RCU -> vexemu cannot be a module", OLD, ["selinux"], None, set(), {**GOOD_OPTS, "PREEMPT_RCU": "n"}, ["vexemu"], ["vexemu"])
case("old CPU, signed modules enforced -> cannot", OLD, ["selinux"], None, set(), {**GOOD_OPTS, "MODULE_SIG_FORCE": "y"}, ["vexemu"], ["vexemu"])
case("AppArmor-only, unpatched -> secctx", FULL, APP, False, set(), GOOD_OPTS, ["secctx"])
case("AppArmor-only, unpatched, module loaded -> nothing", FULL, APP, False, {"gbos_secctx"}, GOOD_OPTS, [])
case("AppArmor-only, unpatched, no kprobes -> cannot be a module", FULL, APP, False, set(), {**GOOD_OPTS, "KPROBES": "n"}, ["secctx"], ["secctx"])
case("cannot read kallsyms -> do not guess", FULL, APP, None, set(), GOOD_OPTS, [])
case("legacy iptables missing -> netfilter", FULL, ["selinux"], None, set(), {**GOOD_OPTS, "IP_NF_NAT": "n"}, ["netfilter"])
case("legacy iptables missing, x_tables built in -> cannot", FULL, ["selinux"], None, set(), {**GOOD_OPTS, "IP_NF_NAT": "n", "NETFILTER_XTABLES": "y"}, ["netfilter"], ["netfilter"])
case("Debian: no LEGACY config symbol, but the table modules exist -> nothing", FULL, ["selinux"], None, set(),
     {**GOOD_OPTS, "NETFILTER_XTABLES_LEGACY": None, "IP_NF_NAT": "n"}, [], avail={"x_tables", "iptable_nat"})
case("the slate: old CPU + AppArmor-only + no tables -> all three", OLD, APP, False, set(), {**GOOD_OPTS, "IP_NF_FILTER": "n"}, ["vexemu", "secctx", "netfilter"])
print("%d passed, %d failed" % (passed, failed))
sys.exit(1 if failed else 0)
PY
