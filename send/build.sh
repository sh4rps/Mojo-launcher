#!/bin/bash
# Build Send Launcher from the decoded AllJava tree.  usage: build.sh <decoded-tree> <workdir> [keystore]
# needs: apktool.jar, uber-apk-signer.jar, dx.jar, android.jar (paths via env TOOLS, DX, AJAR)
set -e
SRC=$1; W=$2; KS=${3:-$(dirname "$0")/../v2/robin.jks}
HERE=$(cd "$(dirname "$0")" && pwd)
rm -rf "$W"; mkdir -p "$W"; cp -r "$SRC" "$W/d"
python3 -I "$HERE/make.py" "$W/d"
mkdir -p "$W/cls" "$W/dex"
javac --release 8 -nowarn -encoding UTF-8 -cp "$AJAR" -d "$W/cls" "$HERE"/java/rl/*.java 2>&1 | grep -v 'JAVA_TOOL\|^Note' || true
java -cp "$DX" com.android.dx.command.Main --dex --min-sdk-version=26 --output="$W/dex/classes5.dex" "$W/cls" 2>&1 | grep -v JAVA_TOOL || true
test -s "$W/dex/classes5.dex"
java -jar "$TOOLS/apktool.jar" b -o "$W/b.apk" "$W/d" 2>&1 | grep -v JAVA_TOOL | tail -4
(cd "$W/dex" && zip -q -0 "$W/b.apk" classes5.dex)
java -jar "$TOOLS/signer.jar" -a "$W/b.apk" --ks "$KS" --ksAlias mojo --ksPass mojogames --ksKeyPass mojogames --allowResign 2>&1 | grep -iE 'error|verified|signed' | head
ls -l "$W"/*signed*.apk
