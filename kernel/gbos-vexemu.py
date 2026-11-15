#!/usr/bin/env python3
"""gbos-vexemu: run GBOS on a CPU that lacks GFNI / VAES / VPCLMULQDQ / AVX-VNNI / SHA-NI, with a loadable kernel module.

GBOS's userland (a Google/Intel-targeted Android build) uses those instructions unconditionally. On a CPU without them the
first process dies with SIGILL. The module `gbos_vexemu` catches that invalid-opcode trap for 64-bit user code, emulates the
instruction (GFNI, VAES, VPCLMULQDQ, AVX-VNNI, AVX-IFMA, SHA-NI) and resumes, so nothing else has to change. It needs no kernel
patch and no kernel rebuild: it hooks the kernel's die-notifier chain through exported symbols only.

    gbos-vexemu.py build     --tree KERNEL_BUILD_TREE --out DIR     build + self-verify (no root; downloads the pinned patch)
    gbos-vexemu.py verify    --tree KERNEL_BUILD_TREE --out DIR     re-run the checks
    sudo gbos-vexemu.py install   --out DIR                          install into updates/, depmod, load it now + at every boot
    sudo gbos-vexemu.py uninstall                                    unload and remove everything
         gbos-vexemu.py status

KERNEL_BUILD_TREE is the tree your running kernel was built in (or its linux-headers package, which has the same files): it
needs .config, Module.symvers and include/generated/. It must be the very build that is running.

Costs and limits (read before relying on it):
  * every emulated instruction is a trap into the kernel (microseconds), so code that uses these instructions in hot loops
    (checksums, crypto, media codecs) runs much slower than on a CPU that has them. It is a way to run GBOS, not to run it fast;
  * a memory operand that cannot be read gets the normal SIGILL, not the SIGSEGV hardware would give;
  * unsigned module: it taints the kernel, and Secure Boot / enforced module signatures would refuse it;
  * needs CONFIG_PREEMPT_RCU (every common desktop distribution kernel has it; the build checks).
Parameters (/sys/module/gbos_vexemu/parameters/): enabled (0/1), verbose (0/1), stats (per-instruction counters).

Licensing: the module source is GPL-2.0. The arithmetic it uses is NOT shipped here: it is cut out of the pinned third-party
patch (project-churro, vex-emulate.patch, GPL-2.0) at build time, after its sha256 is checked, and was verified against
real hardware. Python 3.8+, standard library only; needs make and the compiler the kernel was built with.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request

PATCH_URL = "https://raw.githubusercontent.com/alesimula/project-churro/main/kmod/patches/vex-emulate.patch"
PATCH_SHA256 = "d8decdcffc7a87aea0386ec3ca8476e607f5a26f0ef127e4f051f65ab84e8e0f"
MATH_SHA256 = "d58d7fced360f3781e0d2b3dd61d74e67f00671e0a22a9173b7d86be9967568c"   # of the extracted text, below
MOD = "gbos_vexemu"
SUBDIR = "updates/gbos-vexemu"
LOADCONF = "/etc/modules-load.d/gbos-vexemu.conf"
CPU_NEED = ["gfni", "vaes", "vpclmulqdq", "avx_vnni", "sha_ni"]
ROOT = "/"
HERE = os.path.dirname(os.path.abspath(__file__))


def P(p):
    return p if ROOT == "/" else os.path.join(ROOT, p.lstrip("/"))


def die(msg):
    sys.exit("gbos-vexemu: " + msg)


def say(msg):
    print(msg, flush=True)


def sha256_bytes(b):
    return hashlib.sha256(b).hexdigest()


def sha256(path):
    with open(path, "rb") as f:
        return sha256_bytes(f.read())


def tool(name):
    for d in os.environ.get("PATH", "").split(os.pathsep) + ["/sbin", "/usr/sbin"]:
        p = os.path.join(d, name)
        if os.path.isfile(p) and os.access(p, os.X_OK):
            return p
    return None


def run(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, **kw)


def read_symvers(path):
    out = {}
    with open(path) as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) >= 3:
                out[p[1]] = (p[0], p[2])
    return out


def dump_versions(ko):
    r = run([tool("modprobe") or "modprobe", "--dump-modversions", ko])
    d = {}
    for line in r.stdout.splitlines():
        p = line.split()
        if len(p) == 2:
            d[p[1]] = p[0]
    return d


def tree_info(tree):
    need = [".config", "Module.symvers", "include/generated/utsrelease.h", "include/generated/utsversion.h",
            "scripts/mod/modpost", "Makefile"]
    miss = [n for n in need if not os.path.exists(os.path.join(tree, n))]
    if miss:
        die("%s is not a built kernel tree; missing: %s" % (tree, ", ".join(miss)))
    rel = re.search(r'"(.*)"', open(os.path.join(tree, "include/generated/utsrelease.h")).read()).group(1)
    ver = re.search(r'UTS_VERSION "(.*)"', open(os.path.join(tree, "include/generated/utsversion.h")).read()).group(1)
    cfg = open(os.path.join(tree, ".config")).read()
    return {"release": rel, "version": ver, "clang": "CONFIG_CC_IS_CLANG=y" in cfg, "cfg": cfg}


def extract_math(patch_bytes):
    """The arithmetic helpers (~370 lines) from the pinned patch, as text. Refuses any other patch."""
    if sha256_bytes(patch_bytes) != PATCH_SHA256:
        die("the patch does not match the pinned sha256 %s; refusing (the extraction anchors are only valid for that file)" % PATCH_SHA256)
    added = [l[1:] for l in patch_bytes.decode().splitlines() if l.startswith("+") and not l.startswith("+++")]
    start = next(i for i, l in enumerate(added) if l.startswith("static inline s32 churro_sat_s32"))
    rd = next(i for i, l in enumerate(added) if l.startswith("static void churro_vex_read("))
    end = rd
    while end > start and not (added[end - 1].startswith("/*") and added[end - 1].strip() == "/*"):
        end -= 1                                            # back over the comment block in front of churro_vex_read
    if end <= start:
        die("extraction anchor not found")
    end -= 1
    while added[end - 1].strip() == "":
        end -= 1
    text = "\n".join(added[start:end]) + "\n"
    if sha256_bytes(text.encode()) != MATH_SHA256:
        die("the extracted arithmetic does not match its pinned sha256; refusing")
    return "/* generated by gbos-vexemu.py from the pinned vex-emulate patch (GPL-2.0) - do not edit */\n" + text


def get_patch(path, out):
    if path:
        return open(path, "rb").read()
    cache = os.path.join(out, "vex-emulate.patch")
    if os.path.isfile(cache) and sha256(cache) == PATCH_SHA256:
        return open(cache, "rb").read()
    say("downloading %s" % PATCH_URL)
    try:
        data = urllib.request.urlopen(PATCH_URL, timeout=60).read()
    except Exception as e:                                   # noqa: BLE001 - any network failure is the same advice
        die("download failed (%s); fetch the patch yourself and pass --patch FILE" % e)
    if sha256_bytes(data) != PATCH_SHA256:
        die("downloaded patch has sha256 %s, expected %s; refusing" % (sha256_bytes(data), PATCH_SHA256))
    open(cache, "wb").write(data)
    return data


# ------------------------------------------------------------------------------------------ build
def cmd_build(a):
    tree = os.path.abspath(a.tree)
    info = tree_info(tree)
    cfg = info["cfg"]
    if "CONFIG_X86_64=y" not in cfg:
        die("x86-64 kernels only")
    if "CONFIG_MODULES=y" not in cfg:
        die("this kernel has no module support")
    if "CONFIG_PREEMPT_RCU=y" not in cfg:
        die("this kernel is built without CONFIG_PREEMPT_RCU; the module sleeps (user page faults) inside the trap notifier and needs it")
    if "CONFIG_MODULE_SIG_FORCE=y" in cfg:
        die("this kernel only loads signed modules (CONFIG_MODULE_SIG_FORCE=y); an unsigned module would be refused")
    running = os.uname().release
    if not a.allow_other:
        if info["release"] != running:
            die("the tree is for %s but %s is running (use --allow-other to build for another machine)" % (info["release"], running))
        if run(["uname", "-v"]).stdout.strip() != info["version"]:
            die("same release string but a different build; the module would not match the kernel's symbol checksums")
    out = os.path.abspath(a.out)
    work = os.path.join(out, "work")
    os.makedirs(out, exist_ok=True)
    if os.path.isdir(work):
        shutil.rmtree(work)
    os.makedirs(work)
    math_text = extract_math(get_patch(a.patch, out))
    srcdir = os.path.join(HERE, "vexemu")
    shutil.copy2(os.path.join(srcdir, MOD + ".c"), work)
    with open(os.path.join(work, "churro_math.inc"), "w") as f:
        f.write(math_text)
    with open(os.path.join(work, "Makefile"), "w") as f:
        f.write("# generated by gbos-vexemu.py\nobj-m += %s.o\n" % MOD)
    cmd = ["make", "-C", tree, "M=" + work, "modules"]
    if info["clang"]:
        if not (tool("clang") and tool("ld.lld")):
            die("the kernel was built with clang; install clang and lld (the same major version if you can) and retry")
        cmd.insert(1, "LLVM=1")
    say("building %s for %s with %s" % (MOD, info["release"], "clang/LLVM" if info["clang"] else "the default compiler"))
    pre = (["nice", "-n", "10"] if tool("nice") else [])
    r = run(pre + cmd)
    open(os.path.join(out, "build.log"), "w").write(r.stdout + r.stderr)
    if r.returncode != 0:
        die("the build failed; see %s\n%s" % (os.path.join(out, "build.log"), (r.stdout + r.stderr)[-1500:]))
    kos = os.path.join(out, "modules")
    os.makedirs(kos, exist_ok=True)
    dst = os.path.join(kos, MOD + ".ko")
    shutil.copy2(os.path.join(work, MOD + ".ko"), dst)
    with open(os.path.join(out, "manifest.json"), "w") as f:
        json.dump({"release": info["release"], "version": info["version"], "sha256": sha256(dst)}, f, indent=2)
    return do_verify(tree, out, info)


def do_verify(tree, out, info=None):
    info = info or tree_info(tree)
    ok = True

    def check(good, text):
        nonlocal ok
        say("  [%s] %s" % ("ok" if good else "FAIL", text))
        ok = ok and good

    say("verifying the build in %s" % out)
    ko = os.path.join(out, "modules", MOD + ".ko")
    check(os.path.isfile(ko), "module present")
    if not os.path.isfile(ko):
        return 1
    vm = run([tool("modinfo") or "modinfo", "-F", "vermagic", ko]).stdout.strip()
    check(vm.startswith(info["release"] + " "), "vermagic is '%s'" % vm)
    lic = run([tool("modinfo") or "modinfo", "-F", "license", ko]).stdout.strip()
    check(lic == "GPL", "license is GPL (it uses GPL-only kernel symbols)")
    kernel = {s: c for s, (c, _m) in read_symvers(os.path.join(tree, "Module.symvers")).items()}
    imp = dump_versions(ko)
    bad = [(s, c, kernel.get(s)) for s, c in imp.items() if kernel.get(s) != c]
    check(imp and not bad, "all %d imported symbols are exported by the kernel with matching checksums%s"
          % (len(imp), "" if not bad else "; e.g. %s" % (bad[0],)))
    say("RESULT: %s" % ("verified; safe to install" if ok else "NOT VERIFIED; do not install"))
    return 0 if ok else 1


def cmd_verify(a):
    return do_verify(os.path.abspath(a.tree), os.path.abspath(a.out))


# ------------------------------------------------------------------------------------------ install
def need_root():
    if ROOT == "/" and os.geteuid() != 0:
        die("this needs root: sudo %s" % " ".join(sys.argv))


def depmod(rel):
    if not tool("depmod"):
        die("depmod not found")
    r = run([tool("depmod")] + (["-b", ROOT] if ROOT != "/" else []) + ["-a", rel])
    if r.returncode != 0:
        die("depmod failed: " + r.stderr.strip())


def loaded():
    return os.path.isdir("/sys/module/" + MOD)


def cmd_install(a):
    need_root()
    out = os.path.abspath(a.out)
    try:
        man = json.load(open(os.path.join(out, "manifest.json")))
    except (OSError, ValueError):
        die("%s has no manifest.json: run `build` first" % out)
    rel = man["release"]
    if ROOT == "/":
        if os.uname().release != rel or run(["uname", "-v"]).stdout.strip() != man["version"]:
            die("this module was built for %s (%s), which is not the running kernel" % (rel, man["version"]))
    src = os.path.join(out, "modules", MOD + ".ko")
    if not os.path.isfile(src) or sha256(src) != man["sha256"]:
        die("%s is missing or does not match the manifest (rebuild)" % src)
    dest = P("/lib/modules/%s/%s" % (rel, SUBDIR))
    os.makedirs(dest, exist_ok=True)
    shutil.copyfile(src, os.path.join(dest, MOD + ".ko"))
    os.chmod(os.path.join(dest, MOD + ".ko"), 0o644)
    depmod(rel)
    os.makedirs(os.path.dirname(P(LOADCONF)), exist_ok=True)
    with open(P(LOADCONF), "w") as f:
        f.write("# written by gbos-vexemu.py: load the #UD emulator at boot, before any container starts\n%s\n" % MOD)
    say("installed %s, ran depmod, and set it to load at every boot (%s)." % (os.path.join(dest, MOD + ".ko"), LOADCONF))
    if ROOT == "/" and not a.no_load:
        if loaded():
            say("already loaded (unload it first to pick up a rebuilt one: sudo modprobe -r %s)" % MOD)
        else:
            r = run([tool("modprobe") or "modprobe", MOD])
            if r.returncode != 0:
                die("installed, but loading failed: %s\n(dmesg | tail shows why)" % (r.stderr.strip() or r.stdout.strip()))
            say("loaded now; no reboot needed.")
    say("Check it:  %s status      Undo it:  sudo %s uninstall" % (sys.argv[0], sys.argv[0]))
    return 0


def cmd_uninstall(a):
    need_root()
    rel = a.release or os.uname().release
    if ROOT == "/" and loaded():
        r = run([tool("modprobe") or "modprobe", "-r", MOD])
        say("unloaded." if r.returncode == 0 else "could not unload (%s); it goes away at reboot" % r.stderr.strip())
    dest = P("/lib/modules/%s/%s" % (rel, SUBDIR))
    if os.path.isdir(dest):
        shutil.rmtree(dest)
        try:
            os.rmdir(os.path.dirname(dest))
        except OSError:
            pass
        depmod(rel)
    if os.path.isfile(P(LOADCONF)):
        os.remove(P(LOADCONF))
    say("removed the module, its load-at-boot entry, and ran depmod.")
    return 0


def cmd_status(a):
    rel = os.uname().release
    dest = P("/lib/modules/%s/%s" % (rel, SUBDIR))
    flags = set()
    try:
        for l in open("/proc/cpuinfo"):
            if l.startswith("flags"):
                flags = set(l.split(":", 1)[1].split())
                break
    except OSError:
        pass
    miss = [f for f in CPU_NEED if f not in flags]
    say("kernel %s" % rel)
    say("cpu lacks: %s" % (", ".join(miss) if miss else "nothing (this CPU needs no emulation)"))
    say("installed: %s" % ("yes" if os.path.isfile(os.path.join(dest, MOD + ".ko")) else "no"))
    say("at boot:   %s" % ("yes" if os.path.isfile(P(LOADCONF)) else "no"))
    say("loaded:    %s" % ("yes" if loaded() else "no"))
    if loaded():
        base = "/sys/module/%s/parameters/" % MOD
        for p in ("enabled", "verbose"):
            try:
                say("  %-8s %s" % (p, open(base + p).read().strip()))
            except OSError:
                pass
        try:
            say("emulated so far:")
            for l in open(base + "stats").read().split():
                if not l.endswith("=0"):
                    say("  " + l)
        except OSError:
            pass
    return 0


def main():
    global ROOT
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", default="/", help=argparse.SUPPRESS)
    sub = ap.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("build")
    b.add_argument("--tree", required=True)
    b.add_argument("--out", required=True)
    b.add_argument("--patch", help="a local copy of the pinned vex-emulate.patch (default: download it)")
    b.add_argument("--allow-other", action="store_true", help="build for a kernel that is not the one running here")
    b.set_defaults(fn=cmd_build)
    m = sub.add_parser("math", help=argparse.SUPPRESS)       # used by vexemu/test/test-qemu.sh
    m.add_argument("--patch", required=True)
    m.add_argument("--out", required=True)
    m.set_defaults(fn=lambda a: open(a.out, "w").write(extract_math(open(a.patch, "rb").read())) and 0)
    v = sub.add_parser("verify")
    v.add_argument("--tree", required=True)
    v.add_argument("--out", required=True)
    v.set_defaults(fn=cmd_verify)
    i = sub.add_parser("install")
    i.add_argument("--out", required=True)
    i.add_argument("--no-load", action="store_true", help="install only; load at the next boot")
    i.set_defaults(fn=cmd_install)
    u = sub.add_parser("uninstall")
    u.add_argument("--release")
    u.set_defaults(fn=cmd_uninstall)
    s = sub.add_parser("status")
    s.set_defaults(fn=cmd_status)
    a = ap.parse_args()
    ROOT = os.path.abspath(a.root)
    return a.fn(a)


if __name__ == "__main__":
    sys.exit(main())
