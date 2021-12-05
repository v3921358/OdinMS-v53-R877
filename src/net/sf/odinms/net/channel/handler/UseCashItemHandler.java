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

package net.sf.odinms.net.channel.handler;

import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.server.MapleInventoryManipulator;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class UseCashItemHandler extends AbstractMaplePacketHandler {
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		@SuppressWarnings("unused")
		byte mode = slea.readByte();
		slea.readByte();
		int itemId = slea.readInt();
		MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
		try {
			if (itemId == 5072000) {
				c.getChannelServer().getWorldInterface().broadcastMessage(
					null,
					MaplePacketCreator.serverNotice(3, c.getChannel(),
						c.getPlayer().getName() + " : " + slea.readMapleAsciiString()).getBytes());
			} else if (itemId >= 5390000 && itemId <= 5390002) {
				List<String> lines = new LinkedList<String>();
				for (int i = 0; i < 4; i++) {
					lines.add(slea.readMapleAsciiString());
				}
				c.getChannelServer().getWorldInterface().broadcastMessage(null,
					MaplePacketCreator.getAvatarMega(c.getPlayer(), c.getChannel(), itemId, lines).getBytes());
			}
		} catch (RemoteException e) {
			c.getChannelServer().reconnectWorld();
		}
	}
}
