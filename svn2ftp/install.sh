#!/bin/sh

echo "Clearing current installation if exists ..."
sudo rm -f /usr/local/bin/ftpupload.run 
echo "Installing ..."
sudo ln -s $PWD/ftpupload.run /usr/local/bin/ftpupload.run
sudo chmod +x /usr/local/bin/ftpupload.run
echo "Installed :)"
exit 1