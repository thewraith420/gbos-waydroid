# Cutting a release (maintainers)

Nothing here uploads anything by itself.

1. **Clean tree.** `tools/scrub.sh` must pass. It fails on keys, tokens, personal paths, third-party binaries and
   anything over 5 MiB. Add your own identifiers, one regex per line, to `~/.config/gbos-scrub-extra`.
2. **Build the artifacts.**
   ```sh
   tools/make-release.sh --out ~/gbos-dist --shim /path/to/the/tested/vulkan.hybrid64.so --write-lock
   ```
   It re-runs the scrub, builds `input-bridge`, `vpd-stub`, `libgb-sf-waydroid.so`, `libgb-hwc-waydroid.so`,
   and the Vulkan shim with the pinned NDK (r27c, checked by
   revision), copies in the tested shim if you pass one, writes `SHA256SUMS`, builds a deterministic source archive, and
   with `--write-lock` rewrites [`prebuilt.sha256`](../prebuilt.sha256).
3. **Commit `prebuilt.sha256` with the release**, so a checkout names exactly the assets it expects.
4. **Publish** with the commands the script prints (`git tag`, `gh release create ...`).
5. **Check from the outside.** On a clean directory:
   `installer/gbos-install.py fetch-prebuilt --url <release URL> --dest D` must verify, and
   `installer/gbos-install.py prepare ... --ndk auto` must say each binary "matches the published checksum".

Why it is safe to trust a downloaded prebuilt: the expected checksums live in the repository, not next to the
download, and the Android binaries are reproducible. Rebuilding gives identical bytes. The shim is the exception: the tested
binary was linked with host `gcc`, so only the download can be checked against `prebuilt.sha256`, not rebuilt.

`VERSION` holds the release name. Bump it first.
