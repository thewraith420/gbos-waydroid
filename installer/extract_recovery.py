#!/usr/bin/env python3
"""Pull logical partitions (system_a, product_a, ...) out of a Googlebook recovery image.

The recovery image is a raw disk image (GPT). One GPT partition, "super", holds Android
dynamic partitions described by LP ("liblp") metadata. This tool parses both properly and
verifies their checksums, so it does not depend on any hard-coded offsets.

Input may be:
  * the recovery.zip as downloaded (the disk image inside is read as a forward-only stream;
    no 19 GB temporary file is needed, but the whole stream is decompressed once), or
  * the already-extracted android-desktop_signed_recovery_image.bin (random access, fast).

    extract_recovery.py IMAGE --list
    extract_recovery.py IMAGE --extract system_a product_a system_ext_a --out DIR
    extract_recovery.py IMAGE --extract system_a --out DIR --sha256 system_a=<hex>

Python 3.8+, standard library only.
"""
import argparse
import hashlib
import os
import struct
import sys
import zlib
import zipfile

SECTOR = 512
GPT_SIG = b"EFI PART"
LP_GEOMETRY_MAGIC = 0x616C4467        # "gDla"
LP_HEADER_MAGIC = 0x414C5030          # "0PLA"
LP_RESERVED = 4096                    # LP_PARTITION_RESERVED_BYTES
LP_GEOMETRY_SIZE = 4096
LP_EXTENT_LINEAR, LP_EXTENT_ZERO = 0, 1


class Source:
    """Forward-only reader over either a seekable file or a zip member stream."""

    def __init__(self, path):
        self.path = path
        self.pos = 0
        if path.lower().endswith(".zip"):
            z = zipfile.ZipFile(path)
            bins = [i for i in z.infolist() if i.filename.lower().endswith(".bin")]
            if len(bins) != 1:
                sys.exit("expected exactly one .bin inside the zip, found %d" % len(bins))
            self.size = bins[0].file_size
            self.f = z.open(bins[0])
            self.zip = z
        else:
            self.f = open(path, "rb")
            self.size = os.path.getsize(path)
            self.zip = None

    def skip_to(self, off):
        if off < self.pos:
            if self.zip is not None:
                sys.exit("internal error: stream cannot go backwards (%d < %d)" % (off, self.pos))
            self.f.seek(off)
            self.pos = off
            return
        if self.zip is None:
            self.f.seek(off)
            self.pos = off
            return
        left = off - self.pos
        while left:
            n = len(self.f.read(min(left, 8 << 20)))
            if n == 0:
                sys.exit("image ended early while skipping")
            left -= n
        self.pos = off

    def read(self, n):
        out = bytearray()
        while len(out) < n:
            chunk = self.f.read(n - len(out))
            if not chunk:
                break
            out += chunk
        self.pos += len(out)
        return bytes(out)

    def read_at(self, off, n):
        self.skip_to(off)
        return self.read(n)


def parse_gpt(src):
    buf = src.read_at(0, 1 << 20)                        # protective MBR + GPT header + entry table live here
    hdr = buf[SECTOR:SECTOR + 92]
    if hdr[:8] != GPT_SIG:
        sys.exit("no GPT header at LBA 1: not a disk image?")
    hsize, hcrc = struct.unpack("<II", hdr[12:20])
    raw = bytearray(buf[SECTOR:SECTOR + hsize])
    raw[16:20] = b"\0\0\0\0"
    if zlib.crc32(bytes(raw)) & 0xFFFFFFFF != hcrc:
        sys.exit("GPT header CRC mismatch")
    entries_lba, n_entries, entry_size, entries_crc = struct.unpack("<QIII", hdr[72:92])
    start, end = entries_lba * SECTOR, entries_lba * SECTOR + n_entries * entry_size
    if end > len(buf):
        sys.exit("GPT entry table is beyond the first MiB; unsupported layout")
    table = buf[start:end]
    if zlib.crc32(table) & 0xFFFFFFFF != entries_crc:
        sys.exit("GPT entries CRC mismatch")
    parts = {}
    for i in range(n_entries):
        e = table[i * entry_size:(i + 1) * entry_size]
        if e[:16] == b"\0" * 16:
            continue
        first, last = struct.unpack("<QQ", e[32:48])
        name = e[56:128].decode("utf-16-le").split("\0")[0]
        parts[name] = (first * SECTOR, (last - first + 1) * SECTOR)
    return parts


def parse_lp(src, super_off):
    geo = src.read_at(super_off + LP_RESERVED, LP_GEOMETRY_SIZE)
    magic, gsize = struct.unpack("<II", geo[:8])
    if magic != LP_GEOMETRY_MAGIC:
        sys.exit("super partition has no LP geometry (magic %#x)" % magic)
    chk = bytearray(geo[:gsize])
    want = bytes(chk[8:40])
    chk[8:40] = b"\0" * 32
    if hashlib.sha256(bytes(chk)).digest() != want:
        sys.exit("LP geometry checksum mismatch")
    meta_max, slots, lbs = struct.unpack("<III", geo[40:52])

    meta_off = super_off + LP_RESERVED + 2 * LP_GEOMETRY_SIZE        # primary metadata, slot 0
    meta = src.read_at(meta_off, meta_max)
    hdr = meta[:256]
    magic, major, minor, hsize = struct.unpack("<IHHI", hdr[:12])
    if magic != LP_HEADER_MAGIC:
        sys.exit("bad LP metadata magic %#x" % magic)
    hchk = hdr[12:44]
    tables_size = struct.unpack("<I", hdr[44:48])[0]
    tchk = hdr[48:80]
    h = bytearray(hdr[:hsize])
    h[12:44] = b"\0" * 32
    if hashlib.sha256(bytes(h)).digest() != hchk:
        sys.exit("LP header checksum mismatch")
    if hsize + tables_size > meta_max:
        sys.exit("LP tables larger than metadata_max_size")
    tables = meta[hsize:hsize + tables_size]
    if hashlib.sha256(tables).digest() != tchk:
        sys.exit("LP tables checksum mismatch")

    def desc(o):
        return struct.unpack("<III", hdr[o:o + 12])          # offset, count, entry size

    p_off, p_n, p_sz = desc(80)
    e_off, e_n, e_sz = desc(92)
    b_off, b_n, b_sz = desc(116)
    if b_n != 1:
        sys.exit("expected a single block device in super, found %d" % b_n)
    extents = []
    for i in range(e_n):
        o = e_off + i * e_sz
        nsec, ttype, tdata = struct.unpack("<QIQ", tables[o:o + 20])
        extents.append((nsec, ttype, tdata))
    out = {}
    for i in range(p_n):
        o = p_off + i * p_sz
        name = tables[o:o + 36].split(b"\0")[0].decode()
        attrs, first, count = struct.unpack("<III", tables[o + 36:o + 48])
        out[name] = extents[first:first + count]
    return out, (major, minor)


def part_size(extents):
    return sum(e[0] for e in extents) * SECTOR


def extract(src, super_off, lp, wanted, outdir, expect):
    """One forward pass: write every wanted partition in image order."""
    jobs = []                                              # (abs_offset, length, handle, out_pos)
    handles = {}
    for name in wanted:
        if name not in lp:
            sys.exit("partition %r not in super (have: %s)" % (name, ", ".join(sorted(lp))))
        path = os.path.join(outdir, name + ".img")
        handles[name] = open(path, "wb")
        pos = 0
        for nsec, ttype, tdata in lp[name]:
            ln = nsec * SECTOR
            if ttype == LP_EXTENT_LINEAR:
                jobs.append((super_off + tdata * SECTOR, ln, name, pos))
            elif ttype != LP_EXTENT_ZERO:
                sys.exit("unknown extent type %d in %s" % (ttype, name))
            pos += ln
        handles[name].truncate(pos)
    jobs.sort()
    written = {n: 0 for n in wanted}
    for abs_off, ln, name, pos in jobs:
        src.skip_to(abs_off)
        h = handles[name]
        h.seek(pos)
        left = ln
        while left:
            chunk = src.read(min(left, 8 << 20))
            if not chunk:
                sys.exit("image ended while reading %s" % name)
            h.write(chunk)
            left -= len(chunk)
        written[name] += ln
        sys.stderr.write("\r%-14s %5.1f%%" % (name, 100.0 * written[name] / max(1, part_size(lp[name]))))
        sys.stderr.flush()
    sys.stderr.write("\n")
    for name, h in handles.items():
        h.close()
        digest = hashlib.sha256()
        with open(os.path.join(outdir, name + ".img"), "rb") as f:
            for blk in iter(lambda: f.read(8 << 20), b""):
                digest.update(blk)
        hx = digest.hexdigest()
        print("%-14s %12d bytes  sha256 %s" % (name, written[name], hx))
        if name in expect and expect[name].lower() != hx:
            sys.exit("sha256 mismatch for %s: expected %s" % (name, expect[name]))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("image", help="recovery.zip or the extracted .bin")
    ap.add_argument("--list", action="store_true", help="list GPT partitions and logical partitions")
    ap.add_argument("--extract", nargs="+", metavar="NAME", help="logical partitions to write as NAME.img")
    ap.add_argument("--out", default=".", help="output directory (default: .)")
    ap.add_argument("--sha256", action="append", default=[], metavar="NAME=HEX", help="expected hash of an output")
    a = ap.parse_args()
    if not a.list and not a.extract:
        ap.error("give --list and/or --extract")
    expect = dict(s.split("=", 1) for s in a.sha256)

    src = Source(a.image)
    gpt = parse_gpt(src)
    if "super" not in gpt:
        sys.exit("no 'super' partition in GPT (have: %s)" % ", ".join(gpt))
    super_off, super_len = gpt["super"]
    lp, ver = parse_lp(src, super_off)
    if a.list:
        print("image: %d bytes" % src.size)
        print("GPT partitions:")
        for n, (off, ln) in sorted(gpt.items(), key=lambda kv: kv[1][0]):
            print("  %-22s offset %12d  size %8.1f MiB" % (n, off, ln / 2**20))
        print("super (LP metadata v%d.%d) logical partitions:" % ver)
        for n, es in sorted(lp.items()):
            if es:
                print("  %-22s %9.1f MiB  extents=%d" % (n, part_size(es) / 2**20, len(es)))
    if a.extract:
        os.makedirs(a.out, exist_ok=True)
        extract(src, super_off, lp, a.extract, a.out, expect)


if __name__ == "__main__":
    main()
