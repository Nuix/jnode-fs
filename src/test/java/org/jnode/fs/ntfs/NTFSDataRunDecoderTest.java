package org.jnode.fs.ntfs;

import java.io.IOException;
import java.util.List;

import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.datarun.DataRunDecoder;
import org.jnode.fs.ntfs.datarun.DataRunInterface;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.jnode.fs.FileSystemTestUtils.*;

/**
 * Tests for {@link org.jnode.fs.ntfs.datarun.DataRunDecoder}.
 */
public class NTFSDataRunDecoderTest {

    @Test
    public void testSingleRunDecoding() {
        // Arrange
        byte[] buffer = toByteArray("21 40 AA 06 00");
        DataRunDecoder dataRunDecoder = new DataRunDecoder(false, 1);

        // Act
        dataRunDecoder.readDataRuns(new NTFSStructure(buffer, 0), 0);
        List<DataRunInterface> dataRuns = dataRunDecoder.getDataRuns();

        // Assert
        assertThat(dataRunDecoder.getNumberOfVCNs(), is(64));

        String expectedRuns =
            "[data-run vcn:0-63 cluster:1706]\n";
        assertDataRuns(dataRuns, expectedRuns);
    }

    @Test
    public void testMultiRunDecoding() {
        // Arrange
        byte[] buffer = toByteArray("21 04 B0 00 21 04 93 20 21 04 37 EF 21 08 77 2A 21 04 EC 08 21 04 19 04 21 04 62 01 00");
        DataRunDecoder dataRunDecoder = new DataRunDecoder(false, 1);

        // Act
        dataRunDecoder.readDataRuns(new NTFSStructure(buffer, 0), 0);
        List<DataRunInterface> dataRuns = dataRunDecoder.getDataRuns();

        // Assert
        assertThat(dataRunDecoder.getNumberOfVCNs(), is(32));

        String expectedRuns =
            "[data-run vcn:0-3 cluster:176]\n" +
            "[data-run vcn:4-7 cluster:8515]\n" +
            "[data-run vcn:8-11 cluster:4218]\n" +
            "[data-run vcn:12-19 cluster:15089]\n" +
            "[data-run vcn:20-23 cluster:17373]\n" +
            "[data-run vcn:24-27 cluster:18422]\n" +
            "[data-run vcn:28-31 cluster:18776]\n";
        assertDataRuns(dataRuns, expectedRuns);
    }

    @Test
    public void testCompressedRuns() {
        // Arrange
        byte[] buffer = toByteArray("21 07 B4 08 01 09 11 07 10 01 09 11 07 10 01 09 11 04 10 01 0C 00");
        DataRunDecoder dataRunDecoder = new DataRunDecoder(true, 16);

        // Act
        dataRunDecoder.readDataRuns(new NTFSStructure(buffer, 0), 0);
        List<DataRunInterface> dataRuns = dataRunDecoder.getDataRuns();

        // Assert
        assertThat(dataRunDecoder.getNumberOfVCNs(), is(64));

        String expectedRuns =
            "[compressed-run vcn:0-15 [[data-run vcn:0-6 cluster:2228]]]\n" +
            "[compressed-run vcn:16-31 [[data-run vcn:16-22 cluster:2244]]]\n" +
            "[compressed-run vcn:32-47 [[data-run vcn:32-38 cluster:2260]]]\n" +
            "[compressed-run vcn:48-63 [[data-run vcn:48-51 cluster:2276]]]\n";
        assertDataRuns(dataRuns, expectedRuns);
    }

    @Test
    public void testLargeSetOfRuns() {
        // Arrange
        byte[] buffer = toByteArray("31 06 29 C3 02 01 0A 11 06 06 01 0A 11 06 06 01 0A 11 06 06 01 0A 11 " +
            "06 06 01 0A 11 06 06 01 0A 11 06 06 01 0A 11 06 06 01 0A 11 06 06 01 0A 11 06 06 01 0A 11 06 06 01 0A " +
            "11 06 06 01 0A 11 06 06 01 0A 11 06 06 01 0A 31 06 69 E6 6A 01 0A 21 06 54 14 01 0A 21 06 6D 0B 01 0A " +
            "31 06 87 71 0B 01 0A 31 06 1C AB E2 01 0A 31 06 88 0D F5 01 0A 31 02 EC 2B DE 01 0E 32 C1 00 71 94 3E " +
            "01 0F 00");
        DataRunDecoder dataRunDecoder = new DataRunDecoder(true, 16);

        // Act
        dataRunDecoder.readDataRuns(new NTFSStructure(buffer, 0), 0);
        List<DataRunInterface> dataRuns = dataRunDecoder.getDataRuns();

        // Assert
        assertThat(dataRunDecoder.getNumberOfVCNs(), is(544));

        String expectedRuns =
            "[compressed-run vcn:0-15 [[data-run vcn:0-5 cluster:181033]]]\n" +
            "[compressed-run vcn:16-31 [[data-run vcn:16-21 cluster:181039]]]\n" +
            "[compressed-run vcn:32-47 [[data-run vcn:32-37 cluster:181045]]]\n" +
            "[compressed-run vcn:48-63 [[data-run vcn:48-53 cluster:181051]]]\n" +
            "[compressed-run vcn:64-79 [[data-run vcn:64-69 cluster:181057]]]\n" +
            "[compressed-run vcn:80-95 [[data-run vcn:80-85 cluster:181063]]]\n" +
            "[compressed-run vcn:96-111 [[data-run vcn:96-101 cluster:181069]]]\n" +
            "[compressed-run vcn:112-127 [[data-run vcn:112-117 cluster:181075]]]\n" +
            "[compressed-run vcn:128-143 [[data-run vcn:128-133 cluster:181081]]]\n" +
            "[compressed-run vcn:144-159 [[data-run vcn:144-149 cluster:181087]]]\n" +
            "[compressed-run vcn:160-175 [[data-run vcn:160-165 cluster:181093]]]\n" +
            "[compressed-run vcn:176-191 [[data-run vcn:176-181 cluster:181099]]]\n" +
            "[compressed-run vcn:192-207 [[data-run vcn:192-197 cluster:181105]]]\n" +
            "[compressed-run vcn:208-223 [[data-run vcn:208-213 cluster:181111]]]\n" +
            "[compressed-run vcn:224-239 [[data-run vcn:224-229 cluster:7186912]]]\n" +
            "[compressed-run vcn:240-255 [[data-run vcn:240-245 cluster:7192116]]]\n" +
            "[compressed-run vcn:256-271 [[data-run vcn:256-261 cluster:7195041]]]\n" +
            "[compressed-run vcn:272-287 [[data-run vcn:272-277 cluster:7945000]]]\n" +
            "[compressed-run vcn:288-303 [[data-run vcn:288-293 cluster:6022724]]]\n" +
            "[compressed-run vcn:304-319 [[data-run vcn:304-309 cluster:5305292]]]\n" +
            "[compressed-run vcn:320-335 [[data-run vcn:320-321 cluster:3088312]]]\n" +
            "[data-run vcn:336-527 cluster:7189545]\n" +
            "[compressed-run vcn:528-543 [[data-run vcn:528-528 cluster:7189737]]]\n";
        assertDataRuns(dataRuns, expectedRuns);
    }

    @Test
    public void testCompressedRuns_WithoutSparseRuns() {
        // Arrange
        byte[] buffer = toByteArray(
            "31 03 EE 0A 01 11 01 F5\n" +
                "01 0C 21 04 8A CB 01 0C 21 04 D5 0F 01 0C 21 04\n" +
                "F7 AB 01 0C 21 04 80 17 01 0C 21 04 A2 13 01 0C\n" +
                "21 04 6D 01 01 0C 21 04 AA 14 01 0C 21 04 E4 02\n" +
                "01 0C 21 04 05 03 01 0C 21 03 7B 0A 21 01 75 F7\n" +
                "01 0C 21 03 66 03 21 01 D4 F9 01 0C 31 04 B0 40\n" +
                "FF 01 00 00 00 00");
        DataRunDecoder dataRunDecoder = new DataRunDecoder(true, 16);

        // Act
        dataRunDecoder.readDataRuns(new NTFSStructure(buffer, 0), 0);
        List<DataRunInterface> dataRuns = dataRunDecoder.getDataRuns();

        // Assert
        String expectedRuns =
            "[compressed-run vcn:0-15 [[data-run vcn:0-2 cluster:68334], [data-run vcn:3-3 cluster:68323]]]\n" +
            "[compressed-run vcn:16-31 [[data-run vcn:16-19 cluster:54893]]]\n" +
            "[compressed-run vcn:32-47 [[data-run vcn:32-35 cluster:58946]]]\n" +
            "[compressed-run vcn:48-63 [[data-run vcn:48-51 cluster:37433]]]\n" +
            "[compressed-run vcn:64-79 [[data-run vcn:64-67 cluster:43449]]]\n" +
            "[compressed-run vcn:80-95 [[data-run vcn:80-83 cluster:48475]]]\n" +
            "[compressed-run vcn:96-111 [[data-run vcn:96-99 cluster:48840]]]\n" +
            "[compressed-run vcn:112-127 [[data-run vcn:112-115 cluster:54130]]]\n" +
            "[compressed-run vcn:128-143 [[data-run vcn:128-131 cluster:54870]]]\n" +
            "[compressed-run vcn:144-159 [[data-run vcn:144-147 cluster:55643]]]\n" +
            "[compressed-run vcn:160-175 [[data-run vcn:160-162 cluster:58326], [data-run vcn:163-163 cluster:56139]]]\n" +
            "[compressed-run vcn:176-191 [[data-run vcn:176-178 cluster:57009], [data-run vcn:179-179 cluster:55429]]]\n" +
            "[compressed-run vcn:192-207 [[data-run vcn:192-195 cluster:6453]]]\n";
        assertDataRuns(dataRuns, expectedRuns);
    }

    @Test
    public void testCombiningSubsequentAttributesRuns() {
        // Unfortunately this is a massive test case; the trivial cases don't exhibit this merging requirement

        // Arrange
        byte[] buffer1 = toByteArray(
            "31 04 65 19 01 01 0C 21 04 E9 09 01 0C 21 04 21 16 01 0C 21 04 11 20 01 0C 21 04 DC 0D 01 0C 21 " +
            "04 5A 0C 01 0C 21 04 08 17 01 0C 31 04 ED 8B FE 01 0C 21 04 B3 1B 01 0C 21 03 44 15 21 01 19 08 " +
            "01 0C 31 04 D7 87 00 01 0C 21 04 20 1A 01 0C 21 04 21 58 01 0C 21 04 93 2E 01 0C 21 04 75 BD 01 " +
            "0C 21 01 9B 06 11 01 AB 21 01 BC FD 21 01 A8 D4 01 0C 21 04 8D 95 01 0C 21 04 1E 0B 01 0C 31 04 " +
            "9F C1 00 01 0C 21 04 64 D2 01 0C 21 03 65 C3 21 01 03 FE 01 0C 21 04 83 B8 01 0C 21 04 1F F6 01 " +
            "0C 31 04 CF 6F FF 01 0C 31 04 E8 BB 00 01 0C 31 04 DE 65 FF 01 0C 31 04 A4 F5 00 01 0C 31 04 32 " +
            "5E FF 01 0C 31 04 73 A0 00 01 0C 31 04 48 26 FF 01 0C 21 04 C6 FB 01 0C 21 04 E5 3B 01 0C 31 04 " +
            "0E 90 00 01 0C 31 04 BF F6 FE 01 0C 31 04 DA CE 00 01 0C 21 04 E2 86 01 0C 31 04 BB 83 00 01 0C " +
            "31 04 2A 49 FF 01 0C 31 03 7F D5 00 11 01 1E 01 0C 31 04 B5 13 FF 01 0C 31 03 D8 FA 00 21 01 15 " +
            "FF 01 0C 31 04 0A 3A FF 01 0C 21 04 A7 AD 01 0C 31 04 D0 5D 01 01 0C 21 03 47 DD 21 01 B8 FD 01 " +
            "0C 31 04 9E 74 FF 01 0C 21 04 28 9D 01 0C 21 04 3B 4F 01 0C 31 04 08 B2 00 01 0C 31 04 8D 63 FF " +
            "01 0C 21 04 80 0D 01 0C 21 04 49 7C 01 0C 21 04 E8 E4 01 0C 21 03 FC 39 21 01 3B 01 01 0C 31 04 " +
            "6F AB FE 01 0C 31 04 70 CA 00 01 0C 21 04 5C 7C 01 0C 21 04 82 2E 01 0C 31 04 32 C5 FE 01 0C 31 " +
            "02 11 CC 00 11 02 F6 01 0C 31 03 7E 38 FF 11 01 16 01 0C 21 04 96 0E 00");

        byte[] buffer2 = toByteArray(
            "01 0C 31 04 B0 0D 01 01 0C 21 04 BF 72 01 0C 21 02 61 EE 21 02 7C FF 01 0C 31 04 0F ED FE 01 0C " +
            "21 04 58 43 01 0C 21 04 48 66 01 0C 31 04 E2 84 00 01 0C 21 04 F3 DE 01 0C 21 03 78 92 21 01 F5 " +
            "FC 01 0C 21 04 6F B4 01 0C 21 04 85 7E 01 0C 21 04 3E 24 01 0C 31 04 F5 11 FF 01 0C 31 04 3F 02 " +
            "01 01 0C 31 04 76 D3 FE 01 0C 31 04 C5 E6 00 01 0C 21 04 4C 51 01 0C 31 04 BB A9 FE 01 0C 31 02 " +
            "74 EE 00 11 02 F6 01 0C 21 02 81 B6 11 02 B8 01 0C 31 04 E5 5F FF 01 0C 31 04 FA D3 00 01 0C 21 " +
            "04 9E 34 01 0C 21 04 CC 62 01 0C 31 04 52 0A FF 01 0C 11 04 B6 01 0C 31 04 81 97 00 01 0C 21 04 " +
            "9F 80 01 0C 21 04 80 50 01 0C 31 04 37 75 FF 01 0C 31 02 73 9E 00 21 02 51 FD 01 0C 31 04 0D 20 " +
            "FF 01 0C 21 04 B7 28 01 0C 21 04 C2 65 01 0C 21 04 19 1C 01 0C 21 04 4C 24 01 0C 21 04 3F 3C 01 " +
            "0C 21 04 32 1A 01 0C 21 04 EB 32 01 0C 31 04 DB A0 FE 01 0C 31 04 B0 52 01 01 0C 21 03 FF 14 11 " +
            "01 F9 01 0C 21 03 7F D9 21 01 97 FE 01 0C 31 03 6C EE FE 11 01 29 01 0C 11 04 01 01 0C 21 04 25 " +
            "6B 01 0C 21 04 25 F7 01 0C 21 04 71 48 01 0C 21 04 EC 74 01 0C 31 03 3A CD FE 11 01 14 01 0C 21 " +
            "04 02 DD 01 0C 21 04 A6 4E 01 0C 21 04 19 75 01 0C 21 04 A4 00 01 0C 21 04 D2 37 01 0C 21 04 DD " +
            "11 01 0C 21 04 87 3C 01 0C 21 04 39 17 01 0C 31 04 84 6A FF 01 0C 21 04 20 CF 01 0C 31 04 DE CC " +
            "00 01 0C 31 01 A9 BB FE 11 01 DC 21 01 62 FD 21 01 5E FD 01 0C 21 04 10 E6 01 0C 21 04 37 18 01 " +
            "0C 21 04 D2 22 01 0C 21 04 A6 12 01 0C 21 04 5E 2E 01 0C 21 04 CB 1D 01 0C 21 04 F4 45 01 0C 21 " +
            "04 97 00 01 0C 21 04 8D 19 01 0C 21 04 94 28 01 0C 21 04 38 17 01 0C 21 04 B3 1C 01 0C 31 04 CD " +
            "96 FE 01 0C 31 04 E3 99 00 01 0C 21 04 FF 81 01 0C 21 04 B9 62 01 0C 21 04 08 01 01 0C 21 04 91 " +
            "80 01 0C 21 04 9D 12 01 0C 21 04 0E 25 01 0C 21 04 DA 1E 01 0C 21 04 12 2A 01 0C 21 04 EE 0C 01 " +
            "0C 21 04 55 36 01 0C 21 04 9C 1F 01 0C 21 04 47 24 01 0C 21 04 FD 14 01 0C 21 04 05 A2 01 0C 31 " +
            "04 72 63 FF 01 0C 31 04 54 E5 00 01 0C 21 04 45 E5 01 0C 31 03 6C 88 00 11 01 51 01 0C 21 04 95 " +
            "E2 01 0C 21 04 F5 13 01 0C 21 03 CF AE 21 01 F8 FC 01 0C 21 03 40 F1 21 01 78 FA 01 0C 21 04 86 " +
            "9C 01 0C 21 04 41 02 01 0C 21 04 95 2F 01 0C 21 03 8A 0E 11 01 1B 01 0C 21 03 10 BD 21 01 07 FA " +
            "01 0C 31 04 6F 79 FF 01 0C 21 04 30 E4 01 0C 21 04 9E 30 01 0C 21 04 E7 00 01 0C 21 04 49 2B 01 " +
            "0C 21 04 6E 01 01 0C 21 04 2B 12 01 0C 21 04 C2 21 01 0C 21 04 6F 19 01 0C 21 04 4A 09 01 0C 21 " +
            "04 4D 18 01 0C 11 04 29 01 0C 21 04 C5 25 01 0C 21 04 6D 2C 01 0C 11 04 22 01 0C 21 04 C5 00 01 " +
            "0C 21 04 62 07 01 0C 21 04 FA 2C 01 0C 21 04 97 16 01 0C 21 04 C7 15 01 0C 31 04 3D D2 FE 01 0C " +
            "31 04 D9 30 01 01 0C 31 04 C7 7B FE 01 0C 21 04 58 73 01 0C 31 04 E7 90 00 01 0C 21 04 92 01 01 " +
            "0C 21 04 D8 14 01 0C 21 04 2B 01 01 0C 21 04 83 F1 01 0C 00 ");
        DataRunDecoder dataRunDecoder = new DataRunDecoder(true, 16);

        // Act
        dataRunDecoder.readDataRuns(new NTFSStructure(buffer1, 0), 0);
        dataRunDecoder.readDataRuns(new NTFSStructure(buffer2, 0), 0);
        List<DataRunInterface> dataRuns = dataRunDecoder.getDataRuns();

        // Assert
        String expectedRuns =
            "[compressed-run vcn:0-15 [[data-run vcn:0-3 cluster:72037]]]\n" +
            "[compressed-run vcn:16-31 [[data-run vcn:16-19 cluster:74574]]]\n" +
            "[compressed-run vcn:32-47 [[data-run vcn:32-35 cluster:80239]]]\n" +
            "[compressed-run vcn:48-63 [[data-run vcn:48-51 cluster:88448]]]\n" +
            "[compressed-run vcn:64-79 [[data-run vcn:64-67 cluster:91996]]]\n" +
            "[compressed-run vcn:80-95 [[data-run vcn:80-83 cluster:95158]]]\n" +
            "[compressed-run vcn:96-111 [[data-run vcn:96-99 cluster:101054]]]\n" +
            "[compressed-run vcn:112-127 [[data-run vcn:112-115 cluster:5803]]]\n" +
            "[compressed-run vcn:128-143 [[data-run vcn:128-131 cluster:12894]]]\n" +
            "[compressed-run vcn:144-159 [[data-run vcn:144-146 cluster:18338], [data-run vcn:147-147 cluster:20411]]]\n" +
            "[compressed-run vcn:160-175 [[data-run vcn:160-163 cluster:55186]]]\n" +
            "[compressed-run vcn:176-191 [[data-run vcn:176-179 cluster:61874]]]\n" +
            "[compressed-run vcn:192-207 [[data-run vcn:192-195 cluster:84435]]]\n" +
            "[compressed-run vcn:208-223 [[data-run vcn:208-211 cluster:96358]]]\n" +
            "[compressed-run vcn:224-239 [[data-run vcn:224-227 cluster:79323]]]\n" +
            "[compressed-run vcn:240-255 [[data-run vcn:240-240 cluster:81014], [data-run vcn:241-241 cluster:80929], [data-run vcn:242-242 cluster:80349], [data-run vcn:243-243 cluster:69253]]]\n" +
            "[compressed-run vcn:256-271 [[data-run vcn:256-259 cluster:42002]]]\n" +
            "[compressed-run vcn:272-287 [[data-run vcn:272-275 cluster:44848]]]\n" +
            "[compressed-run vcn:288-303 [[data-run vcn:288-291 cluster:94415]]]\n" +
            "[compressed-run vcn:304-319 [[data-run vcn:304-307 cluster:82739]]]\n" +
            "[compressed-run vcn:320-335 [[data-run vcn:320-322 cluster:67224], [data-run vcn:323-323 cluster:66715]]]\n" +
            "[compressed-run vcn:336-351 [[data-run vcn:336-339 cluster:48414]]]\n" +
            "[compressed-run vcn:352-367 [[data-run vcn:352-355 cluster:45885]]]\n" +
            "[compressed-run vcn:368-383 [[data-run vcn:368-371 cluster:8972]]]\n" +
            "[compressed-run vcn:384-399 [[data-run vcn:384-387 cluster:57076]]]\n" +
            "[compressed-run vcn:400-415 [[data-run vcn:400-403 cluster:17618]]]\n" +
            "[compressed-run vcn:416-431 [[data-run vcn:416-419 cluster:80502]]]\n" +
            "[compressed-run vcn:432-447 [[data-run vcn:432-435 cluster:39080]]]\n" +
            "[compressed-run vcn:448-463 [[data-run vcn:448-451 cluster:80155]]]\n" +
            "[compressed-run vcn:464-479 [[data-run vcn:464-467 cluster:24419]]]\n" +
            "[compressed-run vcn:480-495 [[data-run vcn:480-483 cluster:23337]]]\n" +
            "[compressed-run vcn:496-511 [[data-run vcn:496-499 cluster:38670]]]\n" +
            "[compressed-run vcn:512-527 [[data-run vcn:512-515 cluster:75548]]]\n" +
            "[compressed-run vcn:528-543 [[data-run vcn:528-531 cluster:7643]]]\n" +
            "[compressed-run vcn:544-559 [[data-run vcn:544-547 cluster:60597]]]\n" +
            "[compressed-run vcn:560-575 [[data-run vcn:560-563 cluster:29591]]]\n" +
            "[compressed-run vcn:576-591 [[data-run vcn:576-579 cluster:63314]]]\n" +
            "[compressed-run vcn:592-607 [[data-run vcn:592-595 cluster:16508]]]\n" +
            "[compressed-run vcn:608-623 [[data-run vcn:608-610 cluster:71163], [data-run vcn:611-611 cluster:71193]]]\n" +
            "[compressed-run vcn:624-639 [[data-run vcn:624-627 cluster:10702]]]\n" +
            "[compressed-run vcn:640-655 [[data-run vcn:640-642 cluster:74918], [data-run vcn:643-643 cluster:74683]]]\n" +
            "[compressed-run vcn:656-671 [[data-run vcn:656-659 cluster:24005]]]\n" +
            "[compressed-run vcn:672-687 [[data-run vcn:672-675 cluster:2924]]]\n" +
            "[compressed-run vcn:688-703 [[data-run vcn:688-691 cluster:92476]]]\n" +
            "[compressed-run vcn:704-719 [[data-run vcn:704-706 cluster:83587], [data-run vcn:707-707 cluster:83003]]]\n" +
            "[compressed-run vcn:720-735 [[data-run vcn:720-723 cluster:47321]]]\n" +
            "[compressed-run vcn:736-751 [[data-run vcn:736-739 cluster:22017]]]\n" +
            "[compressed-run vcn:752-767 [[data-run vcn:752-755 cluster:42300]]]\n" +
            "[compressed-run vcn:768-783 [[data-run vcn:768-771 cluster:87876]]]\n" +
            "[compressed-run vcn:784-799 [[data-run vcn:784-787 cluster:47825]]]\n" +
            "[compressed-run vcn:800-815 [[data-run vcn:800-803 cluster:51281]]]\n" +
            "[compressed-run vcn:816-831 [[data-run vcn:816-819 cluster:83098]]]\n" +
            "[compressed-run vcn:832-847 [[data-run vcn:832-835 cluster:76162]]]\n" +
            "[compressed-run vcn:848-863 [[data-run vcn:848-850 cluster:91006], [data-run vcn:851-851 cluster:91321]]]\n" +
            "[compressed-run vcn:864-879 [[data-run vcn:864-867 cluster:4136]]]\n" +
            "[compressed-run vcn:880-895 [[data-run vcn:880-883 cluster:55960]]]\n" +
            "[compressed-run vcn:896-911 [[data-run vcn:896-899 cluster:87796]]]\n" +
            "[compressed-run vcn:912-927 [[data-run vcn:912-915 cluster:99702]]]\n" +
            "[compressed-run vcn:928-943 [[data-run vcn:928-931 cluster:19112]]]\n" +
            "[compressed-run vcn:944-959 [[data-run vcn:944-945 cluster:71353], [data-run vcn:946-947 cluster:71343]]]\n" +
            "[compressed-run vcn:960-975 [[data-run vcn:960-962 cluster:20269], [data-run vcn:963-963 cluster:20291]]]\n" +
            "[compressed-run vcn:976-991 [[data-run vcn:976-979 cluster:24025]]]\n" +
            "[compressed-run vcn:992-1007 [[data-run vcn:992-995 cluster:69040]]]\n" +
            "[compressed-run vcn:1008-1023 [[data-run vcn:1008-1011 cluster:98415]]]\n" +
            "[compressed-run vcn:1024-1039 [[data-run vcn:1024-1025 cluster:93904], [data-run vcn:1026-1027 cluster:93772]]]\n" +
            "[compressed-run vcn:1040-1055 [[data-run vcn:1040-1043 cluster:23387]]]\n" +
            "[compressed-run vcn:1056-1071 [[data-run vcn:1056-1059 cluster:40627]]]\n" +
            "[compressed-run vcn:1072-1087 [[data-run vcn:1072-1075 cluster:66811]]]\n" +
            "[compressed-run vcn:1088-1103 [[data-run vcn:1088-1091 cluster:100829]]]\n" +
            "[compressed-run vcn:1104-1119 [[data-run vcn:1104-1107 cluster:92368]]]\n" +
            "[compressed-run vcn:1120-1135 [[data-run vcn:1120-1122 cluster:64328], [data-run vcn:1123-1123 cluster:63549]]]\n" +
            "[compressed-run vcn:1136-1151 [[data-run vcn:1136-1139 cluster:44204]]]\n" +
            "[compressed-run vcn:1152-1167 [[data-run vcn:1152-1155 cluster:76593]]]\n" +
            "[compressed-run vcn:1168-1183 [[data-run vcn:1168-1171 cluster:85871]]]\n" +
            "[compressed-run vcn:1184-1199 [[data-run vcn:1184-1187 cluster:24932]]]\n" +
            "[compressed-run vcn:1200-1215 [[data-run vcn:1200-1203 cluster:91043]]]\n" +
            "[compressed-run vcn:1216-1231 [[data-run vcn:1216-1219 cluster:14105]]]\n" +
            "[compressed-run vcn:1232-1247 [[data-run vcn:1232-1235 cluster:73182]]]\n" +
            "[compressed-run vcn:1248-1263 [[data-run vcn:1248-1251 cluster:93994]]]\n" +
            "[compressed-run vcn:1264-1279 [[data-run vcn:1264-1267 cluster:6373]]]\n" +
            "[compressed-run vcn:1280-1295 [[data-run vcn:1280-1281 cluster:67417], [data-run vcn:1282-1283 cluster:67407]]]\n" +
            "[compressed-run vcn:1296-1311 [[data-run vcn:1296-1297 cluster:48592], [data-run vcn:1298-1299 cluster:48520]]]\n" +
            "[compressed-run vcn:1312-1327 [[data-run vcn:1312-1315 cluster:7533]]]\n" +
            "[compressed-run vcn:1328-1343 [[data-run vcn:1328-1331 cluster:61799]]]\n" +
            "[compressed-run vcn:1344-1359 [[data-run vcn:1344-1347 cluster:75269]]]\n" +
            "[compressed-run vcn:1360-1375 [[data-run vcn:1360-1363 cluster:100561]]]\n" +
            "[compressed-run vcn:1376-1391 [[data-run vcn:1376-1379 cluster:37667]]]\n" +
            "[compressed-run vcn:1392-1407 [[data-run vcn:1392-1395 cluster:37593]]]\n" +
            "[compressed-run vcn:1408-1423 [[data-run vcn:1408-1411 cluster:76378]]]\n" +
            "[compressed-run vcn:1424-1439 [[data-run vcn:1424-1427 cluster:43769]]]\n" +
            "[compressed-run vcn:1440-1455 [[data-run vcn:1440-1443 cluster:64377]]]\n" +
            "[compressed-run vcn:1456-1471 [[data-run vcn:1456-1459 cluster:28848]]]\n" +
            "[compressed-run vcn:1472-1487 [[data-run vcn:1472-1473 cluster:69411], [data-run vcn:1474-1475 cluster:68724]]]\n" +
            "[compressed-run vcn:1488-1503 [[data-run vcn:1488-1491 cluster:11393]]]\n" +
            "[compressed-run vcn:1504-1519 [[data-run vcn:1504-1507 cluster:21816]]]\n" +
            "[compressed-run vcn:1520-1535 [[data-run vcn:1520-1523 cluster:47866]]]\n" +
            "[compressed-run vcn:1536-1551 [[data-run vcn:1536-1539 cluster:55059]]]\n" +
            "[compressed-run vcn:1552-1567 [[data-run vcn:1552-1555 cluster:64351]]]\n" +
            "[compressed-run vcn:1568-1583 [[data-run vcn:1568-1571 cluster:79774]]]\n" +
            "[compressed-run vcn:1584-1599 [[data-run vcn:1584-1587 cluster:86480]]]\n" +
            "[compressed-run vcn:1600-1615 [[data-run vcn:1600-1603 cluster:99515]]]\n" +
            "[compressed-run vcn:1616-1631 [[data-run vcn:1616-1619 cluster:9622]]]\n" +
            "[compressed-run vcn:1632-1647 [[data-run vcn:1632-1635 cluster:96326]]]\n" +
            "[compressed-run vcn:1648-1663 [[data-run vcn:1648-1650 cluster:101701], [data-run vcn:1651-1651 cluster:101694]]]\n" +
            "[compressed-run vcn:1664-1679 [[data-run vcn:1664-1666 cluster:91837], [data-run vcn:1667-1667 cluster:91476]]]\n" +
            "[compressed-run vcn:1680-1695 [[data-run vcn:1680-1682 cluster:21440], [data-run vcn:1683-1683 cluster:21481]]]\n" +
            "[compressed-run vcn:1696-1711 [[data-run vcn:1696-1699 cluster:21482]]]\n" +
            "[compressed-run vcn:1712-1727 [[data-run vcn:1712-1715 cluster:48911]]]\n" +
            "[compressed-run vcn:1728-1743 [[data-run vcn:1728-1731 cluster:46644]]]\n" +
            "[compressed-run vcn:1744-1759 [[data-run vcn:1744-1747 cluster:65189]]]\n" +
            "[compressed-run vcn:1760-1775 [[data-run vcn:1760-1763 cluster:95121]]]\n" +
            "[compressed-run vcn:1776-1791 [[data-run vcn:1776-1778 cluster:16587], [data-run vcn:1779-1779 cluster:16607]]]\n" +
            "[compressed-run vcn:1792-1807 [[data-run vcn:1792-1795 cluster:7649]]]\n" +
            "[compressed-run vcn:1808-1823 [[data-run vcn:1808-1811 cluster:27783]]]\n" +
            "[compressed-run vcn:1824-1839 [[data-run vcn:1824-1827 cluster:57760]]]\n" +
            "[compressed-run vcn:1840-1855 [[data-run vcn:1840-1843 cluster:57924]]]\n" +
            "[compressed-run vcn:1856-1871 [[data-run vcn:1856-1859 cluster:72214]]]\n" +
            "[compressed-run vcn:1872-1887 [[data-run vcn:1872-1875 cluster:76787]]]\n" +
            "[compressed-run vcn:1888-1903 [[data-run vcn:1888-1891 cluster:92282]]]\n" +
            "[compressed-run vcn:1904-1919 [[data-run vcn:1904-1907 cluster:98227]]]\n" +
            "[compressed-run vcn:1920-1935 [[data-run vcn:1920-1923 cluster:59959]]]\n" +
            "[compressed-run vcn:1936-1951 [[data-run vcn:1936-1939 cluster:47447]]]\n" +
            "[compressed-run vcn:1952-1967 [[data-run vcn:1952-1955 cluster:99893]]]\n" +
            "[compressed-run vcn:1968-1983 [[data-run vcn:1968-1968 cluster:16862], [data-run vcn:1969-1969 cluster:16826], [data-run vcn:1970-1970 cluster:16156], [data-run vcn:1971-1971 cluster:15482]]]\n" +
            "[compressed-run vcn:1984-1999 [[data-run vcn:1984-1987 cluster:8842]]]\n" +
            "[compressed-run vcn:2000-2015 [[data-run vcn:2000-2003 cluster:15041]]]\n" +
            "[compressed-run vcn:2016-2031 [[data-run vcn:2016-2019 cluster:23955]]]\n" +
            "[compressed-run vcn:2032-2047 [[data-run vcn:2032-2035 cluster:28729]]]\n" +
            "[compressed-run vcn:2048-2063 [[data-run vcn:2048-2051 cluster:40599]]]\n" +
            "[compressed-run vcn:2064-2079 [[data-run vcn:2064-2067 cluster:48226]]]\n" +
            "[compressed-run vcn:2080-2095 [[data-run vcn:2080-2083 cluster:66134]]]\n" +
            "[compressed-run vcn:2096-2111 [[data-run vcn:2096-2099 cluster:66285]]]\n" +
            "[compressed-run vcn:2112-2127 [[data-run vcn:2112-2115 cluster:72826]]]\n" +
            "[compressed-run vcn:2128-2143 [[data-run vcn:2128-2131 cluster:83214]]]\n" +
            "[compressed-run vcn:2144-2159 [[data-run vcn:2144-2147 cluster:89158]]]\n" +
            "[compressed-run vcn:2160-2175 [[data-run vcn:2160-2163 cluster:96505]]]\n" +
            "[compressed-run vcn:2176-2191 [[data-run vcn:2176-2179 cluster:4038]]]\n" +
            "[compressed-run vcn:2192-2207 [[data-run vcn:2192-2195 cluster:43433]]]\n" +
            "[compressed-run vcn:2208-2223 [[data-run vcn:2208-2211 cluster:11176]]]\n" +
            "[compressed-run vcn:2224-2239 [[data-run vcn:2224-2227 cluster:36449]]]\n" +
            "[compressed-run vcn:2240-2255 [[data-run vcn:2240-2243 cluster:36713]]]\n" +
            "[compressed-run vcn:2256-2271 [[data-run vcn:2256-2259 cluster:4090]]]\n" +
            "[compressed-run vcn:2272-2287 [[data-run vcn:2272-2275 cluster:8855]]]\n" +
            "[compressed-run vcn:2288-2303 [[data-run vcn:2288-2291 cluster:18341]]]\n" +
            "[compressed-run vcn:2304-2319 [[data-run vcn:2304-2307 cluster:26239]]]\n" +
            "[compressed-run vcn:2320-2335 [[data-run vcn:2320-2323 cluster:37009]]]\n" +
            "[compressed-run vcn:2336-2351 [[data-run vcn:2336-2339 cluster:40319]]]\n" +
            "[compressed-run vcn:2352-2367 [[data-run vcn:2352-2355 cluster:54228]]]\n" +
            "[compressed-run vcn:2368-2383 [[data-run vcn:2368-2371 cluster:62320]]]\n" +
            "[compressed-run vcn:2384-2399 [[data-run vcn:2384-2387 cluster:71607]]]\n" +
            "[compressed-run vcn:2400-2415 [[data-run vcn:2400-2403 cluster:76980]]]\n" +
            "[compressed-run vcn:2416-2431 [[data-run vcn:2416-2419 cluster:52921]]]\n" +
            "[compressed-run vcn:2432-2447 [[data-run vcn:2432-2435 cluster:12843]]]\n" +
            "[compressed-run vcn:2448-2463 [[data-run vcn:2448-2451 cluster:71551]]]\n" +
            "[compressed-run vcn:2464-2479 [[data-run vcn:2464-2467 cluster:64708]]]\n" +
            "[compressed-run vcn:2480-2495 [[data-run vcn:2480-2482 cluster:99632], [data-run vcn:2483-2483 cluster:99713]]]\n" +
            "[compressed-run vcn:2496-2511 [[data-run vcn:2496-2499 cluster:92182]]]\n" +
            "[compressed-run vcn:2512-2527 [[data-run vcn:2512-2515 cluster:97291]]]\n" +
            "[compressed-run vcn:2528-2543 [[data-run vcn:2528-2530 cluster:76506], [data-run vcn:2531-2531 cluster:75730]]]\n" +
            "[compressed-run vcn:2544-2559 [[data-run vcn:2544-2546 cluster:71954], [data-run vcn:2547-2547 cluster:70538]]]\n" +
            "[compressed-run vcn:2560-2575 [[data-run vcn:2560-2563 cluster:45072]]]\n" +
            "[compressed-run vcn:2576-2591 [[data-run vcn:2576-2579 cluster:45649]]]\n" +
            "[compressed-run vcn:2592-2607 [[data-run vcn:2592-2595 cluster:57830]]]\n" +
            "[compressed-run vcn:2608-2623 [[data-run vcn:2608-2610 cluster:61552], [data-run vcn:2611-2611 cluster:61579]]]\n" +
            "[compressed-run vcn:2624-2639 [[data-run vcn:2624-2626 cluster:44443], [data-run vcn:2627-2627 cluster:42914]]]\n" +
            "[compressed-run vcn:2640-2655 [[data-run vcn:2640-2643 cluster:8465]]]\n" +
            "[compressed-run vcn:2656-2671 [[data-run vcn:2656-2659 cluster:1345]]]\n" +
            "[compressed-run vcn:2672-2687 [[data-run vcn:2672-2675 cluster:13791]]]\n" +
            "[compressed-run vcn:2688-2703 [[data-run vcn:2688-2691 cluster:14022]]]\n" +
            "[compressed-run vcn:2704-2719 [[data-run vcn:2704-2707 cluster:25103]]]\n" +
            "[compressed-run vcn:2720-2735 [[data-run vcn:2720-2723 cluster:25469]]]\n" +
            "[compressed-run vcn:2736-2751 [[data-run vcn:2736-2739 cluster:30120]]]\n" +
            "[compressed-run vcn:2752-2767 [[data-run vcn:2752-2755 cluster:38762]]]\n" +
            "[compressed-run vcn:2768-2783 [[data-run vcn:2768-2771 cluster:45273]]]\n" +
            "[compressed-run vcn:2784-2799 [[data-run vcn:2784-2787 cluster:47651]]]\n" +
            "[compressed-run vcn:2800-2815 [[data-run vcn:2800-2803 cluster:53872]]]\n" +
            "[compressed-run vcn:2816-2831 [[data-run vcn:2816-2819 cluster:53913]]]\n" +
            "[compressed-run vcn:2832-2847 [[data-run vcn:2832-2835 cluster:63582]]]\n" +
            "[compressed-run vcn:2848-2863 [[data-run vcn:2848-2851 cluster:74955]]]\n" +
            "[compressed-run vcn:2864-2879 [[data-run vcn:2864-2867 cluster:74989]]]\n" +
            "[compressed-run vcn:2880-2895 [[data-run vcn:2880-2883 cluster:75186]]]\n" +
            "[compressed-run vcn:2896-2911 [[data-run vcn:2896-2899 cluster:77076]]]\n" +
            "[compressed-run vcn:2912-2927 [[data-run vcn:2912-2915 cluster:88590]]]\n" +
            "[compressed-run vcn:2928-2943 [[data-run vcn:2928-2931 cluster:94373]]]\n" +
            "[compressed-run vcn:2944-2959 [[data-run vcn:2944-2947 cluster:99948]]]\n" +
            "[compressed-run vcn:2960-2975 [[data-run vcn:2960-2963 cluster:22697]]]\n" +
            "[compressed-run vcn:2976-2991 [[data-run vcn:2976-2979 cluster:100738]]]\n" +
            "[compressed-run vcn:2992-3007 [[data-run vcn:2992-2995 cluster:1353]]]\n" +
            "[compressed-run vcn:3008-3023 [[data-run vcn:3008-3011 cluster:30881]]]\n" +
            "[compressed-run vcn:3024-3039 [[data-run vcn:3024-3027 cluster:67976]]]\n" +
            "[compressed-run vcn:3040-3055 [[data-run vcn:3040-3043 cluster:68378]]]\n" +
            "[compressed-run vcn:3056-3071 [[data-run vcn:3056-3059 cluster:73714]]]\n" +
            "[compressed-run vcn:3072-3087 [[data-run vcn:3072-3075 cluster:74013]]]\n" +
            "[compressed-run vcn:3088-3103 [[data-run vcn:3088-3091 cluster:70304]]]\n";
        assertDataRuns(dataRuns, expectedRuns);
    }

    @Test
    public void testDataRunWithLargeNegativeOffset() {
        // Arrange
        byte[] buffer = toByteArray(
            "33 C0 3B 01 00 00 0C 43 14 C8 00 2C 43 F5 1E 43 F1 15 01 63 63 EB 25 42 A7 77 FA 5E E8 " +
                "0E 42 94 4A 6E 7B BA 0D 43 70 CA 00 09 FF 50 19 43 A5 0F 01 FC FF D1 B3 42 16 65 AE 99 F8 2A 43 " +
                "6C C8 00 EA 1D 94 15 43 0C C8 00 BF CB 9F B3 43 1D D2 00 71 EE BA 0D 43 03 C8 00 D9 43 B2 00 43 " +
                "32 C9 00 6C F8 B1 5D 43 08 C8 00 AF E4 2A 8E 43 06 C8 00 E2 CB 2F 1F 43 25 C8 00 66 A1 F0 30 43 " +
                "0F C8 00 2B 04 B4 08 43 2D C9 00 D0 A6 87 E0 43 1A C8 00 0B 97 E0 29 52 C2 08 C9 D2 B8 7F FF 43 " +
                "00 88 00 68 5C CC 6B 00");
        DataRunDecoder dataRunDecoder = new DataRunDecoder(false, 1);

        // Act
        dataRunDecoder.readDataRuns(new NTFSStructure(buffer, 0), 0);
        List<DataRunInterface> dataRuns = dataRunDecoder.getDataRuns();

        // Assert
        String expectedRuns =
            "[data-run vcn:0-80831 cluster:786432]\n" +
            "[data-run vcn:80832-132051 cluster:520176428]\n" +
            "[data-run vcn:132052-203204 cluster:1156359823]\n" +
            "[data-run vcn:203205-233835 cluster:1406469513]\n" +
            "[data-run vcn:233836-252927 cluster:1636794615]\n" +
            "[data-run vcn:252928-304751 cluster:2061533184]\n" +
            "[data-run vcn:304752-374292 cluster:783450108]\n" +
            "[data-run vcn:374293-400170 cluster:1504385450]\n" +
            "[data-run vcn:400171-451478 cluster:1866413972]\n" +
            "[data-run vcn:451479-502690 cluster:585040723]\n" +
            "[data-run vcn:502691-556479 cluster:815395268]\n" +
            "[data-run vcn:556480-607682 cluster:827078045]\n" +
            "[data-run vcn:607683-659188 cluster:2399022601]\n" +
            "[data-run vcn:659189-710396 cluster:489231032]\n" +
            "[data-run vcn:710397-761602 cluster:1012457114]\n" +
            "[data-run vcn:761603-812839 cluster:1833533440]\n" +
            "[data-run vcn:812840-864054 cluster:1979548715]\n" +
            "[data-run vcn:864055-915555 cluster:1451567867]\n" +
            "[data-run vcn:915556-966781 cluster:2154152454]\n" +
            "[data-run vcn:966782-969023 cluster:2004175]\n" +
            "[data-run vcn:969024-1003839 cluster:1810559287]\n";
        assertDataRuns(dataRuns, expectedRuns);
    }

    @Test
    public void testCompressedExpectingSparseAfterMerge() {
        // Arrange
        byte[] buffer = toByteArray(
            "41 13 D5 68 A2 0B 21 09 68 FF 01 04 00");
        DataRunDecoder dataRunDecoder = new DataRunDecoder(true, 16);

        // Act
        dataRunDecoder.readDataRuns(new NTFSStructure(buffer, 0), 0);
        List<DataRunInterface> dataRuns = dataRunDecoder.getDataRuns();

        // Assert
        String expectedRuns =
            "[data-run vcn:0-15 cluster:195193045]\n" +
            "[compressed-run vcn:16-31 [[data-run vcn:16-18 cluster:195193061], [data-run vcn:19-27 cluster:195192893]]]\n";
        assertDataRuns(dataRuns, expectedRuns);
    }

    @Test
    public void testCombinationOfCompressedAndSparseRuns() throws IOException {
        // Arrange
        DataRunDecoder dataRunDecoder = new DataRunDecoder(true, 16);
        for (int i = 0; i <= 5; i++) {
            byte[] buffer = FileSystemTestUtils.readFileToByteArray("org/jnode/fs/ntfs/compressed-and-sparse/combination" + i + ".bin");
            // Act
            dataRunDecoder.readDataRuns(new NTFSStructure(buffer, 56), 64);
        }
        List<DataRunInterface> dataRuns = dataRunDecoder.getDataRuns();

        // At the end of the first bin, there is a 3-cluster compressed dataRun and a 10-cluster sparseRun.
        // At the beginning of the second bin, there is a 3-cluster sparse dataRun.

        // For these 3 runs:
        // A, 3-cluster compressed dataRun
        // B, 10-cluster sparseRun
        // C, 3-cluster sparse dataRun
        // the latest code now will combine A, B and C together (the total cluster number is 16), and
        // the previous code combines A and B together, and creates another run for C, which creates a wrong offset for all other data runs after.
        // The main fix is to always check the cluster number.
        // reference: https://flatcap.github.io/linux-ntfs/ntfs/concepts/data_runs.html

        // Assert
        assertThat(dataRunDecoder.getNumberOfVCNs(), is(10272));

        String expectedRuns =
                "[compressed-run vcn:0-15 [[data-run vcn:0-2 cluster:481825]]]\n" +
                        "[compressed-run vcn:16-31 [[data-run vcn:16-18 cluster:481835]]]\n" +
                        "[compressed-run vcn:32-47 [[data-run vcn:32-34 cluster:374]]]\n" +
                        "[compressed-run vcn:48-63 [[data-run vcn:48-50 cluster:377]]]\n" +
                        "[compressed-run vcn:64-79 [[data-run vcn:64-66 cluster:380]]]\n" +
                        "[compressed-run vcn:80-95 [[data-run vcn:80-82 cluster:383]]]\n" +
                        "[compressed-run vcn:96-111 [[data-run vcn:96-98 cluster:386]]]\n" +
                        "[compressed-run vcn:112-127 [[data-run vcn:112-114 cluster:389]]]\n" +
                        "[compressed-run vcn:128-143 [[data-run vcn:128-130 cluster:143]]]\n" +
                        "[compressed-run vcn:144-159 [[data-run vcn:144-146 cluster:16886]]]\n" +
                        "[compressed-run vcn:160-175 [[data-run vcn:160-162 cluster:16889]]]\n" +
                        "[compressed-run vcn:176-191 [[data-run vcn:176-178 cluster:16892]]]\n" +
                        "[compressed-run vcn:192-207 [[data-run vcn:192-194 cluster:16895]]]\n" +
                        "[compressed-run vcn:208-223 [[data-run vcn:208-210 cluster:16898]]]\n" +
                        "[compressed-run vcn:224-239 [[data-run vcn:224-226 cluster:77941]]]\n" +
                        "[compressed-run vcn:240-255 [[data-run vcn:240-242 cluster:71537]]]\n" +
                        "[compressed-run vcn:256-271 [[data-run vcn:256-258 cluster:71540]]]\n" +
                        "[compressed-run vcn:272-287 [[data-run vcn:272-274 cluster:71543]]]\n" +
                        "[compressed-run vcn:288-303 [[data-run vcn:288-290 cluster:71546]]]\n" +
                        "[compressed-run vcn:304-319 [[data-run vcn:304-306 cluster:71549]]]\n" +
                        "[compressed-run vcn:320-335 [[data-run vcn:320-322 cluster:78440]]]\n" +
                        "[compressed-run vcn:336-351 [[data-run vcn:336-338 cluster:78443]]]\n" +
                        "[compressed-run vcn:352-367 [[data-run vcn:352-354 cluster:71654]]]\n" +
                        "[compressed-run vcn:368-383 [[data-run vcn:368-370 cluster:71657]]]\n" +
                        "[compressed-run vcn:384-399 [[data-run vcn:384-386 cluster:71660]]]\n" +
                        "[compressed-run vcn:400-415 [[data-run vcn:400-402 cluster:71663]]]\n" +
                        "[compressed-run vcn:416-431 [[data-run vcn:416-418 cluster:71666]]]\n" +
                        "[compressed-run vcn:432-447 [[data-run vcn:432-434 cluster:71669]]]\n" +
                        "[compressed-run vcn:448-463 [[data-run vcn:448-450 cluster:71744]]]\n" +
                        "[compressed-run vcn:464-479 [[data-run vcn:464-466 cluster:71747]]]\n" +
                        "[compressed-run vcn:480-495 [[data-run vcn:480-482 cluster:71750]]]\n" +
                        "[compressed-run vcn:496-511 [[data-run vcn:496-498 cluster:71753]]]\n" +
                        "[compressed-run vcn:512-527 [[data-run vcn:512-514 cluster:71756]]]\n" +
                        "[compressed-run vcn:528-543 [[data-run vcn:528-530 cluster:78526]]]\n" +
                        "[compressed-run vcn:544-559 [[data-run vcn:544-546 cluster:71823]]]\n" +
                        "[compressed-run vcn:560-575 [[data-run vcn:560-562 cluster:71826]]]\n" +
                        "[compressed-run vcn:576-591 [[data-run vcn:576-578 cluster:71829]]]\n" +
                        "[compressed-run vcn:592-607 [[data-run vcn:592-594 cluster:71832]]]\n" +
                        "[compressed-run vcn:608-623 [[data-run vcn:608-610 cluster:71835]]]\n" +
                        "[compressed-run vcn:624-639 [[data-run vcn:624-626 cluster:78593]]]\n" +
                        "[compressed-run vcn:640-655 [[data-run vcn:640-642 cluster:71918]]]\n" +
                        "[compressed-run vcn:656-671 [[data-run vcn:656-658 cluster:71921]]]\n" +
                        "[compressed-run vcn:672-687 [[data-run vcn:672-674 cluster:71924]]]\n" +
                        "[compressed-run vcn:688-703 [[data-run vcn:688-690 cluster:71927]]]\n" +
                        "[compressed-run vcn:704-719 [[data-run vcn:704-706 cluster:71991]]]\n" +
                        "[compressed-run vcn:720-735 [[data-run vcn:720-722 cluster:72055]]]\n" +
                        "[compressed-run vcn:736-751 [[data-run vcn:736-738 cluster:72071]]]\n" +
                        "[compressed-run vcn:752-767 [[data-run vcn:752-754 cluster:72135]]]\n" +
                        "[compressed-run vcn:768-783 [[data-run vcn:768-770 cluster:72199]]]\n" +
                        "[compressed-run vcn:784-799 [[data-run vcn:784-786 cluster:72215]]]\n" +
                        "[compressed-run vcn:800-815 [[data-run vcn:800-802 cluster:72279]]]\n" +
                        "[compressed-run vcn:816-831 [[data-run vcn:816-818 cluster:72343]]]\n" +
                        "[compressed-run vcn:832-847 [[data-run vcn:832-834 cluster:72359]]]\n" +
                        "[compressed-run vcn:848-863 [[data-run vcn:848-850 cluster:72439]]]\n" +
                        "[compressed-run vcn:864-879 [[data-run vcn:864-866 cluster:72455]]]\n" +
                        "[compressed-run vcn:880-895 [[data-run vcn:880-882 cluster:72519]]]\n" +
                        "[compressed-run vcn:896-911 [[data-run vcn:896-898 cluster:72583]]]\n" +
                        "[compressed-run vcn:912-927 [[data-run vcn:912-914 cluster:72599]]]\n" +
                        "[compressed-run vcn:928-943 [[data-run vcn:928-930 cluster:72684]]]\n" +
                        "[compressed-run vcn:944-959 [[data-run vcn:944-946 cluster:72748]]]\n" +
                        "[compressed-run vcn:960-975 [[data-run vcn:960-962 cluster:72764]]]\n" +
                        "[compressed-run vcn:976-991 [[data-run vcn:976-978 cluster:72828]]]\n" +
                        "[compressed-run vcn:992-1007 [[data-run vcn:992-994 cluster:72892]]]\n" +
                        "[compressed-run vcn:1008-1023 [[data-run vcn:1008-1010 cluster:72908]]]\n" +
                        "[compressed-run vcn:1024-1039 [[data-run vcn:1024-1026 cluster:72988]]]\n" +
                        "[compressed-run vcn:1040-1055 [[data-run vcn:1040-1042 cluster:73004]]]\n" +
                        "[compressed-run vcn:1056-1071 [[data-run vcn:1056-1058 cluster:73068]]]\n" +
                        "[compressed-run vcn:1072-1087 [[data-run vcn:1072-1074 cluster:73132]]]\n" +
                        "[compressed-run vcn:1088-1103 [[data-run vcn:1088-1090 cluster:73148]]]\n" +
                        "[compressed-run vcn:1104-1119 [[data-run vcn:1104-1106 cluster:73212]]]\n" +
                        "[compressed-run vcn:1120-1135 [[data-run vcn:1120-1122 cluster:73276]]]\n" +
                        "[compressed-run vcn:1136-1151 [[data-run vcn:1136-1138 cluster:73292]]]\n" +
                        "[compressed-run vcn:1152-1167 [[data-run vcn:1152-1154 cluster:73356]]]\n" +
                        "[compressed-run vcn:1168-1183 [[data-run vcn:1168-1170 cluster:73420]]]\n" +
                        "[compressed-run vcn:1184-1199 [[data-run vcn:1184-1186 cluster:73436]]]\n" +
                        "[compressed-run vcn:1200-1215 [[data-run vcn:1200-1202 cluster:73516]]]\n" +
                        "[compressed-run vcn:1216-1231 [[data-run vcn:1216-1218 cluster:73532]]]\n" +
                        "[compressed-run vcn:1232-1247 [[data-run vcn:1232-1234 cluster:73596]]]\n" +
                        "[compressed-run vcn:1248-1263 [[data-run vcn:1248-1250 cluster:73660]]]\n" +
                        "[compressed-run vcn:1264-1279 [[data-run vcn:1264-1266 cluster:73676]]]\n" +
                        "[compressed-run vcn:1280-1295 [[data-run vcn:1280-1282 cluster:73740]]]\n" +
                        "[compressed-run vcn:1296-1311 [[data-run vcn:1296-1298 cluster:73804]]]\n" +
                        "[compressed-run vcn:1312-1327 [[data-run vcn:1312-1314 cluster:73820]]]\n" +
                        "[compressed-run vcn:1328-1343 [[data-run vcn:1328-1330 cluster:73884]]]\n" +
                        "[compressed-run vcn:1344-1359 [[data-run vcn:1344-1346 cluster:73948]]]\n" +
                        "[compressed-run vcn:1360-1375 [[data-run vcn:1360-1362 cluster:73964]]]\n" +
                        "[compressed-run vcn:1376-1391 [[data-run vcn:1376-1378 cluster:74044]]]\n" +
                        "[compressed-run vcn:1392-1407 [[data-run vcn:1392-1394 cluster:74060]]]\n" +
                        "[compressed-run vcn:1408-1423 [[data-run vcn:1408-1410 cluster:74124]]]\n" +
                        "[compressed-run vcn:1424-1439 [[data-run vcn:1424-1426 cluster:74188]]]\n" +
                        "[compressed-run vcn:1440-1455 [[data-run vcn:1440-1442 cluster:74204]]]\n" +
                        "[compressed-run vcn:1456-1471 [[data-run vcn:1456-1458 cluster:74268]]]\n" +
                        "[compressed-run vcn:1472-1487 [[data-run vcn:1472-1474 cluster:74332]]]\n" +
                        "[compressed-run vcn:1488-1503 [[data-run vcn:1488-1490 cluster:74348]]]\n" +
                        "[compressed-run vcn:1504-1519 [[data-run vcn:1504-1506 cluster:74412]]]\n" +
                        "[compressed-run vcn:1520-1535 [[data-run vcn:1520-1522 cluster:74476]]]\n" +
                        "[compressed-run vcn:1536-1551 [[data-run vcn:1536-1538 cluster:74492]]]\n" +
                        "[compressed-run vcn:1552-1567 [[data-run vcn:1552-1554 cluster:74572]]]\n" +
                        "[compressed-run vcn:1568-1583 [[data-run vcn:1568-1570 cluster:74588]]]\n" +
                        "[compressed-run vcn:1584-1599 [[data-run vcn:1584-1586 cluster:74652]]]\n" +
                        "[compressed-run vcn:1600-1615 [[data-run vcn:1600-1602 cluster:74716]]]\n" +
                        "[compressed-run vcn:1616-1631 [[data-run vcn:1616-1618 cluster:74732]]]\n" +
                        "[compressed-run vcn:1632-1647 [[data-run vcn:1632-1634 cluster:74796]]]\n" +
                        "[compressed-run vcn:1648-1663 [[data-run vcn:1648-1650 cluster:74860]]]\n" +
                        "[compressed-run vcn:1664-1679 [[data-run vcn:1664-1666 cluster:74876]]]\n" +
                        "[compressed-run vcn:1680-1695 [[data-run vcn:1680-1682 cluster:74940]]]\n" +
                        "[compressed-run vcn:1696-1711 [[data-run vcn:1696-1698 cluster:75004]]]\n" +
                        "[compressed-run vcn:1712-1727 [[data-run vcn:1712-1714 cluster:75020]]]\n" +
                        "[compressed-run vcn:1728-1743 [[data-run vcn:1728-1730 cluster:75100]]]\n" +
                        "[compressed-run vcn:1744-1759 [[data-run vcn:1744-1746 cluster:75116]]]\n" +
                        "[compressed-run vcn:1760-1775 [[data-run vcn:1760-1762 cluster:75180]]]\n" +
                        "[compressed-run vcn:1776-1791 [[data-run vcn:1776-1778 cluster:75244]]]\n" +
                        "[compressed-run vcn:1792-1807 [[data-run vcn:1792-1794 cluster:75260]]]\n" +
                        "[compressed-run vcn:1808-1823 [[data-run vcn:1808-1810 cluster:75324]]]\n" +
                        "[compressed-run vcn:1824-1839 [[data-run vcn:1824-1826 cluster:75388]]]\n" +
                        "[compressed-run vcn:1840-1855 [[data-run vcn:1840-1842 cluster:75404]]]\n" +
                        "[compressed-run vcn:1856-1871 [[data-run vcn:1856-1858 cluster:75468]]]\n" +
                        "[compressed-run vcn:1872-1887 [[data-run vcn:1872-1874 cluster:75532]]]\n" +
                        "[compressed-run vcn:1888-1903 [[data-run vcn:1888-1890 cluster:75548]]]\n" +
                        "[compressed-run vcn:1904-1919 [[data-run vcn:1904-1906 cluster:75628]]]\n" +
                        "[compressed-run vcn:1920-1935 [[data-run vcn:1920-1922 cluster:75644]]]\n" +
                        "[compressed-run vcn:1936-1951 [[data-run vcn:1936-1938 cluster:75708]]]\n" +
                        "[compressed-run vcn:1952-1967 [[data-run vcn:1952-1954 cluster:75772]]]\n" +
                        "[compressed-run vcn:1968-1983 [[data-run vcn:1968-1970 cluster:75788]]]\n" +
                        "[compressed-run vcn:1984-1999 [[data-run vcn:1984-1986 cluster:75852]]]\n" +
                        "[compressed-run vcn:2000-2015 [[data-run vcn:2000-2002 cluster:75916]]]\n" +
                        "[compressed-run vcn:2016-2031 [[data-run vcn:2016-2018 cluster:75932]]]\n" +
                        "[compressed-run vcn:2032-2047 [[data-run vcn:2032-2034 cluster:75996]]]\n" +
                        "[compressed-run vcn:2048-2063 [[data-run vcn:2048-2050 cluster:76060]]]\n" +
                        "[compressed-run vcn:2064-2079 [[data-run vcn:2064-2066 cluster:76076]]]\n" +
                        "[compressed-run vcn:2080-2095 [[data-run vcn:2080-2082 cluster:76156]]]\n" +
                        "[compressed-run vcn:2096-2111 [[data-run vcn:2096-2098 cluster:76172]]]\n" +
                        "[compressed-run vcn:2112-2127 [[data-run vcn:2112-2114 cluster:76236]]]\n" +
                        "[compressed-run vcn:2128-2143 [[data-run vcn:2128-2130 cluster:76300]]]\n" +
                        "[compressed-run vcn:2144-2159 [[data-run vcn:2144-2146 cluster:76316]]]\n" +
                        "[compressed-run vcn:2160-2175 [[data-run vcn:2160-2162 cluster:76380]]]\n" +
                        "[compressed-run vcn:2176-2191 [[data-run vcn:2176-2178 cluster:76444]]]\n" +
                        "[compressed-run vcn:2192-2207 [[data-run vcn:2192-2194 cluster:76460]]]\n" +
                        "[compressed-run vcn:2208-2223 [[data-run vcn:2208-2210 cluster:76524]]]\n" +
                        "[compressed-run vcn:2224-2239 [[data-run vcn:2224-2226 cluster:76588]]]\n" +
                        "[compressed-run vcn:2240-2255 [[data-run vcn:2240-2242 cluster:76604]]]\n" +
                        "[compressed-run vcn:2256-2271 [[data-run vcn:2256-2258 cluster:76684]]]\n" +
                        "[compressed-run vcn:2272-2287 [[data-run vcn:2272-2274 cluster:76700]]]\n" +
                        "[compressed-run vcn:2288-2303 [[data-run vcn:2288-2290 cluster:76764]]]\n" +
                        "[compressed-run vcn:2304-2319 [[data-run vcn:2304-2306 cluster:76828]]]\n" +
                        "[compressed-run vcn:2320-2335 [[data-run vcn:2320-2322 cluster:76844]]]\n" +
                        "[compressed-run vcn:2336-2351 [[data-run vcn:2336-2338 cluster:76908]]]\n" +
                        "[compressed-run vcn:2352-2367 [[data-run vcn:2352-2354 cluster:76972]]]\n" +
                        "[compressed-run vcn:2368-2383 [[data-run vcn:2368-2370 cluster:76988]]]\n" +
                        "[compressed-run vcn:2384-2399 [[data-run vcn:2384-2386 cluster:77052]]]\n" +
                        "[compressed-run vcn:2400-2415 [[data-run vcn:2400-2402 cluster:77116]]]\n" +
                        "[compressed-run vcn:2416-2431 [[data-run vcn:2416-2418 cluster:77132]]]\n" +
                        "[compressed-run vcn:2432-2447 [[data-run vcn:2432-2434 cluster:77212]]]\n" +
                        "[compressed-run vcn:2448-2463 [[data-run vcn:2448-2450 cluster:77228]]]\n" +
                        "[compressed-run vcn:2464-2479 [[data-run vcn:2464-2466 cluster:77292]]]\n" +
                        "[compressed-run vcn:2480-2495 [[data-run vcn:2480-2482 cluster:77356]]]\n" +
                        "[compressed-run vcn:2496-2511 [[data-run vcn:2496-2498 cluster:77372]]]\n" +
                        "[compressed-run vcn:2512-2527 [[data-run vcn:2512-2514 cluster:77436]]]\n" +
                        "[compressed-run vcn:2528-2543 [[data-run vcn:2528-2530 cluster:77500]]]\n" +
                        "[compressed-run vcn:2544-2559 [[data-run vcn:2544-2546 cluster:78708]]]\n" +
                        "[compressed-run vcn:2560-2575 [[data-run vcn:2560-2562 cluster:78711]]]\n" +
                        "[compressed-run vcn:2576-2591 [[data-run vcn:2576-2578 cluster:78714]]]\n" +
                        "[compressed-run vcn:2592-2607 [[data-run vcn:2592-2594 cluster:78717]]]\n" +
                        "[compressed-run vcn:2608-2623 [[data-run vcn:2608-2610 cluster:78720]]]\n" +
                        "[compressed-run vcn:2624-2639 [[data-run vcn:2624-2626 cluster:78723]]]\n" +
                        "[compressed-run vcn:2640-2655 [[data-run vcn:2640-2642 cluster:78726]]]\n" +
                        "[compressed-run vcn:2656-2671 [[data-run vcn:2656-2658 cluster:78729]]]\n" +
                        "[compressed-run vcn:2672-2687 [[data-run vcn:2672-2674 cluster:78732]]]\n" +
                        "[compressed-run vcn:2688-2703 [[data-run vcn:2688-2690 cluster:78735]]]\n" +
                        "[compressed-run vcn:2704-2719 [[data-run vcn:2704-2706 cluster:79796]]]\n" +
                        "[compressed-run vcn:2720-2735 [[data-run vcn:2720-2722 cluster:78818]]]\n" +
                        "[compressed-run vcn:2736-2751 [[data-run vcn:2736-2738 cluster:78821]]]\n" +
                        "[compressed-run vcn:2752-2767 [[data-run vcn:2752-2754 cluster:78824]]]\n" +
                        "[compressed-run vcn:2768-2783 [[data-run vcn:2768-2770 cluster:78827]]]\n" +
                        "[compressed-run vcn:2784-2799 [[data-run vcn:2784-2786 cluster:78830]]]\n" +
                        "[compressed-run vcn:2800-2815 [[data-run vcn:2800-2802 cluster:78833]]]\n" +
                        "[compressed-run vcn:2816-2831 [[data-run vcn:2816-2818 cluster:78884]]]\n" +
                        "[compressed-run vcn:2832-2847 [[data-run vcn:2832-2834 cluster:78887]]]\n" +
                        "[compressed-run vcn:2848-2863 [[data-run vcn:2848-2850 cluster:78890]]]\n" +
                        "[compressed-run vcn:2864-2879 [[data-run vcn:2864-2866 cluster:78954]]]\n" +
                        "[compressed-run vcn:2880-2895 [[data-run vcn:2880-2882 cluster:78970]]]\n" +
                        "[compressed-run vcn:2896-2911 [[data-run vcn:2896-2898 cluster:79034]]]\n" +
                        "[compressed-run vcn:2912-2927 [[data-run vcn:2912-2914 cluster:79098]]]\n" +
                        "[compressed-run vcn:2928-2943 [[data-run vcn:2928-2930 cluster:79114]]]\n" +
                        "[compressed-run vcn:2944-2959 [[data-run vcn:2944-2946 cluster:79178]]]\n" +
                        "[compressed-run vcn:2960-2975 [[data-run vcn:2960-2962 cluster:79943]]]\n" +
                        "[compressed-run vcn:2976-2991 [[data-run vcn:2976-2978 cluster:80007]]]\n" +
                        "[compressed-run vcn:2992-3007 [[data-run vcn:2992-2994 cluster:80023]]]\n" +
                        "[compressed-run vcn:3008-3023 [[data-run vcn:3008-3010 cluster:80087]]]\n" +
                        "[compressed-run vcn:3024-3039 [[data-run vcn:3024-3026 cluster:80151]]]\n" +
                        "[compressed-run vcn:3040-3055 [[data-run vcn:3040-3042 cluster:80167]]]\n" +
                        "[compressed-run vcn:3056-3071 [[data-run vcn:3056-3058 cluster:80231]]]\n" +
                        "[compressed-run vcn:3072-3087 [[data-run vcn:3072-3074 cluster:80295]]]\n" +
                        "[compressed-run vcn:3088-3103 [[data-run vcn:3088-3090 cluster:80311]]]\n" +
                        "[compressed-run vcn:3104-3119 [[data-run vcn:3104-3106 cluster:80391]]]\n" +
                        "[compressed-run vcn:3120-3135 [[data-run vcn:3120-3122 cluster:80407]]]\n" +
                        "[compressed-run vcn:3136-3151 [[data-run vcn:3136-3138 cluster:80471]]]\n" +
                        "[compressed-run vcn:3152-3167 [[data-run vcn:3152-3154 cluster:80535]]]\n" +
                        "[compressed-run vcn:3168-3183 [[data-run vcn:3168-3170 cluster:80551]]]\n" +
                        "[compressed-run vcn:3184-3199 [[data-run vcn:3184-3186 cluster:80615]]]\n" +
                        "[compressed-run vcn:3200-3215 [[data-run vcn:3200-3202 cluster:80679]]]\n" +
                        "[compressed-run vcn:3216-3231 [[data-run vcn:3216-3218 cluster:80695]]]\n" +
                        "[compressed-run vcn:3232-3247 [[data-run vcn:3232-3234 cluster:80759]]]\n" +
                        "[compressed-run vcn:3248-3263 [[data-run vcn:3248-3250 cluster:80823]]]\n" +
                        "[compressed-run vcn:3264-3279 [[data-run vcn:3264-3266 cluster:80839]]]\n" +
                        "[compressed-run vcn:3280-3295 [[data-run vcn:3280-3282 cluster:80919]]]\n" +
                        "[compressed-run vcn:3296-3311 [[data-run vcn:3296-3298 cluster:80935]]]\n" +
                        "[compressed-run vcn:3312-3327 [[data-run vcn:3312-3314 cluster:80999]]]\n" +
                        "[compressed-run vcn:3328-3343 [[data-run vcn:3328-3330 cluster:81063]]]\n" +
                        "[compressed-run vcn:3344-3359 [[data-run vcn:3344-3346 cluster:81079]]]\n" +
                        "[compressed-run vcn:3360-3375 [[data-run vcn:3360-3362 cluster:81143]]]\n" +
                        "[compressed-run vcn:3376-3391 [[data-run vcn:3376-3378 cluster:81207]]]\n" +
                        "[compressed-run vcn:3392-3407 [[data-run vcn:3392-3394 cluster:81223]]]\n" +
                        "[compressed-run vcn:3408-3423 [[data-run vcn:3408-3410 cluster:81287]]]\n" +
                        "[compressed-run vcn:3424-3439 [[data-run vcn:3424-3426 cluster:81351]]]\n" +
                        "[compressed-run vcn:3440-3455 [[data-run vcn:3440-3442 cluster:81367]]]\n" +
                        "[compressed-run vcn:3456-3471 [[data-run vcn:3456-3458 cluster:81447]]]\n" +
                        "[compressed-run vcn:3472-3487 [[data-run vcn:3472-3474 cluster:81463]]]\n" +
                        "[compressed-run vcn:3488-3503 [[data-run vcn:3488-3490 cluster:81527]]]\n" +
                        "[compressed-run vcn:3504-3519 [[data-run vcn:3504-3506 cluster:81591]]]\n" +
                        "[compressed-run vcn:3520-3535 [[data-run vcn:3520-3522 cluster:81607]]]\n" +
                        "[compressed-run vcn:3536-3551 [[data-run vcn:3536-3538 cluster:81671]]]\n" +
                        "[compressed-run vcn:3552-3567 [[data-run vcn:3552-3554 cluster:81735]]]\n" +
                        "[compressed-run vcn:3568-3583 [[data-run vcn:3568-3570 cluster:81751]]]\n" +
                        "[compressed-run vcn:3584-3599 [[data-run vcn:3584-3586 cluster:81815]]]\n" +
                        "[compressed-run vcn:3600-3615 [[data-run vcn:3600-3602 cluster:81879]]]\n" +
                        "[compressed-run vcn:3616-3631 [[data-run vcn:3616-3618 cluster:81895]]]\n" +
                        "[compressed-run vcn:3632-3647 [[data-run vcn:3632-3634 cluster:81975]]]\n" +
                        "[compressed-run vcn:3648-3663 [[data-run vcn:3648-3650 cluster:81991]]]\n" +
                        "[compressed-run vcn:3664-3679 [[data-run vcn:3664-3666 cluster:82055]]]\n" +
                        "[compressed-run vcn:3680-3695 [[data-run vcn:3680-3682 cluster:82119]]]\n" +
                        "[compressed-run vcn:3696-3711 [[data-run vcn:3696-3698 cluster:82135]]]\n" +
                        "[compressed-run vcn:3712-3727 [[data-run vcn:3712-3714 cluster:82199]]]\n" +
                        "[compressed-run vcn:3728-3743 [[data-run vcn:3728-3730 cluster:82263]]]\n" +
                        "[compressed-run vcn:3744-3759 [[data-run vcn:3744-3746 cluster:82279]]]\n" +
                        "[compressed-run vcn:3760-3775 [[data-run vcn:3760-3762 cluster:82343]]]\n" +
                        "[compressed-run vcn:3776-3791 [[data-run vcn:3776-3778 cluster:82407]]]\n" +
                        "[compressed-run vcn:3792-3807 [[data-run vcn:3792-3794 cluster:82423]]]\n" +
                        "[compressed-run vcn:3808-3823 [[data-run vcn:3808-3810 cluster:82503]]]\n" +
                        "[compressed-run vcn:3824-3839 [[data-run vcn:3824-3826 cluster:82519]]]\n" +
                        "[compressed-run vcn:3840-3855 [[data-run vcn:3840-3842 cluster:82583]]]\n" +
                        "[compressed-run vcn:3856-3871 [[data-run vcn:3856-3858 cluster:82647]]]\n" +
                        "[compressed-run vcn:3872-3887 [[data-run vcn:3872-3874 cluster:82663]]]\n" +
                        "[compressed-run vcn:3888-3903 [[data-run vcn:3888-3890 cluster:82727]]]\n" +
                        "[compressed-run vcn:3904-3919 [[data-run vcn:3904-3906 cluster:82791]]]\n" +
                        "[compressed-run vcn:3920-3935 [[data-run vcn:3920-3922 cluster:82807]]]\n" +
                        "[compressed-run vcn:3936-3951 [[data-run vcn:3936-3938 cluster:82871]]]\n" +
                        "[compressed-run vcn:3952-3967 [[data-run vcn:3952-3954 cluster:82935]]]\n" +
                        "[compressed-run vcn:3968-3983 [[data-run vcn:3968-3970 cluster:82951]]]\n" +
                        "[compressed-run vcn:3984-3999 [[data-run vcn:3984-3986 cluster:83031]]]\n" +
                        "[compressed-run vcn:4000-4015 [[data-run vcn:4000-4002 cluster:83047]]]\n" +
                        "[compressed-run vcn:4016-4031 [[data-run vcn:4016-4018 cluster:83111]]]\n" +
                        "[compressed-run vcn:4032-4047 [[data-run vcn:4032-4034 cluster:83175]]]\n" +
                        "[compressed-run vcn:4048-4063 [[data-run vcn:4048-4050 cluster:83191]]]\n" +
                        "[compressed-run vcn:4064-4079 [[data-run vcn:4064-4066 cluster:83255]]]\n" +
                        "[compressed-run vcn:4080-4095 [[data-run vcn:4080-4082 cluster:83319]]]\n" +
                        "[compressed-run vcn:4096-4111 [[data-run vcn:4096-4098 cluster:83335]]]\n" +
                        "[compressed-run vcn:4112-4127 [[data-run vcn:4112-4114 cluster:83399]]]\n" +
                        "[compressed-run vcn:4128-4143 [[data-run vcn:4128-4130 cluster:83463]]]\n" +
                        "[compressed-run vcn:4144-4159 [[data-run vcn:4144-4146 cluster:83479]]]\n" +
                        "[compressed-run vcn:4160-4175 [[data-run vcn:4160-4162 cluster:83559]]]\n" +
                        "[compressed-run vcn:4176-4191 [[data-run vcn:4176-4178 cluster:83575]]]\n" +
                        "[compressed-run vcn:4192-4207 [[data-run vcn:4192-4194 cluster:83639]]]\n" +
                        "[compressed-run vcn:4208-4223 [[data-run vcn:4208-4210 cluster:83703]]]\n" +
                        "[compressed-run vcn:4224-4239 [[data-run vcn:4224-4226 cluster:83719]]]\n" +
                        "[compressed-run vcn:4240-4255 [[data-run vcn:4240-4242 cluster:83783]]]\n" +
                        "[compressed-run vcn:4256-4271 [[data-run vcn:4256-4258 cluster:83847]]]\n" +
                        "[compressed-run vcn:4272-4287 [[data-run vcn:4272-4274 cluster:83863]]]\n" +
                        "[compressed-run vcn:4288-4303 [[data-run vcn:4288-4290 cluster:83927]]]\n" +
                        "[compressed-run vcn:4304-4319 [[data-run vcn:4304-4306 cluster:83991]]]\n" +
                        "[compressed-run vcn:4320-4335 [[data-run vcn:4320-4322 cluster:84007]]]\n" +
                        "[compressed-run vcn:4336-4351 [[data-run vcn:4336-4338 cluster:84087]]]\n" +
                        "[compressed-run vcn:4352-4367 [[data-run vcn:4352-4354 cluster:84103]]]\n" +
                        "[compressed-run vcn:4368-4383 [[data-run vcn:4368-4370 cluster:84167]]]\n" +
                        "[compressed-run vcn:4384-4399 [[data-run vcn:4384-4386 cluster:84231]]]\n" +
                        "[compressed-run vcn:4400-4415 [[data-run vcn:4400-4402 cluster:84247]]]\n" +
                        "[compressed-run vcn:4416-4431 [[data-run vcn:4416-4418 cluster:84311]]]\n" +
                        "[compressed-run vcn:4432-4447 [[data-run vcn:4432-4434 cluster:84375]]]\n" +
                        "[compressed-run vcn:4448-4463 [[data-run vcn:4448-4450 cluster:84391]]]\n" +
                        "[compressed-run vcn:4464-4479 [[data-run vcn:4464-4466 cluster:84455]]]\n" +
                        "[compressed-run vcn:4480-4495 [[data-run vcn:4480-4482 cluster:84519]]]\n" +
                        "[compressed-run vcn:4496-4511 [[data-run vcn:4496-4498 cluster:84535]]]\n" +
                        "[compressed-run vcn:4512-4527 [[data-run vcn:4512-4514 cluster:84615]]]\n" +
                        "[compressed-run vcn:4528-4543 [[data-run vcn:4528-4530 cluster:84631]]]\n" +
                        "[compressed-run vcn:4544-4559 [[data-run vcn:4544-4546 cluster:84695]]]\n" +
                        "[compressed-run vcn:4560-4575 [[data-run vcn:4560-4562 cluster:84759]]]\n" +
                        "[compressed-run vcn:4576-4591 [[data-run vcn:4576-4578 cluster:84775]]]\n" +
                        "[compressed-run vcn:4592-4607 [[data-run vcn:4592-4594 cluster:84839]]]\n" +
                        "[compressed-run vcn:4608-4623 [[data-run vcn:4608-4610 cluster:84903]]]\n" +
                        "[compressed-run vcn:4624-4639 [[data-run vcn:4624-4626 cluster:84919]]]\n" +
                        "[compressed-run vcn:4640-4655 [[data-run vcn:4640-4642 cluster:84983]]]\n" +
                        "[compressed-run vcn:4656-4671 [[data-run vcn:4656-4658 cluster:85047]]]\n" +
                        "[compressed-run vcn:4672-4687 [[data-run vcn:4672-4674 cluster:85063]]]\n" +
                        "[compressed-run vcn:4688-4703 [[data-run vcn:4688-4690 cluster:85143]]]\n" +
                        "[compressed-run vcn:4704-4719 [[data-run vcn:4704-4706 cluster:85159]]]\n" +
                        "[compressed-run vcn:4720-4735 [[data-run vcn:4720-4722 cluster:85223]]]\n" +
                        "[compressed-run vcn:4736-4751 [[data-run vcn:4736-4738 cluster:85287]]]\n" +
                        "[compressed-run vcn:4752-4767 [[data-run vcn:4752-4754 cluster:85303]]]\n" +
                        "[compressed-run vcn:4768-4783 [[data-run vcn:4768-4770 cluster:85367]]]\n" +
                        "[compressed-run vcn:4784-4799 [[data-run vcn:4784-4786 cluster:85431]]]\n" +
                        "[compressed-run vcn:4800-4815 [[data-run vcn:4800-4802 cluster:85447]]]\n" +
                        "[compressed-run vcn:4816-4831 [[data-run vcn:4816-4818 cluster:85511]]]\n" +
                        "[compressed-run vcn:4832-4847 [[data-run vcn:4832-4834 cluster:85575]]]\n" +
                        "[compressed-run vcn:4848-4863 [[data-run vcn:4848-4850 cluster:85591]]]\n" +
                        "[compressed-run vcn:4864-4879 [[data-run vcn:4864-4866 cluster:85671]]]\n" +
                        "[compressed-run vcn:4880-4895 [[data-run vcn:4880-4882 cluster:85687]]]\n" +
                        "[compressed-run vcn:4896-4911 [[data-run vcn:4896-4898 cluster:85751]]]\n" +
                        "[compressed-run vcn:4912-4927 [[data-run vcn:4912-4914 cluster:85815]]]\n" +
                        "[compressed-run vcn:4928-4943 [[data-run vcn:4928-4930 cluster:85831]]]\n" +
                        "[compressed-run vcn:4944-4959 [[data-run vcn:4944-4946 cluster:85895]]]\n" +
                        "[compressed-run vcn:4960-4975 [[data-run vcn:4960-4962 cluster:85959]]]\n" +
                        "[compressed-run vcn:4976-4991 [[data-run vcn:4976-4978 cluster:85975]]]\n" +
                        "[compressed-run vcn:4992-5007 [[data-run vcn:4992-4994 cluster:86039]]]\n" +
                        "[compressed-run vcn:5008-5023 [[data-run vcn:5008-5010 cluster:86103]]]\n" +
                        "[compressed-run vcn:5024-5039 [[data-run vcn:5024-5026 cluster:86119]]]\n" +
                        "[compressed-run vcn:5040-5055 [[data-run vcn:5040-5042 cluster:86199]]]\n" +
                        "[compressed-run vcn:5056-5071 [[data-run vcn:5056-5058 cluster:86215]]]\n" +
                        "[compressed-run vcn:5072-5087 [[data-run vcn:5072-5074 cluster:86279]]]\n" +
                        "[compressed-run vcn:5088-5103 [[data-run vcn:5088-5090 cluster:86343]]]\n" +
                        "[compressed-run vcn:5104-5119 [[data-run vcn:5104-5106 cluster:86359]]]\n" +
                        "[compressed-run vcn:5120-5135 [[data-run vcn:5120-5122 cluster:86423]]]\n" +
                        "[compressed-run vcn:5136-5151 [[data-run vcn:5136-5138 cluster:86487]]]\n" +
                        "[compressed-run vcn:5152-5167 [[data-run vcn:5152-5154 cluster:86503]]]\n" +
                        "[compressed-run vcn:5168-5183 [[data-run vcn:5168-5170 cluster:86567]]]\n" +
                        "[compressed-run vcn:5184-5199 [[data-run vcn:5184-5186 cluster:86631]]]\n" +
                        "[compressed-run vcn:5200-5215 [[data-run vcn:5200-5202 cluster:86647]]]\n" +
                        "[compressed-run vcn:5216-5231 [[data-run vcn:5216-5218 cluster:86727]]]\n" +
                        "[compressed-run vcn:5232-5247 [[data-run vcn:5232-5234 cluster:86743]]]\n" +
                        "[compressed-run vcn:5248-5263 [[data-run vcn:5248-5250 cluster:86807]]]\n" +
                        "[compressed-run vcn:5264-5279 [[data-run vcn:5264-5266 cluster:86871]]]\n" +
                        "[compressed-run vcn:5280-5295 [[data-run vcn:5280-5282 cluster:86887]]]\n" +
                        "[compressed-run vcn:5296-5311 [[data-run vcn:5296-5298 cluster:86951]]]\n" +
                        "[compressed-run vcn:5312-5327 [[data-run vcn:5312-5314 cluster:87015]]]\n" +
                        "[compressed-run vcn:5328-5343 [[data-run vcn:5328-5330 cluster:87031]]]\n" +
                        "[compressed-run vcn:5344-5359 [[data-run vcn:5344-5346 cluster:87095]]]\n" +
                        "[compressed-run vcn:5360-5375 [[data-run vcn:5360-5362 cluster:87159]]]\n" +
                        "[compressed-run vcn:5376-5391 [[data-run vcn:5376-5378 cluster:87175]]]\n" +
                        "[compressed-run vcn:5392-5407 [[data-run vcn:5392-5394 cluster:87255]]]\n" +
                        "[compressed-run vcn:5408-5423 [[data-run vcn:5408-5410 cluster:87271]]]\n" +
                        "[compressed-run vcn:5424-5439 [[data-run vcn:5424-5426 cluster:87335]]]\n" +
                        "[compressed-run vcn:5440-5455 [[data-run vcn:5440-5442 cluster:87399]]]\n" +
                        "[compressed-run vcn:5456-5471 [[data-run vcn:5456-5458 cluster:87415]]]\n" +
                        "[compressed-run vcn:5472-5487 [[data-run vcn:5472-5474 cluster:87479]]]\n" +
                        "[compressed-run vcn:5488-5503 [[data-run vcn:5488-5490 cluster:87543]]]\n" +
                        "[compressed-run vcn:5504-5519 [[data-run vcn:5504-5506 cluster:87559]]]\n" +
                        "[compressed-run vcn:5520-5535 [[data-run vcn:5520-5522 cluster:87623]]]\n" +
                        "[compressed-run vcn:5536-5551 [[data-run vcn:5536-5538 cluster:87687]]]\n" +
                        "[compressed-run vcn:5552-5567 [[data-run vcn:5552-5554 cluster:87703]]]\n" +
                        "[compressed-run vcn:5568-5583 [[data-run vcn:5568-5570 cluster:87783]]]\n" +
                        "[compressed-run vcn:5584-5599 [[data-run vcn:5584-5586 cluster:87799]]]\n" +
                        "[compressed-run vcn:5600-5615 [[data-run vcn:5600-5602 cluster:87863]]]\n" +
                        "[compressed-run vcn:5616-5631 [[data-run vcn:5616-5618 cluster:87927]]]\n" +
                        "[compressed-run vcn:5632-5647 [[data-run vcn:5632-5634 cluster:87943]]]\n" +
                        "[compressed-run vcn:5648-5663 [[data-run vcn:5648-5650 cluster:88007]]]\n" +
                        "[compressed-run vcn:5664-5679 [[data-run vcn:5664-5666 cluster:88071]]]\n" +
                        "[compressed-run vcn:5680-5695 [[data-run vcn:5680-5682 cluster:88087]]]\n" +
                        "[compressed-run vcn:5696-5711 [[data-run vcn:5696-5698 cluster:88151]]]\n" +
                        "[compressed-run vcn:5712-5727 [[data-run vcn:5712-5714 cluster:88215]]]\n" +
                        "[compressed-run vcn:5728-5743 [[data-run vcn:5728-5730 cluster:88231]]]\n" +
                        "[compressed-run vcn:5744-5759 [[data-run vcn:5744-5746 cluster:88311]]]\n" +
                        "[compressed-run vcn:5760-5775 [[data-run vcn:5760-5762 cluster:88327]]]\n" +
                        "[compressed-run vcn:5776-5791 [[data-run vcn:5776-5778 cluster:88391]]]\n" +
                        "[compressed-run vcn:5792-5807 [[data-run vcn:5792-5794 cluster:88455]]]\n" +
                        "[compressed-run vcn:5808-5823 [[data-run vcn:5808-5810 cluster:88471]]]\n" +
                        "[compressed-run vcn:5824-5839 [[data-run vcn:5824-5826 cluster:88535]]]\n" +
                        "[compressed-run vcn:5840-5855 [[data-run vcn:5840-5842 cluster:88599]]]\n" +
                        "[compressed-run vcn:5856-5871 [[data-run vcn:5856-5858 cluster:88615]]]\n" +
                        "[compressed-run vcn:5872-5887 [[data-run vcn:5872-5874 cluster:88679]]]\n" +
                        "[compressed-run vcn:5888-5903 [[data-run vcn:5888-5890 cluster:88743]]]\n" +
                        "[compressed-run vcn:5904-5919 [[data-run vcn:5904-5906 cluster:88759]]]\n" +
                        "[compressed-run vcn:5920-5935 [[data-run vcn:5920-5922 cluster:88839]]]\n" +
                        "[compressed-run vcn:5936-5951 [[data-run vcn:5936-5938 cluster:88855]]]\n" +
                        "[compressed-run vcn:5952-5967 [[data-run vcn:5952-5954 cluster:88919]]]\n" +
                        "[compressed-run vcn:5968-5983 [[data-run vcn:5968-5970 cluster:88983]]]\n" +
                        "[compressed-run vcn:5984-5999 [[data-run vcn:5984-5986 cluster:88999]]]\n" +
                        "[compressed-run vcn:6000-6015 [[data-run vcn:6000-6002 cluster:89063]]]\n" +
                        "[compressed-run vcn:6016-6031 [[data-run vcn:6016-6018 cluster:89127]]]\n" +
                        "[compressed-run vcn:6032-6047 [[data-run vcn:6032-6034 cluster:89143]]]\n" +
                        "[compressed-run vcn:6048-6063 [[data-run vcn:6048-6050 cluster:89207]]]\n" +
                        "[compressed-run vcn:6064-6079 [[data-run vcn:6064-6066 cluster:89271]]]\n" +
                        "[compressed-run vcn:6080-6095 [[data-run vcn:6080-6082 cluster:89287]]]\n" +
                        "[compressed-run vcn:6096-6111 [[data-run vcn:6096-6098 cluster:89367]]]\n" +
                        "[compressed-run vcn:6112-6127 [[data-run vcn:6112-6114 cluster:89383]]]\n" +
                        "[compressed-run vcn:6128-6143 [[data-run vcn:6128-6130 cluster:89447]]]\n" +
                        "[compressed-run vcn:6144-6159 [[data-run vcn:6144-6146 cluster:89511]]]\n" +
                        "[compressed-run vcn:6160-6175 [[data-run vcn:6160-6162 cluster:89527]]]\n" +
                        "[compressed-run vcn:6176-6191 [[data-run vcn:6176-6178 cluster:89591]]]\n" +
                        "[compressed-run vcn:6192-6207 [[data-run vcn:6192-6194 cluster:89655]]]\n" +
                        "[compressed-run vcn:6208-6223 [[data-run vcn:6208-6210 cluster:89671]]]\n" +
                        "[compressed-run vcn:6224-6239 [[data-run vcn:6224-6226 cluster:89735]]]\n" +
                        "[compressed-run vcn:6240-6255 [[data-run vcn:6240-6242 cluster:89799]]]\n" +
                        "[compressed-run vcn:6256-6271 [[data-run vcn:6256-6258 cluster:89815]]]\n" +
                        "[compressed-run vcn:6272-6287 [[data-run vcn:6272-6274 cluster:89895]]]\n" +
                        "[compressed-run vcn:6288-6303 [[data-run vcn:6288-6290 cluster:89911]]]\n" +
                        "[compressed-run vcn:6304-6319 [[data-run vcn:6304-6306 cluster:89975]]]\n" +
                        "[compressed-run vcn:6320-6335 [[data-run vcn:6320-6322 cluster:90039]]]\n" +
                        "[compressed-run vcn:6336-6351 [[data-run vcn:6336-6338 cluster:90055]]]\n" +
                        "[compressed-run vcn:6352-6367 [[data-run vcn:6352-6354 cluster:90119]]]\n" +
                        "[compressed-run vcn:6368-6383 [[data-run vcn:6368-6370 cluster:90183]]]\n" +
                        "[compressed-run vcn:6384-6399 [[data-run vcn:6384-6386 cluster:90199]]]\n" +
                        "[compressed-run vcn:6400-6415 [[data-run vcn:6400-6402 cluster:90263]]]\n" +
                        "[compressed-run vcn:6416-6431 [[data-run vcn:6416-6418 cluster:90327]]]\n" +
                        "[compressed-run vcn:6432-6447 [[data-run vcn:6432-6434 cluster:90343]]]\n" +
                        "[compressed-run vcn:6448-6463 [[data-run vcn:6448-6450 cluster:90423]]]\n" +
                        "[compressed-run vcn:6464-6479 [[data-run vcn:6464-6466 cluster:90439]]]\n" +
                        "[compressed-run vcn:6480-6495 [[data-run vcn:6480-6482 cluster:90503]]]\n" +
                        "[compressed-run vcn:6496-6511 [[data-run vcn:6496-6498 cluster:90567]]]\n" +
                        "[compressed-run vcn:6512-6527 [[data-run vcn:6512-6514 cluster:90583]]]\n" +
                        "[compressed-run vcn:6528-6543 [[data-run vcn:6528-6530 cluster:90647]]]\n" +
                        "[compressed-run vcn:6544-6559 [[data-run vcn:6544-6546 cluster:90711]]]\n" +
                        "[compressed-run vcn:6560-6575 [[data-run vcn:6560-6562 cluster:90727]]]\n" +
                        "[compressed-run vcn:6576-6591 [[data-run vcn:6576-6578 cluster:90791]]]\n" +
                        "[compressed-run vcn:6592-6607 [[data-run vcn:6592-6594 cluster:90855]]]\n" +
                        "[compressed-run vcn:6608-6623 [[data-run vcn:6608-6610 cluster:90871]]]\n" +
                        "[compressed-run vcn:6624-6639 [[data-run vcn:6624-6626 cluster:90951]]]\n" +
                        "[compressed-run vcn:6640-6655 [[data-run vcn:6640-6642 cluster:90967]]]\n" +
                        "[compressed-run vcn:6656-6671 [[data-run vcn:6656-6658 cluster:91031]]]\n" +
                        "[compressed-run vcn:6672-6687 [[data-run vcn:6672-6674 cluster:91095]]]\n" +
                        "[compressed-run vcn:6688-6703 [[data-run vcn:6688-6690 cluster:91111]]]\n" +
                        "[compressed-run vcn:6704-6719 [[data-run vcn:6704-6706 cluster:91175]]]\n" +
                        "[compressed-run vcn:6720-6735 [[data-run vcn:6720-6722 cluster:91239]]]\n" +
                        "[compressed-run vcn:6736-6751 [[data-run vcn:6736-6738 cluster:91255]]]\n" +
                        "[compressed-run vcn:6752-6767 [[data-run vcn:6752-6754 cluster:91319]]]\n" +
                        "[compressed-run vcn:6768-6783 [[data-run vcn:6768-6770 cluster:91383]]]\n" +
                        "[compressed-run vcn:6784-6799 [[data-run vcn:6784-6786 cluster:91399]]]\n" +
                        "[compressed-run vcn:6800-6815 [[data-run vcn:6800-6802 cluster:91479]]]\n" +
                        "[compressed-run vcn:6816-6831 [[data-run vcn:6816-6818 cluster:91495]]]\n" +
                        "[compressed-run vcn:6832-6847 [[data-run vcn:6832-6834 cluster:91559]]]\n" +
                        "[compressed-run vcn:6848-6863 [[data-run vcn:6848-6850 cluster:91623]]]\n" +
                        "[compressed-run vcn:6864-6879 [[data-run vcn:6864-6866 cluster:91639]]]\n" +
                        "[compressed-run vcn:6880-6895 [[data-run vcn:6880-6882 cluster:91703]]]\n" +
                        "[compressed-run vcn:6896-6911 [[data-run vcn:6896-6898 cluster:91767]]]\n" +
                        "[compressed-run vcn:6912-6927 [[data-run vcn:6912-6914 cluster:91783]]]\n" +
                        "[compressed-run vcn:6928-6943 [[data-run vcn:6928-6930 cluster:91847]]]\n" +
                        "[compressed-run vcn:6944-6959 [[data-run vcn:6944-6946 cluster:91911]]]\n" +
                        "[compressed-run vcn:6960-6975 [[data-run vcn:6960-6962 cluster:91927]]]\n" +
                        "[compressed-run vcn:6976-6991 [[data-run vcn:6976-6978 cluster:92007]]]\n" +
                        "[compressed-run vcn:6992-7007 [[data-run vcn:6992-6994 cluster:92023]]]\n" +
                        "[compressed-run vcn:7008-7023 [[data-run vcn:7008-7010 cluster:92087]]]\n" +
                        "[compressed-run vcn:7024-7039 [[data-run vcn:7024-7026 cluster:92151]]]\n" +
                        "[compressed-run vcn:7040-7055 [[data-run vcn:7040-7042 cluster:92167]]]\n" +
                        "[compressed-run vcn:7056-7071 [[data-run vcn:7056-7058 cluster:92231]]]\n" +
                        "[compressed-run vcn:7072-7087 [[data-run vcn:7072-7074 cluster:92295]]]\n" +
                        "[compressed-run vcn:7088-7103 [[data-run vcn:7088-7090 cluster:92311]]]\n" +
                        "[compressed-run vcn:7104-7119 [[data-run vcn:7104-7106 cluster:92375]]]\n" +
                        "[compressed-run vcn:7120-7135 [[data-run vcn:7120-7122 cluster:92439]]]\n" +
                        "[compressed-run vcn:7136-7151 [[data-run vcn:7136-7138 cluster:92455]]]\n" +
                        "[compressed-run vcn:7152-7167 [[data-run vcn:7152-7154 cluster:92535]]]\n" +
                        "[compressed-run vcn:7168-7183 [[data-run vcn:7168-7170 cluster:92551]]]\n" +
                        "[compressed-run vcn:7184-7199 [[data-run vcn:7184-7186 cluster:92615]]]\n" +
                        "[compressed-run vcn:7200-7215 [[data-run vcn:7200-7202 cluster:92679]]]\n" +
                        "[compressed-run vcn:7216-7231 [[data-run vcn:7216-7218 cluster:92695]]]\n" +
                        "[compressed-run vcn:7232-7247 [[data-run vcn:7232-7234 cluster:92759]]]\n" +
                        "[compressed-run vcn:7248-7263 [[data-run vcn:7248-7250 cluster:92823]]]\n" +
                        "[compressed-run vcn:7264-7279 [[data-run vcn:7264-7266 cluster:92839]]]\n" +
                        "[compressed-run vcn:7280-7295 [[data-run vcn:7280-7282 cluster:92903]]]\n" +
                        "[compressed-run vcn:7296-7311 [[data-run vcn:7296-7298 cluster:92967]]]\n" +
                        "[compressed-run vcn:7312-7327 [[data-run vcn:7312-7314 cluster:92983]]]\n" +
                        "[compressed-run vcn:7328-7343 [[data-run vcn:7328-7330 cluster:93063]]]\n" +
                        "[compressed-run vcn:7344-7359 [[data-run vcn:7344-7346 cluster:93079]]]\n" +
                        "[compressed-run vcn:7360-7375 [[data-run vcn:7360-7362 cluster:93143]]]\n" +
                        "[compressed-run vcn:7376-7391 [[data-run vcn:7376-7378 cluster:93207]]]\n" +
                        "[compressed-run vcn:7392-7407 [[data-run vcn:7392-7394 cluster:93223]]]\n" +
                        "[compressed-run vcn:7408-7423 [[data-run vcn:7408-7410 cluster:93287]]]\n" +
                        "[compressed-run vcn:7424-7439 [[data-run vcn:7424-7426 cluster:93351]]]\n" +
                        "[compressed-run vcn:7440-7455 [[data-run vcn:7440-7442 cluster:93367]]]\n" +
                        "[compressed-run vcn:7456-7471 [[data-run vcn:7456-7458 cluster:93431]]]\n" +
                        "[compressed-run vcn:7472-7487 [[data-run vcn:7472-7474 cluster:93495]]]\n" +
                        "[compressed-run vcn:7488-7503 [[data-run vcn:7488-7490 cluster:93511]]]\n" +
                        "[compressed-run vcn:7504-7519 [[data-run vcn:7504-7506 cluster:93591]]]\n" +
                        "[compressed-run vcn:7520-7535 [[data-run vcn:7520-7522 cluster:93607]]]\n" +
                        "[compressed-run vcn:7536-7551 [[data-run vcn:7536-7538 cluster:93671]]]\n" +
                        "[compressed-run vcn:7552-7567 [[data-run vcn:7552-7554 cluster:93735]]]\n" +
                        "[compressed-run vcn:7568-7583 [[data-run vcn:7568-7570 cluster:93751]]]\n" +
                        "[compressed-run vcn:7584-7599 [[data-run vcn:7584-7586 cluster:93815]]]\n" +
                        "[compressed-run vcn:7600-7615 [[data-run vcn:7600-7602 cluster:93879]]]\n" +
                        "[compressed-run vcn:7616-7631 [[data-run vcn:7616-7618 cluster:93895]]]\n" +
                        "[compressed-run vcn:7632-7647 [[data-run vcn:7632-7634 cluster:93959]]]\n" +
                        "[compressed-run vcn:7648-7663 [[data-run vcn:7648-7650 cluster:94023]]]\n" +
                        "[compressed-run vcn:7664-7679 [[data-run vcn:7664-7666 cluster:94039]]]\n" +
                        "[compressed-run vcn:7680-7695 [[data-run vcn:7680-7682 cluster:94119]]]\n" +
                        "[compressed-run vcn:7696-7711 [[data-run vcn:7696-7698 cluster:94135]]]\n" +
                        "[compressed-run vcn:7712-7727 [[data-run vcn:7712-7714 cluster:94199]]]\n" +
                        "[compressed-run vcn:7728-7743 [[data-run vcn:7728-7730 cluster:94263]]]\n" +
                        "[compressed-run vcn:7744-7759 [[data-run vcn:7744-7746 cluster:94279]]]\n" +
                        "[compressed-run vcn:7760-7775 [[data-run vcn:7760-7762 cluster:94343]]]\n" +
                        "[compressed-run vcn:7776-7791 [[data-run vcn:7776-7778 cluster:94407]]]\n" +
                        "[compressed-run vcn:7792-7807 [[data-run vcn:7792-7794 cluster:94423]]]\n" +
                        "[compressed-run vcn:7808-7823 [[data-run vcn:7808-7810 cluster:94487]]]\n" +
                        "[compressed-run vcn:7824-7839 [[data-run vcn:7824-7826 cluster:94551]]]\n" +
                        "[compressed-run vcn:7840-7855 [[data-run vcn:7840-7842 cluster:94567]]]\n" +
                        "[compressed-run vcn:7856-7871 [[data-run vcn:7856-7858 cluster:94647]]]\n" +
                        "[compressed-run vcn:7872-7887 [[data-run vcn:7872-7874 cluster:94663]]]\n" +
                        "[compressed-run vcn:7888-7903 [[data-run vcn:7888-7890 cluster:94727]]]\n" +
                        "[compressed-run vcn:7904-7919 [[data-run vcn:7904-7906 cluster:94791]]]\n" +
                        "[compressed-run vcn:7920-7935 [[data-run vcn:7920-7922 cluster:94807]]]\n" +
                        "[compressed-run vcn:7936-7951 [[data-run vcn:7936-7938 cluster:94871]]]\n" +
                        "[compressed-run vcn:7952-7967 [[data-run vcn:7952-7954 cluster:94935]]]\n" +
                        "[compressed-run vcn:7968-7983 [[data-run vcn:7968-7970 cluster:94951]]]\n" +
                        "[compressed-run vcn:7984-7999 [[data-run vcn:7984-7986 cluster:95015]]]\n" +
                        "[compressed-run vcn:8000-8015 [[data-run vcn:8000-8002 cluster:95079]]]\n" +
                        "[compressed-run vcn:8016-8031 [[data-run vcn:8016-8018 cluster:95095]]]\n" +
                        "[compressed-run vcn:8032-8047 [[data-run vcn:8032-8034 cluster:95175]]]\n" +
                        "[compressed-run vcn:8048-8063 [[data-run vcn:8048-8050 cluster:95191]]]\n" +
                        "[compressed-run vcn:8064-8079 [[data-run vcn:8064-8066 cluster:95255]]]\n" +
                        "[compressed-run vcn:8080-8095 [[data-run vcn:8080-8082 cluster:95319]]]\n" +
                        "[compressed-run vcn:8096-8111 [[data-run vcn:8096-8098 cluster:95335]]]\n" +
                        "[compressed-run vcn:8112-8127 [[data-run vcn:8112-8114 cluster:95399]]]\n" +
                        "[compressed-run vcn:8128-8143 [[data-run vcn:8128-8130 cluster:95463]]]\n" +
                        "[compressed-run vcn:8144-8159 [[data-run vcn:8144-8146 cluster:95479]]]\n" +
                        "[compressed-run vcn:8160-8175 [[data-run vcn:8160-8162 cluster:95543]]]\n" +
                        "[compressed-run vcn:8176-8191 [[data-run vcn:8176-8178 cluster:95607]]]\n" +
                        "[compressed-run vcn:8192-8207 [[data-run vcn:8192-8194 cluster:95623]]]\n" +
                        "[compressed-run vcn:8208-8223 [[data-run vcn:8208-8210 cluster:95703]]]\n" +
                        "[compressed-run vcn:8224-8239 [[data-run vcn:8224-8226 cluster:95719]]]\n" +
                        "[compressed-run vcn:8240-8255 [[data-run vcn:8240-8242 cluster:95783]]]\n" +
                        "[compressed-run vcn:8256-8271 [[data-run vcn:8256-8258 cluster:95847]]]\n" +
                        "[compressed-run vcn:8272-8287 [[data-run vcn:8272-8274 cluster:95863]]]\n" +
                        "[compressed-run vcn:8288-8303 [[data-run vcn:8288-8290 cluster:95927]]]\n" +
                        "[compressed-run vcn:8304-8319 [[data-run vcn:8304-8306 cluster:95991]]]\n" +
                        "[compressed-run vcn:8320-8335 [[data-run vcn:8320-8322 cluster:96007]]]\n" +
                        "[compressed-run vcn:8336-8351 [[data-run vcn:8336-8338 cluster:96071]]]\n" +
                        "[compressed-run vcn:8352-8367 [[data-run vcn:8352-8354 cluster:96135]]]\n" +
                        "[compressed-run vcn:8368-8383 [[data-run vcn:8368-8370 cluster:96151]]]\n" +
                        "[compressed-run vcn:8384-8399 [[data-run vcn:8384-8386 cluster:96231]]]\n" +
                        "[compressed-run vcn:8400-8415 [[data-run vcn:8400-8402 cluster:96247]]]\n" +
                        "[compressed-run vcn:8416-8431 [[data-run vcn:8416-8418 cluster:96311]]]\n" +
                        "[compressed-run vcn:8432-8447 [[data-run vcn:8432-8434 cluster:96375]]]\n" +
                        "[compressed-run vcn:8448-8463 [[data-run vcn:8448-8450 cluster:96391]]]\n" +
                        "[compressed-run vcn:8464-8479 [[data-run vcn:8464-8466 cluster:96455]]]\n" +
                        "[compressed-run vcn:8480-8495 [[data-run vcn:8480-8482 cluster:96519]]]\n" +
                        "[compressed-run vcn:8496-8511 [[data-run vcn:8496-8498 cluster:96535]]]\n" +
                        "[compressed-run vcn:8512-8527 [[data-run vcn:8512-8514 cluster:96599]]]\n" +
                        "[compressed-run vcn:8528-8543 [[data-run vcn:8528-8530 cluster:96663]]]\n" +
                        "[compressed-run vcn:8544-8559 [[data-run vcn:8544-8546 cluster:96679]]]\n" +
                        "[compressed-run vcn:8560-8575 [[data-run vcn:8560-8562 cluster:96759]]]\n" +
                        "[compressed-run vcn:8576-8591 [[data-run vcn:8576-8578 cluster:96775]]]\n" +
                        "[compressed-run vcn:8592-8607 [[data-run vcn:8592-8594 cluster:96839]]]\n" +
                        "[compressed-run vcn:8608-8623 [[data-run vcn:8608-8610 cluster:96903]]]\n" +
                        "[compressed-run vcn:8624-8639 [[data-run vcn:8624-8626 cluster:96919]]]\n" +
                        "[compressed-run vcn:8640-8655 [[data-run vcn:8640-8642 cluster:96983]]]\n" +
                        "[compressed-run vcn:8656-8671 [[data-run vcn:8656-8658 cluster:97047]]]\n" +
                        "[compressed-run vcn:8672-8687 [[data-run vcn:8672-8674 cluster:97063]]]\n" +
                        "[compressed-run vcn:8688-8703 [[data-run vcn:8688-8690 cluster:97127]]]\n" +
                        "[compressed-run vcn:8704-8719 [[data-run vcn:8704-8706 cluster:97191]]]\n" +
                        "[compressed-run vcn:8720-8735 [[data-run vcn:8720-8722 cluster:97207]]]\n" +
                        "[compressed-run vcn:8736-8751 [[data-run vcn:8736-8738 cluster:97287]]]\n" +
                        "[compressed-run vcn:8752-8767 [[data-run vcn:8752-8754 cluster:97303]]]\n" +
                        "[compressed-run vcn:8768-8783 [[data-run vcn:8768-8770 cluster:97367]]]\n" +
                        "[compressed-run vcn:8784-8799 [[data-run vcn:8784-8786 cluster:97431]]]\n" +
                        "[compressed-run vcn:8800-8815 [[data-run vcn:8800-8802 cluster:97447]]]\n" +
                        "[compressed-run vcn:8816-8831 [[data-run vcn:8816-8818 cluster:97511]]]\n" +
                        "[compressed-run vcn:8832-8847 [[data-run vcn:8832-8834 cluster:97575]]]\n" +
                        "[compressed-run vcn:8848-8863 [[data-run vcn:8848-8850 cluster:97591]]]\n" +
                        "[compressed-run vcn:8864-8879 [[data-run vcn:8864-8866 cluster:97655]]]\n" +
                        "[compressed-run vcn:8880-8895 [[data-run vcn:8880-8882 cluster:97719]]]\n" +
                        "[compressed-run vcn:8896-8911 [[data-run vcn:8896-8898 cluster:97735]]]\n" +
                        "[compressed-run vcn:8912-8927 [[data-run vcn:8912-8914 cluster:97815]]]\n" +
                        "[compressed-run vcn:8928-8943 [[data-run vcn:8928-8930 cluster:97831]]]\n" +
                        "[compressed-run vcn:8944-8959 [[data-run vcn:8944-8946 cluster:97895]]]\n" +
                        "[compressed-run vcn:8960-8975 [[data-run vcn:8960-8962 cluster:97959]]]\n" +
                        "[compressed-run vcn:8976-8991 [[data-run vcn:8976-8978 cluster:97975]]]\n" +
                        "[compressed-run vcn:8992-9007 [[data-run vcn:8992-8994 cluster:98039]]]\n" +
                        "[compressed-run vcn:9008-9023 [[data-run vcn:9008-9010 cluster:98103]]]\n" +
                        "[compressed-run vcn:9024-9039 [[data-run vcn:9024-9026 cluster:98119]]]\n" +
                        "[compressed-run vcn:9040-9055 [[data-run vcn:9040-9042 cluster:98183]]]\n" +
                        "[compressed-run vcn:9056-9071 [[data-run vcn:9056-9058 cluster:98247]]]\n" +
                        "[compressed-run vcn:9072-9087 [[data-run vcn:9072-9074 cluster:98263]]]\n" +
                        "[compressed-run vcn:9088-9103 [[data-run vcn:9088-9090 cluster:98343]]]\n" +
                        "[compressed-run vcn:9104-9119 [[data-run vcn:9104-9106 cluster:98359]]]\n" +
                        "[compressed-run vcn:9120-9135 [[data-run vcn:9120-9122 cluster:98423]]]\n" +
                        "[compressed-run vcn:9136-9151 [[data-run vcn:9136-9138 cluster:98487]]]\n" +
                        "[compressed-run vcn:9152-9167 [[data-run vcn:9152-9154 cluster:98503]]]\n" +
                        "[compressed-run vcn:9168-9183 [[data-run vcn:9168-9170 cluster:98567]]]\n" +
                        "[compressed-run vcn:9184-9199 [[data-run vcn:9184-9186 cluster:98631]]]\n" +
                        "[compressed-run vcn:9200-9215 [[data-run vcn:9200-9202 cluster:98647]]]\n" +
                        "[compressed-run vcn:9216-9231 [[data-run vcn:9216-9218 cluster:98711]]]\n" +
                        "[compressed-run vcn:9232-9247 [[data-run vcn:9232-9234 cluster:98775]]]\n" +
                        "[compressed-run vcn:9248-9263 [[data-run vcn:9248-9250 cluster:98791]]]\n" +
                        "[compressed-run vcn:9264-9279 [[data-run vcn:9264-9266 cluster:98871]]]\n" +
                        "[compressed-run vcn:9280-9295 [[data-run vcn:9280-9282 cluster:98887]]]\n" +
                        "[compressed-run vcn:9296-9311 [[data-run vcn:9296-9298 cluster:98951]]]\n" +
                        "[compressed-run vcn:9312-9327 [[data-run vcn:9312-9314 cluster:99015]]]\n" +
                        "[compressed-run vcn:9328-9343 [[data-run vcn:9328-9330 cluster:99031]]]\n" +
                        "[compressed-run vcn:9344-9359 [[data-run vcn:9344-9346 cluster:99095]]]\n" +
                        "[compressed-run vcn:9360-9375 [[data-run vcn:9360-9362 cluster:99159]]]\n" +
                        "[compressed-run vcn:9376-9391 [[data-run vcn:9376-9378 cluster:99162]]]\n" +
                        "[compressed-run vcn:9392-9407 [[data-run vcn:9392-9394 cluster:99165]]]\n" +
                        "[compressed-run vcn:9408-9423 [[data-run vcn:9408-9410 cluster:99168]]]\n" +
                        "[compressed-run vcn:9424-9439 [[data-run vcn:9424-9426 cluster:99171]]]\n" +
                        "[compressed-run vcn:9440-9455 [[data-run vcn:9440-9442 cluster:99174]]]\n" +
                        "[compressed-run vcn:9456-9471 [[data-run vcn:9456-9458 cluster:99177]]]\n" +
                        "[compressed-run vcn:9472-9487 [[data-run vcn:9472-9474 cluster:99180]]]\n" +
                        "[compressed-run vcn:9488-9503 [[data-run vcn:9488-9490 cluster:99183]]]\n" +
                        "[compressed-run vcn:9504-9519 [[data-run vcn:9504-9506 cluster:99186]]]\n" +
                        "[compressed-run vcn:9520-9535 [[data-run vcn:9520-9522 cluster:71930]]]\n" +
                        "[compressed-run vcn:9536-9551 [[data-run vcn:9536-9538 cluster:71933]]]\n" +
                        "[compressed-run vcn:9552-9567 [[data-run vcn:9552-9554 cluster:99317]]]\n" +
                        "[compressed-run vcn:9568-9583 [[data-run vcn:9568-9570 cluster:99320]]]\n" +
                        "[compressed-run vcn:9584-9599 [[data-run vcn:9584-9586 cluster:99323]]]\n" +
                        "[compressed-run vcn:9600-9615 [[data-run vcn:9600-9602 cluster:99326]]]\n" +
                        "[compressed-run vcn:9616-9631 [[data-run vcn:9616-9618 cluster:99329]]]\n" +
                        "[compressed-run vcn:9632-9647 [[data-run vcn:9632-9634 cluster:99332]]]\n" +
                        "[compressed-run vcn:9648-9663 [[data-run vcn:9648-9650 cluster:99399]]]\n" +
                        "[compressed-run vcn:9664-9679 [[data-run vcn:9664-9666 cluster:99402]]]\n" +
                        "[compressed-run vcn:9680-9695 [[data-run vcn:9680-9682 cluster:99405]]]\n" +
                        "[compressed-run vcn:9696-9711 [[data-run vcn:9696-9698 cluster:99408]]]\n" +
                        "[compressed-run vcn:9712-9727 [[data-run vcn:9712-9714 cluster:99411]]]\n" +
                        "[compressed-run vcn:9728-9743 [[data-run vcn:9728-9730 cluster:99414]]]\n" +
                        "[compressed-run vcn:9744-9759 [[data-run vcn:9744-9746 cluster:99417]]]\n" +
                        "[compressed-run vcn:9760-9775 [[data-run vcn:9760-9762 cluster:99420]]]\n" +
                        "[compressed-run vcn:9776-9791 [[data-run vcn:9776-9778 cluster:99423]]]\n" +
                        "[compressed-run vcn:9792-9807 [[data-run vcn:9792-9794 cluster:99426]]]\n" +
                        "[compressed-run vcn:9808-9823 [[data-run vcn:9808-9810 cluster:71994]]]\n" +
                        "[compressed-run vcn:9824-9839 [[data-run vcn:9824-9826 cluster:71997]]]\n" +
                        "[compressed-run vcn:9840-9855 [[data-run vcn:9840-9842 cluster:99557]]]\n" +
                        "[compressed-run vcn:9856-9871 [[data-run vcn:9856-9858 cluster:99560]]]\n" +
                        "[compressed-run vcn:9872-9887 [[data-run vcn:9872-9874 cluster:99563]]]\n" +
                        "[compressed-run vcn:9888-9903 [[data-run vcn:9888-9890 cluster:99566]]]\n" +
                        "[compressed-run vcn:9904-9919 [[data-run vcn:9904-9906 cluster:99569]]]\n" +
                        "[compressed-run vcn:9920-9935 [[data-run vcn:9920-9922 cluster:99572]]]\n" +
                        "[compressed-run vcn:9936-9951 [[data-run vcn:9936-9938 cluster:99623]]]\n" +
                        "[compressed-run vcn:9952-9967 [[data-run vcn:9952-9954 cluster:99626]]]\n" +
                        "[compressed-run vcn:9968-9983 [[data-run vcn:9968-9970 cluster:99629]]]\n" +
                        "[compressed-run vcn:9984-9999 [[data-run vcn:9984-9986 cluster:99632]]]\n" +
                        "[compressed-run vcn:10000-10015 [[data-run vcn:10000-10002 cluster:99635]]]\n" +
                        "[compressed-run vcn:10016-10031 [[data-run vcn:10016-10018 cluster:72074]]]\n" +
                        "[compressed-run vcn:10032-10047 [[data-run vcn:10032-10034 cluster:99702]]]\n" +
                        "[compressed-run vcn:10048-10063 [[data-run vcn:10048-10050 cluster:99705]]]\n" +
                        "[compressed-run vcn:10064-10079 [[data-run vcn:10064-10066 cluster:99708]]]\n" +
                        "[compressed-run vcn:10080-10095 [[data-run vcn:10080-10082 cluster:99711]]]\n" +
                        "[compressed-run vcn:10096-10111 [[data-run vcn:10096-10098 cluster:99714]]]\n" +
                        "[compressed-run vcn:10112-10127 [[data-run vcn:10112-10114 cluster:72077]]]\n" +
                        "[compressed-run vcn:10128-10143 [[data-run vcn:10128-10130 cluster:72138]]]\n" +
                        "[compressed-run vcn:10144-10159 [[data-run vcn:10144-10146 cluster:99845]]]\n" +
                        "[compressed-run vcn:10160-10175 [[data-run vcn:10160-10162 cluster:99848]]]\n" +
                        "[compressed-run vcn:10176-10191 [[data-run vcn:10176-10178 cluster:99851]]]\n" +
                        "[compressed-run vcn:10192-10207 [[data-run vcn:10192-10194 cluster:99854]]]\n" +
                        "[compressed-run vcn:10208-10223 [[data-run vcn:10208-10210 cluster:99857]]]\n" +
                        "[compressed-run vcn:10224-10239 [[data-run vcn:10224-10226 cluster:99860]]]\n" +
                        "[compressed-run vcn:10240-10255 [[data-run vcn:10240-10242 cluster:99927]]]\n" +
                        "[compressed-run vcn:10256-10271 [[data-run vcn:10256-10258 cluster:99930]]]\n";
        assertDataRuns(dataRuns, expectedRuns);
    }

    /**
     * Asserts the list of data runs is correct.
     *
     * @param dataRuns the data runs to check.
     * @param expected the expected list.
     */
    private void assertDataRuns(List<DataRunInterface> dataRuns, String expected) {
        StringBuilder builder = new StringBuilder();
        for (DataRunInterface dataRun : dataRuns) {
            builder.append(dataRun);
            builder.append('\n');
        }

        String actual = builder.toString();
        assertThat(actual, is(equalTo(expected)));
    }
}
