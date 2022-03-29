#!/bin/bash

#############################################################
# The current script creates an XFS image with a size of 2TB. 
# To improve the performance of the file creation the 
# files is suggested to run this script ouf of the VM
# and copy the file to the XFS image.
#############################################################
echo "starting..."
img_name="bigimg_v4"
temp_dir="/tmp/nuix"
outdir="/Volumes/apfs_vol1/temp_dir"
maxSize=3000
current_num_files=0

###########################
function clean() {
   rm /media/sf_repository/${img_name}.img
   rm -r /media/mnt/${img_name} 
}

###########################
function setup() {
   dd if=/dev/zero of=/media/sf_repository/${img_name}".img" bs=1 count=0 seek=2000000M
   mkfs.xfs -d "name=/media/sf_repository/${img_name}.img"
   mkdir -p /media/mnt/${img_name}
   mount -o loop "/media/sf_repository/${img_name}.img" /media/mnt/${img_name}
}

##############################
# Create files with size between 1 to 3GB 
function create_files() {
   num_files=$1
   dir_name=$2

   MG=$((1024*1024))
   tempFile=$temp_dir/$dir_name/test-file
   for i in $(seq $num_files); do
      
      touch ${tempFile}
      size=$((1 + $RANDOM % maxSize))
      echo "creating a ${tempFile} with size ${size} MB"
      head -c $((${size} * ${MG})) /dev/urandom > ${tempFile}
      
      checksum=($(md5sum ${tempFile} | cut -d ' ' -f 1)) 
      outFile=${outdir}/$dir_name/${checksum}
      mv ${tempFile} ${outFile}
      echo "$i, ${outFile} created with size ${size} MB"
      sync 
   done
}

###########################
function create_directory() {
   dir_name=$1
   if [ ! -d ${temp_dir}/${dir_name} ]; then
      mkdir -p ${temp_dir}/${dir_name}
   fi
   if [ ! -d ${outdir}/${dir_name} ]; then 
      mkdir -p ${outdir}/${dir_name}
   fi
}

###########################
function end() {
   umount /media/mnt/${img_name}
   echo "ending..."
}

function create_flag() {
   touch $outdir/done.txt
}

###########################
# main code.
# Usage: sudo ./crtImg.sh
###########################
function number_files_created() {
  
   ls $outdir/dir1 > listOfFiles.txt
   current_num_files=$(cat listOfFiles.txt | wc -l)
   echo "current number : ${current_num_files}"
}

# clean 
# setup
# create_directory dir1
while ! [ -d $outdir/end.txt ];
do
   number_files_created 
   while ! [ ${current_num_files} -gt 50 ];
   do
     echo "creting 10 files"
     create_files 10 dir1
     number_files_created
   done
   echo "currently there are more of 50 wait 10 seconds"
   # sleep for a while to allow to move the files to the image
   sleep 10
done

#end

