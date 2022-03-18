#!/bin/bash

#######################################################
# The current script creates an image in the current 
# directory, create the required files to generate the 
# different directory structures. Then define to the 5 
# first file the number of attributes depending of each
# kind of directory.
#######################################################
echo "starting..."
img_name="ext_attr_v4"

###########################
function clean() {
   rm ./${img_name}.img
   rm -r /media/${img_name} 
}

###########################
function setup() {

   dd if=/dev/zero of=${img_name}".img" bs=1 count=0 seek=100M
   mkfs.xfs -d "name=ext_attr_v4.img"
   mkdir -p /media/${img_name}
   mount -o loop "./${img_name}.img" /media/${img_name}
}

##############################
function create_directories() {

   mkdir -p /media/${img_name}/short-form-dir
   mkdir -p /media/${img_name}/block-dir
   mkdir -p /media/${img_name}/leaf-dir
   mkdir -p /media/${img_name}/node-dir
   mkdir -p /media/${img_name}/btree-dir
}

#############################
function create_attr() {
 
  num_attr=$1
  dir_name=$2

  for i in $(seq 5); do
    value="/media/${img_name}/${dir_name}/test-file${i}.txt"
    file_name=${value}

    for j in $(seq $num_attr); do
       attr -s attr_name${j} -V ${value} ${file_name}
    done
  done
}

##############################
function create_object_attr() {

  create_attr 2 "short-form-dir"
  create_attr 20 "block-dir"
  create_attr 50 "leaf-dir"
  create_attr 100 "node-dir"
  create_attr 500 "btree-dir"
  
}

##############################
function create_files() {
   num_files=$1
   dir_name=$2

   for i in $(seq $num_files); do
      touch /media/${img_name}/${dir_name}/test-file${i}.txt
      echo "test-file${i}.txt created"
   done
}

###########################
function create_objects() {
   
  create_files 2 "short-form-dir"
  create_files 20 "block-dir"
  create_files 200 "leaf-dir"
  create_files 1000 "node-dir"
  create_files 10000 "btree-dir"
}

###########################
function end() {
   umount /media/${img_name}
   echo "ending..."
}

###########################
# main code.
# Usage: sudo ./create_ext_attr.sh
###########################

clean 
setup
create_directories
create_objects
create_object_attr
end

