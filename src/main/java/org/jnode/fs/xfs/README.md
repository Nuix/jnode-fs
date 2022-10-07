# Resources

1. http://ftp.ntu.edu.tw/linux/utils/fs/xfs/docs/xfs_filesystem_structure.pdf
2. https://github.com/libyal/libfsxfs/blob/main/documentation/X%20File%20System%20(XFS).asciidoc

## Required information
XFS file systems are divided into Allocation groups(AG)

Each Allocation group contains several "main" elements of which only the Superblock(SB) of the first AG is useful for reading

In order to partition data information is stored in Blocks(Blocks default size is 4096 Bytes)

Files, directories, SymLinks and all major data structures are represented as INodes.(Inode Default Size is 512 Bytes)

In order to find the adequate byte offset to read either an INode or a Block the required calculations are denoted in the 
**XfsFileSystem** Class, and in the **DataExtent** Class respectively

INodes may contain either information directly in their body for small directory structures(type 1) 
and contains either data extents(type 2) or block pointers for b trees(type 3)

In order to better be able to understand the structures the use of a binary file reader is recommended.

Most data structures start at an offset 0 of the inode or block offset, and values sizes are measured in number of bytes. Some data structures are either 8 bytes or 16 bytes aligned

### Libyal

This repository documentation is recomended as a base to compare with the PDF version which contains the actual spec, 
it is recommended to compare the data structure Superblock to its PDF counterpart in order to get a better understanding of how data is aligned  


### XFS Algorithms & Data Structures (PDF)

This document contains the most recent spec as well as the v4 and v5 spec for every structure. 
B+tree Hash information can be ignored as it is only useful for optimized searches

The information is recommended to be read in the following order:

1. Allocation groups
2. SuperBlocks
3. Data Extents
4. INodes in the described order in the order presented in the PDF
5. Extended Attributes in the order presented in the PDF

Inside de documentation all structures are described as struct, they are just classes that contain data, so for example 
in the superblock structure it starts with:

```c
struct xfs_sb {
__uint32_t      sb_magicnum;
__uint32_t      sb_blocksize
xfs_rfsblock_t  sb_dblocks
// ..... more properties
}
```
uint32 represents a 4 byte integer number and xfs_rfsblock_t is a 8 bytes(64 bit) integer number representing a block 
in the file system (this info can be found by searching **xfs_rfsblock_t** inside de PDF), 
this means that starting from the superblock original offset(for out usecase of only reading is always 0 on the superblock)

| property     | offset | byte size |
|--------------|--------|-----------|
| sb_magicnum  | 0      | 4         |
| sb_blocksize | 4      | 4         |
| sb_dblocks   | 8      | 4         |

number values are sometimes represented in their hexadecimal form specially in magic numbers
(allows you to ascertain the type of structure you are reading), and signatures.

Strings are represented as ascii bytes, so each byte is a character, for easy reference checkout 
https://www.rapidtables.com/code/text/ascii-table.html

For exploration the xfs_db tool is really useful. for this the samples inside the documentation are really good, 
the only command to add would be **addr** which will print the offset from the beginning of the file for the currently selected structure
allowing for a fast search using the hexeditor rool