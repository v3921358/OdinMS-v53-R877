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
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.server.MaplePortal;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.server.maps.SavedLocationType;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class ChangeMapSpecialHandler extends AbstractMaplePacketHandler {
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		slea.readByte();
		String startwp = slea.readMapleAsciiString();
		slea.readByte();
		byte sourcefm = slea.readByte();
		slea.readByte();

		MaplePortal portal = c.getPlayer().getMap().getPortal(startwp);
		if (portal.getType() == 7) {
			MapleMap to;
			MaplePortal pto;
			if (sourcefm != -1) {
				to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(910000000);
				c.getPlayer().saveLocation(SavedLocationType.FREE_MARKET);
				pto = to.getPortal("out00"); // or st00?
			} else {
				int returnMap = c.getPlayer().getSavedLocation(SavedLocationType.FREE_MARKET);
				if (returnMap < 0) {
					returnMap = 102000000; // oh well..., to fix people stuck in fm
				}
				to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(returnMap);
				c.getPlayer().clearSavedLocation(SavedLocationType.FREE_MARKET);
				pto = to.getPortal("market00");
				if(pto == null) {
				    pto = to.getPortal(0);
				}
			}
			c.getPlayer().changeMap(to, pto);
		}
		c.getPlayer().updateStatsEmpty(); // avoid clientlock
	}
}
