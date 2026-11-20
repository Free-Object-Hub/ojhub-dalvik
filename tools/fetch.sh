# Подключается через `. tools/fetch.sh`. Ничего не собирает, только докачивает.
# Нужны переменные: AJAR R8 (и SDK BTV для ensure_buildtools).

die() { echo "ошибка: $*" >&2; exit 1; }

# get URL DEST: качает в DEST.part, потом переименовывает,
# чтобы недокачанный файл не остался под настоящим именем.
get() {
	url=$1
	dest=$2
	mkdir -p "$(dirname "$dest")"
	echo "качаю: $url"
	if command -v curl >/dev/null 2>&1; then
		curl -fL --retry 2 -o "$dest.part" "$url" || { rm -f "$dest.part"; return 1; }
	elif command -v fetch >/dev/null 2>&1; then
		fetch -o "$dest.part" "$url" || { rm -f "$dest.part"; return 1; }
	else
		echo "нет ни curl, ни fetch" >&2
		return 1
	fi
	mv "$dest.part" "$dest"
}

# jar-файлы и zip проверяем на целостность: битая докачка хуже её отсутствия
is_zip() { jar tf "$1" >/dev/null 2>&1; }

# android.jar API 19. Репозиторий-зеркало платформ; URL не проверял, переопредели AJAR_URL, если 404.
AJAR_URL=${AJAR_URL:-https://github.com/Sable/android-platforms/raw/master/android-19/android.jar}
ensure_ajar() {
	[ -f "$AJAR" ] && return 0
	get "$AJAR_URL" "$AJAR" || die "не скачался android.jar. Положи руками: $AJAR"
	is_zip "$AJAR" || { rm -f "$AJAR"; die "android.jar битый (URL: $AJAR_URL)"; }
}

# R8/D8 из Google Maven. Версию я не проверял: если 404, глянь актуальную
# на maven.google.com (com/android/tools/r8) и задай R8_VER=.
R8_VER=${R8_VER:-8.5.35}
R8_URL=${R8_URL:-https://maven.google.com/com/android/tools/r8/$R8_VER/r8-$R8_VER.jar}
ensure_r8() {
	[ -f "$R8" ] && return 0
	get "$R8_URL" "$R8" || die "не скачался r8. Положи руками: $R8"
	is_zip "$R8" || { rm -f "$R8"; die "r8 битый (URL: $R8_URL)"; }
}

# Linux build-tools (aapt2, zipalign, lib/apksigner.jar). Для FreeBSD+linuxulator.
BT_URL=${BT_URL:-https://dl.google.com/android/repository/build-tools_r34-linux.zip}
ensure_buildtools() {
	[ -x "$SDK/build-tools/$BTV/aapt2" ] && return 0
	tmp=$SDK/.dl
	rm -rf "$tmp"
	mkdir -p "$tmp/x"
	get "$BT_URL" "$tmp/bt.zip" || die "не скачались build-tools. Положи руками в $SDK/build-tools/$BTV"
	# bsdtar на FreeBSD читает zip; на всякий случай пробуем unzip
	if command -v unzip >/dev/null 2>&1; then
		unzip -q "$tmp/bt.zip" -d "$tmp/x"
	else
		tar -xf "$tmp/bt.zip" -C "$tmp/x"
	fi
	top=$(ls "$tmp/x" | head -n 1)   # внутри один каталог, имя у Google плавает
	mkdir -p "$SDK/build-tools"
	mv "$tmp/x/$top" "$SDK/build-tools/$BTV"
	chmod +x "$SDK/build-tools/$BTV/aapt2" "$SDK/build-tools/$BTV/zipalign" 2>/dev/null || true
	rm -rf "$tmp"
}
