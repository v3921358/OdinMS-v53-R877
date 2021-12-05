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

import org.apache.mina.common.ByteBuffer;

public class ByteBufferLittleEndianWriter extends GenericLittleEndianWriter {
	private ByteBuffer bb;

	public ByteBufferLittleEndianWriter() {
		this(50, true);
	}

	public ByteBufferLittleEndianWriter(int size) {
		this(size, false);
	}

	public ByteBufferLittleEndianWriter(int initialSize, boolean autoExpand) {
		bb = ByteBuffer.allocate(initialSize);
		bb.setAutoExpand(autoExpand);
		setByteOutputStream(new ByteBufferOutputstream(bb));
	}

	public ByteBuffer getFlippedBB() {
		return bb.flip();
	}

	public ByteBuffer getByteBuffer() {
		return bb;
	}
}
