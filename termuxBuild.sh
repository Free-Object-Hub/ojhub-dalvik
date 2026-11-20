#!/data/data/com.termux/files/usr/bin/sh
# Termux: пакеты из pkg, android.jar и r8lib.jar докачиваются в ~/android-tools.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/tools/fetch.sh"

TOOLS=${TOOLS:-$HOME/android-tools}
AJAR=${AJAR:-$TOOLS/android-4.4.2/android.jar}
R8=${R8:-$TOOLS/r8lib.jar}
OUT=${OUT:-$HOME/storage/downloads/dalvik}
T=${T:-$PREFIX/tmp/ojhub-build}
AAPT2=${AAPT2:-aapt2}
ZIPALIGN=${ZIPALIGN:-zipalign}
APKSIGNER=${APKSIGNER:-apksigner}
export TOOLS AJAR R8 OUT T AAPT2 ZIPALIGN APKSIGNER

# На Termux root не нужен, поэтому пакеты ставим сами
MISSING=""
need() {  # команда, пакет
	command -v "$1" >/dev/null 2>&1 || MISSING="$MISSING $2"
}
need javac openjdk-17
need jar openjdk-17
need curl curl
need aapt2 aapt2
need apksigner apksigner
need zipalign zipalign
if [ -n "$MISSING" ]; then
	echo "ставлю:$MISSING"
	pkg install -y $MISSING
fi

ensure_ajar
ensure_r8

exec sh "$HERE/build.sh"
