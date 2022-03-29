#!/bin/bash

#######################################################
# The current script creates an image in the current 
# directory, due to the amount of the files created a 
# btree directory with level 2 is created, allowing
# to test the extract of data of a multilevel btree 
# directory.
#######################################################
echo "starting..."
img_name="mult_btree_level_v4"

###########################
function clean() {
   rm ./${img_name}.img
   rm -r /media/${img_name} 
}

###########################
function setup() {
   dd if=/dev/zero of=${img_name}".img" bs=1 count=0 seek=100M
   mkfs.xfs -d "name=mult_btree_level_v4.img"
   mkdir -p /media/${img_name}
   mount -o loop "./${img_name}.img" /media/${img_name}
}

##############################
# The size of the image allows 
# to create only 317,436 files
# 
function create_files() {
   for i in {1..317436}; do
      touch /media/${img_name}/btree_dir/test-file${i}.txt
      echo "test-file${i}.txt created"
   done
}

###########################
function create_directory() {
   mkdir -p /media/${img_name}/btree_dir
}

###########################
function end() {
   umount /media/${img_name}
   echo "ending..."
}

###########################
# main code.
# Usage: sudo ./mult_btree_level.sh
###########################
clean 
setup
create_directory
create_files
end

