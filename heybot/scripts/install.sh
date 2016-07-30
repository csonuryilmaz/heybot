#!/bin/sh

echo "Clearing current installation if exists ..."
sudo rm -f /usr/local/bin/heybot
echo "Installing ..."
sudo ln -s $PWD/heybot.run /usr/local/bin/heybot
sudo chmod +x /usr/local/bin/heybot
echo "Installed :)"
exit 1