# $LogFile fixtures

Each `.LogFile.gz` is a `$LogFile` stream that was cut out of a volume image, then compressed with `gzip -9`. Each
`.LogFile.lsn` file lists the records that the walk must find, one per line: the LSN, then the offset that the LSN
encodes. The lists were generated once by a Python model of the walk. The model is separate code, but it was written
with this walk and follows the same design, so it is not an independent reference. It has one change: a record that
crosses into a page from another pass of the log is not in the list.

| Fixture | Origin | Licence |
|---|---|---|
| `dfir-orc.LogFile.gz` | `tests/OrcLibTest/ntfs_images/ntfs.7z` (`ntfs.vhd`, partition at LBA 128) from <https://github.com/DFIR-ORC/dfir-orc>. MD5 of the stream: `a2d7bbf03f98f557ce1d0777cac84f67`. | LGPL-2.1-or-later, Copyright 2011-2019 ANSSI (per the repository's source headers) |
| `win11-reparse.LogFile.gz` | `tests/data/ntfs_windows_reparse.zip` (`ntfs_windows_reparse.img`, Windows 11) from <https://github.com/SecurityRonin/ntfs-forensic>. MD5 of the stream: `b720acd216925c7629b73a1d1ad1c636`. | Apache-2.0, Albert Hui (SecurityRonin) |
| `sample-tiny.LogFile.gz` | `SampleTinyNtfsVolume/$LogFile` from `LogFileParser_v2.0.0.53.zip` at <https://github.com/jschicht/LogFileParser>, as redistributed in `tests/data/SampleTinyNtfsVolume.zip` of SecurityRonin/ntfs-forensic. MD5 of the stream: `f82b4c5437ee297913847a94d30afece`. | MIT, Copyright (c) 2022 Joakim Schicht |
| `tiny-blank.LogFile.gz` | `tests/data/tiny.zip` (`tiny.img`, made by `mkntfs` from ntfs-3g 2022.10.3) from <https://github.com/SecurityRonin/ntfs-forensic>. The stream is all 0xFF. MD5 of the stream: `b23b5d09162b92c0284923a7f628d2a5`. | Apache-2.0, Albert Hui (SecurityRonin) |

## MIT notice for `sample-tiny.LogFile.gz`

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
