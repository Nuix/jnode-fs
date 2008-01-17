package org.jnode.fs.fat;

public enum FatType {
	FAT12(0xFFF, 1.5f),
	FAT16(0xFFFF, 2.0f),
	FAT32(0xFFFFFFFF, 4.0f);

	private final long minReservedEntry;
	private final long maxReservedEntry;
	private final long eofCluster;
	private final long eofMarker;
	private final float entrySize;

	private FatType(long bitMask, float entrySize)
	{
		this.minReservedEntry = (0xFFFFFFF0 & bitMask);
		this.maxReservedEntry = (0xFFFFFFF6 & bitMask);
		this.eofCluster = (0xFFFFFFF8 & bitMask);
		this.eofMarker = (0xFFFFFFFF & bitMask);
		this.entrySize = entrySize;
	}

	final public boolean isReservedCluster(long entry) {
		return ((entry >= minReservedEntry) && (entry <= maxReservedEntry));
	}

	final public boolean isEofCluster(long entry) {
		return (entry >= eofCluster);
	}

	final public long getEofMarker() {
		return eofMarker;
	}

	final public float getEntrySize() {
		return entrySize;
	}
}