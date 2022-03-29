#!/bin/bash

# This script creates various XFS images with different options for testing.
# For the most part, the contents of the images are the same.

# Ways to copy a sparse file without expanding it.
# cp --sparse=always source_file new_file
# rsync --sparse source_file new_file
# cpio --sparse
# tar --sparse


function create_test_data() {
	# Create a few directories
	mkdir -p mnt/foo/bar/baz
	touch mnt/foo/empty_file
	echo "Some simple content" > mnt/foo/non_empty_file

	mkdir -p mnt/home/acme/.settings/myapp
	echo "Very long file name..." > "mnt/home/acme/.settings/myapp/thisIsAVeryVeryLongFileNameWhichShouldTestThatWeHaven'tLimitedABufferAnywhere.ok"

	dd if=/dev/zero of=mnt/medium_empty.txt bs=1024 count=1024
	yes "Mary had a little lamb,
	   Its fleece was white as snow,
	   And every where that Mary went
	      The lamb was sure to go ;" | head -c 250KB > medium_content.txt


	# Set some different attributes.
	echo -e "#!/bin/bash\necho \"Hellp World!\"" > mnt/foo/helloworld.sh
	chmod uo+x mnt/foo/helloworld.sh
	chmod g-r mnt/foo/helloworld.sh

	echo "Secret information" > mnt/notReadable.txt
	chmod ugo-rw mnt/notReadable.txt

	# Set some extra attributes.
	echo "Scary content from the internet..." > mnt/attributes.txt
	attr -q -s foo -V bar mnt/attributes.txt
	attr -q -s "character-set" -V kanji mnt/attributes.txt
	cat image.png | attr-q  -s "A really really long key name with spaces and other #$%&* punctuation marks... AND a binary value" mnt/attributes.txt
	attr -q -s "isThisADirectory" -V "Yes" mnt/foo/bar



	# Create some sparse files.
	dd if=/dev/zero of=mnt/sparse_empty.dat bs=1M count=0 seek=1000

	echo "Some text at the beginning of the file" > mnt/sparse_with_holes.dat
	echo "Some text in the middle of the file" | dd bs=1M seek=5 of=mnt/sparse_with_holes.dat
	dd if=/dev/zero bs=1M seek=10 count=0 of=mnt/sparse_with_holes.dat
	echo "Some text at the end of the file" >> mnt/sparse_with_holes.dat

	dd if=/dev/zero bs=1G seek=150 count=0 of=mnt/sparse_huge.dat
	echo "Just a little bit of data right at the end..." >> mnt/sparse_huge.dat


	# Create various types of soft and hard links.
	ln -s mnt/medium_empty.txt mnt/foo/this_is_a_symlink_to_a_file
	ln -s mnt/foo/this_is_a_symlink_to_a_dir mnt/home/acme/.settings
	ln mnt/sparse_empty.dat mnt/foo/this_is_a_hardlink_to_a_file
	ln "mnt/home/acme/.settings/myapp/thisIsAVeryVeryLongFileNameWhichShouldTestThatWeHaven'tLimitedABufferAnywhere.ok" mnt/foo/this_is_a_hardlink_to_a_dir


	# Push file system limits.
	# By putting this last, if the image isn't big enough, we don't miss out on the other features.
	mkdir -p mnt/limits/large_dir
	for i in {1..100000}; do
		echo "$1" > mnt/limits/large_dir/file_${i}.txt || break
	done
}

# Continuously create largish files of various sizes filling the disk then
# randomly delte a few and create more files.
function create_fragmented_files() {
	fragDir="mnt/fragmented"
	mkdir -p "${fragDir}"
	maxSize=50

	# First fill the disk with randomly sized files.
	while true; do
		size=$((1 + $RANDOM % $maxSize))
		file=$fragDir/`date +%s`.dat
		cat /dev/urandom | head -c "${size}MB" > $file && echo "Created $file: $size" || break
	done
	sync && sync && sync

	# Now delete a few files
	for i in {1..100}; do
		# Pick a size of a new file and delete as many files as required to clear enough space.
		newSize=$((1 + $RANDOM % $maxSize))
		echo -e "Round $i:\tWant ${newSize}Mb, free space $(($(stat -f --format="(%a*%S)/(1024*1024)" ${fragDir})))Mb [$(stat -f --format="%a blocks, %f user blocks, %d inodes" ${fragDir})]"
		while [ $(($(stat -f --format="(%a*%S)/(1024*1024)" ${fragDir}))) -lt $(($newSize + 5)) ]; do
			files=( `ls -1 ${fragDir}` )
			#for ((i=0; i < ${#files[@]}; i++)); do
			#        echo "List $i - [${files[$i]}]"
			#done

			# Randonly select a file to delete.
			file=${files[$(($RANDOM % ${#files[@]}))]}

			echo -n "Deleting ${file}  $(($(stat -f --format="%s/(1024*1024)" ${fragDir}/${file}))) [$(stat -f --format="%a blocks, %f user blocks, %d inodes" ${fragDir})]: "
			rm -f "${fragDir}/${file}" && echo "Success"
		done
		sync && sync && sync

		size=$((1 + $RANDOM % $maxSize))
		file="${fragDir}/`date +%s`.dat"
		cat /dev/urandom | head -c "${size}MB" > $file && echo "Created ${file}: ${size}" || echo "Failed to create file."
		sync && sync && sync
	done
}

function mk_image() {

	size=$1
	shift
	name="$1"
	shift

	mkdir -p disk_images
	rm -f "disk_images/${name}"

	echo -e "\n\n*** Creating image ${name} ***\n\n"

	# Create a sparse disk image of the requested size and name.
	dd if=/dev/zero of="disk_images/${name}" bs=1 count=0 seek=${size}
	if [ $? -ne 0 ]; then
		echo "Failed to create image file for \"${name}\"."
		exit 2
	fi

	mkfs.xfs -d "name=disk_images/${name}"
	if [ $? -ne 0 ]; then
		echo "Failed to create XFS image for \"${name}\"."
		exit 2
	fi

	# Mount the image and add some data to it.
	umount mnt
	mkdir -p mnt
	mount -o loop "disk_images/${name}" mnt

	create_test_data

	df -h | grep "${pwd}/mnt"

	echo -e "*** Done ***\n\n"
}

mk_image 32M small_image.img
mk_image 512M default_named_image.img -L my-XFS-Disk

mk_image 32M small_block_size.img -b 512
mk_image 32M 8k_block_size.img -b 8192
mk_image 32M large_block_size.img -b 65536

mk_image 512M metadata_crc_and_free_inode_btrees.img -m crc=1,finobot=1

mk_image 128M 512_inode_size.img -i size=512
mk_image 64M 2k_inode_size.img -i size=2048

mk_image 64M 1k_sector_size.img -b 8192 -s 1024
mk_image 64M large_sector_size.img -b 65536 -s 65536

mk_image 630M fragmented_files.img -b 512
create_fragmented_files


# Clean up.
umount mnt
rmdir mnt
