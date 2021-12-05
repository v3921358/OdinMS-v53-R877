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

import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.net.MaplePacketHandler;
import net.sf.odinms.net.login.LoginWorker;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class LoginPasswordHandler implements MaplePacketHandler {
	// private static Logger log = LoggerFactory.getLogger(LoginPasswordHandler.class);

	@Override
	public boolean validateState(MapleClient c) {
		return !c.isLoggedIn();
	}

	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		String login = slea.readAsciiString(slea.readShort());
		String pwd = slea.readAsciiString(slea.readShort());

		// log.info("Loginname: " + login);
		// log.info("Password: " + pwd);

		int loginok = 0;
		c.setAccountName(login);
		boolean ipBan = c.hasBannedIP();
		boolean macBan = c.hasBannedMac();
		loginok = c.login(login, pwd, ipBan || macBan);
		if (loginok == 0 && (ipBan || macBan)) {
			// only show ip bans for correct details
			// and of course...ban the account
			loginok = 3;
			// no can do this - ipban not because americans often get the same ip and we don't want to
			// ban multiple peoples accounts behind one router
			// macban not because I don't want to make banned users banning machines o.o
			
			// if (!(ipBan && macBan)) {
			// String reason = "Enforcing ";
			// if (ipBan) {
			// reason += "IP ban " + c.getSession().getRemoteAddress().toString();
			// } else {
			// reason += "MAC ban ";
			// for (String mac : c.getMacs())
			// reason += mac + ", ";
			// }
			// MapleCharacter.ban(login, reason, true);
			// }
			if (macBan) {
				// this is only an ipban o.O" - maybe we should refactor this a bit so it's more readable
				String[] ipSplit = c.getSession().getRemoteAddress().toString().split(":");
				MapleCharacter.ban(ipSplit[0], "Enforcing account ban, account " + login, false);
			}
		}
		// if (loginok == 3) {
		// // make sure everything is banned ;))
		// if (!ipBan || !macBan) {
		// String[] ipSplit = c.getSession().getRemoteAddress().toString().split(":");
		// MapleCharacter.ban(ipSplit[0], "Enforcing account ban, account " + login, false);
		// }
		// // c.banMacs();
		// }
		if (loginok != 0) {
			c.getSession().write(MaplePacketCreator.getLoginFailed(loginok));
		} else {
			if (c.isGm()) {
				LoginWorker.getInstance().registerGMClient(c);
			} else {
				LoginWorker.getInstance().registerClient(c);
			}
		}
	}
}
