#!/bin/bash

# Example: update-plist iOSProject-Info.plist 4321 CFBundleVersion
# Example: update-plist iOSProject-Info.plist 5.0.4 CFBundleShortVersionString

set -e

VERSION="CFBundleShortVersionString"
BUILD="CFBundleVersion"

if [[ ! -e $1 ]]; then
    echo "[e] Arg(1): Wrong file path. File not found!"
    exit 255;
fi

if [ "$3" != $VERSION ] && [ "$3" != $BUILD ]; then
  echo "[e] Arg(3): Wrong pattern. "$VERSION" or "$BUILD" should be given."
  exit 255;
fi

PATTERN="<key>"$3"</key>"
echo "[i] File: "$1
echo "[*] Finding pattern "$PATTERN" ..."
LINE=`grep -nri $PATTERN $1 | cut -d ':' -f 1`
echo "[.] Found line: "$LINE
echo "[1] Creating new file with lines up to pattern ..."
head -n $LINE $1 > $1"_tmp"
echo "[2] Appending new pattern value as "$2" ..."
echo -e "\t<string>"$2"</string>" >> $1"_tmp"
echo "[3] Appending other lines after pattern value ..."
TOTAL=`wc -l $1 | cut -d ' ' -f 1`
echo "[i] Total line count of file: "$TOTAL
tail -n $(( $TOTAL-($LINE+1) )) $1 >> $1"_tmp"
echo "[*] Replacing old file with new one ..."
mv $1"_tmp" $1
printf "[\xE2\x9C\x94] Modified.\n"