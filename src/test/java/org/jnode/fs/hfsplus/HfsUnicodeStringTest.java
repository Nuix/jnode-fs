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

package org.jnode.fs.hfsplus;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class HfsUnicodeStringTest
{
    private final byte[] STRING_AS_BYTES_ARRAY =
        new byte[] { 0, 8, 0, 116, 0, 101, 0, 115, 0, 116, 0, 46, 0, 116, 0, 120, 0, 116 };
    private final String STRING_AS_TEXT = "test.txt";

    @Test
    public void testConstructAsBytesArray()
    {
        HfsUnicodeString string = new HfsUnicodeString(STRING_AS_BYTES_ARRAY, 0);
        assertThat(string.getLength(), is(8));
        assertThat(string.getUnicodeString(), is(STRING_AS_TEXT));
    }

    @Test
    public void testConstructAsString()
    {
        HfsUnicodeString string = new HfsUnicodeString(STRING_AS_TEXT);
        assertThat(string.getLength(), is(8));
        byte[] array = string.getBytes();
        int index = 0;
        for (byte b : array)
        {
            assertThat(b, is(STRING_AS_BYTES_ARRAY[index]));
            index++;
        }
    }

    @Test
    public void testEquals()
    {
        HfsUnicodeString string1 = new HfsUnicodeString(STRING_AS_TEXT);
        HfsUnicodeString string2 = new HfsUnicodeString(STRING_AS_TEXT);
        HfsUnicodeString string3 = new HfsUnicodeString(null);
        HfsUnicodeString string4 = new HfsUnicodeString(null);

        assertThat(string1, is(equalTo(string2)));
        assertThat(string3, is(equalTo(string4)));
        assertThat(string1, is(not(equalTo(string3))));
        assertThat(string4, is(not(equalTo(string2))));
    }

    @Test
    public void testCompareTo()
    {
        HfsUnicodeString nullStr = new HfsUnicodeString(null);
        HfsUnicodeString emptyStr = new HfsUnicodeString("");
        HfsUnicodeString string1 = new HfsUnicodeString("test");
        HfsUnicodeString string2 = new HfsUnicodeString("test");
        HfsUnicodeString longerStr = new HfsUnicodeString("testzzz");

        assertThat(nullStr.compareTo(emptyStr), is(-1));
        assertThat(emptyStr.compareTo(nullStr), is(1));

        assertThat(string1.compareTo(string2), is(0));
        assertThat(string1.compareTo(longerStr), is(lessThan(0)));
        assertThat(longerStr.compareTo(string1), is(greaterThan(0)));

        assertThat(string1.compareTo(nullStr), is(1));
        assertThat(nullStr.compareTo(string1), is(-1));
    }
}
