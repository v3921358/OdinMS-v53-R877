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

package net.sf.odinms.provider.wz;

import net.sf.odinms.provider.MapleCanvas;

public class PNGMapleCanvas implements MapleCanvas {
	private int height;
	private int width;
	private int dataLength;
	private int dataOffset;
	private int format;
	
	private byte[] data;

	public PNGMapleCanvas(int width, int height, int dataLength, int dataOffset, int format, byte[] data) {
		super();
		this.height = height;
		this.width = width;
		this.dataLength = dataLength;
		this.dataOffset = dataOffset;
		this.format = format;
		this.data = data;
	}

	public int getHeight() {
		return height;
	}

	public int getWidth() {
		return width;
	}

	public int getDataLength() {
		return dataLength;
	}

	public int getDataOffset() {
		return dataOffset;
	}

	public int getFormat() {
		return format;
	}
	
	public byte[] getData()
	{
		return data;
	}
}
