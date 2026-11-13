#!/data/data/com.termux/files/usr/bin/sh
# ojhub-dalvik: Termux build
# aapt -> javac -> D8 -> aapt add -> zipalign -> apksigner

set -eu

# Configuration
TOOLS=${TOOLS:-$HOME/android-tools}
AJAR=${AJAR:-$TOOLS/android-4.4.2/android.jar}
R8=${R8:-$TOOLS/r8lib.jar}

JAVA=${JAVA:-java}
JAVAC=${JAVAC:-javac}
KEYTOOL=${KEYTOOL:-keytool}

DEBUG=${DEBUG:-1}
OUT=${OUT:-$HOME/storage/downloads/dalvik}
KS=${KS:-$HOME/.ojhub-debug.keystore}
T=${T:-$PREFIX/tmp/ojhub-build}

HERE=$(cd "$(dirname "$0")" && pwd)
APK="ojhub-dalvik-$(date +%Y-%m-%d_%H-%M-%S).apk"

echo "debug status: $DEBUG"
echo "Android API: 19"
echo "R8: $R8"

# Check dependencies
for CMD in aapt zipalign apksigner "$JAVA" "$JAVAC" "$KEYTOOL"; do
    command -v "$CMD" >/dev/null 2>&1 || {
        echo "missing tool: $CMD" >&2
        exit 1
    }
done

[ -f "$AJAR" ] || {
    echo "missing android.jar: $AJAR" >&2
    exit 1
}

[ -f "$R8" ] || {
    echo "missing r8lib.jar: $R8" >&2
    exit 1
}

# Prepare build directory
rm -rf "$T"
mkdir -p "$T/classes" "$T/dex"

mkdir -p "$T/compiled"

aapt2 compile \
    --dir "$HERE/res" \
    -o "$T/compiled/resources.zip"

# 2. Link manifest and resources
DBG=""
[ "$DEBUG" = 1 ] && DBG="--debug-mode"

aapt2 link \
    -o "$T/base.apk" \
    -I "$AJAR" \
    --manifest "$HERE/AndroidManifest.xml" \
    -R "$T/compiled/resources.zip" \
    --min-sdk-version 19 \
    --target-sdk-version 34 \
    $DBG

# 2. Java compilation
find "$HERE/src" -name '*.java' > "$T/sources.txt"

"$JAVAC" \
    -encoding UTF-8 \
    -source 8 -target 8 \
    -Xlint:-options \
    -cp "$AJAR" \
    -d "$T/classes" \
    @"$T/sources.txt"

# 3. DEX compilation
find "$T/classes" -name '*.class' > "$T/classes.txt"

"$JAVA" -cp "$R8" \
    com.android.tools.r8.D8 \
    --min-api 19 \
    --lib "$AJAR" \
    --output "$T/dex" \
    @"$T/classes.txt"

# 4. Add DEX to APK
(
    cd "$T/dex"
    aapt add "$T/base.apk" classes.dex >/dev/null
)

# 5. Alignment
zipalign -f 4 "$T/base.apk" "$T/aligned.apk"

# 6. Debug keystore
if [ ! -f "$KS" ]; then
    echo "creating debug keystore: $KS"

    "$KEYTOOL" -genkeypair \
        -keystore "$KS" \
        -storepass android \
        -keypass android \
        -alias ojhub \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=ojhub debug"
fi

# 7. APK signing
apksigner sign \
    --ks "$KS" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$T/$APK" \
    "$T/aligned.apk"

# 8. Output
mkdir -p "$OUT"
mv "$T/$APK" "$OUT/$APK"

echo ""
echo "================================"
echo "Build successful!"
echo "APK: $OUT/$APK"
echo "================================"
