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

import net.sf.odinms.client.ISkill;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleStat;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.server.AutobanManager;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributeSPHandler extends AbstractMaplePacketHandler {
	private static Logger log = LoggerFactory.getLogger(DistributeSPHandler.class);

	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		slea.readInt(); // something strange
		int skillid = slea.readInt();
		boolean isBegginnerSkill = false;

		MapleCharacter player = c.getPlayer();
		int remainingSp = player.getRemainingSp();
		if (skillid == 1000 || skillid == 1001 || skillid == 1002) { // boo beginner skill
			int snailsLevel = player.getSkillLevel(SkillFactory.getSkill(1000));
			int recoveryLevel = player.getSkillLevel(SkillFactory.getSkill(1001));
			int nimbleFeetLevel = player.getSkillLevel(SkillFactory.getSkill(1002));
			remainingSp = Math.min((player.getLevel() - 1), 6) - snailsLevel - recoveryLevel - nimbleFeetLevel;
			isBegginnerSkill = true;
		}
		ISkill skill = SkillFactory.getSkill(skillid);
		int maxlevel = skill.getMaxLevel();
		int curLevel = player.getSkillLevel(skill);
		if ((remainingSp > 0 && curLevel + 1 <= maxlevel) && skill.canBeLearnedBy(player.getJob())) {
			if (!isBegginnerSkill) {
				player.setRemainingSp(player.getRemainingSp() - 1);
			}
			player.updateSingleStat(MapleStat.AVAILABLESP, player.getRemainingSp());
			player.changeSkillLevel(skill, curLevel + 1, player.getMasterLevel(skill));
		} else if (!skill.canBeLearnedBy(player.getJob())) {
			AutobanManager.getInstance().addPoints(c, 1000, 0, "Trying to learn a skill for a different job (" + player.getJob().name() + ":" + skillid + ")");
		} else if (!(remainingSp > 0 && curLevel + 1 <= maxlevel)) {
			//AutobanManager.getInstance().addPoints(c, 334, 120000, "Trying to distribute SP to " + skillid + " without having any");
			log.info("[h4x] Player {} is distributing SP to {} without having any", player.getName(), Integer.valueOf(skillid));
		}
	}
}
