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

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.scripting.NPCScriptManager;
import net.sf.odinms.server.life.MapleNPC;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class NPCTalkHandler extends AbstractMaplePacketHandler {
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {	
		int oid = slea.readInt();
		slea.readInt(); // dont know
		MapleNPC npc = (MapleNPC) c.getPlayer().getMap().getMapObject(oid);
		if (npc.hasShop()) {
			npc.sendShop(c);
		} else {
			NPCScriptManager.getInstance().start(c, npc.getId());
			// NPCMoreTalkHandler.npc = npc.getId();
			// 0 = next button
			// 1 = yes no
			// 2 = accept decline
			// 5 = select a link
			// c.getSession().write(MaplePacketCreator.getNPCTalk(npc.getId(), (byte) 0,
			// "Yo! I'm #p" + npc.getId() + "#, lulz! I can warp you lululululu.", "00 01"));
		}
		
	}
}
