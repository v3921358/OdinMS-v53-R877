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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.odinms.net.channel.handler;

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.scripting.NPCScriptManager;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

/**
 *
 * @author Matze
 */
public class NPCMoreTalkHandler extends AbstractMaplePacketHandler {

	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		byte lastMsg = slea.readByte(); // 00 (last msg type I think)
		byte action = slea.readByte(); // 00 = end chat, 01 == follow
		//if (action == 1) {
			byte selection = -1;
			if (slea.available() > 0)
				selection = slea.readByte();
			NPCScriptManager.getInstance().action(c, action, lastMsg, selection);
		//}
		/*System.out.println("moretalk action: " + action);
		if (talkStatus == 0) {
			c.getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 5, 
				"CHOOOOOSE! BOHAHAHAHAHAH ;))\r\n#L0##m60000##l\r\n#L1##m221000300##l"));
			talkStatus = 1;
		} else if (talkStatus == 1) {
			c.getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 2, 
				"Here is your debug info: " + slea.readByte()));
			talkStatus = 0;
		}*/
	}

}
