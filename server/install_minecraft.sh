#!/bin/bash

set -e

modpack_id=52
modpack_version=216

installer_download_url="https://api.modpacks.ch/public/modpack/$modpack_id/$modpack_version/server/linux"
username="ubuntu"
home_dir=/home/$username

modpack_folder=$home_dir/minecraft

installer_name=modpack_installer

installer_location=$home_dir/$installer_name


curl $installer_download_url -o $installer_location
chmod +x $installer_location

mkdir $modpack_folder

./$installer_name $modpack_id $modpack_version --path $modpack_folder
