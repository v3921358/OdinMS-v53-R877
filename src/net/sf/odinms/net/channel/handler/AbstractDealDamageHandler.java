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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.odinms.client.ISkill;
import net.sf.odinms.client.MapleBuffStat;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleJob;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.client.anticheat.CheatingOffense;
import net.sf.odinms.client.status.MonsterStatus;
import net.sf.odinms.client.status.MonsterStatusEffect;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.server.AutobanManager;
import net.sf.odinms.server.MapleStatEffect;
import net.sf.odinms.server.TimerManager;
import net.sf.odinms.server.life.Element;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.server.maps.MapleMapItem;
import net.sf.odinms.server.maps.MapleMapObject;
import net.sf.odinms.server.maps.MapleMapObjectType;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.Pair;
import net.sf.odinms.tools.data.input.LittleEndianAccessor;

public abstract class AbstractDealDamageHandler extends AbstractMaplePacketHandler {
	// private static Logger log = LoggerFactory.getLogger(AbstractDealDamageHandler.class);

	protected static class AttackInfo {
		public int numAttacked, numDamage, numAttackedAndDamage;
		public int skill, stance;
		public List<Pair<Integer, List<Integer>>> allDamage;

		private MapleStatEffect getAttackEffect(MapleCharacter chr, ISkill theSkill) {
			ISkill mySkill = theSkill;
			if (mySkill == null) {
				mySkill = SkillFactory.getSkill(skill);
			}
			int skillLevel = chr.getSkillLevel(mySkill);
			if (skillLevel == 0) {
				return null;
			}
			return mySkill.getEffect(skillLevel);
		}
		
		public MapleStatEffect getAttackEffect(MapleCharacter chr) {
			return getAttackEffect(chr, null);
		}
	}

	protected void applyAttack(AttackInfo attack, MapleCharacter player, int maxDamagePerMonster, int attackCount) {
		player.getCheatTracker().resetHPRegen();
		player.getCheatTracker().checkAttack();
		
		ISkill theSkill = null;
		MapleStatEffect attackEffect = null;
		if (attack.skill != 0) {
			theSkill = SkillFactory.getSkill(attack.skill);
			attackEffect = attack.getAttackEffect(player, theSkill);
			if (attackEffect == null) {
				AutobanManager.getInstance().autoban(player.getClient(),
					"Using a skill he doesn't have (" + attack.skill + ")");
			}
			if (attack.skill != 2301002) {
				// heal is both an attack and a special move (healing)
				// so we'll let the whole applying magic live in the special move part
				if (player.isAlive()) {
					attackEffect.applyTo(player);
				} else {
					player.getClient().getSession().write(MaplePacketCreator.enableActions());
				}
			}
		}
		if (!player.isAlive()) {
			player.getCheatTracker().registerOffense(CheatingOffense.ATTACKING_WHILE_DEAD);
			return;
		}
		// meso explosion has a variable bullet count
		if (attackCount != attack.numDamage && attack.skill != 4211006) {
			player.getCheatTracker().registerOffense(CheatingOffense.MISMATCHING_BULLETCOUNT,
				attack.numDamage + "/" + attackCount);
		}
		int totDamage = 0;
		MapleMap map = player.getMap();
		for (Pair<Integer, List<Integer>> oned : attack.allDamage) {
			MapleMonster monster = map.getMonsterByOid(oned.getLeft().intValue());

			if (monster == null) {
				MapleMapObject mapobject = (MapleMapObject) map.getMapObject(oned.getLeft().intValue());
				if (mapobject != null && mapobject.getType() == MapleMapObjectType.ITEM) {
					MapleMapItem mapitem = (MapleMapItem) mapobject;
					if (mapitem.getMeso() > 0) {
						synchronized (mapitem) {
							if (mapitem.isPickedUp())
								return;
							map.removeMapObject(mapitem);
							map.broadcastMessage(MaplePacketCreator.removeItemFromMap(mapitem.getObjectId(), 4, 0), mapitem.getPosition());
							mapitem.setPickedUp(true);
						}
					} else if (mapitem.getMeso() == 0) {
						player.getCheatTracker().registerOffense(CheatingOffense.ETC_EXPLOSION);
					}
				}
			} else {
				int totDamageToOneMonster = 0;
				for (Integer eachd : oned.getRight()) {
					totDamageToOneMonster += eachd.intValue();
				}
				totDamage += totDamageToOneMonster;

				Point playerPos = player.getPosition();
				if (monster != null) {
					if (totDamageToOneMonster > attack.numDamage + 1) {
						int dmgCheck = player.getCheatTracker().checkDamage(totDamageToOneMonster);
						if (dmgCheck > 5) {
							player.getCheatTracker().registerOffense(CheatingOffense.SAME_DAMAGE,
								dmgCheck + " times: " + totDamageToOneMonster);
						}
					}
					checkHighDamage(player, monster, attack, theSkill, attackEffect, totDamageToOneMonster, maxDamagePerMonster);
					double distance = playerPos.distanceSq(monster.getPosition());
					if (distance > 360000.0) { // 600^2, 550 is approximatly the range of ultis
						player.getCheatTracker().registerOffense(CheatingOffense.ATTACK_FARAWAY_MONSTER,
							Double.toString(Math.sqrt(distance)));
						// if (distance > 1000000.0)
						// AutobanManager.getInstance().addPoints(player.getClient(), 50, 120000, "Exceeding attack
						// range");
					}
					if (!monster.isControllerHasAggro()) {
						if (monster.getController() == player) {
							monster.setControllerHasAggro(true);
						} else {
							monster.switchController(player, true);
						}
					}
					// only ds, sb, assaulter, normal (does it work for thieves, bs, or assasinate?)
					if ((attack.skill == 4001334 || attack.skill == 4201005 || attack.skill == 0 || attack.skill == 4211002) &&
						player.getBuffedValue(MapleBuffStat.PICKPOCKET) != null) {
						handlePickPocket(player, monster, oned);
					}
					if(attack.skill == 4101005) { // drain
						ISkill drain = SkillFactory.getSkill(4101005);
						int gainhp = (int)((double)totDamageToOneMonster * (double)drain.getEffect(player.getSkillLevel(drain)).getX() / 100.0);
						gainhp = Math.min(monster.getMaxHp(), Math.min(gainhp, player.getMaxHp() / 2));
						player.addHP(gainhp);
					}

					if (player.getJob().isA(MapleJob.WHITEKNIGHT)) {
						int[] charges = new int[] {1211005, 1211006};
						for (int charge : charges) {
							ISkill chargeSkill = SkillFactory.getSkill(charge);
							if (player.isBuffFrom(MapleBuffStat.WK_CHARGE, chargeSkill)) {
								MapleStatEffect chargeEffect = chargeSkill.getEffect(player.getSkillLevel(chargeSkill));
								MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.FREEZE, 1), chargeSkill, false);
								monster.applyStatus(player, monsterStatusEffect, false, chargeEffect.getY() * 2000);
								break;
							}
						}
					}
					
					if (totDamageToOneMonster > 0 && attackEffect != null && attackEffect.getMonsterStati().size() > 0) {
						if (attackEffect.makeChanceResult()) {
							MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(attackEffect.getMonsterStati(), theSkill, false);
							monster.applyStatus(player, monsterStatusEffect, attackEffect.isPoison(), attackEffect.getDuration());
						}
					}
					map.damageMonster(player, monster, totDamageToOneMonster);
				}
			}
		}
		if (totDamage > 1) {
			player.getCheatTracker().setAttacksWithoutHit(player.getCheatTracker().getAttacksWithoutHit() + 1);
			if (player.getCheatTracker().getAttacksWithoutHit() > 100) {
				player.getCheatTracker().registerOffense(CheatingOffense.ATTACK_WITHOUT_GETTING_HIT,
					Integer.toString(player.getCheatTracker().getAttacksWithoutHit()));
			}
		}
	}

	private void handlePickPocket(MapleCharacter player, MapleMonster monster, Pair<Integer, List<Integer>> oned) {
		ISkill pickpocket = SkillFactory.getSkill(4211003);
		int delay = 0;
		int maxmeso = player.getBuffedValue(MapleBuffStat.PICKPOCKET).intValue();
		int reqdamage = 20000;
		Point monsterPosition = monster.getPosition();
		
		for (Integer eachd : oned.getRight()) {
			if (pickpocket.getEffect(player.getSkillLevel(pickpocket)).makeChanceResult()) {
				double perc = (double) eachd / (double) reqdamage;

				final int todrop = Math.min((int) Math.max(perc * (double) maxmeso, (double) 1),
					maxmeso);
				final MapleMap tdmap = player.getMap();
				final Point tdpos = new Point((int) (monsterPosition.getX() + (Math.random() * 100) - 50),
											  (int) (monsterPosition.getY()));
				final MapleMonster tdmob = monster;
				final MapleCharacter tdchar = player;

				TimerManager.getInstance().schedule(new Runnable() {
					public void run() {
						tdmap.spawnMesoDrop(todrop, todrop, tdpos, tdmob, tdchar, false);
					}
				}, delay);

				delay += 200;
			}
		}
	}

	private void checkHighDamage(MapleCharacter player, MapleMonster monster, AttackInfo attack, ISkill theSkill,
								MapleStatEffect attackEffect, int damageToMonster, int maximumDamageToMonster) {
		int elementalMaxDamagePerMonster;
		Element element = Element.NEUTRAL;
		if (theSkill != null) {
			element = theSkill.getElement();
		}
		if (player.getBuffedValue(MapleBuffStat.WK_CHARGE) != null) {
			int chargeSkillId = player.getBuffSource(MapleBuffStat.WK_CHARGE);
			switch (chargeSkillId) {
				case 1211003:
				case 1211004:
					element = Element.FIRE;
					break;
				case 1211005:
				case 1211006:
					element = Element.ICE;
					break;
				case 1211007:
				case 1211008:
					element = Element.LIGHTING;
					break;
			}
			ISkill chargeSkill = SkillFactory.getSkill(chargeSkillId);
			maximumDamageToMonster *= chargeSkill.getEffect(player.getSkillLevel(chargeSkill)).getDamage() / 100.0;
		}
		if (element != Element.NEUTRAL) {
			double elementalEffect;
			if (attack.skill == 3211003 || attack.skill == 3111003) { // inferno and blizzard
				elementalEffect = attackEffect.getX() / 200.0;
			} else {
				elementalEffect = 0.5;
			}
			switch (monster.getEffectiveness(element)) {
				case IMMUNE:
					elementalMaxDamagePerMonster = 1;
					break;
				case NORMAL:
					elementalMaxDamagePerMonster = maximumDamageToMonster;
					break;
				case WEAK:
					elementalMaxDamagePerMonster = (int) (maximumDamageToMonster * (1.0 + elementalEffect));
					break;
				case STRONG:
					elementalMaxDamagePerMonster = (int) (maximumDamageToMonster * (1.0 - elementalEffect));
					break;
				default:
					throw new RuntimeException("Unknown enum constant");
			}
		} else {
			elementalMaxDamagePerMonster = maximumDamageToMonster;
		}
		if (damageToMonster > elementalMaxDamagePerMonster) {
			player.getCheatTracker().registerOffense(CheatingOffense.HIGH_DAMAGE);
			// log.info("[h4x] Player {} is doing high damage to one monster: {} (maxdamage: {}, skill:
			// {})",
			// new Object[] { player.getName(), Integer.valueOf(totDamageToOneMonster),
			// Integer.valueOf(maxDamagePerMonster), Integer.valueOf(attack.skill) });
			if (damageToMonster > elementalMaxDamagePerMonster * 3) { // * 3 until implementation of lagsafe pingchecks for buff expiration
				AutobanManager.getInstance().autoban(player.getClient(), damageToMonster +
					" damage (level: " + player.getLevel() + " watk: " + player.getTotalWatk() +
					" skill: " + attack.skill + ", monster: " + monster.getId() + " assumed max damage: " +
					elementalMaxDamagePerMonster + ")");
			}
		}
	}

	public AttackInfo parseDamage(LittleEndianAccessor lea, boolean ranged) {
		AttackInfo ret = new AttackInfo();

		lea.readByte();
		ret.numAttackedAndDamage = lea.readByte();
		ret.numAttacked = (ret.numAttackedAndDamage >>> 4) & 0xF; // guess why there are no skills damaging more than
		// 15 monsters...
		ret.numDamage = ret.numAttackedAndDamage & 0xF; // how often each single monster was attacked o.o
		ret.allDamage = new ArrayList<Pair<Integer, List<Integer>>>();
		ret.skill = lea.readInt();
		lea.readByte(); // always 0 (?)
		ret.stance = lea.readByte();

		if (ret.skill == 4211006) {
			return parseMesoExplosion(lea, ret);
		}

		if (ranged) {
			lea.readShort();
			lea.readShort(); // somehow related to crits? this is the only value that changes between two otherwise
			// identical attacks
			// System.out.println(Integer.toBinaryString(wui & 0xFFFF) + "_" + Integer.toHexString(wui & 0xFFFF));
			lea.skip(7);
			// System.out.println("Unk1: " + HexTool.toString(lea.read(7)));
		} else {
			lea.skip(6);
		}

		// TODO we need information if an attack was a crit or not but it does not seem to be in this packet - find out
		// if it is o.o
		// noncrit strafe
		// 24 00 01 14 FE FE 30 00 00 97 04 06 99 2F EE 00 04 00 00 00 41 6B 00 00 00 06 81 00 01 00 00 5F 00 00 00 5F 00 D2 02 A3 19 00 00 43 0C 00 00 AD 0B 00 00 DB 12 00 00 64 00 5F 00
		//
		// fullcrit strafe:
		// 24 00 01 14 FE FE 30 00 00 97 04 06 F5 C3 EE 00 04 00 00 00 41 6B 00 00 00 06 81 00 01 00 00 5F 00 00 00 5F 00 D2 02 6E 0F 00 00 EA 12 00 00 58 15 00 00 56 11 00 00 64 00 5F 00

		for (int i = 0; i < ret.numAttacked; i++) {
			int oid = lea.readInt();
			// System.out.println("Unk2: " + HexTool.toString(lea.read(14)));
			lea.skip(14); // seems to contain some position info o.o

			List<Integer> allDamageNumbers = new ArrayList<Integer>();
			for (int j = 0; j < ret.numDamage; j++) {
				int damage = lea.readInt();
				// System.out.println("Damage: " + damage);
				allDamageNumbers.add(Integer.valueOf(damage));
			}
			ret.allDamage.add(new Pair<Integer, List<Integer>>(Integer.valueOf(oid), allDamageNumbers));
		}

		// System.out.println("Unk3: " + HexTool.toString(lea.read(4)));
		return ret;
	}

	public AttackInfo parseMesoExplosion(LittleEndianAccessor lea, AttackInfo ret) {

		if (ret.numAttackedAndDamage == 0) {
			lea.skip(10);

			int bullets = lea.readByte();
			for (int j = 0; j < bullets; j++) {
				int mesoid = lea.readInt();
				lea.skip(1);
				ret.allDamage.add(new Pair<Integer, List<Integer>>(Integer.valueOf(mesoid), null));
			}
			return ret;

		} else {
			lea.skip(6);
		}

		for (int i = 0; i < ret.numAttacked + 1; i++) {

			int oid = lea.readInt();

			if (i < ret.numAttacked) {
				lea.skip(12);
				int bullets = lea.readByte();

				List<Integer> allDamageNumbers = new ArrayList<Integer>();
				for (int j = 0; j < bullets; j++) {
					int damage = lea.readInt();
					// System.out.println("Damage: " + damage);
					allDamageNumbers.add(Integer.valueOf(damage));
				}
				ret.allDamage.add(new Pair<Integer, List<Integer>>(Integer.valueOf(oid), allDamageNumbers));

			} else {

				int bullets = lea.readByte();
				for (int j = 0; j < bullets; j++) {
					int mesoid = lea.readInt();
					lea.skip(1);
					ret.allDamage.add(new Pair<Integer, List<Integer>>(Integer.valueOf(mesoid), null));
				}
			}
		}

		return ret;
	}
}
