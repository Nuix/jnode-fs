"""Extracts the $MFT from the NIST CFReDS Hacking Case image over HTTP range requests.

The image is published as eight raw dd segments totalling 4.53 GiB, but the $MFT is only ~12 MB, so this reads
the boot sector, follows $MFT's own data runs and fetches just those extents.
"""
import struct
import sys
import urllib.request

BASE = 'https://cfreds-archive.nist.gov/images/hacking-dd'
SEGMENT = 666238976          # the size of segments 1-7; the last is shorter
SEGMENTS = 8
PARTITION = 63 * 512         # from the MBR


def http_range(url, start, end):
    req = urllib.request.Request(url, headers={'Range': 'bytes=%d-%d' % (start, end), 'User-Agent': 'extract'})
    with urllib.request.urlopen(req, timeout=180) as r:
        return r.read()


def fetch(start, length):
    """Reads a range of the concatenated image."""
    out = bytearray()
    while length > 0:
        segment = start // SEGMENT
        offset = start % SEGMENT
        if segment >= SEGMENTS:
            raise IOError('offset %d is past the end of the image' % start)
        count = min(length, SEGMENT - offset)
        chunk = http_range('%s/SCHARDT.%03d' % (BASE, segment + 1), offset, offset + count - 1)
        if not chunk:
            raise IOError('empty read at %d' % start)
        out += chunk
        start += len(chunk)
        length -= len(chunk)
    return bytes(out)


def fixup(record, sector_size):
    """Applies the update sequence array to an MFT record."""
    usa_offset = struct.unpack_from('<H', record, 0x04)[0]
    usa_count = struct.unpack_from('<H', record, 0x06)[0]
    record = bytearray(record)
    for i in range(1, usa_count):
        tail = i * sector_size - 2
        if tail + 2 > len(record):
            break
        record[tail:tail + 2] = record[usa_offset + i * 2:usa_offset + i * 2 + 2]
    return bytes(record)


def data_runs(record, offset):
    """Decodes the run list of a non-resident attribute at the given offset."""
    runs = []
    position = offset + struct.unpack_from('<H', record, offset + 0x20)[0]
    lcn = 0
    while record[position] != 0:
        header = record[position]
        length_size, offset_size = header & 0x0F, header >> 4
        position += 1
        length = int.from_bytes(record[position:position + length_size], 'little')
        position += length_size
        delta = int.from_bytes(record[position:position + offset_size], 'little', signed=True)
        position += offset_size
        lcn += delta
        runs.append((lcn, length))
    return runs


def main(out_path):
    boot = fetch(PARTITION, 512)
    bytes_per_sector = struct.unpack_from('<H', boot, 0x0B)[0]
    sectors_per_cluster = boot[0x0D]
    cluster = bytes_per_sector * sectors_per_cluster
    mft_lcn = struct.unpack_from('<q', boot, 0x30)[0]
    raw = struct.unpack_from('<b', boot, 0x40)[0]
    record_size = raw * cluster if raw >= 0 else 1 << -raw

    print('sector %d, cluster %d, $MFT at LCN %d, record size %d'
          % (bytes_per_sector, cluster, mft_lcn, record_size))

    record = fixup(fetch(PARTITION + mft_lcn * cluster, record_size), bytes_per_sector)
    assert record[:4] == b'FILE', 'MFT record 0 is not a FILE record'

    position = struct.unpack_from('<H', record, 0x14)[0]
    runs = None
    while position + 8 <= len(record):
        attribute_type = struct.unpack_from('<I', record, position)[0]
        if attribute_type == 0xFFFFFFFF:
            break
        length = struct.unpack_from('<I', record, position + 4)[0]
        if attribute_type == 0x80 and record[position + 8] == 1:
            size = struct.unpack_from('<q', record, position + 0x30)[0]
            runs = data_runs(record, position)
            print('$MFT $DATA: %d bytes in %d run(s)' % (size, len(runs)))
            break
        position += length

    assert runs, 'no non-resident $DATA on $MFT'

    with open(out_path, 'wb') as out:
        written = 0
        for lcn, clusters in runs:
            remaining = clusters * cluster
            offset = PARTITION + lcn * cluster
            while remaining > 0:
                count = min(remaining, 4 << 20)
                out.write(fetch(offset, count))
                offset += count
                remaining -= count
                written += count
            print('  run at LCN %d, %d clusters (%d bytes so far)' % (lcn, clusters, written))

    print('wrote %s (%d bytes)' % (out_path, written))


if __name__ == '__main__':
    main(sys.argv[1])
