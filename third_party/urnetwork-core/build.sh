#!/bin/bash
set -e
cd "$(dirname "$0")"

export GOPROXY='https://proxy.golang.org,direct'
export GOFLAGS='-mod=mod'

mkdir -p out/arm64-v8a
mkdir -p out/armeabi-v7a

echo "========================================="
echo "1. Building for ARMv8 (arm64-v8a)..."
echo "========================================="
CGO_ENABLED=0 GOOS=android GOARCH=arm64 \
go build -trimpath -ldflags "-s -w -checklinkname=0" -o "out/arm64-v8a/liburnetwork.so" .
echo "✓ ARMv8 Built successfully ($(stat -c %s out/arm64-v8a/liburnetwork.so) bytes)"

echo ""
echo "========================================="
echo "2. Building for ARMv7 (armeabi-v7a)..."
echo "========================================="
CGO_ENABLED=0 GOOS=linux GOARCH=arm GOARM=7 \
go build -trimpath -ldflags "-s -w -checklinkname=0" -o "out/armeabi-v7a/liburnetwork.so" .
echo "✓ ARMv7 Built successfully ($(stat -c %s out/armeabi-v7a/liburnetwork.so) bytes)"

echo ""
echo "All done! Both architectures built successfully."