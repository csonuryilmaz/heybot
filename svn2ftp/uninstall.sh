#!/bin/sh

echo "Clearing current installation if exists ..."
sudo rm -f /usr/local/bin/svn2ftp.run 
echo "Checking ..."
if [ -f /usr/local/bin/svn2ftp.run ] ; then
	echo "Try again!"
	exit 1
fi
echo "Uninstalled :("
exit 1