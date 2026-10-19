#!/bin/sh
# Builds ntfs-split-compression-units.raw: a 16 MB NTFS volume holding compressed files whose compression units
# are stored raw and split across data runs. Run inside a container that can mount FUSE, with this directory as
# /work:
#
#   podman run --rm --device /dev/fuse --cap-add SYS_ADMIN --security-opt label=disable \
#       -v "$PWD":/work docker.io/library/alpine:3.20 sh /work/build-split-compression-units.sh
#
# ntfs-3g only splits an allocation once the volume has run out of space, allocates a whole compression unit even
# for a partial write, and gets page sized writes from FUSE. So the volume is filled to ENOSPC, each hole is freed
# just before the unit that should land in it is written, and every unit is written as a single 64 KiB request.
# The payloads are random, so a rebuild changes the hashes in NTFSSplitCompressionUnitImageTest.
set -u
cd /work
apk add --no-cache ntfs-3g ntfs-3g-progs attr >/dev/null 2>&1
IMAGE=ntfs-split-compression-units.raw
rm -f $IMAGE *.bin

# payloads: random units do not compress and are stored raw; the text unit compresses to a cluster or two
dd if=/dev/urandom of=/tmp/r1 bs=65536 count=1 iflag=fullblock 2>/dev/null
dd if=/dev/urandom of=/tmp/r2 bs=65536 count=2 iflag=fullblock 2>/dev/null
yes "jnode-fs compressible unit" | head -c 65536 > /tmp/t1
cp /tmp/r1 split-last.bin; cat /tmp/r1 /tmp/t1 > split-then-compressed.bin; cat /tmp/r2 /tmp/t1 > coalesced.bin
unit() { dd if=$1.bin bs=65536 skip=$2 count=1 2>/dev/null; }

truncate -s 16M $IMAGE && mkntfs -F -Q -s 512 -c 4096 -L SPLITCU $IMAGE >/dev/null 2>&1
mkdir -p mnt && ntfs-3g -o rw,big_writes $IMAGE mnt
mk() { head -c $(( $2 * 4096 )) /dev/zero > mnt/$1; }
fillup() { while head -c 4096 /dev/zero >> mnt/fill 2>/dev/null; do :; done; sync; }

mkdir mnt/comp && setfattr -h -n system.ntfs_attrib_be -v 0x00000810 mnt/comp
for f in split-last split-then-compressed coalesced; do : > mnt/comp/$f.bin; done

# the holes, in LCN order; g* are one cluster guards so neighbouring holes cannot merge, m3 follows h3 directly
mk f1 10; mk g1 1; mk h1 6; mk g2 1
mk f3 10; mk g3 1; mk h3 6; mk m3 16; mk g4 1; mk k3 16; mk g5 1
mk f2 10; mk g6 1; mk h2 6; mk g7 1; mk k2 16; mk g8 1
sync
: > mnt/fill; while head -c $((256*1024)) /dev/zero >> mnt/fill 2>/dev/null; do :; done; fillup

# 1. a raw unit split 10 + 6 and nothing after it
rm mnt/f1 mnt/h1; sync; unit split-last 0 > mnt/comp/split-last.bin; sync
# 2. the same, then a unit that compresses; its 16 cluster hole is freed only once the raw unit is on disk
fillup; rm mnt/f2 mnt/h2; sync; unit split-then-compressed 0 > mnt/comp/split-then-compressed.bin; sync
rm mnt/k2; sync; unit split-then-compressed 1 >> mnt/comp/split-then-compressed.bin; sync; fillup
# 3. a raw unit split 10 + 6, a second raw unit directly after the 6 so the runs merge to 22, then a unit that compresses
rm mnt/f3 mnt/h3; sync; unit coalesced 0 > mnt/comp/coalesced.bin; sync
rm mnt/m3; sync; unit coalesced 1 >> mnt/comp/coalesced.bin; sync
rm mnt/k3; sync; unit coalesced 2 >> mnt/comp/coalesced.bin; sync

for f in split-last split-then-compressed coalesced; do cmp mnt/comp/$f.bin $f.bin && echo "verified $f"; done
rm mnt/fill mnt/g?; sync
umount mnt 2>/dev/null || fusermount3 -u mnt
md5sum *.bin
