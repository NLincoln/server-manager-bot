#!/bin/bash

set -e

service_file_name=minecraft.service

sudo mv /home/ubuntu/$service_file_name /etc/systemd/system/

sudo systemctl enable $service_file_name

chmod +x /home/ubuntu/minecraft/start.sh
