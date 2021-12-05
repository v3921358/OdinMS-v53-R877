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
import net.sf.odinms.client.MapleBuffStat;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleStat;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.server.MapleStatEffect;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class CloseRangeDamageHandler extends AbstractDealDamageHandler {
	private boolean isFinisher(int skillId) {
		return skillId >= 1111003 && skillId <= 1111006;
	}
	
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		// attack 15 monsters *_*
		// 23 00 03 F1 29 4F 4C 00' 00 AA 01 04' 81 1C 5D 00' 04 00 00 00' 06 80 01 01' F7 02 D7 00' FA 02 D7 00' 6A 03'
		// 22 14 00 00 0A 00 00 00 06 00 00 01 FB 02 D7 00 F8 02 D7 00 B0 03 A4 13 00 00 0C 00 00 00 06 80 04 01 C4 02
		// AB FF C6 02 AB FF F6 03 10 14 00 00 0F 00 00 00 06 80 01 01 B7 02 AB FF B9 02 AB FF 3C 04 29 14 00 00 00 00
		// 00 00 06 80 00 01 B4 02 D7 00 B6 02 D7 00 82 04 1B 14 00 00 02 00 00 00 06 80 00 01 7E 02 D7 00 80 02 D7 00
		// 82 04 15 14 00 00 01 00 00 00 06 80 02 01 6F 02 D7 00 71 02 D7 00 82 04 BE 14 00 00 0D 00 00 00 06 80 00 01
		// 69 02 AB FF 6B 02 AB FF 82 04 E4 13 00 00 05 00 00 00 06 80 02 01 5E 02 D7 00 60 02 D7 00 82 04 B5 13 00 00
		// 09 00 00 00 06 80 03 01 50 02 D7 00 52 02 D7 00 82 04 05 14 00 00 08 00 00 00 06 80 03 01 3D 02 D7 00 3F 02
		// D7 00 82 04 61 14 00 00 06 00 00 00 06 80 02 01 3C 02 D7 00 3E 02 D7 00 82 04 90 14 00 00 03 00 00 00 06 80
		// 00 01 42 02 D7 00 44 02 D7 00 82 04 E0 13 00 00 0B 00 00 00 06 00 03 01 37 02 D7 00 33 02 D7 00 82 04 7B 14
		// 00 00 07 00 00 00 06 00 01 01 21 02 D7 00 20 02 D7 00 82 04 DC 13 00 00 9C 02 27 00
		// attack CC CC CC CC mm
		// 23 00 03 11 00 00 00 00' 00 06 01 04' 91 EA 4E 00' CC CC CC CC' 06 80 04 01' B3 FD D7 00' B7 FD D7 00' 89 01'
		// 01 00 00 00 38 FD D7 00
		// attack one monster again
		// 23 00 03 11 00 00 00 00' 00 05 01 04' 41 AE 65 00' 24 00 00 00' 06 81 00 01' 04 00 BB FE' 04 00 BB FE' 89 01'
		// 0F 02 00 00 B5 FF 9C FE
		// attack air
		// 23 00 03 01 00 00 00 00' 00 90 01 04' DB 82 A9 00' FB FC D7 00
		// attack air again
		// 23 00 03 01 00 00 00 00' 00 05 01 04' E9 0B 60 00' 42 02 AB FF
		// 26 00 01 1F 3E 41 40 00' 00 B8 01 04' AD 86 EF' 11 01 CF 5F 00 06 80 00 03 56 FE 3E 00 54 FE 3E 00 0F 3C 01 00 00 0E 01 00 00 F3 00 00 00 4A 01 00 00 4E 01 00 00 D6 00 00 00 0C 01 00 00 3C 01 00 00 0E 01 00 00 F3 00 00 00 4A 01 00 00 4E 01 00 00 D6 00 00 00 0C 01 00 00 3C 01 00 00 02 FF 3E 00 13 5A 21 00 00 01 5B 21 00 00 01 5C 21 00 00 01 5D 21 00 00 01 5E 21 00 00 01 5F 21 00 00 01 60 21 00 00 01 61 21 00 00 01 62 21 00 00 01 63 21 00 00 01 64 21 00 00 01 65 21 00 00 01 66 21 00 00 01 67 21 00 00 01 68 21 00 00 01 69 21 00 00 00 6A 21 00 00 00 6B 21 00 00 00 6C 21 00 00 00 63 02
		// 26 00 01 00 3E 41 40 00' 00 B8 01 04' D9 47 E4' 11 2B FF 3E 00 14 C4 1F 00 00 00 B2 1F 00 00 00 BA 1F 00 00 00 B9 1F 00 00 00 C6 1F 00 00 00 BD 1F 00 00 00 BF 1F 00 00 00 C5 1F 00 00 00 B4 1F 00 00 00 BC 1F 00 00 00 B1 1F 00 00 00 BB 1F 00 00 00 B7 1F 00 00 00 C3 1F 00 00 00 C2 1F 00 00 00 B6 1F 00 00 00 BE 1F 00 00 00 B5 1F 00 00 00 B8 1F 00 00 00 C1 1F 00 00 00 63 02
		// seems to contain a list of things that get damaged <3
		
		AttackInfo attack = parseDamage(slea, false);
		MapleCharacter player = c.getPlayer();
		
		MaplePacket packet = MaplePacketCreator.closeRangeAttack(player.getId(), attack.skill, attack.stance,
			attack.numAttackedAndDamage, attack.allDamage);
		player.getMap().broadcastMessage(player, packet, false, true);
		// MaplePacket packet = MaplePacketCreator.closeRangeAttack(30000, attack.skill, attack.stance,
		// attack.numAttackedAndDamage, attack.allDamage);
		// player.getMap().broadcastMessage(player, packet, true);

		// handle combo orbconsume
		int numFinisherOrbs = 0;
		Integer comboBuff = player.getBuffedValue(MapleBuffStat.COMBO);
		if (isFinisher(attack.skill)) {
			if (comboBuff != null) {
				numFinisherOrbs = comboBuff.intValue() - 1; 
			}
			player.handleOrbconsume();
		} else if (attack.numAttacked > 0 && comboBuff != null) {
			// handle combo orbgain
			if (attack.skill != 1111008) { // shout should not give orbs
				player.handleOrbgain();
			}
		}

		// handle sacrifice hp loss
		if(attack.numAttacked > 0 && attack.skill == 1311005) {
		    int totDamageToOneMonster = attack.allDamage.get(0).getRight().get(0).intValue(); // sacrifice attacks only 1 mob with 1 attack
		    player.setHp(player.getHp() - totDamageToOneMonster * attack.getAttackEffect(player).getX() / 100);
		    player.updateSingleStat(MapleStat.HP, player.getHp());
		}
		
		// handle charged blow
		if(attack.numAttacked > 0 && attack.skill == 1211002 /*&& player.getBuffedValue(MapleBuffStat.ENDLESSCHARGE) != null*/) {
			player.cancelEffectFromBuffStat(MapleBuffStat.WK_CHARGE);
		}
		
		int maxdamage = c.getPlayer().getCurrentMaxBaseDamage();
		int attackCount = 1;
		if (attack.skill != 0) {
			MapleStatEffect effect = attack.getAttackEffect(c.getPlayer());
			attackCount = effect.getAttackCount();
			maxdamage *= effect.getDamage() / 100.0;
			maxdamage *= attackCount;
		}
		maxdamage = Math.min(maxdamage, 99999);
		if (attack.skill == 4211006) {
			maxdamage = 700000;
		} else if (numFinisherOrbs > 0) {
			maxdamage *= numFinisherOrbs;
		} else if (comboBuff != null) {
			ISkill combo = SkillFactory.getSkill(1111002);
			int comboLevel = player.getSkillLevel(combo);
			MapleStatEffect comboEffect = combo.getEffect(comboLevel);
			double comboMod = 1.0 + (comboEffect.getDamage() / 100.0 - 1.0) * (comboBuff.intValue() - 1);
			maxdamage *= comboMod;
		}
		if (numFinisherOrbs == 0 && isFinisher(attack.skill)) {
			return; // can only happen when lagging o.o
		}
		if (isFinisher(attack.skill)) {
			maxdamage = 99999; // FIXME reenable damage calculation for finishers
		}
		applyAttack(attack, player, maxdamage, attackCount);
	}
}
