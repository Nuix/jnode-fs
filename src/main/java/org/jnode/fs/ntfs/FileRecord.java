/*
 * $Id$
 *
 * Copyright (C) 2003-2015 JNode.org
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public 
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; If not, write to the Free Software Foundation, Inc., 
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
 
package org.jnode.fs.ntfs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

import lombok.Getter;
import org.jnode.fs.ntfs.attribute.AttributeListAttribute;
import org.jnode.fs.ntfs.attribute.AttributeListBuilder;
import org.jnode.fs.ntfs.attribute.AttributeListEntry;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.fs.ntfs.attribute.NTFSNonResidentAttribute;
import org.jnode.fs.ntfs.attribute.NTFSResidentAttribute;
import org.jnode.fs.ntfs.attribute.ReparsePointAttribute;
import org.jnode.fs.ntfs.attribute.ReparsePointTags;
import org.jnode.fs.ntfs.datarun.CompressedDataRun;
import org.jnode.fs.ntfs.datarun.CompressionUnitProblem;
import org.jnode.fs.ntfs.datarun.DataRunInterface;
import org.jnode.fs.ntfs.wof.WofCompressedStream;
import org.jnode.fs.ntfs.wof.WofCompressionFormat;
import org.jnode.fs.util.FSUtils;
import org.jnode.util.LittleEndian;
import org.jnode.util.NumberUtils;

/**
 * MFT file record structure.
 *
 * @author Chira
 * @author Ewout Prangsma (epr@users.sourceforge.net)
 * @author Daniel Noll (daniel@noll.id.au) (new attribute iteration support)
 */
public class FileRecord extends NTFSRecord {

    /**
     * The name of the stream holding the data of a WOF compressed file.
     */
    public static final String WOF_STREAM_NAME = "WofCompressedData";

    private static final int WOF_PROVIDER_FILE = 2;

    private static final int WOF_REPARSE_DATA_SIZE = 16;

    /**
     * The volume this record is a part of.
     */
    @Getter
    private final NTFSVolume volume;

    /**
     * The cluster size for the volume containing this record.
     */
    @Getter
    private final int clusterSize;

    /**
     * Gets the reference number (index number) of this record within the MFT. This value is not actually stored in the
     * record, but passed in from the outside.
     */
    @Getter
    private final long referenceNumber;

    /**
     * Cached attribute list attribute.
     */
    protected AttributeListAttribute attributeListAttribute;

    /**
     * The stored attributes.
     */
    protected List<NTFSAttribute> storedAttributeList;

    /**
     * A cached copy of the full list of attributes.
     */
    protected List<NTFSAttribute> attributeList;

    /**
     * A cached copy of the attribute list attributes.
     */
    protected List<NTFSAttribute> attributeListAttributes;

    /**
     * Cached standard information attribute.
     */
    private StandardInformationAttribute standardInformationAttribute;

    /**
     * List of file name attributes.
     */
    private List<FileNameAttribute> fileNameAttributes;

    /**
     * The outcome of looking for WOF compressed data, once looked for. Volatile so that reads after the first,
     * including every MFT record fetch, need no lock.
     */
    private volatile WofLookup wofLookup;

    /**
     * Why the attribute list could not be read, if it could not, leaving only the attributes stored here.
     */
    private Exception attributeListFailure;

    /**
     * Why the walk of the attributes stored here stopped before the end marker, if it did.
     */
    private Exception storedAttributeFailure;

    /**
     * Initialize this instance.
     *
     * @param volume          reference to the NTFS volume.
     * @param referenceNumber the reference number of the file within the MFT.
     * @param buffer          data buffer.
     * @param offset          offset into the buffer.
     */
    public FileRecord(NTFSVolume volume, long referenceNumber, byte[] buffer, int offset) throws IOException {
        this(volume, volume.getClusterSize(), true, referenceNumber,
            buffer, offset);
    }

    /**
     * Initialize this instance.
     *
     * @param volume          reference to the NTFS volume.
     * @param clusterSize     the cluster size for the volume containing this record.
     * @param strictFixUp     indicates whether an exception should be throw if fix-up values don't match.
     * @param referenceNumber the reference number of the file within the MFT.
     * @param buffer          data buffer.
     * @param offset          offset into the buffer.
     */
    public FileRecord(NTFSVolume volume, int clusterSize, boolean strictFixUp, long referenceNumber,
                      byte[] buffer, int offset) throws IOException {

        super(strictFixUp, buffer, offset);

        this.volume = volume;
        this.clusterSize = clusterSize;
        this.referenceNumber = referenceNumber;
    }

    /**
     * Checks if the record appears to be valid.
     *
     * @throws IOException if an error occurs.
     */
    public void checkIfValid() throws IOException {
        // check for the magic number to see if we have a file record
        if (getMagic() != Magic.FILE) {
            if (log.isDebugEnabled()) {
                log.debug("Invalid magic number found for FILE record({}): {} -- dumping buffer", referenceNumber, getMagic());
                for (int off = 0; off < getBuffer().length; off += 32) {
                    StringBuilder builder = new StringBuilder();
                    for (int i = off; i < off + 32 && i < getBuffer().length; i++) {
                        String hex = Integer.toHexString(getBuffer()[i]);
                        while (hex.length() < 2) {
                            hex = '0' + hex;
                        }

                        builder.append(' ').append(hex);
                    }
                    log.debug(builder.toString());
                }
            }

            throw new IOException("Invalid magic found on record(" + referenceNumber + "): " + getMagic());
        }

        // This additional sanity check is possible if the record also contains the MFT number.
        // Helps catch bugs where a record is being read from the wrong offset.
        final long storedReferenceNumber = getStoredReferenceNumber();
        if (storedReferenceNumber >= 0 && referenceNumber != storedReferenceNumber) {
            throw new IOException("Stored reference number " + getStoredReferenceNumber()
                + " does not match reference number " + referenceNumber);
        }
    }

    /**
     * Gets the allocated size of the FILE record in bytes.
     *
     * @return Returns the allocated size.
     */
    public long getAllocatedSize() {
        return getUInt32(0x1C);
    }

    /**
     * Gets the reference number of the base record. For continuation MFT entries this will reference the main record.
     * For main records this should match {@link #referenceNumber}.
     *
     * @return Returns the base reference number.
     */
    public long getBaseReferenceNumber() {
        return getUInt48(0x20);
    }

    /**
     * Gets the real size of the FILE record in bytes.
     *
     * @return Returns the realSize.
     */
    public long getRealSize() {
        return getUInt32(0x18);
    }

    /**
     * Is this record in use?
     *
     * @return {@code true} if the record is in use.
     */
    public boolean isInUse() {
        return (getFlags() & 0x01) != 0;
    }

    /**
     * Is this a directory?
     *
     * @return {@code true} if the record is a directory.
     */
    public boolean isDirectory() {
        return (getFlags() & 0x02) != 0;
    }

    /**
     * Gets the hard link count.
     *
     * @return Returns the hardLinkCount.
     */
    public int getHardLinkCount() {
        return getUInt16(0x12);
    }

    /**
     * Gets the byte offset to the first attribute in this mft record from the start of the mft record.
     *
     * @return the first attribute offset.
     */
    public int getFirstAttributeOffset() {
        return getUInt16(0x14);
    }

    /**
     * Gets the flags.
     *
     * @return Returns the flags.
     */
    public int getFlags() {
        return getUInt16(0x16);
    }

    /**
     * Gets the Next Attribute Id.
     *
     * @return Returns the nextAttributeID.
     */
    public int getNextAttributeID() {
        return getUInt16(0x28);
    }

    /**
     * Gets the $LogFile sequence number.
     *
     * @return the $LogFile sequence number.
     */
    public long getLsn() {
        return getInt64(0x08);
    }

    /**
     * Gets the number of times this mft record has been reused.
     *
     * @return Returns the sequenceNumber.
     */
    public int getSequenceNumber() {
        return getUInt16(0x10);
    }

    /**
     * @return Returns the updateSequenceOffset.
     */
    public int getUpdateSequenceOffset() {
        return getUInt16(0x4);
    }

    /**
     * Gets the stored reference number. This can be compared against the reference number to confirm that the correct
     * file record was returned, however it is not available on all versions of NTFS, and even on recent versions some
     * MFT records lack it.
     *
     * @return the stored file reference number, or {@code -1} if it is not stored.
     */
    public long getStoredReferenceNumber() {
        // Expected to be 0x2A pre-XP.
        if (getUpdateSequenceOffset() >= 0x30) {
            return getUInt32(0x2C);
        } else {
            return -1;
        }
    }

    /**
     * <p>Gets the name of this file.</p>
     *
     * <p>The file name can be different for every hard-linked copy of the file. To find the correct name
     * we need to look for a matching parent MFT index. If the {@code parentRef} cannot be matched to
     * a {@link FileNameAttribute}, this will return the first file name found.
     *
     * <p>Currently preferring names in the {@link FileNameAttribute.NameSpace#WIN32} namespace, with a fallback
     * to values in other namespaces.</p>
     *
     * @param parentRef the parent MFT record reference.
     * @return the filename.
     */
    public String getFileName(long parentRef) {
        FileNameAttribute firstNameAttribute = null;
        FileNameAttribute attributeByParentRef = null;

        getFileNameAttributes();

        for (FileNameAttribute fileNameAttribute : fileNameAttributes) {
            if (firstNameAttribute == null || firstNameAttribute.getNameSpace() != FileNameAttribute.NameSpace.WIN32) {
                firstNameAttribute = fileNameAttribute;
            }
            if (fileNameAttribute.getParentMftIndex() == parentRef &&
                (attributeByParentRef == null || attributeByParentRef.getNameSpace() != FileNameAttribute.NameSpace.WIN32)) {
                attributeByParentRef = fileNameAttribute;
            }
        }

        if (attributeByParentRef != null) {
            return attributeByParentRef.getFileName();
        } else if (firstNameAttribute != null) {
            return firstNameAttribute.getFileName();
        } else {
            return null;
        }
    }

    /**
     * Gets the standard information attribute for this file record.
     *
     * @return the standard information attribute.
     */
    public StandardInformationAttribute getStandardInformationAttribute() {
        if (standardInformationAttribute == null) {
            standardInformationAttribute =
                (StandardInformationAttribute) findAttributeByType(NTFSAttribute.Types.STANDARD_INFORMATION);
        }
        return standardInformationAttribute;
    }

    /**
     * Gets all the file name attributes for this file record.
     *
     * @return a list of file name attributes.
     */
    public List<FileNameAttribute> getFileNameAttributes() {
        if (fileNameAttributes == null) {
            fileNameAttributes = new ArrayList<>(10);
            Iterator<NTFSAttribute> iterator = findAttributesByType(NTFSAttribute.Types.FILE_NAME);

            while (iterator.hasNext()) {
                fileNameAttributes.add((FileNameAttribute) iterator.next());
            }
        }

        return fileNameAttributes;
    }

    /**
     * Gets the attributes stored in this file record.
     *
     * @return an iterator over attributes stored in this file record.
     */
    public List<NTFSAttribute> getAllStoredAttributes() {
        if (storedAttributeList == null) {
            storedAttributeList = readStoredAttributes();
        }
        return storedAttributeList;
    }

    /**
     * Finds a single stored attribute by ID.
     *
     * @param id the ID.
     * @return the attribute found, or {@code null} if not found.
     */
    private NTFSAttribute findStoredAttributeByID(int id) {
        for (NTFSAttribute attr : getAllStoredAttributes()) {
            if (attr != null && attr.getAttributeID() == id) {
                return attr;
            }
        }
        return null;
    }

    /**
     * Finds a single stored attribute by type.
     *
     * @param typeID the type ID
     * @return the attribute found, or {@code null} if not found.
     * @see NTFSAttribute.Types
     */
    private NTFSAttribute findStoredAttributeByType(NTFSAttribute.Types typeID) {
        for (NTFSAttribute attr : getAllStoredAttributes()) {
            if (attr != null && attr.getAttributeType() == typeID) {
                return attr;
            }
        }
        return null;
    }

    /**
     * Gets the attributes list attribute, if the record has one.
     *
     * @return the attribute, or {@code null}.
     */
    public AttributeListAttribute getAttributeListAttribute() {
        if (attributeListAttribute == null) {
            // Linux NTFS docs say there can only be one of these, so I'll believe them.
            attributeListAttribute =
                (AttributeListAttribute) findStoredAttributeByType(NTFSAttribute.Types.ATTRIBUTE_LIST);
        }

        return attributeListAttribute;
    }

    /**
     * Gets a collection of all attributes in this file record, including any attributes
     * which are stored in other file records referenced from an $ATTRIBUTE_LIST attribute.
     *
     * @return a collection of all attributes.
     */
    public synchronized List<NTFSAttribute> getAllAttributes() {
        if (attributeList == null) {
            try {
                attributeList = new ArrayList<>(getAllStoredAttributes());
                if (getAttributeListAttribute() != null) {
                    log.debug("Attributes in attribute list ({})", referenceNumber);
                    if (attributeListAttributes == null) {
                        attributeListAttributes = readAttributeListAttributes(referenceNumber -> {
                            // When reading the MFT itself don't attempt to check the index is in range
                            // (we won't know the total MFT length yet)
                            MasterFileTable mft = getVolume().getMFT();
                            return getReferenceNumber() == MasterFileTable.SystemFiles.MFT
                                   ? mft.getRecordUnchecked(referenceNumber)
                                   : mft.getRecord(referenceNumber);
                        });
                    }
                    attributeList = new ArrayList<>(attributeListAttributes);
                }

                if (log.isDebugEnabled()) {
                    log.debug("Final list of attributes for {}:\n{}", referenceNumber, attributeList.stream().map(Objects::toString).collect(Collectors.joining("\n")));
                }
            } catch (Exception e) {
                log.error("Error getting attributes for file record: {}, returning stored attributes", referenceNumber, e);
                attributeListFailure = e;
                attributeList = storedAttributeList != null
                    ? new ArrayList<>(storedAttributeList)
                    : new ArrayList<NTFSAttribute>();
            }
        }

        return attributeList;
    }

    /**
     * Gets the first attribute in this file record with a given type.
     *
     * @param attrTypeID the type ID of the attribute we're looking for.
     * @return the attribute.
     */
    public NTFSAttribute findAttributeByType(NTFSAttribute.Types attrTypeID) {
        if (log.isDebugEnabled()) {
            log.debug("{}:findAttributeByType(0x{})", referenceNumber, NumberUtils.hex(attrTypeID.getValue(), 4));
        }

        for (NTFSAttribute attr : getAllAttributes()) {
            if (attr.getAttributeType() == attrTypeID) {
                if (log.isDebugEnabled()) {
                    log.debug("{}:findAttributeByType(0x{}) found", referenceNumber, NumberUtils.hex(attrTypeID.getValue(), 4));
                }
                return attr;
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("{}:findAttributeByType(0x{}) not found", referenceNumber, NumberUtils.hex(attrTypeID.getValue(), 4));
        }
        return null;
    }

    /**
     * Gets attributes in this file record with a given type.
     *
     * @param attrTypeID the type ID of the attribute we're looking for.
     * @return an iterator for the matching the attributes.
     */
    public Iterator<NTFSAttribute> findAttributesByType(final NTFSAttribute.Types attrTypeID) {
        if (log.isDebugEnabled()) {
            log.debug("{}:findAttributesByType(0x{})", referenceNumber, NumberUtils.hex(attrTypeID.getValue(), 4));
        }

        return new FilteredAttributeIterator(getAllAttributes().iterator()) {
            @Override
            protected boolean matches(NTFSAttribute attr) {
                return attr.getAttributeType() == attrTypeID;
            }
        };
    }

    /**
     * Gets attributes in this file record with a given type and name.
     *
     * @param attrTypeID the type ID of the attribute we're looking for.
     * @param name       the name to look for.
     * @return an iterator for the matching the attributes.
     */
    public Iterator<NTFSAttribute> findAttributesByTypeAndName(final NTFSAttribute.Types attrTypeID, final String name) {
        if (log.isDebugEnabled()) {
            log.debug("{}:findAttributesByTypeAndName(0x{},{})", referenceNumber, NumberUtils.hex(attrTypeID.getValue(), 4), name);
        }
        return new FilteredAttributeIterator(getAllAttributes().iterator()) {
            @Override
            protected boolean matches(NTFSAttribute attr) {
                if (attr.getAttributeType() == attrTypeID) {
                    String attrName = attr.getAttributeName();
                    if (Objects.equals(name, attrName)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    /**
     * Gets the total size used for the given attribute. Often the directory index entry and the FileRecord will have
     * stale values for the file length, so checking the length of the {@link NTFSAttribute.Types#DATA} attribute is the
     * most reliable way to get the actual file length.
     *
     * @param attrTypeID the type of attribute to get the size for, e.g. {@link NTFSAttribute.Types#DATA}.
     * @param name       the name of the attribute or {@code null} for no name.
     * @return the total size of the attribute.
     */
    public long getAttributeTotalSize(NTFSAttribute.Types attrTypeID, String name) {
        Iterator<NTFSAttribute> attributes = findAttributesByTypeAndName(attrTypeID, name);

        if (!attributes.hasNext()) {
            // If the file is deleted, or a partial record from a volume shadow copy, then it may not have all
            // expected attributes
            log.debug("{}:No attributes found for attribute type: {} ({}), returning 0 as size", referenceNumber, attrTypeID, name);
            return 0;
        }

        NTFSAttribute attribute = attributes.next();
        if (attribute.isResident()) {
            // If the attribute is resident it should be the only attribute of that type present, so just return
            // the length
            return ((NTFSResidentAttribute) attribute).getAttributeLength();
        } else {
            // The total length seems to be stored in the first attribute of a certain type. E.g. if there are two
            // DATA attributes each with data runs, the first one has the total length, and the intermediate ones
            // seem to contain the length of that particular attribute. So here just return the length of the first
            // attribute
            return ((NTFSNonResidentAttribute) attribute).getAttributeActualSize();
        }
    }

    /**
     * Reads data from the file.
     *
     * @param fileOffset the offset into the file.
     * @param dest       the destination byte array into which to copy the file data.
     * @param off        the offset into the destination byte array.
     * @param len        the number of bytes of data to read.
     * @throws IOException if an error occurs reading from the filesystem.
     */
    public void readData(long fileOffset, byte[] dest, int off, int len) throws IOException {
        WofCompressedStream wof = getWofCompressedStream();
        int wofLength = 0;
        if (wof != null) {
            wofLength = (int) Math.max(0, Math.min(len, wof.getUncompressedSize() - fileOffset));
            if (wofLength > 0) {
                wof.read(fileOffset, dest, off, wofLength);
            }
        }

        // Explicitly look for the attribute with no name, to avoid getting alternate streams. For a WOF file this
        // reads what lies past the end from the sparse attribute, so it behaves as any other file does there.
        readData(NTFSAttribute.Types.DATA, null, fileOffset + wofLength, dest, off + wofLength, len - wofLength, true);
    }

    /**
     * Gets the WOF compressed form of the file data, for a file compressed with {@code compact /exe}.
     *
     * <p>Such a file has a WOF reparse point naming the file provider and a compression format, an unnamed data
     * attribute that is sparse with nothing allocated but carries the real file size, and the data itself in the
     * {@value #WOF_STREAM_NAME} stream. The stream is still listed among the file's streams, as it is on disk.
     * The WIM provider, which points into an external WIM file, is not supported.</p>
     *
     * <p>A file that is WOF compressed but cannot be read this way fails rather than reading the unnamed attribute,
     * which holds nothing but zeros.</p>
     *
     * @return the stream, or {@code null} if the data is not WOF compressed.
     * @throws IOException if the data is WOF compressed in a form that cannot be read.
     */
    public WofCompressedStream getWofCompressedStream() throws IOException {
        WofLookup lookup = wofLookup;
        if (lookup == null) {
            synchronized (this) {
                lookup = wofLookup;
                if (lookup == null) {
                    try {
                        lookup = new WofLookup(findWofCompressedStream(), null);
                    } catch (IOException e) {
                        // Kept, so that each read of a file that cannot be read fails without looking again
                        lookup = new WofLookup(null, new IOException(
                            "Cannot read the WOF compressed data of " + this + ": " + e.getMessage(), e));
                    }
                    wofLookup = lookup;
                }
            }
        }
        return lookup.get();
    }

    private WofCompressedStream findWofCompressedStream() throws IOException {
        NTFSAttribute reparsePoint = findAttributeByType(NTFSAttribute.Types.REPARSE_POINT);
        if (reparsePoint == null && attributeListFailure != null && isFlaggedAsReparsePoint()) {
            throw new IOException("the reparse point may be in an extension record, but the attribute list could " +
                "not be read", attributeListFailure);
        }
        if (reparsePoint == null && storedAttributeFailure != null && isFlaggedAsReparsePoint()) {
            throw new IOException("the reparse point may be past the attribute that could not be read",
                storedAttributeFailure);
        }
        if (!(reparsePoint instanceof ReparsePointAttribute)) {
            return null;
        }

        int tag;
        try {
            tag = ((ReparsePointAttribute) reparsePoint).getReparseTag();
        } catch (UncheckedIOException e) {
            // Without the tag there is no telling whether this is WOF. Without the stream it could not be read as
            // WOF either way, so leave it to read as any other reparse point does.
            if (!findAttributesByTypeAndName(NTFSAttribute.Types.DATA, WOF_STREAM_NAME).hasNext()) {
                return null;
            }
            throw e.getCause();
        }
        if (tag != ReparsePointTags.IO_REPARSE_TAG_WOF) {
            return null;
        }

        // WOF_EXTERNAL_INFO {version, provider}, then the file provider's {version, format}
        byte[] value = ((ReparsePointAttribute) reparsePoint).getReparseData();
        if (value.length < WOF_REPARSE_DATA_SIZE) {
            throw new IOException("the reparse point holds " + value.length + " bytes, too few for WOF");
        }

        int wofVersion = LittleEndian.getInt32(value, 0);
        int provider = LittleEndian.getInt32(value, 4);
        int providerVersion = LittleEndian.getInt32(value, 8);
        int formatId = LittleEndian.getInt32(value, 12);
        WofCompressionFormat format = WofCompressionFormat.fromId(formatId);
        if (wofVersion != 1 || provider != WOF_PROVIDER_FILE || providerVersion != 1 || format == null) {
            throw new IOException(String.format("unsupported WOF version %d provider %d version %d format %d",
                wofVersion, provider, providerVersion, formatId));
        }

        if (!findAttributesByTypeAndName(NTFSAttribute.Types.DATA, WOF_STREAM_NAME).hasNext() ||
            !findAttributesByTypeAndName(NTFSAttribute.Types.DATA, null).hasNext()) {
            throw new IOException("the " + WOF_STREAM_NAME + " stream or the unnamed data attribute is missing");
        }

        return new WofCompressedStream(
            (offset, dest, destOffset, length) ->
                readData(NTFSAttribute.Types.DATA, WOF_STREAM_NAME, offset, dest, destOffset, length, true),
            getAttributeTotalSize(NTFSAttribute.Types.DATA, WOF_STREAM_NAME),
            getAttributeTotalSize(NTFSAttribute.Types.DATA, null),
            format);
    }

    private boolean isFlaggedAsReparsePoint() {
        StandardInformationAttribute standardInformation = getStandardInformationAttribute();
        return standardInformation != null &&
            StandardInformationAttribute.Flags.REPARSE_POINT.isSet(standardInformation.getFlags());
    }

    /**
     * The outcome of looking for WOF compressed data: the stream, none, or why it cannot be read.
     */
    private static final class WofLookup {
        private final WofCompressedStream stream;
        private final IOException failure;

        WofLookup(WofCompressedStream stream, IOException failure) {
            this.stream = stream;
            this.failure = failure;
        }

        WofCompressedStream get() throws IOException {
            if (failure != null) {
                throw new IOException(failure.getMessage(), failure);
            }
            return stream;
        }
    }

    /**
     * Reads data from the file.
     *
     * @param attributeType the attribute type to read from.
     * @param streamName the stream name to read from, or {@code null} to read from the default stream.
     * @param fileOffset the offset into the file.
     * @param dest       the destination byte array into which to copy the file data.
     * @param off        the offset into the destination byte array.
     * @param len        the number of bytes of data to read.
     * @param limitToInitialised {@code true} if the data read in should be limited to the initalised part of the
     *                    attribute.
     * @throws IOException if an error occurs reading from the filesystem.
     */
    public void readData(NTFSAttribute.Types attributeType, String streamName, long fileOffset, byte[] dest, int off, int len,
                         boolean limitToInitialised)
        throws IOException {

        if (log.isDebugEnabled()) {
            log.debug("{}:readData: offset {}, attr: {}, stream: {}, length {}, file record = {}", referenceNumber, fileOffset, attributeType, streamName, len, this);
        }

        if (len == 0) {
            return;
        }

        Iterator<NTFSAttribute> dataAttrs = findAttributesByTypeAndName(attributeType, streamName);

        if (!dataAttrs.hasNext()) {
            throw new IOException(attributeType + " attribute not found, file record = " + this);
        }

        NTFSAttribute attr = dataAttrs.next();
        if (attr.isResident()) {
            if (dataAttrs.hasNext()) {
                throw new IOException("Resident attribute should be by itself, file record = " + this);
            }

            NTFSResidentAttribute resData = (NTFSResidentAttribute) attr;
            int attrLength = resData.getAttributeLength();
            if (attrLength < len) {
                throw new IOException("File data(" + attrLength + "b) is not large enough to read:" + len + "b, file record = " + this);
            }
            resData.getData(resData.getAttributeOffset() + (int) fileOffset, dest, off, len);

            if (log.isDebugEnabled()) {
                log.debug("{}:readData: read from resident data", referenceNumber);
            }

            return;
        }

        // At this point we know that at least the first attribute is non-resident...

        // Limit to the initialised size for compressed attributes
        limitToInitialised = limitToInitialised || attr.isCompressedAttribute();

        NTFSNonResidentAttribute nresData = (NTFSNonResidentAttribute) attr;
        long initialisedSize = nresData.getAttributeInitializedSize();

        // Only the fragment that starts at VCN 0 records the sizes; the fragments continuing a split attribute
        // carry zero in all three of them. A zero there means 'not recorded' and there is nothing to limit to, but
        // a zero on the first fragment means the attribute holds no initialised data at all, so the whole of it
        // reads as zeros.
        if (initialisedSize == 0 && nresData.getStartVCN() != 0) {
            limitToInitialised = false;
        }

        // Calculate start and end offsets and clusters
        int clusterSize = getClusterSize();
        long startCluster = fileOffset / clusterSize;
        long endOffset = (fileOffset + len - 1);
        long endCluster = endOffset / clusterSize;
        int nrClusters = (int) (endCluster - startCluster + 1);
        int clustersToRead = nrClusters;

        if (limitToInitialised && endOffset >= initialisedSize) {
            long lastInitialisedCluster = FSUtils.roundUpToBoundary(clusterSize, initialisedSize);
            clustersToRead = Math.max((int) ((lastInitialisedCluster / clusterSize) - startCluster), 0);
        }

        byte[] tmp = new byte[nrClusters * clusterSize];

        int clustersRead = nresData.readVCN(startCluster, tmp, 0, clustersToRead);

        if (clustersRead > 0 && limitToInitialised) {
            // Any part of the buffer past the 'initialised' part of the attribute is uninitialised and must be read
            // as zeros. Annoyingly the initialised portion isn't even cluster aligned, so the tail of the last
            // initialised cluster holds whatever happened to be on disk and has to be cleared here.

            long uninitialisedStart = initialisedSize - startCluster * (long) clusterSize;
            int startIndex = (int) Math.min(Math.max(uninitialisedStart, 0), tmp.length);

            if (startIndex < tmp.length) {
                Arrays.fill(tmp, startIndex, tmp.length, (byte) 0);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("{}:readData: read {} from non-resident attributes", referenceNumber, clustersRead);
        }

        if (clustersRead != clustersToRead) {
            throw new IOException("Requested " + clustersToRead + " clusters but only read " + clustersRead +
                ", file offset = " + fileOffset + ", file record = " + this);
        }

        System.arraycopy(tmp, (int) (fileOffset % clusterSize), dest, off, len);
    }

    /**
     * Gets the compression units of a stream that fail strict LZNT1, by the VCN they start at. Their decoded bytes are
     * not reliable: such a unit is usually read from clusters that now hold other data.
     *
     * <p>Every unit is checked: one that this record hasn't read yet is read now, so the result doesn't depend on
     * what was read before. In the unit that holds the valid data length, a last chunk cut short is expected and
     * isn't reported.</p>
     *
     * @param attributeType the attribute type.
     * @param streamName    the stream name, or {@code null} for the default stream.
     * @return the problems for each failing unit, in VCN order; empty if there are none.
     * @throws IOException if a unit can't be read.
     */
    public SortedMap<Long, Set<CompressionUnitProblem>> getCorruptCompressionUnits(NTFSAttribute.Types attributeType,
                                                                                String streamName)
        throws IOException {
        Map<Long, CompressedDataRun> units = new TreeMap<>();
        Iterator<NTFSAttribute> attributes = findAttributesByTypeAndName(attributeType, streamName);
        long initialisedSize = -1;

        while (attributes.hasNext()) {
            NTFSAttribute attribute = attributes.next();
            if (attribute.isResident() || !attribute.isCompressedAttribute()) {
                continue;
            }

            NTFSNonResidentAttribute nonResident = (NTFSNonResidentAttribute) attribute;
            if (nonResident.getStartVCN() == 0) {
                initialisedSize = nonResident.getAttributeInitializedSize();
            }

            for (DataRunInterface run : nonResident.getDataRuns()) {
                if (run instanceof CompressedDataRun) {
                    units.put(run.getFirstVcn(), (CompressedDataRun) run);
                }
            }
        }

        int clusterSize = getClusterSize();
        SortedMap<Long, Set<CompressionUnitProblem>> corrupt = new TreeMap<>();
        for (CompressedDataRun unit : units.values()) {
            if (!unit.isChecked()) {
                int clusters = (int) unit.getLength();
                unit.readClusters(unit.getFirstVcn(), new byte[clusters * clusterSize], 0, clusters, clusterSize,
                    getVolume());
            }

            Set<CompressionUnitProblem> problems = unit.getProblems();
            if (problems.isEmpty()) {
                continue;
            }

            long unitStart = unit.getFirstVcn() * (long) clusterSize;
            long unitEnd = unitStart + unit.getLength() * clusterSize;
            if (initialisedSize > unitStart && initialisedSize < unitEnd
                && problems.contains(CompressionUnitProblem.CHUNK_PAST_STORED_DATA)) {
                Set<CompressionUnitProblem> rest = new LinkedHashSet<>(problems);
                rest.remove(CompressionUnitProblem.CHUNK_PAST_STORED_DATA);
                problems = Collections.unmodifiableSet(rest);
            }

            if (!problems.isEmpty()) {
                corrupt.put(unit.getFirstVcn(), problems);
            }
        }

        return corrupt;
    }

    @Override
    public String toString() {
        String fileName = null;

        try {
            // Only look at stored attributes to determine the file name to avoid a possible stack overflow
            for (NTFSAttribute attribute : getAllStoredAttributes()) {
                if (attribute.getAttributeType() == NTFSAttribute.Types.FILE_NAME) {
                    FileNameAttribute fileNameAttribute = (FileNameAttribute) attribute;
                    if (fileName == null || fileNameAttribute.getNameSpace() == FileNameAttribute.NameSpace.WIN32) {
                        fileName = fileNameAttribute.getFileName();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error getting file name for file record: " + referenceNumber, e);
        }

        if (isInUse()) {
            return String.format("FileRecord [%d name='%s']", referenceNumber, fileName);
        } else {
            return String.format("FileRecord [%d unused name='%s']", referenceNumber, fileName);
        }
    }

    /**
     * Reads in all attributes referenced by the attribute-list attribute, merging the fragments of each split
     * attribute into its first. The list names this record's attributes too, so the result also holds those, and
     * any attributes stored here that the list does not name, such as the attribute list itself, come first.
     *
     * @param recordSupplier the FILE record supplier.
     * @return the list of attributes.
     */
    private List<NTFSAttribute> readAttributeListAttributes(FileRecordSupplier recordSupplier) {
        Iterator<AttributeListEntry> entryIterator;
        try {
            if (getAttributeListAttribute() == null) {
                return Collections.emptyList();
            }
            entryIterator = attributeListAttribute.getAllEntries();
        } catch (Exception e) {
            throw new IllegalStateException("Error getting attributes from attribute list, file record: " +
                referenceNumber, e);
        }

        AttributeListBuilder attributeListBuilder = new AttributeListBuilder();
        Set<NTFSAttribute> listedHere = new LinkedHashSet<>();

        while (entryIterator.hasNext()) {
            AttributeListEntry entry = entryIterator.next();

            try {
                // If it's resident (i.e. in the current file record) then we don't need to
                // look it up, and doing so would risk infinite recursion.
                NTFSAttribute attribute;
                if (entry.getFileReferenceNumber() == referenceNumber) {
                    attribute = findStoredAttributeByID(entry.getAttributeID());
                    if (attribute != null) {
                        listedHere.add(attribute);
                    }
                    attributeListBuilder.add(attribute);
                } else {
                    if (entry.getFileReferenceNumber() == 0) {
                        log.debug("{}:Skipping lookup for entry: {}", referenceNumber, entry);
                        continue;
                    }

                    if (log.isDebugEnabled()) {
                        log.debug("{}:Looking up MFT entry for: {}", referenceNumber, entry.getFileReferenceNumber());
                    }

                    FileRecord holdingRecord = recordSupplier.getRecord(entry.getFileReferenceNumber());
                    if (holdingRecord == null) {
                        log.error("Failed to look up holding record {} for entry '{}'", referenceNumber, entry);
                    } else {
                        attribute = holdingRecord.findStoredAttributeByID(entry.getAttributeID());

                        if (attribute == null) {
                            if (isInUse()) {
                                log.error("Failed to find an attribute matching entry '{}' in the holding record, ref={}", entry, referenceNumber);
                            }
                        } else {
                            attributeListBuilder.add(attribute);
                        }
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("Error getting MFT or FileRecord for attribute in list, ref = 0x" +
                    Long.toHexString(entry.getFileReferenceNumber()) + " record=" + this, e);
            }
        }

        List<NTFSAttribute> attributes = new ArrayList<>();
        for (NTFSAttribute attribute : getAllStoredAttributes()) {
            if (attribute != null && !listedHere.contains(attribute)) {
                attributes.add(attribute);
            }
        }
        attributes.addAll(attributeListBuilder.toList());
        return attributes;
    }

    /**
     * Works out how far into the record the attribute walk is allowed to go: the allocated size, bounded by what is
     * in the buffer.
     *
     * <p>The used size doesn't bound the walk. On deleted and some corrupt records it is stale and cuts through an
     * attribute, while the chain past it is intact and ends on the marker within the allocated size. When the
     * allocated size is implausible the buffer is the only bound, so a buffer holding several records must not be
     * passed with a corrupt record in it.</p>
     *
     * @return the exclusive upper bound, relative to the start of the record.
     */
    private int getAttributeWalkLimit() {
        int limit = getBuffer().length - getOffset();

        long allocatedSize = getAllocatedSize();
        if (allocatedSize >= getFirstAttributeOffset() + 8 && allocatedSize < limit) {
            limit = (int) allocatedSize;
        }

        return limit;
    }

    /**
     * Reads in the stored attributes.
     *
     * @return the stored attributes.
     */
    public List<NTFSAttribute> readStoredAttributes() {
        AttributeListBuilder attributeListBuilder = new AttributeListBuilder();
        int offset = getFirstAttributeOffset();

        // Attributes cannot extend past the record's allocated size. Without this bound a corrupt size walks off the
        // end of the buffer.
        final int limit = getAttributeWalkLimit();

        while (true) {
            if (offset < 0 || offset + 4 > limit) {
                log.debug("{}:Attribute offset {} is outside the record (limit {}), stopping the walk",
                    referenceNumber, offset, limit);
                break;
            }

            int type = getUInt32AsInt(offset);

            if (type == 0xFFFFFFFF) {
                // Normal end of list condition.
                break;
            } else if (offset + NTFSAttribute.COMMON_HEADER_LENGTH > limit ||
                       offset + NTFSAttribute.getHeaderLength(this, offset) > limit) {
                // The end of list marker is only four bytes, so a whole header is only needed once a real attribute
                // turns out to follow. Building one reads all of it, so check for all of it first.
                log.debug("{}:Attribute header at offset {} runs past the end of the record (limit {}), stopping " +
                          "the walk", referenceNumber, offset, limit);
                break;
            } else {
                NTFSAttribute attribute;
                try {
                    attribute = NTFSAttribute.getAttribute(FileRecord.this, offset);
                } catch (RuntimeException e) {
                    log.debug("{}:Attribute at offset {} could not be read, keeping the {} before it",
                        referenceNumber, offset, attributeListBuilder.toList().size(), e);
                    storedAttributeFailure = e;
                    break;
                }

                if (attribute != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("{}:Attribute: {}", referenceNumber, attribute.toDebugString());
                    }

                    int offsetToNextOffset = attribute.getSize();
                    if (offsetToNextOffset <= 0) {
                        log.debug("Non-positive offset, preventing infinite loop.  Data on disk may be corrupt.  "
                                  + "referenceNumber = {}", referenceNumber);
                        break;
                    } else if (offset + offsetToNextOffset > limit) {
                        log.debug("{}:Attribute at offset {} has size {} which runs past the end of the record "
                                  + "(limit {}).  Data on disk may be corrupt.", referenceNumber, offset,
                            offsetToNextOffset, limit);
                        break;
                    } else {
                        try {
                            attributeListBuilder.add(attribute);
                        } catch (RuntimeException e) {
                            log.debug("{}:Attribute at offset {} could not be read, keeping the {} before it",
                                referenceNumber, offset, attributeListBuilder.toList().size(), e);
                            storedAttributeFailure = e;
                            break;
                        }

                        if (offset < getRealSize() && offset + offsetToNextOffset > getRealSize()) {
                            log.debug("{}:Attribute at offset {} runs past the used size {}", referenceNumber, offset,
                                getRealSize());
                        }
                        offset += offsetToNextOffset;
                    }
                } else {
                    if (isInUse()) {
                        log.info("{}:attribute at offset {} invalid", referenceNumber, offset);
                    }
                    break;
                }
            }
        }

        return attributeListBuilder.toList();
    }

    /**
     * An iterator for filtering another iterator.
     */
    private abstract static class FilteredAttributeIterator implements Iterator<NTFSAttribute> {
        private final Iterator<NTFSAttribute> attributes;
        private NTFSAttribute cached;
        private boolean hasCached;

        private FilteredAttributeIterator(Iterator<NTFSAttribute> attributes) {
            this.attributes = attributes;
        }

        @Override
        public boolean hasNext() {
            if (hasCached) {
                return true;
            } else {
                nextMatch();
                return hasCached;
            }
        }

        @Override
        public NTFSAttribute next() {
            if (hasNext()) {
                hasCached = false;
                return cached;
            }

            throw new NoSuchElementException();
        }

        /**
         * Gets the next matching attribute.
         *
         * @return the next match.
         */
        private NTFSAttribute nextMatch() {
            while (attributes.hasNext()) {
                NTFSAttribute attribute = attributes.next();

                if (matches(attribute)) {
                    hasCached = true;
                    cached = attribute;
                    return attribute;
                }
            }

            hasCached = false;
            return null;
        }

        /**
         * Implemented by subclasses to perform matching logic.
         *
         * @param attr the attribute.
         * @return {@code true} if it matches, {@code false} otherwise.
         */
        protected abstract boolean matches(NTFSAttribute attr);

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
