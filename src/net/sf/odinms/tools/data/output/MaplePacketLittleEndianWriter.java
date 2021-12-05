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

import java.io.ByteArrayOutputStream;

import net.sf.odinms.net.ByteArrayMaplePacket;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.tools.HexTool;

public class MaplePacketLittleEndianWriter extends GenericLittleEndianWriter {
	private ByteArrayOutputStream baos;

	public MaplePacketLittleEndianWriter() {
		this(32);
	}

	public MaplePacketLittleEndianWriter(int size) {
		this.baos = new ByteArrayOutputStream(size);
		setByteOutputStream(new BAOSByteOutputStream(baos));
	}
	
	public MaplePacket getPacket() {
		return new ByteArrayMaplePacket(baos.toByteArray());
	}
	
	public String toString() {
		return HexTool.toString(baos.toByteArray());
	}
}
