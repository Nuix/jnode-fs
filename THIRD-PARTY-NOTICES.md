# Third-party notices

jnode-fs is licensed under the GNU Lesser General Public License v2.1 or later (see `LICENSE.txt`).
It includes the third-party material listed below, each under its own license.

## Source code

### lzfse (LZVN decoder)

- File: `src/main/java/org/jnode/fs/hfsplus/compression/LzvnDecode.java`
- Adapted from: https://github.com/lzfse/lzfse/blob/master/src/lzvn_decode_base.c
- Copyright (c) 2015-2016, Apple Inc. All rights reserved.
- License: BSD 3-Clause. The full license text is kept in the header of the source file.

## Test resources

These files are used only by the unit tests and are not included in the distributed jar.

### libfsntfs test data

- Files: `src/test/resources/org/jnode/fs/ntfs/libfsntfs/`
- Source: https://github.com/libyal/libfsntfs (tests)
- Copyright (C) Joachim Metz
- License: GNU Lesser General Public License v3.0 or later

### Python lznt1 test data

- Files: `src/test/resources/org/jnode/fs/ntfs/py-lznt1/`
- Generated with: https://github.com/you0708/lznt1
- Content: "The Tragedy of Hamlet" by William Shakespeare (public domain)

### NIST CFReDS "Hacking Case"

- File: `src/test/resources/org/jnode/fs/ntfs/nist-hacking-case.mft.gz`
- Source: NIST Computer Forensic Reference Data Sets (CFReDS), https://cfreds.nist.gov/
- The $MFT extracted from the publicly released "Hacking Case" image.
