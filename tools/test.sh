#!/bin/sh
# Тест VirtualLink без Android SDK. Нужен только JDK.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
T=${T:-/tmp/ojhub-vltest}
rm -rf "$T" && mkdir -p "$T"
javac -encoding UTF-8 -d "$T" "$HERE/src/xyz/objecthub/android/VirtualLink.java" "$HERE/tools/VirtualLinkTest.java"
java -cp "$T" xyz.objecthub.android.VirtualLinkTest
