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

import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.messages.CommandProcessor;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class PartychatHandler extends AbstractMaplePacketHandler {
	// private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PartychatHandler.class);

	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		int type = slea.readByte(); // 0 for buddys, 1 for partys
		int numRecipients = slea.readByte();
		int recipients[] = new int[numRecipients];
		for (int i = 0; i < numRecipients; i++) {
			recipients[i] = slea.readInt();
		}
		String chattext = slea.readMapleAsciiString();
		if (!CommandProcessor.processCommand(c, chattext)) {
			MapleCharacter player = c.getPlayer();
			try {
				if (type == 0) {
					c.getChannelServer().getWorldInterface().buddyChat(recipients, player.getId(), player.getName(), chattext);
				} else if (type == 1 && player.getParty() != null) {
					c.getChannelServer().getWorldInterface().partyChat(player.getParty().getId(), chattext, player.getName());
				}
			} catch (RemoteException e) {
				c.getChannelServer().reconnectWorld();
			}
		}
	}
}
