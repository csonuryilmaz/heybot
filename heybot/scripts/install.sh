#!/bin/sh

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

tryGetHeybotOldPath
[[ !  -z  $HEYBOT_OLD_PATH  ]] && tryGetHeybotOldWorkspace && tryCopyOldWorkpaceIntoNewOne && tryRemoveOldInstallation

echo "-- Installing new version ... "
sudo ln -s $PWD/heybot.run /usr/local/bin/heybot
sudo chmod +x /usr/local/bin/heybot
echo "Done. :)"
exit 0