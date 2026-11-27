# Native pieces of GBOS. Two toolchains:
#   * Android NDK clang  -> runs inside the container (bionic):  input-bridge, vpd-stub
#   * host gcc, static   -> runs inside the container (no libc dependency): unlock-signin, eval-js
# Prebuilt copies are published as release assets, so most users never need to run this.
#
#   make NDK=$HOME/android-ndk-r27c
NDK     ?= $(HOME)/android-ndk-r27c
TOOLBIN := $(NDK)/toolchains/llvm/prebuilt/linux-x86_64/bin
CC_AND  := $(TOOLBIN)/x86_64-linux-android34-clang
OUT     ?= out

HOST_BINS := $(OUT)/unlock-signin $(OUT)/eval-js $(OUT)/gb-dhcpd $(OUT)/gb-portrelay
NDK_BINS  := $(OUT)/input-bridge $(OUT)/vpd-stub $(OUT)/libgb-sf-waydroid.so $(OUT)/libgb-hwc-waydroid.so

all: $(NDK_BINS) $(HOST_BINS)
host: $(HOST_BINS)   # no NDK needed

$(OUT):
	mkdir -p $(OUT)

# The 2026-11-04 rebuild of the original source (PTR_H=1080) was instruction-for-instruction identical to the working binary.
# It has since gained an optional persist.gbos.display override (default unchanged: 1920x1080); the new build has not been run on a device.
$(OUT)/input-bridge: input-bridge.c | $(OUT)
	$(CC_AND) -O2 -o $@ $< -llog

# libbinder_ndk's AServiceManager_* are platform-only, so they are resolved at runtime in the container.
# Verified: byte-identical to the binary the working image uses.
$(OUT)/vpd-stub: vpd-stub.c | $(OUT)
	$(CC_AND) -O2 -Wl,--unresolved-symbols=ignore-all -o $@ $< -lbinder_ndk

$(OUT)/libgb-sf-waydroid.so: sf-waydroid-shim.c | $(OUT)
	$(CC_AND) -shared -fPIC -O2 -Wall -Wextra -Wl,-soname,libgb-sf-waydroid.so -o $@ $< -ldl -llog

$(OUT)/libgb-hwc-waydroid.so: hwc-waydroid-shim.c | $(OUT)
	$(CC_AND) -shared -fPIC -O2 -Wall -Wextra -Wl,-soname,libgb-hwc-waydroid.so -o $@ $< -ldl -llog

$(OUT)/unlock-signin: unlock-signin.c | $(OUT)
	gcc -O2 -static -o $@ $<

$(OUT)/eval-js: eval-js.c | $(OUT)
	gcc -O2 -static -o $@ $<

# Small static helpers whose sources live with the recipe scripts that install them.
$(OUT)/gb-dhcpd: ../recipe/fakeap/gb-dhcpd.c | $(OUT)
	gcc -O2 -static -o $@ $<

$(OUT)/gb-portrelay: ../recipe/portrelay/gb-portrelay.c | $(OUT)
	gcc -O2 -static -o $@ $<

# Optional hybrid-GPU module (gpu/): the Vulkan routing shim that is mounted into the container as vulkan.virtio.so.
# Built here with the NDK; same exported symbols, dependencies and soname as the binary the module was tested with
# (that one was linked with host gcc and is offered as a release asset). Not part of `all`.
$(OUT)/vulkan.hybrid64.so: ../gpu/vulkan_hybrid.c | $(OUT)
	$(CC_AND) -shared -fPIC -O2 -Wl,-soname,libvulkan_virtio.so -o $@ $< -ldl -llog
gpu-shim: $(OUT)/vulkan.hybrid64.so

clean:
	rm -rf $(OUT)
.PHONY: all host gpu-shim clean
