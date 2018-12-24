#!/bin/bash

VERSION="1.32.1.1"
RELEASE_PATH="../release/heybot-$VERSION"

if [ -d "$RELEASE_PATH" ]; then
  rm -Rf "$RELEASE_PATH"
  rm -f "$RELEASE_PATH.tar.gz"
fi
mkdir -p "$RELEASE_PATH"
echo "[*] Listing ../dist/ directory ... "
ls -lh ../dist/
echo "[*] Packaging ... "
cp -R ../dist/* "$RELEASE_PATH/"
cp -R ../templates "$RELEASE_PATH/"
cp ./heybot.run "$RELEASE_PATH/"
cat "$RELEASE_PATH/heybot.run" "$RELEASE_PATH/heybot.jar" > "$RELEASE_PATH/heybot.final" && chmod +x "$RELEASE_PATH/heybot.final"
rm -f "$RELEASE_PATH/heybot.run"
rm -f "$RELEASE_PATH/heybot.jar"
mv "$RELEASE_PATH/heybot.final" "$RELEASE_PATH/heybot.run"
cp ./install.sh "$RELEASE_PATH/"
cp ./uninstall.sh "$RELEASE_PATH/"
cd "$RELEASE_PATH/../"
tar -zcvf "heybot-$VERSION.tar.gz" "heybot-$VERSION"
rm -Rf "heybot-$VERSION"
mkdir "heybot-$VERSION"
mv "heybot-$VERSION.tar.gz" "heybot-$VERSION/"
printf "[\xE2\x9C\x94] Release ready for distribution. \o/"
echo ""
