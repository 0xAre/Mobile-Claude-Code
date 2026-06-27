#!/usr/bin/env bash
#
# build-bootstrap.sh — assemble a relocatable Claude Terminal bootstrap for one
# Android ABI: a Termux userland + Node.js + the Claude Code CLI, packaged as
# bootstrap-<abi>.zip for the app's BootstrapInstaller to extract.
#
# ⚠️ EXPERIMENTAL. This produces an archive but has NOT been validated on a real
# device yet. It relies on qemu-user emulation and on the prefix living at the
# app's data path so Termux binaries' RUNPATH/LD_LIBRARY_PATH resolve. Expect to
# iterate with on-device feedback. See docs/FASE2.md.
#
# Usage:  scripts/build-bootstrap.sh <android-abi> [out-dir]
#   android-abi: arm64-v8a | armeabi-v7a | x86_64
#
# Requirements (Linux CI): curl, unzip, zip, sudo, qemu-user-static + binfmt
# (e.g. `sudo apt-get install -y qemu-user-static`).
set -euo pipefail

ABI="${1:-arm64-v8a}"
OUT_DIR="${2:-$PWD/dist}"
APP_ID="com.zeroxare.claudemobile"
PREFIX="/data/data/${APP_ID}/files/usr"   # must match LinuxEnvironment.prefixDir

case "$ABI" in
  arm64-v8a)   TERMUX_ARCH=aarch64 ;;
  armeabi-v7a) TERMUX_ARCH=arm ;;
  x86_64)      TERMUX_ARCH=x86_64 ;;
  *) echo "Unknown ABI: $ABI" >&2; exit 1 ;;
esac

echo ">> Building bootstrap for $ABI (termux arch: $TERMUX_ARCH)"
mkdir -p "$OUT_DIR"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# 1) Fetch the Termux bootstrap (bash, coreutils, apt, dpkg, …) for this arch.
BOOT_URL="https://github.com/termux/termux-packages/releases/latest/download/bootstrap-${TERMUX_ARCH}.zip"
echo ">> Downloading $BOOT_URL"
curl -fSL "$BOOT_URL" -o "$WORK/bootstrap.zip"

# 2) Stage it at the REAL app prefix path so Termux binaries resolve their libs.
sudo rm -rf "$PREFIX"
sudo mkdir -p "$PREFIX"
sudo unzip -q "$WORK/bootstrap.zip" -d "$PREFIX"
# Recreate symlinks Termux records in SYMLINKS.txt (kept in the zip too, but the
# app recreates them on extract; do it here so the emulated apt/npm work).
if [ -f "$PREFIX/SYMLINKS.txt" ]; then
  while IFS='←' read -r target link; do
    [ -n "${target:-}" ] && [ -n "${link:-}" ] || continue
    sudo ln -sf "$target" "$PREFIX/$link"
  done < "$PREFIX/SYMLINKS.txt"
fi
sudo chmod -R u+rwX "$PREFIX"

export PATH="$PREFIX/bin:$PATH"
export HOME="$PREFIX/../home"; mkdir -p "$HOME"
export LD_LIBRARY_PATH="$PREFIX/lib"
export PREFIX

# 3) Under qemu emulation, install Node.js then the Claude Code CLI.
#    binfmt must be registered (qemu-user-static). The Termux apt downloads
#    arch-native packages from the Termux mirror.
echo ">> apt update / install nodejs (emulated)"
"$PREFIX/bin/apt" update -y || true
"$PREFIX/bin/apt" install -y nodejs || {
  echo "!! apt install nodejs failed under emulation — see docs/FASE2.md" >&2
  exit 2
}
echo ">> npm install -g @anthropic-ai/claude-code (emulated)"
"$PREFIX/bin/npm" install -g @anthropic-ai/claude-code

# 4) Repackage the prefix CONTENTS (entries like bin/…, lib/…, SYMLINKS.txt) so
#    BootstrapInstaller extracts them straight into its prefix dir.
OUT="$OUT_DIR/bootstrap-${ABI}.zip"
echo ">> Packaging $OUT"
( cd "$PREFIX" && sudo zip -qry "$OUT" . )
sudo chown "$(id -u):$(id -g)" "$OUT"
echo ">> Done: $OUT ($(du -h "$OUT" | cut -f1))"
