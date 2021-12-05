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

import java.util.ArrayList;
import java.util.List;

import net.sf.odinms.client.MapleBuffStat;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleStat;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.server.AutobanManager;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.Pair;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class TakeDamageHandler extends AbstractMaplePacketHandler {
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		// damage from map object
		// 26 00 EB F2 2B 01 FE 25 00 00 00 00 00
		// damage from monster
		// 26 00 0F 60 4C 00 FF 48 01 00 00 B5 89 5D 00 CC CC CC CC 00 00 00 00
		
		slea.readInt();
		int damagefrom = slea.readByte();
		int damage = slea.readInt();
		int oid = 0;
		int monsteridfrom = 0;
		if (damagefrom != -2) {
			monsteridfrom = slea.readInt();
			oid = slea.readInt();
		}

		MapleCharacter player = c.getPlayer();

		if (damage < 0 || damage > 60000) {
			AutobanManager.getInstance().addPoints(c, 1000, 60000, "Taking abnormal amounts of damge from " + monsteridfrom + ": " + damage);
			return;
		}
		player.getCheatTracker().checkTakeDamage();
		if (damage > 0 && !player.isHidden()) {
			player.getCheatTracker().setAttacksWithoutHit(0);
			if (damagefrom == -1 && damage > 0) {
				Integer pguard = player.getBuffedValue(MapleBuffStat.POWERGUARD);
				if (pguard != null) {
					// why do we have to do this? -.- the client shows the damage...
					MapleMonster attacker = (MapleMonster) player.getMap().getMapObject(oid);
					if (attacker != null && !attacker.isBoss()) {
						int bouncedamage = (int) (damage * (pguard.doubleValue() / 100));
						bouncedamage = Math.min(bouncedamage, attacker.getMaxHp() / 10);
						player.getMap().damageMonster(player, attacker, bouncedamage);
						damage -= bouncedamage;
						player.getMap().broadcastMessage(player, MaplePacketCreator.damageMonster(oid, bouncedamage), false, true);
					}
				}
			}
			Integer mguard = player.getBuffedValue(MapleBuffStat.MAGIC_GUARD);
			Integer mesoguard = player.getBuffedValue(MapleBuffStat.MESOGUARD);
			if (mguard != null) {
				List<Pair<MapleStat, Integer>> stats = new ArrayList<Pair<MapleStat, Integer>>(2);
				int mploss = (int) (damage * (mguard.doubleValue() / 100.0));
				int hploss = damage - mploss;
				if (mploss > player.getMp()) {
					hploss += mploss - player.getMp();
					mploss = player.getMp();
				}
				
				player.setHp(player.getHp() - hploss);
				player.setMp(player.getMp() - mploss);
				stats.add(new Pair<MapleStat, Integer>(MapleStat.HP, player.getHp()));
				stats.add(new Pair<MapleStat, Integer>(MapleStat.MP, player.getMp()));
				c.getSession().write(MaplePacketCreator.updatePlayerStats(stats));
			} else if(mesoguard != null) { 
				damage = (damage % 2 == 0) ? damage / 2 : (damage / 2) + 1;
				int mesoloss = (int) (damage * (mesoguard.doubleValue() / 100.0));
				if(player.getMeso() < mesoloss) {
					player.gainMeso(-player.getMeso(), false);
					player.cancelBuffStats(MapleBuffStat.MESOGUARD);
				} else {
					player.gainMeso(-mesoloss, false);
				}				
				player.addHP(-damage);
			} else {
				player.addHP(-damage);
			}
			player.getCheatTracker().resetHPRegen();
		}
		// player.getMap().broadcastMessage(null, MaplePacketCreator.damagePlayer(oid, 30000, damage));
		if (!player.isHidden()) {
			player.getMap().broadcastMessage(player,
				MaplePacketCreator.damagePlayer(damagefrom, monsteridfrom, player.getId(), damage), false);
		}
	}
}
