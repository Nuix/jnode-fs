#!/bin/bash

#######################################################
# The current script creates an image in the current 
# directory, due to the amount of the files created a 
# btree directory with level 2 is created, allowing
# to test the extract of data of a multilevel btree 
# directory.
#######################################################
echo "starting..."
img_name="big_img_2tb_v4"

###########################
function clean() {
   rm ./media/sf_repo/${img_name}.img
   rm -r /media/mnt/${img_name} 
}

###########################
function setup() {
   #dd if=/dev/zero of=/media/sf_repo/${img_name}".img" bs=1 count=0 seek=2000000M
   #mkfs.xfs -d "name=/media/sf_repo/big_img_2tb_v4.img"
   mkdir -p /media/mnt/${img_name}
   mount -o loop "/media/sf_repo/${img_name}.img" /media/mnt/${img_name}
}

##############################
# The size of the image allows 
# to create only 317,436 files
# 
function create_files() {
   num_files=$1
   dir_name=$2
   maxSize=$3
   tempFile=/media/sf_temp/test-file
   for i in $(seq $num_files); do
      
      touch ${tempFile}
      size=$((1 + $RANDOM % maxSize))
      echo "creating a ${tempFile} with size ${size} MB"
      head -c ${size}M /dev/urandom > ${tempFile}
      checksum=($(md5sum ${tempFile} | cut -d ' ' -f 1)) 
      outFile=/media/mnt/${img_name}/$dir_name/${checksum}
      mv ${tempFile} ${outFile}
      echo "${outFile} created with size ${size} MB"
      sync 
   done
}

###########################
function create_directory() {
   dir_name=$1
   mkdir -p /media/mnt/${img_name}/${dir_name}
}

###########################
function end() {
   umount /media/mnt/${img_name}
   echo "ending..."
}

###########################
# main code.
# Usage: sudo ./crt_2tb.sh
###########################
#clean 
#setup
create_directory "dir4"
# create_files 50 "dir1" 1024
create_files 1500 "dir4" 3000

#end

