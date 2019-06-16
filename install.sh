#!/usr/bin/env bash

LATEST="2.8.0.0"
FOLDER="$HOME/Downloads"

set -e;
cd "$HOME"
echo "[*] Downloading latest release into: $FOLDER"
mkdir -p "$FOLDER" 
wget --backups=1 https://github.com/csonuryilmaz/heybot/releases/download/$LATEST/heybot-$LATEST.tar.gz -P "$FOLDER"
printf "[\xE2\x9C\x94] Downloaded. \n"
echo "[*] Extracting archive with tar ..."
tar -zxvf "$FOLDER/heybot-$LATEST.tar.gz"
printf "[\xE2\x9C\x94] Extracted. \n"
chmod a+x "heybot-$LATEST/install.sh"
heybot-$LATEST/install.sh
