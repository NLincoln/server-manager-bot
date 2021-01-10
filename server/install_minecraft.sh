#!/bin/bash

set -e

installer_download_url="https://api.modpacks.ch/public/modpack/$MODPACK_ID/$MODPACK_VERSION/server/linux"
username="ubuntu"
home_dir=/home/$username

modpack_folder=$home_dir/minecraft

installer_name=modpack_installer

installer_location=$home_dir/$installer_name

sudo apt-get update
sudo apt-get install openjdk-8-jre-headless --assume-yes

curl "$installer_download_url" -o $installer_location
chmod +x $installer_location

mkdir $modpack_folder

./$installer_name "$MODPACK_ID" "$MODPACK_VERSION" --path $modpack_folder

cd $modpack_folder

# The forge installer has a bunch of version numbers in it
java -jar forge-*-installer.jar --installServer
