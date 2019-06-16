#!/bin/bash

DIRECTORY=`dirname $0`
if [[ -d "$DIRECTORY" ]]; then
  cd "$DIRECTORY"
else 
  cd "$(dirname "$DIRECTORY")"
fi
# Current Working Directory
echo "[i] CWD: $(/bin/pwd)";

VERSION=`grep -in "version.*=.*;$" ../src/heybot/heybot.java | cut -d "\"" -f 2 | tr -d '[:space:]'`
if [[ -z "$VERSION" ]]; then
  echo "[e] Version not found in heybot.java file!";
  exit -1;
fi
echo "[i] VERSION: $VERSION";

RELEASE_PATH="../release/heybot-$VERSION"

sed -i 's/LATEST="*.*.*.*"/LATEST="'"$VERSION"'"/' ../../install.sh
\cp -rf ../doc/README-draft.md ../doc/README-draft-tmp.md
sed -i 's/#_LATEST_#/'"$VERSION"'/g' ../doc/README-draft-tmp.md
\cp -rf ../doc/README-draft-tmp.md ../../README.md
rm -f ../doc/README-draft-tmp.md

if [[ -d "$RELEASE_PATH" ]]; then
  rm -Rf "$RELEASE_PATH"
  rm -f "$RELEASE_PATH.tar.gz"
fi
mkdir -p "$RELEASE_PATH"

ARTIFACT_PATH="../out/artifacts/dist"
echo "[*] Listing artifacts ... "
ls -lh "$ARTIFACT_PATH"

#cp -R ../templates "$RELEASE_PATH/" @todo: needs revision

# make jar file runnable with java -jar
echo "[*] Packaging ... "
cp -R ${ARTIFACT_PATH}/*.jar "$RELEASE_PATH/"
cp ./heybot.run "$RELEASE_PATH/"
cat "$RELEASE_PATH/heybot.run" "$RELEASE_PATH/heybot.jar" > "$RELEASE_PATH/heybot.final" && chmod +x "$RELEASE_PATH/heybot.final"
rm -f "$RELEASE_PATH/heybot.run"
rm -f "$RELEASE_PATH/heybot.jar"
mv "$RELEASE_PATH/heybot.final" "$RELEASE_PATH/heybot.run"

# copy install, uninstall scripts
cp ./install.sh "$RELEASE_PATH/"
cp ./uninstall.sh "$RELEASE_PATH/"

# make tgz package
cd "$RELEASE_PATH/../"
tar -zcvf "heybot-$VERSION.tar.gz" "heybot-$VERSION"
rm -Rf "heybot-$VERSION"

printf "[\xE2\x9C\x94] Release ready for distribution. \o/"
echo ""
