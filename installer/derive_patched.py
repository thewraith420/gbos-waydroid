#!/usr/bin/env python3
"""Build the patched Googlebook libraries/binaries the image needs, from the user's own system_a.img.

    derive_patched.py --system-a system_a.img --out WORKDIR [--recipe DIR] [--only NAME ...]

For each output this pulls the stock file out of the erofs image (or out of an APEX inside it), runs the
patch script(s) from recipe/ on a copy, and checks the result against a pinned sha256. A mismatch, or a patch
script that does not find its expected bytes, stops the install: the image is not the build we support.

Needs erofs-utils (`dump.erofs`) and `unzip`. Temp files go inside --out (never /tmp, which is RAM on many systems) and are removed afterwards.
"""
import argparse
import hashlib
import os
import shutil
import subprocess
import sys
import tempfile

# name -> (source, [patch steps], sha256 of the finished file)
#   source: ("erofs", path)  or  ("apex", apex path in system_a, path in the apex payload)
#   steps : ("inplace", script)  runs  script FILE
#           ("srcdst",  script)  runs  script SRC DST
# Hashes were produced by running exactly these steps on build 17.CL3B.260622.271.R1 and matching them
# against the files of a working system (see recipe/PATCHES.md).
OUTPUTS = {
    "libselinux-gb.so": (("erofs", "/system/lib64/libselinux.so"),
                         [("inplace", "patch-libselinux-base.py"), ("inplace", "patch-libselinux-ctx.py")],
                         "be256f5e6112d0baf855058839dc533a53f8f742c5139b4d0e0efc436c89e5a6"),
    "libalarm_jni-gb.so": (("erofs", "/system/lib64/libalarm_jni.so"),
                           [("srcdst", "patch-alarm-jni.py")],
                           "dacea1d71ee0d13a5bf7d38145e9f3ee48798c6cddfa4f68752c936f1d393e42"),
    "libmeminfo-gb.so": (("erofs", "/system/lib64/libmeminfo.so"),
                         [("inplace", "patch-libmeminfo-gpumem.py")],
                         "35d32f260385f09b614cb4e176a051392e45871ae84622ebc9bc86ff40defcd6"),
    "libandroid_runtime-gb.so": (("erofs", "/system/lib64/libandroid_runtime.so"),
                                 [("inplace", "patch-androidruntime-stubs.py")],
                                 "a73f1cd804c26f69934eb06e51c48d561466c6b4b520b4aff233237525cdca26"),
    "libservice-connectivity-gb.so": (("apex", "/system/apex/com.google.android.tethering.apex",
                                       "/lib64/libservice-connectivity.so"),
                                      [("srcdst", "patch-clat-selinux.py")],
                                      "62e458d5edec0cd0355e18031ad91b29dfcca03e236fd4fde9e0ef6abefa699c"),
    "netd-gb.o": (("apex", "/system/apex/com.google.android.tethering.apex", "/etc/bpf/mainline/netd.o"),
                  [("srcdst", "patch-netd-bpf.py"), ("inplace", "patch-netd-bpf-extra.py")],
                  "1849dfa1de854e258304de8a4ebc71b50d544d169bfd92149a5a548b94b25f8b"),
    "libbluetooth_jni-gb.so": (("apex", "/system/apex/com.google.android.bt.apex", "/lib64/libbluetooth_jni.so"),
                               [("srcdst", "patch-bt-alarm.py")],
                               "315ca6b3bfb19c00c3540fa6f174b040c7f9b0b8f23bb04da47737ab33d0020d"),
    "vold_prepare_subdirs-gb": (("erofs", "/system/bin/vold_prepare_subdirs"),
                                [("srcdst", "patch-vold-subdirs.py")],
                                "c9cbbf8180ae6aa9f4933aff6040b04ef3c664839235cef03435e1f0df8bb5d4"),
    # Unpatched stock inputs that bake-apps.sh patches itself (in-line); no steps, only extraction + pin.
    "libaudiohal-7.1.orig.so": (("erofs", "/system/lib64/libaudiohal@7.1.so"), [],
                                "0e87e32567a9df8e7a047804160e7058b19bcf15861c90be5f2aa74f181b4fa5"),
    "libinputflinger.orig.so": (("erofs", "/system/lib64/libinputflinger.so"), [],
                                "e27014ec1e2e4d611cdd6df2c1175d86d679d1353ee970f190e831953fbcb3e4"),
    "bpfloader-gb": (("erofs", "/system/bin/bpfloader"),
                     [("srcdst", "patch-bpfloader.py")],
                     "029390bb0ecaf2918038a221e60beae95420df9876e1fc27367aad0583f85ba2"),
}


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for blk in iter(lambda: f.read(1 << 20), b""):
            h.update(blk)
    return h.hexdigest()


def cat_from_erofs(image, path, dest):
    with open(dest, "wb") as out:
        r = subprocess.run(["dump.erofs", "--cat", "--path=" + path, image], stdout=out,
                           stderr=subprocess.PIPE)
    if r.returncode != 0 or os.path.getsize(dest) == 0:
        sys.exit("could not read %s from %s: %s" % (path, image, r.stderr.decode(errors="replace").strip()))


def fetch_source(spec, system_a, tmp, apex_cache):
    dest = os.path.join(tmp, "stock_" + os.path.basename(spec[-1]))
    if spec[0] == "erofs":
        cat_from_erofs(system_a, spec[1], dest)
        return dest
    apex, inner = spec[1], spec[2]
    if apex not in apex_cache:
        apex_file = os.path.join(tmp, os.path.basename(apex))
        cat_from_erofs(system_a, apex, apex_file)
        payload = os.path.join(tmp, os.path.basename(apex) + ".payload.img")
        with open(payload, "wb") as out:
            r = subprocess.run(["unzip", "-p", apex_file, "apex_payload.img"], stdout=out, stderr=subprocess.PIPE)
        if r.returncode != 0:
            sys.exit("could not unpack %s: %s" % (apex, r.stderr.decode(errors="replace").strip()))
        os.unlink(apex_file)
        apex_cache[apex] = payload
    cat_from_erofs(apex_cache[apex], inner, dest)
    return dest


def run_script(recipe, script, *args):
    r = subprocess.run([sys.executable, os.path.join(recipe, script), *args],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if r.returncode != 0:
        sys.exit("%s failed:\n%s%s" % (script, r.stdout, r.stderr))


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--system-a", required=True, help="system_a.img from extract_recovery.py")
    ap.add_argument("--out", required=True, help="work directory that receives the *-gb files")
    ap.add_argument("--recipe", default=os.path.join(here, "..", "recipe"), help="directory holding patch-*.py")
    ap.add_argument("--only", nargs="*", help="build only these outputs")
    a = ap.parse_args()
    for tool in ("dump.erofs", "unzip"):
        if not shutil.which(tool):
            sys.exit("%s not found (install erofs-utils / unzip)" % tool)
    os.makedirs(a.out, exist_ok=True)
    names = a.only or list(OUTPUTS)
    bad = [n for n in names if n not in OUTPUTS]
    if bad:
        sys.exit("unknown output(s): %s" % ", ".join(bad))

    with tempfile.TemporaryDirectory(prefix=".gbos-derive-", dir=a.out) as tmp:
        apex_cache = {}
        for name in names:
            source, steps, want = OUTPUTS[name]
            work = os.path.join(tmp, name)
            stock = fetch_source(source, a.system_a, tmp, apex_cache)
            shutil.copyfile(stock, work)
            for kind, script in steps:
                if kind == "inplace":
                    run_script(a.recipe, script, work)
                else:                                  # src -> dst; keep the chain going on the result
                    nxt = work + ".next"
                    run_script(a.recipe, script, work, nxt)
                    os.replace(nxt, work)
            got = sha256(work)
            if got != want:
                sys.exit("%s: result %s does not match the pinned %s. This image is not the supported build "
                         "(see sources.lock); refusing to continue." % (name, got, want))
            shutil.copyfile(work, os.path.join(a.out, name))
            print("ok  %-32s %s" % (name, got[:16]))


if __name__ == "__main__":
    main()
