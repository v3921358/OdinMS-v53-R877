/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc> 
					   Matthias Butz <matze@odinms.de>
					   Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package net.sf.odinms.tools;

public class BitTools {
	private BitTools() {

	}

	public static int getShort(byte array[], int index) {
		int ret = array[index];
		ret &= 0xFF;
		ret |= ((int) (array[index + 1]) << 8) & 0xFF00;
		return ret;
	}

	public static String getString(byte array[], int index, int length) {
		char[] cret = new char[length];
		for (int x = 0; x < length; x++) {
			cret[x] = (char) array[x + index];
		}
		return String.valueOf(cret);
	}
	
	public static String getMapleString(byte array[], int index) {
		int length = ((int)(array[index]) & 0xFF) | ((int)(array[index+1] << 8) & 0xFF00); 
		return BitTools.getString(array, index+2, length);
	}

	public static byte rollLeft(byte in, int count) {
		/*
		 * in: 11001101 count: 3 out: 0110 1110
		 */
		int tmp = (int) in & 0xFF;
		;
		tmp = tmp << (count % 8);
		return (byte) ((tmp & 0xFF) | (tmp >> 8));
	}

	public static byte rollRight(byte in, int count) {
		/*
		 * in: 11001101 count: 3 out: 1011 10011
		 * 
		 * 0000 1011 1011 0000 0101 1000
		 * 
		 */
		int tmp = (int) in & 0xFF;
		tmp = (tmp << 8) >>> (count % 8);

		return (byte) ((tmp & 0xFF) | (tmp >>> 8));
	}

	public static byte[] multiplyBytes(byte[] in, int count, int mul) {
		byte[] ret = new byte[count * mul];
		for (int x = 0; x < count * mul; x++) {
			ret[x] = in[x % count];
		}
		return ret;
	}
	
	public static int doubleToShortBits(double d) {
		long l = Double.doubleToLongBits(d);
		return (int) (l >> 48);
	}
}
