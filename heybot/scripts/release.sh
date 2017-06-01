#!/bin/bash

VERSION="1.11.0.2"

RELEASE_PATH="../../release/heybot_$VERSION"

if [ -d "$RELEASE_PATH" ]; then
  rm -Rf "$RELEASE_PATH"
  rm -f "$RELEASE_PATH.tar.gz"
fi
mkdir -p "$RELEASE_PATH"
echo "== Listing ../dist/ directory ... "
ls -lh ../dist/
echo "== Packaging ... "
cp -R ../dist/* "$RELEASE_PATH/"
mkdir "$RELEASE_PATH/workspace"
cp -R ../templates "$RELEASE_PATH/"
cp ./heybot.run "$RELEASE_PATH/"
cat "$RELEASE_PATH/heybot.run" "$RELEASE_PATH/heybot.jar" > "$RELEASE_PATH/heybot.final" && chmod +x "$RELEASE_PATH/heybot.final"
rm -f "$RELEASE_PATH/heybot.run"
rm -f "$RELEASE_PATH/heybot.jar"
mv "$RELEASE_PATH/heybot.final" "$RELEASE_PATH/heybot.run"
cp ./install.sh "$RELEASE_PATH/"
cp ./uninstall.sh "$RELEASE_PATH/"
cd "$RELEASE_PATH/../"
tar -zcvf "heybot_$VERSION.tar.gz" "heybot_$VERSION"
rm -Rf "heybot_$VERSION"
mkdir "heybot_$VERSION"
mv "heybot_$VERSION.tar.gz" "heybot_$VERSION/"
cp ../heybot/README.md "heybot_$VERSION/"
