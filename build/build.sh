#!/bin/sh
# build.sh: compile the MIDlet and package a clean jar for the Fit3 J2ME runtime.
#
# Why this exists: the previously distributed MinecraftJ2ME(3).jar bundled javax/com.nokia
# stub classes whose constant pools lack the "Code" UTF8 entry (method attributes point to
# index #13 of a 12-entry pool). Such classes crash the Fit3 VM (SIGSEGV on the PC host).
# This script never packages API classes: the device/runtime provides javax.microedition.*.
#
# Needs: JDK 8 (javac -source 1.3 / -target 1.1), python 3 (for cpcheck.py).
# Optional: RT_CLASSES = compiled runtime MIDP classes (default: ../j2me-fit3/jvm/rt_classes).
#
# usage: sh build/build.sh [out.jar]
set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JDK=${JDK:-"/c/Program Files/Eclipse Adoptium/jdk-8.0.504.1-hotspot"}
PY=${PY:-python3}
RT_CLASSES=${RT_CLASSES:-/c/Users/Usuario/j2me-fit3/jvm/rt_classes}
OUT=${1:-"$ROOT/dist/MinecraftJ2ME_fit3.jar"}
WORK="$ROOT/build/work"

w() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }

rm -rf "$WORK" && mkdir -p "$WORK/classes" "$(dirname "$OUT")"

# 1. compile (MIDP API from the runtime + compile-only interfaces + library stubs)
LIBS=""
for j in "$ROOT"/libs/*.jar; do LIBS="$LIBS;$(w "$j")"; done
CP="$(w "$RT_CLASSES");$(w "$ROOT/build/compile-stubs")$LIBS"
( cd "$ROOT" && find src -name "*.java" > "$WORK/sources.txt" )
( cd "$ROOT" && "$JDK/bin/javac.exe" -nowarn -source 1.3 -target 1.1 -encoding UTF-8 \
    -cp "$CP" -d "$(w "$WORK/classes")" @"$(w "$WORK/sources.txt")" 2>&1 | grep -v "^Note:" ) || true
[ -f "$WORK/classes/MinecraftMIDlet.class" ] || { echo "javac failed"; exit 1; }

# 2. resources + manifest (values from project.cfg)
cp -r "$ROOT/res" "$WORK/classes/"
cat > "$WORK/manifest.mf" <<'EOF'
Manifest-Version: 1.0
MIDlet-Name: MinecraftJ2ME_PE_0.6.1
MIDlet-Version: 0.6.1
MIDlet-Vendor: XaViewStudios
MIDlet-1: MinecraftJ2ME_PE_0.6.1,,MinecraftMIDlet
MicroEdition-Profile: MIDP-2.0
MicroEdition-Configuration: CLDC-1.1
Nokia-UI-Enhancement: EnableMultiPointTouchEvents
EOF

# 3. package, then validate every class (constant pool / attribute name references)
rm -f "$OUT"
( cd "$WORK/classes" && "$JDK/bin/jar.exe" cfm "$(w "$OUT")" "$(w "$WORK/manifest.mf")" . )
"$PY" "$ROOT/build/cpcheck.py" "$OUT"
ls -la "$OUT"
