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

import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.anticheat.CheatingOffense;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.server.MaplePortal;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeMapHandler extends AbstractMaplePacketHandler {
	private static Logger log = LoggerFactory.getLogger(ChangeMapHandler.class);

	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		@SuppressWarnings("unused")
		byte something = slea.readByte(); //?
		int targetid = slea.readInt(); //FF FF FF FF

		String startwp = slea.readMapleAsciiString();
		MaplePortal portal = c.getPlayer().getMap().getPortal(startwp);
		
		MapleCharacter player = c.getPlayer();
		if (targetid != -1 && !c.getPlayer().isAlive()) {
			player.setHp(50);
			MapleMap to = c.getPlayer().getMap().getReturnMap();
			MaplePortal pto = to.getPortal(0);
			player.setStance(0);
			player.changeMap(to, pto);
		} else if (targetid != -1 && c.getPlayer().isGM()) {
			MapleMap to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(targetid);
			MaplePortal pto = to.getPortal(0);
			player.changeMap(to, pto);
		} else if (targetid != -1 && !c.getPlayer().isGM()) {
			log.warn("Player {} attempted Mapjumping without being a gm", c.getPlayer().getName());
		} else {
			if (portal != null) {
				double distanceSq = portal.getPosition().distanceSq(player.getPosition());
				if (distanceSq > 22500) {
					player.getCheatTracker().registerOffense(CheatingOffense.USING_FARAWAY_PORTAL, "D" + Math.sqrt(distanceSq));
				}
				MapleMap to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(portal.getTargetMapId());
				MaplePortal pto = to.getPortal(portal.getTarget());
				if (pto == null) { // fallback for missing portals - only case showa street 3
					pto = to.getPortal(0);
				}
				c.getPlayer().changeMap(to, pto); //late resolving makes this harder but prevents us from loading the whole world at once
			} else {
				log.warn("Portal {} not found on map {}", startwp, c.getPlayer().getMap().getId());
			}
		}
	}

}
