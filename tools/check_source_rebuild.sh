#!/usr/bin/env bash
set -euo pipefail
# Run as builder inside WSL. Use a new directory; preserve the original build outputs.
bundle=${1:?source bundle directory}
destination=${2:?new verification directory}
test ! -e "$destination"
mkdir -p "$destination"
tar -xzf "$bundle/termux-recipes.tar.gz" -C "$destination"
cd "$destination/termux-packages"
TERMUX_PKG_MAKE_PROCESSES=4 ./build-package.sh -a aarch64 -f quickjs
