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

package net.sf.odinms.tools.data.output;

import java.nio.charset.Charset;

public class GenericLittleEndianWriter implements LittleEndianWriter {
	private static Charset ASCII = Charset.forName("US-ASCII");
	private ByteOutputStream bos;

	protected GenericLittleEndianWriter() {
		// bla
	}

	protected void setByteOutputStream(ByteOutputStream bos) {
		this.bos = bos;
	}

	public GenericLittleEndianWriter(ByteOutputStream bos) {
		this.bos = bos;
	}

	@Override
	public void write(byte[] b) {
		for (int x = 0; x < b.length; x++) {
			bos.writeByte(b[x]);
		}
	}

	@Override
	public void write(byte b) {
		bos.writeByte(b);
	}

	@Override
	public void write(int b) {
		bos.writeByte((byte) b);
	}

	@Override
	public void writeShort(int i) {
		bos.writeByte((byte) (i & 0xFF));
		bos.writeByte((byte) ((i >>> 8) & 0xFF));
	}

	@Override
	public void writeInt(int i) {
		bos.writeByte((byte) (i & 0xFF));
		bos.writeByte((byte) ((i >>> 8) & 0xFF));
		bos.writeByte((byte) ((i >>> 16) & 0xFF));
		bos.writeByte((byte) ((i >>> 24) & 0xFF));
	}

	@Override
	public void writeAsciiString(String s) {
		write(s.getBytes(ASCII));
	}

	@Override
	public void writeMapleAsciiString(String s) {
		writeShort((short) s.length());
		writeAsciiString(s);
	}

	@Override
	public void writeNullTerminatedAsciiString(String s) {
		writeAsciiString(s);
		write(0);
	}
	
	@Override
	public void writeLong(long l) {
		bos.writeByte((byte) (l & 0xFF));
		bos.writeByte((byte) ((l >>> 8) & 0xFF));
		bos.writeByte((byte) ((l >>> 16) & 0xFF));
		bos.writeByte((byte) ((l >>> 24) & 0xFF));
		bos.writeByte((byte) ((l >>> 32) & 0xFF));
		bos.writeByte((byte) ((l >>> 40) & 0xFF));
		bos.writeByte((byte) ((l >>> 48) & 0xFF));
		bos.writeByte((byte) ((l >>> 56) & 0xFF));
	}
}
