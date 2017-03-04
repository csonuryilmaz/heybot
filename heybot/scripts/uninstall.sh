#!/bin/bash

echo "Clearing current installation if exists ..."
sudo rm -f /usr/local/bin/heybot
echo "Checking ..."
if [ -f /usr/local/bin/heybot ] ; then
	echo "Try again!"
	exit 1
fi
echo "Uninstalled :("
exit 1