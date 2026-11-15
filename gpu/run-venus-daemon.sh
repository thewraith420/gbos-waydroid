#!/bin/bash
# Start the host Venus render server (virgl_test_server --venus) on the NVIDIA GPU.
# Run by the user service gbos-venus.service. Settings come from $LIB/gpu.conf, which `gbos-gpu enable` writes
# (root-owned): NODE = the NVIDIA render node, ICD = its Vulkan ICD file.
set -euo pipefail

LIB="${GBOS_GPU_LIB:-/usr/local/lib/gbos-gpu}"
# shellcheck disable=SC1091
. "$LIB/gpu.conf"
: "${NODE:?gpu.conf has no NODE}" "${ICD:?gpu.conf has no ICD}" "${XDG_RUNTIME_DIR:?not running in a user session}"

# This directory is bind-mounted into the container as /dev/venus. Android apps run under their own uids, so
# they need to traverse it; the socket itself is created world-connectable by the server. 755, not 777: nobody
# else needs to create files here.
SOCKET_DIR="$XDG_RUNTIME_DIR/waydroid-venus"
mkdir -p "$SOCKET_DIR"
chmod 755 "$SOCKET_DIR"
rm -f "$SOCKET_DIR/venus.sock"

export RENDER_SERVER_EXEC_PATH="$LIB/host/virgl_render_server"
export LD_LIBRARY_PATH="$LIB/host:${LD_LIBRARY_PATH:-}"
export VK_DRIVER_FILES="$ICD"
export VK_ICD_FILENAMES="$ICD"
export __GLX_VENDOR_LIBRARY_NAME=nvidia

echo "Starting virgl_test_server (Venus) on $NODE"
exec "$LIB/host/virgl_test_server" --venus --multi-clients --rendernode "$NODE" --socket-path "$SOCKET_DIR/venus.sock"
