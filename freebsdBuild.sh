#!/bin/sh
# FreeBSD: нативный JDK 17, build-tools (Linux-бинарники) через linuxulator.
# Докачивает что может, на невозможном падает с подсказкой. Потом собирает.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/tools/fetch.sh"

TOOLS=${TOOLS:-$HOME/android-tools}
SDK=${SDK:-$HOME/android-sdk}
BTV=${BTV:-34.0.0}
PLAT=${PLAT:-19}
JAVA_HOME=${JAVA_HOME:-/usr/local/openjdk17}
AJAR=${AJAR:-$TOOLS/android-4.4.2/android.jar}
R8=${R8:-$TOOLS/r8lib.jar}
export TOOLS SDK BTV PLAT JAVA_HOME AJAR R8

# Невозможное: падаем с командой
[ -x "$JAVA_HOME/bin/javac" ] || die "нет JDK в $JAVA_HOME. Выполни: doas pkg install openjdk17"

# Возможное: докачиваем
ensure_buildtools
ensure_ajar
ensure_r8

# aapt2 качается, но запуститься может только под linuxulator
"$SDK/build-tools/$BTV/aapt2" version >/dev/null 2>&1 \
	|| die "aapt2 не запускается. Linuxulator: doas sysrc linux_enable=YES; doas service linux start; doas pkg install linux_base-rl9"

exec sh "$HERE/build.sh"
