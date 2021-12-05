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

package net.sf.odinms.net.login.handler;

import java.net.InetAddress;
import java.net.UnknownHostException;

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.net.login.LoginServer;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CharSelectedHandler extends AbstractMaplePacketHandler {
	private static Logger log = LoggerFactory.getLogger(CharSelectedHandler.class);

	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		//final String channelHost = "78.47.155.10"; //dual sailr
		//final String channelHost = "127.0.0.1";
		//final String channelHost = "209.160.33.9";
		//String channelHost = System.getProperty("net.sf.odinms.channelserver.host");
		int charId = slea.readInt();
		String macs = slea.readMapleAsciiString();
		c.updateMacs(macs);
		if (c.hasBannedMac()) {
			c.getSession().close();
			return;
		}
		try {
			if (c.getIdleTask() != null) {
				c.getIdleTask().cancel(true);
			}
			//c.getSession().write(MaplePacketCreator.getServerIP(InetAddress.getByName("127.0.0.1"), 7575, charId));
			c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION);
			String[] socket = LoginServer.getInstance().getIP(c.getChannel()).split(":");
			c.getSession().write(MaplePacketCreator.getServerIP(InetAddress.getByName(socket[0]), Integer.parseInt(socket[1]), charId));
		} catch (UnknownHostException e) {
			log.error("Host not found", e);
		}
	}
}
