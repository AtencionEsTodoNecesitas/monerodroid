#!/bin/bash

# Script to download and bundle monerod binaries for Android
# This downloads the official Monero CLI binaries and extracts monerod
# for inclusion in the APK

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JNILIBS_DIR="$PROJECT_DIR/app/src/main/jniLibs"

# URLs for official Monero CLI downloads
ARMV7_URL="https://downloads.getmonero.org/cli/androidarm7"
ARMV8_URL="https://downloads.getmonero.org/cli/androidarm8"

# Output directories
ARMV7_DIR="$JNILIBS_DIR/armeabi-v7a"
ARMV8_DIR="$JNILIBS_DIR/arm64-v8a"

# Temp directory for downloads
TEMP_DIR="/tmp/monerod_download_$$"

echo "=== MoneroDroid Binary Bundler ==="
echo ""
echo "This script downloads official monerod binaries from getmonero.org"
echo "and bundles them into the APK."
echo ""

# Create directories
mkdir -p "$ARMV7_DIR"
mkdir -p "$ARMV8_DIR"
mkdir -p "$TEMP_DIR"

cleanup() {
    echo "Cleaning up temporary files..."
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

download_and_extract() {
    local url="$1"
    local output_dir="$2"
    local arch_name="$3"

    echo "Downloading $arch_name binary from $url..."

    local archive="$TEMP_DIR/monero-$arch_name.tar.bz2"
    local extract_dir="$TEMP_DIR/extract-$arch_name"

    # Download
    curl -L -o "$archive" "$url"

    echo "Extracting $arch_name binary..."
    mkdir -p "$extract_dir"

    # Extract
    tar -xjf "$archive" -C "$extract_dir"

    # Find monerod binary
    local monerod=$(find "$extract_dir" -name "monerod" -type f | head -1)

    if [ -z "$monerod" ]; then
        echo "ERROR: monerod binary not found in $arch_name archive!"
        exit 1
    fi

    # Copy to jniLibs with .so extension (required for Android to include it)
    echo "Installing $arch_name binary to $output_dir/libmonerod.so"
    cp "$monerod" "$output_dir/libmonerod.so"
    chmod 755 "$output_dir/libmonerod.so"

    # Get version
    echo "Binary size: $(du -h "$output_dir/libmonerod.so" | cut -f1)"
}

echo ""
echo "=== Downloading ARMv7 (32-bit) binary ==="
download_and_extract "$ARMV7_URL" "$ARMV7_DIR" "armv7"

echo ""
echo "=== Downloading ARMv8 (64-bit) binary ==="
download_and_extract "$ARMV8_URL" "$ARMV8_DIR" "armv8"

echo ""
echo "=== Summary ==="
echo "ARMv7 binary: $ARMV7_DIR/libmonerod.so ($(du -h "$ARMV7_DIR/libmonerod.so" | cut -f1))"
echo "ARMv8 binary: $ARMV8_DIR/libmonerod.so ($(du -h "$ARMV8_DIR/libmonerod.so" | cut -f1))"
echo ""
echo "Binaries have been bundled successfully!"
echo "The APK will now include monerod for both ARM architectures."
echo ""
echo "Note: These binaries will significantly increase APK size."
echo "Consider using App Bundles or split APKs for distribution."
