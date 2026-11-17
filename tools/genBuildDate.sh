#!/bin/sh
# usage: gen-buildinfo.sh <gen-dir>
D="$1/xyz/objecthub/android"
mkdir -p "$D"
cat > "$D/BuildInfo.java" <<EOF
package xyz.objecthub.android;
final class BuildInfo {
    static final String DATE = "$(date '+%d.%m %H:%M')";
    static final String REV  = "$(git rev-parse --short HEAD 2>/dev/null || echo nogit)";
}
EOF
