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

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputStreamByteStream implements ByteInputStream {
	private InputStream is;
	private long read = 0;
	private static Logger log = LoggerFactory.getLogger(InputStreamByteStream.class);
	
	public InputStreamByteStream(InputStream is)
	{
		this.is = is;
	}
	
	@Override
	public int readByte() {
		int temp;
		try {
			temp = is.read();
			if (temp == -1)
				throw new RuntimeException("EOF");
			read++;
			return temp;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public long getBytesRead() {
		return read;
	}

	@Override
	public long available() {
		try {
			return is.available();
		} catch (IOException e) {
			log.error("ERROR", e);
			return 0;
		}
	}
}
