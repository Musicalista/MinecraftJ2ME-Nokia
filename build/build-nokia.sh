#!/bin/sh
# build-nokia.sh: compile MinecraftJ2ME for Nokia S40/S60 phones (MIDP 2.0, CLDC 1.1).
#
# The phone supplies the MIDP/CLDC/JSR APIs at run time, so the jar must not contain them.
# Compilation uses:
#   MIDP_API       compile-only MIDP 2.0 API classes (javax.microedition.lcdui, midlet, rms, ...)
#   libs/*.jar     JSR-184 (M3G), JSR-75, JSR-82 and Nokia multipoint-touch API stubs from this repo
#   build/compile-stubs  interfaces the libs do not provide (StreamConnection, DatagramConnection, Datagram)
# The jar gets the game classes, the resources at its root and a Nokia manifest, nothing else.
#
# Size options:
#   classes are built with -g:none (no line/variable tables; stack traces lose line numbers)
#   SND_RATE=8000  resample the sound effects (ffmpeg) to 8 kHz mono 8-bit; the repo keeps the originals
#
# Needs: JDK 8 (javac -source 1.3 / -target 1.1), Python 3 (cpcheck.py), ffmpeg when SND_RATE is set.
# usage: MIDP_API=<dir with MIDP 2.0 compile API> [SND_RATE=8000] sh build/build-nokia.sh [out.jar]
set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JDK=${JDK:-"/c/Program Files/Eclipse Adoptium/jdk-8.0.504.1-hotspot"}
PY=${PY:-python3}
FFMPEG=${FFMPEG:-ffmpeg}
MIDP_API=${MIDP_API:?set MIDP_API to a directory with the MIDP 2.0 compile API}
OUT=${1:-"$ROOT/dist/MinecraftJ2ME_nokia.jar"}
WORK="$ROOT/build/work-nokia"

w() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }

rm -rf "$WORK" && mkdir -p "$WORK/classes" "$(dirname "$OUT")"

# 1. compile against the compile-only APIs
LIBS=""
for j in "$ROOT"/libs/*.jar; do LIBS="$LIBS;$(w "$j")"; done
CP="$(w "$MIDP_API")$LIBS"
( cd "$ROOT" && { find src -name "*.java"; find build/compile-stubs -name "*.java"; } > "$WORK/sources.txt" )
( cd "$ROOT" && "$JDK/bin/javac.exe" -nowarn -g:none -source 1.3 -target 1.1 -encoding UTF-8 \
    -cp "$CP" -d "$(w "$WORK/classes")" @"$(w "$WORK/sources.txt")" 2>&1 | grep -v "^Note:" ) || true
[ -f "$WORK/classes/MinecraftMIDlet.class" ] || { echo "javac failed"; exit 1; }

# compile-only API classes must not ship: drop them before packaging
rm -rf "$WORK/classes/javax"

# 2. resources at the jar root (the game loads /gui/..., /snd/...)
cp -r "$ROOT/res/." "$WORK/classes/"

# 2b. optional sound resampling, on the work copy only
if [ -n "$SND_RATE" ]; then
    for f in "$WORK/classes/snd/"*.wav; do
        "$FFMPEG" -v error -y -i "$f" -ar "$SND_RATE" -ac 1 -acodec pcm_u8 "$f.tmp.wav"
        mv "$f.tmp.wav" "$f"
    done
fi

# 3. Nokia manifest: values from project.cfg plus the Nokia touch and category attributes
cat > "$WORK/manifest.mf" <<'MF'
Manifest-Version: 1.0
MIDlet-Name: MinecraftJ2ME_PE_0.6.1
MIDlet-Version: 0.6.1
MIDlet-Vendor: XaViewStudios
MIDlet-1: MinecraftJ2ME_PE_0.6.1,,MinecraftMIDlet
MicroEdition-Profile: MIDP-2.0
MicroEdition-Configuration: CLDC-1.1
Nokia-UI-Enhancement: EnableMultiPointTouchEvents
Nokia-MIDlet-Category: Game
MF

# 4. package and validate every class
rm -f "$OUT"
( cd "$WORK/classes" && "$JDK/bin/jar.exe" cfm "$(w "$OUT")" "$(w "$WORK/manifest.mf")" . )
"$PY" "$ROOT/build/cpcheck.py" "$OUT"
ls -la "$OUT"
