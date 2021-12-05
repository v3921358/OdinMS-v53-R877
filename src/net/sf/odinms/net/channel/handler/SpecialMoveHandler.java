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

import java.awt.Point;

import net.sf.odinms.client.ISkill;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.server.AutobanManager;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class SpecialMoveHandler extends AbstractMaplePacketHandler {
	// private static Logger log = LoggerFactory.getLogger(SpecialMoveHandler.class);
	
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		//first 2 bytes always semi randomly change
		slea.readByte();
		slea.readByte();
		@SuppressWarnings("unused")
		int unk = slea.readShort();
		@SuppressWarnings("unused")
		int skillid = slea.readInt();
		// seems to be skilllevel for movement skills and -32748 for buffs
		Point pos = null;
		@SuppressWarnings("unused")
		int __skillLevel = slea.readByte();
		if (slea.available() == 4) {
			pos = new Point(slea.readShort(), slea.readShort());
		}
		ISkill skill = SkillFactory.getSkill(skillid);
		int skillLevel = c.getPlayer().getSkillLevel(skill);
		
		if (skillLevel == 0) {
			AutobanManager.getInstance().addPoints(c.getPlayer().getClient(), 1000, 0, "Using a move skill he doesn't have (" + skill.getId() + ")");
		} else {
			if (c.getPlayer().isAlive()) {
				skill.getEffect(skillLevel).applyTo(c.getPlayer(), pos);
			} else {
				c.getSession().write(MaplePacketCreator.enableActions());
			}
		}
	}

}
