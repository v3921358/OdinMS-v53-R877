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

package net.sf.odinms.tools.data.input;

import java.io.ByteArrayOutputStream;

public class GenericLittleEndianAccessor implements LittleEndianAccessor {
	private ByteInputStream bs;

	public GenericLittleEndianAccessor(ByteInputStream bs) {
		this.bs = bs;
	}

	@Override
	public byte readByte() {
		return (byte) bs.readByte();
	}

	@Override
	public int readInt() {
		int byte1, byte2, byte3, byte4;

		byte1 = bs.readByte();
		byte2 = bs.readByte();
		byte3 = bs.readByte();
		byte4 = bs.readByte();
		return (byte4 << 24) + (byte3 << 16) + (byte2 << 8) + byte1;
	}

	@Override
	public short readShort() {
		int byte1, byte2;
		byte1 = bs.readByte();
		byte2 = bs.readByte();
		return (short) ((byte2 << 8) + byte1);
	}

	@Override
	public char readChar() {
		return (char) readShort();
	}

	@Override
	public long readLong() {
		long byte1 = bs.readByte();
		long byte2 = bs.readByte();
		long byte3 = bs.readByte();
		long byte4 = bs.readByte();
		long byte5 = bs.readByte();
		long byte6 = bs.readByte();
		long byte7 = bs.readByte();
		long byte8 = bs.readByte();

		return (byte8 << 56) + (byte7 << 48) + (byte6 << 40) + (byte5 << 32) + (byte4 << 24) + (byte3 << 16) +
			(byte2 << 8) + byte1;
	}

	@Override
	public float readFloat() {
		return Float.intBitsToFloat(readInt());
	}

	@Override
	public double readDouble() {
		return Double.longBitsToDouble(readLong());
	}

	public final String readAsciiString(int n) {
		char ret[] = new char[n];
		for (int x = 0; x < n; x++) {
			ret[x] = (char) readByte();
		}
		return String.valueOf(ret);
	}

	public final String readNullTerminatedAsciiString() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte b = 1;
		while (b != 0) {
			b = readByte();
			baos.write(b);
		}
		byte[] buf = baos.toByteArray();
		char[] chrBuf = new char[buf.length];
		for (int x = 0; x < buf.length; x++) {
			chrBuf[x] = (char) buf[x];
		}
		return String.valueOf(chrBuf);
	}

	public long getBytesRead() {
		return bs.getBytesRead();
	}

	@Override
	public String readMapleAsciiString() {
		return readAsciiString(readShort());
	}

	@Override
	public byte[] read(int num) {
		byte[] ret = new byte[num];
		for (int x = 0; x < num; x++) {
			ret[x] = readByte();
		}
		return ret;
	}

	@Override
	public void skip(int num) {
		for (int x = 0; x < num; x++) {
			readByte();
		}
	}

	@Override
	public long available() {
		return bs.available();
	}

	@Override
	public String toString() {
		return bs.toString();
	}
}
