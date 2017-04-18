#!/bin/bash

strIndex()
{
    x="${1%%$2*}"
    [[ "$x" = "$1" ]] && echo -1 || echo "${#x}"
}

tryGetHeybotOldPath()
{
    HEYBOT_OLD_PATH=$(ls -lh /usr/local/bin | grep heybot | awk '{print $(NF-1), $NF}' | tail -1)
    echo "-- Heybot old working path:"
    [[ !  -z  $HEYBOT_OLD_PATH  ]] && echo $HEYBOT_OLD_PATH || echo "Not found."
}

tryGetHeybotOldWorkspace()
{
    FIRST_INDEX_OF_SLASH=$(strIndex "${HEYBOT_OLD_PATH}" "/")
    HEYBOT_RUN_FILE="${HEYBOT_OLD_PATH##*/}"
    LAST_INDEX_OF_SLASH=$(strIndex "${HEYBOT_OLD_PATH}" "/${HEYBOT_RUN_FILE}")

    HEYBOT_OLD_FOLDER=$(echo "${HEYBOT_OLD_PATH}" | cut -c $FIRST_INDEX_OF_SLASH-$LAST_INDEX_OF_SLASH)

    HEYBOT_OLD_WORKSPACE="${HEYBOT_OLD_FOLDER}/workspace"

    echo "-- Heybot old workspace:"
    echo $HEYBOT_OLD_WORKSPACE
}

tryCopyOldWorkpaceIntoNewOne()
{
    rsync -a -v --ignore-existing $HEYBOT_OLD_WORKSPACE $(pwd)
    echo "-- Old workspace files are copied to new workspace. :)"
}

tryRemoveOldInstallation()
{
    echo "-- Removing old installation ..."
    sudo rm -f /usr/local/bin/heybot
    sudo rm -Rf $HEYBOT_OLD_FOLDER
}

install()
{
    echo "-- Installing new version ... "
    sudo ln -s $PWD/heybot.run /usr/local/bin/heybot && sudo chmod +x /usr/local/bin/heybot && echo "Installed successfully."
    exit 0
}

tryGetHeybotOldPath
[[ !  -z  $HEYBOT_OLD_PATH  ]] && tryGetHeybotOldWorkspace && tryCopyOldWorkpaceIntoNewOne && tryRemoveOldInstallation

echo "-- Checking whether java is installed ? ..."
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

echo "-- Checking whether java version is 1.8+ ? ..."
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
