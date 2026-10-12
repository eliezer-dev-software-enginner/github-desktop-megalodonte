#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
arch="$(uname -m)"
if [[ "$arch" != x86_64 && "$arch" != aarch64 ]]; then
  echo "Arquitetura AppImage não suportada: $arch" >&2; exit 1
fi
java_home="${MEGALODONTE_JAVA_HOME:-${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}}"
tool="${APPIMAGETOOL:-$project_dir/../megalodonte-engine/build/tools/appimagetool-$arch.AppImage}"
if [[ ! -x "$java_home/bin/jlink" ]]; then echo "Java 25 com jlink não encontrado: $java_home" >&2; exit 1; fi
if [[ ! -x "$tool" ]]; then echo "appimagetool não encontrado: $tool" >&2; exit 1; fi
cd "$project_dir"
JAVA_HOME="$java_home" bash gradlew installDist --offline --console=plain
stage="$project_dir/build/appimage/MegalodonteGit.AppDir"
rm -rf "$stage"
mkdir -p "$stage/opt/app" "$project_dir/build/appimage"
cp -a build/install/megalodonte-git/lib "$stage/opt/app/"
"$java_home/bin/jlink" --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
  --add-modules java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.security.jgss,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported \
  --output "$stage/opt/runtime"
cat > "$stage/AppRun" <<'RUN'
#!/bin/sh
APPDIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec "$APPDIR/opt/runtime/bin/java" --enable-native-access=ALL-UNNAMED -Xms32m -Xmx256m -XX:+UseSerialGC \
  -cp "$APPDIR/opt/app/lib/*" megalodonte.script.ScriptLauncher "$@"
RUN
chmod +x "$stage/AppRun"
cat > "$stage/megalodonte-git.desktop" <<'DESKTOP'
[Desktop Entry]
Type=Application
Name=Megalodonte Git
Comment=Cliente Git nativo com interface Megalodonte
Exec=megalodonte-git
Icon=megalodonte-git
Categories=Development;
Terminal=false
DESKTOP
cp "$project_dir/src/main/resources/megalodonte-git.svg" "$stage/megalodonte-git.svg"
cp "$stage/megalodonte-git.svg" "$stage/.DirIcon"
output="$project_dir/build/appimage/MegalodonteGit-$arch.AppImage"
ARCH="$arch" APPIMAGE_EXTRACT_AND_RUN=1 "$tool" --no-appstream --mksquashfs-opt '-processors' --mksquashfs-opt '2' "$stage" "$output.tmp"
chmod +x "$output.tmp"
mv "$output.tmp" "$output"
echo "AppImage: $output"
if [[ "${MEGALODONTE_NO_OPEN:-0}" != 1 ]]; then
  log="$project_dir/build/appimage/run.log"
  setsid nohup env APPIMAGE_EXTRACT_AND_RUN=1 "$output" >"$log" 2>&1 </dev/null &
  pid=$!
  sleep 3
  if kill -0 "$pid" 2>/dev/null; then echo "AppImage iniciado (PID $pid). Log: $log"
  else echo "AppImage não iniciou. Log: $log" >&2; exit 1; fi
fi
