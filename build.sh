#!/bin/sh
# ojhub-dalvik: сборка без Gradle и без XML (кроме AndroidManifest.xml).
# aapt (только манифест) -> javac -> d8 -> aapt add -> zipalign -> apksigner.
#
# Пути переопределяются окружением. Дефолты под Debian-коробку:
#   SDK=~/android-sdk  BT=build-tools/34.0.0  JAVA_HOME=~/jdk17 (JDK 21 роняет d8)
#
# DEBUG=1 (по умолчанию): aapt --debug-mode, в App включается StrictMode.
# DEBUG=0: релизный флаг, без StrictMode.
# OUT: куда положить готовый apk (по умолчанию /var/www/files).
# Сборка идёт в /tmp (tmpfs), на диск ложится только готовый apk.

echo "debug status: ${DEBUG}"

set -eu

SDK=${SDK:-$HOME/android-sdk}
BTV=${BTV:-34.0.0}
# бля забудьте теперь оно работает на далвике
PLAT=${PLAT:-19}
#JAVA_HOME=${JAVA_HOME:-$HOME/jdk17}
JAVA_HOME=/usr/local/openjdk17
DEBUG=${DEBUG:-1}
OUT=${OUT:-/var/www/files}
KS=${KS:-$HOME/.ojhub-debug.keystore}

BT=$SDK/build-tools/$BTV
AJAR=${AJAR:-$SDK/platforms/android-$PLAT/android.jar}
PATH=$JAVA_HOME/bin:$BT:$PATH
export PATH

HERE=$(cd "$(dirname "$0")" && pwd)
T=${T:-/tmp/ojhub-build}
APK=ojhub-dalvik-$(date +%Y-%m-%d_%H-%M-%S).apk

[ -f "$AJAR" ] || { echo "нет android.jar: $AJAR (задай AJAR= или PLAT=)" >&2; exit 1; }

rm -rf "$T"
mkdir -p "$T/classes" "$T/dex"

# 1. Манифест -> каркас apk. res/ нет, R.java нет.
DBG=""
[ "$DEBUG" = 1 ] && DBG="--debug-mode"
aapt package -f $DBG \
    -M "$HERE/AndroidManifest.xml" \
	-S "$HERE/res" \
    -I "$AJAR" \
    --min-sdk-version 19 --target-sdk-version 34 \
    -F "$T/base.apk"

# 2. Java.  -encoding обязателен: в комментариях русский, а в C-локали
#    javac по умолчанию читает ASCII и валится.
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options \
    -cp "$AJAR" -d "$T/classes" \
    $(find "$HERE/src" -name '*.java')

# 3. dex. Только наши классы; android.jar подключаем как lib.
d8 --min-api 19 --lib "$AJAR" --output "$T/dex" \
    $(find "$T/classes" -name '*.class')

# 4. Добавить dex в apk (aapt add кладёт файл по относительному пути).
( cd "$T/dex" && aapt add "$T/base.apk" classes.dex >/dev/null )

# 4b. assets -> тот же base.apk. Запускать из $HERE, чтобы путь внутри
#     apk остался assets/certs/x.pem (aapt add кладёт файл по переданному пути).
if [ -d "$HERE/assets" ]; then
    ( cd "$HERE" && find assets -type f | while read -r f; do
        aapt add "$T/base.apk" "$f" >/dev/null
    done )
fi

# 5. Выравнивание и подпись.
zipalign -f 4 "$T/base.apk" "$T/aligned.apk"

if [ ! -f "$KS" ]; then
    echo "создаю debug-keystore: $KS"
    keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
        -alias ojhub -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=ojhub debug"
fi
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --out "$T/$APK" "$T/aligned.apk"

mkdir -p "$OUT"
mv "$T/$APK" "$OUT/$APK"
echo "готово: $OUT/$APK"
