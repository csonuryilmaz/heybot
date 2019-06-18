#!/bin/bash

set -e

strIndex()
{
    x="${1%%$2*}"
    [[ "$x" = "$1" ]] && echo -1 || echo "${#x}"
}

removeOldInstallation() {
    echo "[*] Removing old heybot installation ..."
    sudo rm -f /usr/local/bin/heybot
    sudo rm -rf $1
    if [[ -d "$1" ]]; then
        echo "[w] Could not be removed!"
    else
        printf "[\xE2\x9C\x94] Removed.\n"
    fi
}

askUserAndRemoveOldInstallationIfAccepts() {
    echo "[i] Removing old heybot installation."
    read -p "[?] Are you sure? (Y/n) " -n 1 -r
    if [[ $REPLY == '' ]]; then
        removeOldInstallation $1
    else
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            removeOldInstallation $1
        elif [[ $REPLY =~ ^[Nn]$ ]]; then
            echo "[i] Old installation is kept!"
        else
            echo "[e] Unexpected answer!"
            askUserAndRemoveOldInstallationIfAccepts $1
        fi
    fi
}

findOldInstallationAndTryRemove()
{
    if [[ -e "$HOME/.heybot/installed.path" ]]; then
        HEYBOT_INSTALLED_PATH=$(head -1 $HOME/.heybot/installed.path)
        echo "[i] Previous: "${HEYBOT_INSTALLED_PATH}
        echo "[i] Current : "${DIRECTORY}
        if [[ ${HEYBOT_INSTALLED_PATH} != ${DIRECTORY} ]]; then
            askUserAndRemoveOldInstallationIfAccepts ${HEYBOT_INSTALLED_PATH}
        else
            echo "[i] Re-installation and maintenance will be done."
        fi
    else
	echo "[i] Previous installation not found, clean system."
    fi
}

setDirectory() {
    DIRECTORY=`dirname $0`
    if [[ -d "$DIRECTORY" ]]; then
        cd "$DIRECTORY"
        DIRECTORY=$(pwd -P)
    else 
        cd "$(dirname "$DIRECTORY")"
        DIRECTORY=$(pwd -P)/$(basename "$DIRECTORY")
    fi
}

install()
{
    echo "[*] Installing ... "
    sudo ln -sf ${DIRECTORY}/heybot.run /usr/local/bin/heybot && sudo chmod +x /usr/local/bin/heybot
    LN_RESULT="$?"
    if [[ "$LN_RESULT" -ne 0 ]]; then
        echo "[e] Could not create runnable in /usr/local/bin!"
        exit 1
    fi
    if [[ -x "$(command -v heybot)" ]]; then
        HEYBOT_LINK=$(command -v heybot)
        echo -e "\t[i] "${HEYBOT_LINK}
        HEYBOT_EXEC=$(ls -lh ${HEYBOT_LINK} | awk '{print $(NF-1), $NF}' | tail -1)
        echo -e "\t[i] "${HEYBOT_EXEC}
        FIRST_INDEX_OF_SLASH=$(strIndex "${HEYBOT_EXEC}" "/")
        HEYBOT_RUN="${HEYBOT_EXEC##*/}"
        LAST_INDEX_OF_SLASH=$(strIndex "${HEYBOT_EXEC}" "/${HEYBOT_RUN}")
        HEYBOT_FOLDER=$(echo "${HEYBOT_EXEC}" | cut -c ${FIRST_INDEX_OF_SLASH}-${LAST_INDEX_OF_SLASH})
        HEYBOT_FOLDER="$(echo -e "${HEYBOT_FOLDER}" | sed -e 's/^[[:space:]]*//')"
        echo -e "\t[i] "${HEYBOT_FOLDER}
        if [[ ! -d "$HOME/.heybot" ]]; then
        mkdir $HOME/.heybot
        echo -e "\t[i] Created "$HOME/.heybot
        fi
        echo ${HEYBOT_FOLDER} > $HOME/.heybot/installed.path
        echo -e "\t[i] Installation folder is saved."
        printf "[\xE2\x9C\x94] Installed! \\o/ \n"
        exit 0
    fi
}

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
if [[ ! -d "$BIN_FOLDER" ]]; then
    echo "Not exists! Needs to create directory ..."
    sudo mkdir ${BIN_FOLDER} && printf "\n[\xE2\x9C\x94]\n"
else
    printf "[\xE2\x9C\x94] Exists.\n"
fi

echo "[*] Checking whether java version is 1.8+? ..."
if [[ "$JAVA_EXECUTABLE" ]]; then
    version=$("$JAVA_EXECUTABLE" -version 2>&1 | awk -F '"' '/version/ {print $2}')
    echo version "$version"
    if [[ "$version" > "1.8" ]]; then
        setDirectory
        findOldInstallationAndTryRemove
        install
    else
        echo "Java version 1.8 or later required!"
        exit -1
    fi
fi
