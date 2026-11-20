#!/bin/sh
# ojhub-dalvik: сборка без Gradle и без XML (кроме AndroidManifest.xml и res/).
# aapt2 compile+link -> javac -> d8 -> jar (dex в apk) -> zipalign -> apksigner.
# Старый aapt не нужен. Платформу скрипт не знает: окружение приходит переменными,
# а freebsdBuild.sh / termuxBuild.sh настраивают их и зовут этот файл.
#
# SDK BTV PLAT   SDK, версия build-tools, платформа (android.jar)
# JAVA_HOME      JDK. Проверено на 17; другая версия даёт только предупреждение
# AJAR           android.jar (по умолчанию из SDK и PLAT)
# R8             jar с com.android.tools.r8.D8 (по умолчанию build-tools/lib/d8.jar)
# AAPT2 ZIPALIGN D8 APKSIGNER   команды (по умолчанию из build-tools)
# DEBUG=1        debuggable + StrictMode в App; DEBUG=0 релизный флаг
# OUT            куда положить apk
# KS             debug keystore
# T              каталог сборки

set -eu

DEBUG=${DEBUG:-1}
echo "debug status: $DEBUG"

die() { echo "ошибка: $*" >&2; exit 1; }

SDK=${SDK:-$HOME/android-sdk}
BTV=${BTV:-34.0.0}
PLAT=${PLAT:-19}
MINSDK=${MINSDK:-19}
TARGETSDK=${TARGETSDK:-34}
OUT=${OUT:-/var/www/files}
KS=${KS:-$HOME/.ojhub-debug.keystore}

if [ -z "${JAVA_HOME:-}" ] && [ -x "$HOME/jdk17/bin/java" ]; then
	JAVA_HOME=$HOME/jdk17
fi
if [ -n "${JAVA_HOME:-}" ]; then
	PATH=$JAVA_HOME/bin:$PATH
	export PATH
fi
command -v java  >/dev/null 2>&1 || die "нет java"
command -v javac >/dev/null 2>&1 || die "нет javac (нужен JDK, не JRE)"
command -v jar   >/dev/null 2>&1 || die "нет jar (нужен JDK, не JRE)"
JV=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')
[ "$JV" = 17 ] || echo "внимание: JDK $JV, проверено только на 17" >&2

BT=$SDK/build-tools/$BTV
AJAR=${AJAR:-$SDK/platforms/android-$PLAT/android.jar}
AAPT2=${AAPT2:-$BT/aapt2}
ZIPALIGN=${ZIPALIGN:-$BT/zipalign}
APKSIGNER=${APKSIGNER:-java -jar $BT/lib/apksigner.jar}
if [ -z "${D8:-}" ]; then
	R8=${R8:-$BT/lib/d8.jar}
	[ -f "$R8" ] || die "нет d8/r8 jar: $R8 (задай R8= или D8=)"
	D8="java -cp $R8 com.android.tools.r8.D8"
fi

[ -f "$AJAR" ] || die "нет android.jar: $AJAR (задай AJAR= или PLAT=)"
command -v "$AAPT2"    >/dev/null 2>&1 || die "нет aapt2: $AAPT2"
command -v "$ZIPALIGN" >/dev/null 2>&1 || die "нет zipalign: $ZIPALIGN"

HERE=$(cd "$(dirname "$0")" && pwd)
T=${T:-${TMPDIR:-/tmp}/ojhub-build}
APK=ojhub-dalvik-$(date +%Y-%m-%d_%H-%M-%S).apk

rm -rf "$T"
mkdir -p "$T/classes" "$T/dex"

sh "$HERE/tools/genBuildDate.sh" "$T/gen"

# 1. Ресурсы, манифест и assets -> каркас apk.
LINKRES=""
if [ -d "$HERE/res" ] && [ -n "$(find "$HERE/res" -type f | head -n 1)" ]; then
	"$AAPT2" compile --dir "$HERE/res" -o "$T/res.zip"
	LINKRES="-R $T/res.zip"
fi
DBG=""
[ "$DEBUG" = 1 ] && DBG="--debug-mode"
ASSETS=""
[ -d "$HERE/assets" ] && ASSETS="-A $HERE/assets"

"$AAPT2" link -o "$T/base.apk" -I "$AJAR" \
	--manifest "$HERE/AndroidManifest.xml" \
	--min-sdk-version "$MINSDK" --target-sdk-version "$TARGETSDK" \
	$DBG $ASSETS $LINKRES

# 2. Java. -encoding обязателен: в комментариях русский.
find "$HERE/src" "$T/gen" -name '*.java' > "$T/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options \
	-cp "$AJAR" -d "$T/classes" @"$T/sources.txt"

# 3. dex. Только наши классы, android.jar как lib.
find "$T/classes" -name '*.class' > "$T/classes.txt"
$D8 --min-api "$MINSDK" --lib "$AJAR" --output "$T/dex" @"$T/classes.txt"

# 4. dex -> тот же apk. aapt2 add не умеет, берём jar из JDK.
jar --update --no-manifest --file "$T/base.apk" -C "$T/dex" classes.dex

# 5. Выравнивание. Потом проверка: для targetSdk 30+ resources.arsc
#    должен лежать несжатым, иначе Android 11+ apk не поставит.
"$ZIPALIGN" -f 4 "$T/base.apk" "$T/aligned.apk"
if "$ZIPALIGN" -c -v 4 "$T/aligned.apk" | grep resources.arsc | grep -q compressed; then
	die "resources.arsc сжат: Android 11+ такой apk не установит"
fi

# 6. Подпись.
if [ ! -f "$KS" ]; then
	echo "создаю debug-keystore: $KS"
	keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
		-alias ojhub -keyalg RSA -keysize 2048 -validity 10000 \
		-dname "CN=ojhub debug"
fi
$APKSIGNER sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
	--out "$T/$APK" "$T/aligned.apk"

mkdir -p "$OUT"
mv "$T/$APK" "$OUT/$APK"
echo "готово: $OUT/$APK"
