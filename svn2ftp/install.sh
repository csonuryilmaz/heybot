#!/bin/sh

echo "Clearing current installation if exists ..."
sudo rm -f /usr/local/bin/svn2ftp.run 
echo "Installing ..."
sudo ln -s $PWD/svn2ftp.run /usr/local/bin/svn2ftp.run
sudo chmod +x /usr/local/bin/svn2ftp.run
echo "Installed :)"
exit 1