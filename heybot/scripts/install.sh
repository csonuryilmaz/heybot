#!/bin/bash

strIndex()
{
    x="${1%%$2*}"
    [[ "$x" = "$1" ]] && echo -1 || echo "${#x}"
}

removeOldHeybot()
{
    echo "[*] Finding old heybot installation ..."
    HEYBOT_OLD_PATH=$(ls -lh /usr/local/bin | grep heybot | awk '{print $(NF-1), $NF}' | tail -1)
    if [[ ! -z  $HEYBOT_OLD_PATH ]]; then 
	FIRST_INDEX_OF_SLASH=$(strIndex "${HEYBOT_OLD_PATH}" "/")
	HEYBOT_RUN_FILE="${HEYBOT_OLD_PATH##*/}"
	LAST_INDEX_OF_SLASH=$(strIndex "${HEYBOT_OLD_PATH}" "/${HEYBOT_RUN_FILE}")
	HEYBOT_OLD_FOLDER=$(echo "${HEYBOT_OLD_PATH}" | cut -c $FIRST_INDEX_OF_SLASH-$LAST_INDEX_OF_SLASH)
	HEYBOT_OLD_FOLDER="$(echo -e "${HEYBOT_OLD_FOLDER}" | sed -e 's/^[[:space:]]*//')"
	echo "[i] "$HEYBOT_OLD_FOLDER
	read -p "[?] Removing old heybot installation. Are you sure? (y/n) " -n 1 -r
	if [[ $REPLY =~ ^[Yy]$ ]]; then
	    echo ""
	    echo "[*] Removing old heybot installation ..."
	    sudo rm -f /usr/local/bin/heybot
	    sudo rm -Rf $HEYBOT_OLD_FOLDER
	    if [ -d "$HEYBOT_OLD_FOLDER" ]; then
		echo "[w] Could not be removed!"
	    else
		printf "[\xE2\x9C\x94] Removed.\n"
	    fi
	fi
	echo ""
    else
	echo "[i] Not found."
    fi
}

install()
{
    echo "[*] Installing ... "
    DIRECTORY=`dirname $0`
    sudo ln -s $DIRECTORY/heybot.run /usr/local/bin/heybot && sudo chmod +x /usr/local/bin/heybot && printf "[\xE2\x9C\x94] Installed! \\o/ \n"
    exit 0
}

removeOldHeybot

echo "[*] Checking whether java is installed? ..."
if type -p java; then
    echo "Found java executable in PATH."
    JAVA_EXECUTABLE=java
elif [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]];  then
    echo "Found java executable in JAVA_HOME."
    JAVA_EXECUTABLE="$JAVA_HOME/bin/java"
else
    echo "You will need Java installed on your system!"
    exit -1
fi

BIN_FOLDER="/usr/local/bin"
echo "[*] Checking is $BIN_FOLDER exists? ..."
if [ ! -d "$BIN_FOLDER" ]; then
    echo "Not exists! Needs to create directory ..."
    sudo mkdir $BIN_FOLDER && printf "\n[\xE2\x9C\x94]\n"
else
    printf "[\xE2\x9C\x94] Exists.\n"
fi

echo "[*] Checking whether java version is 1.8+? ..."
if [[ "$JAVA_EXECUTABLE" ]]; then
    version=$("$JAVA_EXECUTABLE" -version 2>&1 | awk -F '"' '/version/ {print $2}')
    echo version "$version"
    if [[ "$version" > "1.8" ]]; then
        install
    else
        echo "Java version 1.8 or later required!"
        exit -1
    fi
fi
