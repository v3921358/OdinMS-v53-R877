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
 * ItemEndEffect.java
 * 
 * Created on 29. November 2007, 01:34
 * 
 * To change this template, choose Tools | Template Manager and open the template in the editor.
 */

package net.sf.odinms.server;

import java.awt.Point;
import java.awt.Rectangle;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import net.sf.odinms.client.IItem;
import net.sf.odinms.client.ISkill;
import net.sf.odinms.client.MapleBuffStat;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.client.MapleJob;
import net.sf.odinms.client.MapleStat;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.client.status.MonsterStatus;
import net.sf.odinms.client.status.MonsterStatusEffect;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.provider.MapleData;
import net.sf.odinms.provider.MapleDataTool;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.server.maps.MapleDoor;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.server.maps.MapleMapObject;
import net.sf.odinms.server.maps.MapleMapObjectType;
import net.sf.odinms.server.maps.MapleMist;
import net.sf.odinms.server.maps.MapleSummon;
import net.sf.odinms.server.maps.SummonMovementType;
import net.sf.odinms.tools.ArrayMap;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.Pair;

/**
 * @author Matze
 * @author Frz
 */
public class MapleStatEffect {
	private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleStatEffect.class);
	private short watk, matk, wdef, mdef, acc, avoid, hands, speed, jump;
	private short hp, mp;
	private double hpR, mpR;
	private short mpCon, hpCon;
	private int duration;
	private boolean overTime;
	private int sourceid;
	private int moveTo;
	private boolean skill;
	private List<Pair<MapleBuffStat, Integer>> statups;
	private Map<MonsterStatus, Integer> monsterStatus;
	private int x, y, z;
	private double prop;
	private int itemCon, itemConNo;
	private int damage, attackCount, bulletCount, bulletConsume;
	private Point lt, rb;
	private int mobCount;
	private int moneyCon;
	
	private MapleStatEffect() {

	}

	public static MapleStatEffect loadSkillEffectFromData(MapleData source, int skillid, boolean overtime) {
		return loadFromData(source, skillid, true, overtime);
	}

	public static MapleStatEffect loadItemEffectFromData(MapleData source, int itemid) {
		return loadFromData(source, itemid, false, false);
	}
	
	private static void addBuffStatPairToListIfNotZero(List<Pair<MapleBuffStat, Integer>> list, MapleBuffStat buffstat, Integer val) {
		if (val.intValue() != 0) {
			list.add(new Pair<MapleBuffStat, Integer>(buffstat, val));
		}
	}

	private static MapleStatEffect loadFromData(MapleData source, int sourceid, boolean skill, boolean overTime) {
		MapleStatEffect ret = new MapleStatEffect();
		ret.duration = MapleDataTool.getInt("time", source, -1);
		ret.hp = (short) MapleDataTool.getInt("hp", source, 0);
		ret.hpR = MapleDataTool.getInt("hpR", source, 0) / 100.0;
		ret.mp = (short) MapleDataTool.getInt("mp", source, 0);
		ret.mpR = MapleDataTool.getInt("mpR", source, 0) / 100.0;
		ret.mpCon = (short) MapleDataTool.getInt("mpCon", source, 0);
		ret.hpCon = (short) MapleDataTool.getInt("hpCon", source, 0);
		ret.prop = MapleDataTool.getInt("prop", source, 100) / 100.0;
		ret.mobCount = MapleDataTool.getInt("mobCount", source, 1);

		ret.sourceid = sourceid;
		ret.skill = skill;

		if (!ret.skill && ret.duration > -1) {
			ret.overTime = true;
		} else {
			ret.duration *= 1000; // items have their times stored in ms, of course
			ret.overTime = overTime;
		}
		ArrayList<Pair<MapleBuffStat, Integer>> statups = new ArrayList<Pair<MapleBuffStat, Integer>>();

		ret.watk = (short) MapleDataTool.getInt("pad", source, 0);
		ret.wdef = (short) MapleDataTool.getInt("pdd", source, 0);
		ret.matk = (short) MapleDataTool.getInt("mad", source, 0);
		ret.mdef = (short) MapleDataTool.getInt("mdd", source, 0);
		ret.acc = (short) MapleDataTool.getIntConvert("acc", source, 0);
		ret.avoid = (short) MapleDataTool.getInt("eva", source, 0);
		ret.speed = (short) MapleDataTool.getInt("speed", source, 0);
		ret.jump = (short) MapleDataTool.getInt("jump", source, 0);
		if (ret.overTime && ret.getSummonMovementType() == null) {
			addBuffStatPairToListIfNotZero(statups, MapleBuffStat.WATK, Integer.valueOf(ret.watk));
			addBuffStatPairToListIfNotZero(statups, MapleBuffStat.WDEF, Integer.valueOf(ret.wdef));
			addBuffStatPairToListIfNotZero(statups, MapleBuffStat.MATK, Integer.valueOf(ret.matk));
			addBuffStatPairToListIfNotZero(statups, MapleBuffStat.MDEF, Integer.valueOf(ret.mdef));
			addBuffStatPairToListIfNotZero(statups, MapleBuffStat.ACC, Integer.valueOf(ret.acc));
			addBuffStatPairToListIfNotZero(statups, MapleBuffStat.AVOID, Integer.valueOf(ret.avoid));
			addBuffStatPairToListIfNotZero(statups, MapleBuffStat.SPEED, Integer.valueOf(ret.speed));
			addBuffStatPairToListIfNotZero(statups, MapleBuffStat.JUMP, Integer.valueOf(ret.jump));
		}

		MapleData ltd = source.getChildByPath("lt");
		if (ltd != null) {
			ret.lt = (Point) ltd.getData();
			ret.rb = (Point) source.getChildByPath("rb").getData();
		}

		int x = MapleDataTool.getInt("x", source, 0);
		ret.x = x;
		ret.y = MapleDataTool.getInt("y", source, 0);
		ret.z = MapleDataTool.getInt("z", source, 0);
		ret.damage = MapleDataTool.getIntConvert("damage", source, 100);
		ret.attackCount = MapleDataTool.getIntConvert("attackCount", source, 1);
		ret.bulletCount = MapleDataTool.getIntConvert("bulletCount", source, 1);
		ret.bulletConsume = MapleDataTool.getIntConvert("bulletConsume", source, 0);
		ret.moneyCon = MapleDataTool.getIntConvert("moneyCon", source, 0);

		ret.itemCon = MapleDataTool.getInt("itemCon", source, 0);
		ret.itemConNo = MapleDataTool.getInt("itemConNo", source, 0);
		ret.moveTo = MapleDataTool.getInt("moveTo", source, -1);

		Map<MonsterStatus, Integer> monsterStatus = new ArrayMap<MonsterStatus, Integer>();
		
		// TODO hs, wk charges (?), recovery (?) (beginner)
		// dragonblood (? - takes away hp constantly), meso guard,
		// sharpeyes, maplehero, more 4th skills ?
		// others should see pg damage
		if (skill) { // hack because we can't get from the datafile...
			switch (sourceid) {
				case 2001002: // magic guard
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.MAGIC_GUARD, Integer.valueOf(x)));
					break;
				case 2301003: // invincible
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.INVINCIBLE, Integer.valueOf(x)));
					break;
				case 5101004: // hide
					ret.duration = 60 * 120 * 1000;
					ret.overTime = true;
					// falltrough intended
				case 4001003: // darksight
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.DARKSIGHT, Integer.valueOf(x)));
					break;
				case 4211003: // pickpocket
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.PICKPOCKET, Integer.valueOf(x)));
					break;
				case 4211005: // mesoguard
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.MESOGUARD, Integer.valueOf(x)));
					break;
				case 4111001: // mesoup
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.MESOUP, Integer.valueOf(x)));
					break;
				case 4111002: // shadowpartner
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.SHADOWPARTNER, Integer.valueOf(x)));
					break;
				case 3101004: // soul arrow
				case 3201004:
				case 2311002: // mystic door - hacked buff icon
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.SOULARROW, Integer.valueOf(x)));
					break;
				case 1211006: // wk charges
				case 1211003:
				case 1211004:
				case 1211005:
				case 1211008:
				case 1211007:
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.WK_CHARGE, Integer.valueOf(x)));
					break;
				case 1101005: // booster
				case 1101004:
				case 1201005:
				case 1201004:
				case 1301005:
				case 1301004:
				case 3101002:
				case 3201002:
				case 4101003:
				case 4201002:
				case 2111005: // spell booster, do these work the same?
				case 2211005:
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.BOOSTER, Integer.valueOf(x)));
					break;
				case 1101007: // pguard
				case 1201007:
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.POWERGUARD, Integer.valueOf(x)));
					break;
				case 1301007:
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.HYPERBODYHP, Integer.valueOf(x)));
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.HYPERBODYMP, Integer.valueOf(ret.y)));
					break;
				case 1001: // recovery
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.RECOVERY, Integer.valueOf(x)));
					break;
				case 1111002: // combo
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.COMBO, Integer.valueOf(1)));
					break;
				case 1004: // monster riding
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.MONSTER_RIDING, Integer.valueOf(1)));
					break;
				case 1311006: //dragon roar
					ret.hpR = -x / 100.0;
					break;
				case 1311008: // dragon blood
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.DRAGONBLOOD, Integer.valueOf(ret.x)));
					break;
				case 4001002: // disorder
					monsterStatus.put(MonsterStatus.WATK, Integer.valueOf(ret.x));
					monsterStatus.put(MonsterStatus.WDEF, Integer.valueOf(ret.y));
					break;
				case 1201006: // threaten
					monsterStatus.put(MonsterStatus.WATK, Integer.valueOf(ret.x));
					monsterStatus.put(MonsterStatus.WDEF, Integer.valueOf(ret.y));
					break;
				case 1211002: // charged blow
				case 1111008: // shout
				case 4211002: // assaulter
				case 3101005: // arrow bomb
				case 1111005: // coma: sword
				case 1111006: // coma: axe
					monsterStatus.put(MonsterStatus.STUN, Integer.valueOf(1));
					break;
				case 2201004: // cold beam
				case 2211002: // ice strike
				case 3211003: // blizzard
				case 2211006: // il elemental compo
					monsterStatus.put(MonsterStatus.FREEZE, Integer.valueOf(1));
					ret.duration *= 2; // freezing skills are a little strange
					break;
				case 2101003: // fp slow
				case 2201003: // il slow
					monsterStatus.put(MonsterStatus.SPEED, Integer.valueOf(ret.x));
					break;
				case 2101005: // poison breath
				// case 2111003: // poison mist - does not poison itself
				case 2111006: // fp elemental compo
					monsterStatus.put(MonsterStatus.POISON, Integer.valueOf(1));
					break;
				case 2311005:
					monsterStatus.put(MonsterStatus.DOOM, Integer.valueOf(1));
					break;
					
				case 3111002: // puppet ranger
				case 3211002: // puppet sniper
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.PUPPET, Integer.valueOf(1)));
					break;	
				case 3211005: // golden eagle
				case 3111005: // golden hawk
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.SUMMON, Integer.valueOf(1)));
					monsterStatus.put(MonsterStatus.STUN, Integer.valueOf(1));
					break;
				case 3221005: // frostprey
				case 2121005: // elquines
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.SUMMON, Integer.valueOf(1)));
					monsterStatus.put(MonsterStatus.FREEZE, Integer.valueOf(1));
					break;
				case 2311006: // summon dragon
				case 3121006: // phoenix
				case 2221005: // ifrit
				case 2321003: // bahamut
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.SUMMON, Integer.valueOf(1)));
					break;
				case 2311003: // hs
				case 5101002: // GM hs
					statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.HOLY_SYMBOL, Integer.valueOf(x)));
					break;
				case 2211004: // il seal
				case 2111004: // fp seal
					monsterStatus.put(MonsterStatus.SEAL, 1);
					break;
				case 4111003: // shadow web
					monsterStatus.put(MonsterStatus.SHADOW_WEB, 1);
					break;
				default:
					// nothing needs to be added, that's ok
			}
		}
		ret.monsterStatus = monsterStatus;
		// TODO: fixDamage, coolTime
		
		statups.trimToSize();
		ret.statups = statups;
		
		return ret;
	}
	
	/**
	 * 
	 * @param applyto
	 * @param obj
	 * @param attack damage done by the skill
	 */
	public void applyPassive(MapleCharacter applyto, MapleMapObject obj, int attack) {
		if (makeChanceResult()) {
			switch (sourceid) {
				// MP eater
				case 2100000:
				case 2200000:
				case 2300000:
					if (obj == null || obj.getType() != MapleMapObjectType.MONSTER)
						return;
					MapleMonster mob = (MapleMonster) obj;
					// x is absorb percentage
					if (!mob.isBoss()) {
						int absorbMp = Math.min((int) (mob.getMaxMp() * (getX() / 100.0)), mob.getMp());
						if (absorbMp > 0) {
							mob.setMp(mob.getMp() - absorbMp);
							applyto.addMP(absorbMp);
							applyto.getClient().getSession().write(MaplePacketCreator.showOwnBuffEffect(sourceid, 1));
							applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.showBuffeffect(applyto.getId(), sourceid, 1), false);
						}
					}
					break;
			}
		}
	}

	public boolean applyTo(MapleCharacter chr) {
		return applyTo(chr, chr, true, null);
	}
	
	public boolean applyTo(MapleCharacter chr, Point pos) {
		return applyTo(chr, chr, true, pos);
	}
	
	private boolean applyTo(MapleCharacter applyfrom, MapleCharacter applyto, boolean primary, Point pos) {
		int hpchange = calcHPChange(applyfrom, primary);
		int mpchange = calcMPChange(applyfrom, primary);

		if (primary) {
			if (itemConNo != 0) {
				MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(itemCon);
				MapleInventoryManipulator.removeById(applyto.getClient(), type, itemCon, itemConNo, false, true);
			}
		}
		List<Pair<MapleStat, Integer>> hpmpupdate = new ArrayList<Pair<MapleStat, Integer>>(2);
		if (!primary && isResurrection()) {
			hpchange = applyto.getMaxHp();
			applyto.setStance(0);
		}
		if (hpchange != 0) {
			if (hpchange < 0 && (-hpchange) > applyto.getHp()) {
				return false;
			}
			applyto.setHp(applyto.getHp() + hpchange);
			hpmpupdate.add(new Pair<MapleStat, Integer>(MapleStat.HP, Integer.valueOf(applyto.getHp())));
		}
		if (mpchange != 0) {
			if (mpchange < 0 && (-mpchange) > applyto.getMp()) {
				return false;
			}
			applyto.setMp(applyto.getMp() + mpchange);
			hpmpupdate.add(new Pair<MapleStat, Integer>(MapleStat.MP, Integer.valueOf(applyto.getMp())));
		}
		applyto.getClient().getSession().write(MaplePacketCreator.updatePlayerStats(hpmpupdate, true));

		if (moveTo != -1) {
			MapleMap target;
			if (moveTo == 999999999) {
				target = applyto.getMap().getReturnMap();
			} else {
				target = ChannelServer.getInstance(applyto.getClient().getChannel()).getMapFactory().getMap(moveTo);
				if (target.getId() / 10000000 != applyto.getMapId() / 10000000) {
					log.info("Player {} is trying to use a return scroll to an illegal location ({}->{})",
						new Object[] { applyto.getName(), applyto.getMapId(), target.getId() });
					return false;
				}
			}
			applyto.changeMap(target, target.getPortal(0));
		}
		
		if (overTime) {
			applyBuffEffect(applyfrom, applyto, primary);
		}
		if (primary && (overTime || isHeal())) {
			applyBuff(applyfrom);
		}
		if (primary && isMonsterBuff()) {
			applyMonsterBuff(applyfrom);
		}
		
		SummonMovementType summonMovementType = getSummonMovementType();
		if(summonMovementType != null && pos != null) {
		    final MapleSummon tosummon = new MapleSummon(applyfrom, sourceid, pos, summonMovementType);
		    if (!tosummon.isPuppet()) {
		    	applyfrom.getCheatTracker().resetSummonAttack();
		    }
		    applyfrom.getMap().spawnSummon(tosummon);
		    applyfrom.getSummons().put(sourceid, tosummon);
		    tosummon.addHP(x);
		}
		
		// Magic Door
		if (isMagicDoor()) {
			//applyto.cancelMagicDoor();
			Point doorPosition = new Point(applyto.getPosition());
			//doorPosition.y -= 280;
			MapleDoor door = new MapleDoor(applyto, doorPosition);
			applyto.getMap().spawnDoor(door);
			applyto.addDoor(door);
			door = new MapleDoor(door);
			applyto.addDoor(door);
			door.getTown().spawnDoor(door);
			if (applyto.getParty() != null) {
				// update town doors
				applyto.silentPartyUpdate();
			}
		} else if (isMist()) {
			Rectangle bounds = calculateBoundingBox(applyfrom.getPosition(), applyfrom.isFacingLeft());
			MapleMist mist = new MapleMist(bounds, applyfrom, this);
			applyfrom.getMap().spawnMist(mist, getDuration(), sourceid == 2111003);
		}
		return true;
	}

	private void applyBuff(MapleCharacter applyfrom) {
		if (isPartyBuff() && (applyfrom.getParty() != null || isGmBuff())) {
			Rectangle bounds = calculateBoundingBox(applyfrom.getPosition(), applyfrom.isFacingLeft());
			List<MapleMapObject> affecteds = applyfrom.getMap().getMapObjectsInBox(bounds,
				Arrays.asList(MapleMapObjectType.PLAYER));
			List<MapleCharacter> affectedp = new ArrayList<MapleCharacter>(affecteds.size());
			for (MapleMapObject affectedmo : affecteds) {
				MapleCharacter affected = (MapleCharacter) affectedmo;
				if (affected != applyfrom && (isGmBuff() || applyfrom.getParty().equals(affected.getParty()))) {
					boolean isRessurection = isResurrection();
					if ((isRessurection && !affected.isAlive()) || (!isRessurection && affected.isAlive())) {
						affectedp.add(affected);
					}
				}
			}
			for (MapleCharacter affected : affectedp) {
				// TODO actually heal (and others) shouldn't recalculate everything
				// for heal this is an actual bug since heal hp is decreased with the number
				// of affected players
				applyTo(applyfrom, affected, false, null);
				affected.getClient().getSession().write(MaplePacketCreator.showOwnBuffEffect(sourceid, 2));
				affected.getMap().broadcastMessage(affected,
					MaplePacketCreator.showBuffeffect(affected.getId(), sourceid, 2), false);
			}
		}
	}

	private void applyMonsterBuff(MapleCharacter applyfrom) {
		Rectangle bounds = calculateBoundingBox(applyfrom.getPosition(), applyfrom.isFacingLeft());
		List<MapleMapObject> affected = applyfrom.getMap().getMapObjectsInBox(bounds,
			Arrays.asList(MapleMapObjectType.MONSTER));
		ISkill skill = SkillFactory.getSkill(sourceid);
		int i = 0;
		for (MapleMapObject mo : affected) {
			MapleMonster monster = (MapleMonster) mo;
			if (makeChanceResult()) {
				monster.applyStatus(applyfrom, new MonsterStatusEffect(getMonsterStati(), skill, false), isPoison(), getDuration());
			}
			i++;
			if (i >= mobCount) {
				break;
			}
		}
	}

	private Rectangle calculateBoundingBox(Point posFrom, boolean facingLeft) {
		Point mylt;
		Point myrb;
		if (facingLeft) {
			mylt = new Point(lt.x + posFrom.x, lt.y + posFrom.y);
			myrb = new Point(rb.x + posFrom.x, rb.y + posFrom.y);
		} else {
			myrb = new Point(lt.x * -1 + posFrom.x, rb.y + posFrom.y);
			mylt = new Point(rb.x * -1 + posFrom.x, lt.y + posFrom.y);
		}
		Rectangle bounds = new Rectangle(mylt.x, mylt.y, myrb.x - mylt.x, myrb.y - mylt.y);
		return bounds;
	}

	private void applyBuffEffect(MapleCharacter applyfrom, MapleCharacter applyto, boolean primary) {
		applyto.cancelEffect(this, true, -1);
		List<Pair<MapleBuffStat, Integer>> localstatups = statups;
		if (isMonsterRiding()) {
			int ridingLevel = 0;
			IItem mount = applyfrom.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -18);
			if (mount != null) {
				ridingLevel = mount.getItemId() - 1902000 + 1;
			}
			localstatups = Collections.singletonList(new Pair<MapleBuffStat, Integer>(MapleBuffStat.MONSTER_RIDING, ridingLevel));
		}
		int localDuration = duration;
		if (primary) {
			localDuration = alchemistModifyVal(applyfrom, localDuration, false);
		}
		
		if (localstatups.size() > 0) {
			applyto.getClient().getSession().write(MaplePacketCreator.giveBuff((skill ? sourceid : -sourceid), localDuration, localstatups));
		} else {
			log.warn(MapleClient.getLogMessage(applyto, "Applying empty statups (skill {}, id {})", skill, sourceid));
		}
		if (isDs()) {
			List<Pair<MapleBuffStat, Integer>> dsstat = Collections.singletonList(new Pair<MapleBuffStat, Integer>(
				MapleBuffStat.DARKSIGHT, 0));
			applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.giveForeignBuff(applyto.getId(), dsstat),
				false);
		}
		if (isCombo()) {
			List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<MapleBuffStat, Integer>(
				MapleBuffStat.COMBO, 1));
			applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.giveForeignBuff(applyto.getId(), stat),
				false);
		}
		if (isMonsterRiding()) {
			List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<MapleBuffStat, Integer>(
				MapleBuffStat.MONSTER_RIDING, 1));
			applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.giveForeignBuff(applyto.getId(), stat),
				false);
		}
		if (isShadowPartner()) {
			List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<MapleBuffStat, Integer>(
				MapleBuffStat.SHADOWPARTNER, 0));
			applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.giveForeignBuff(applyto.getId(), stat),
				false);
		}
		if(isEnrage()) {
		    applyto.handleOrbconsume();
		}
		long starttime = System.currentTimeMillis();
		CancelEffectAction cancelAction = new CancelEffectAction(applyto, this, starttime);
		ScheduledFuture<?> schedule = TimerManager.getInstance().schedule(cancelAction, localDuration);
		applyto.registerEffect(this, starttime, schedule);

		if (primary) {
			applyto.getMap().broadcastMessage(applyto,
				MaplePacketCreator.showBuffeffect(applyto.getId(), sourceid, 1), false);
		}
	}
	
	private int calcHPChange(MapleCharacter applyfrom, boolean primary) {
		int hpchange = 0;
		if (hp != 0) {
			if (!skill) {
				if (primary) {
					hpchange += alchemistModifyVal(applyfrom, hp, true);
				} else {
					hpchange += hp;
				}
			} else { // assumption: this is heal
				hpchange += makeHealHP(hp / 100.0, applyfrom.getTotalMagic(), 3, 5);
			}
		}
		if (hpR != 0) {
			hpchange += (int) (applyfrom.getCurrentMaxHp() * hpR);
		}
		// actually receivers probably never get any hp when it's not heal but whatever
		if (primary) {
			if (hpCon != 0) {
				hpchange -= hpCon;
			}
		}
		if (isChakra()) {
			hpchange += makeHealHP(getY() / 100.0, applyfrom.getTotalLuk(), 2.3, 3.5);
		}
		return hpchange;
	}
	
	private int makeHealHP (double rate, double stat, double lowerfactor, double upperfactor) {
		int maxHeal = (int) (stat * upperfactor * rate);
		int minHeal = (int) (stat * lowerfactor * rate);
		return (int) ((Math.random() * (maxHeal - minHeal + 1)) + minHeal);
	}
	
	private int calcMPChange(MapleCharacter applyfrom, boolean primary) {
		int mpchange = 0;
		if (mp != 0) {
			if (primary) {
				mpchange += alchemistModifyVal(applyfrom, mp, true);
			} else {
				mpchange += mp;
			}
		}
		if (mpR != 0) {
			mpchange += (int) (applyfrom.getCurrentMaxMp() * mpR);
		}
		if (primary) {
			if (mpCon != 0) {
				double mod = 1.0;
				boolean isAFpMage = applyfrom.getJob().isA(MapleJob.FP_MAGE);
				if (isAFpMage || applyfrom.getJob().isA(MapleJob.IL_MAGE)) {
					ISkill amp;
					if (isAFpMage) {
						amp = SkillFactory.getSkill(2110001);
					} else {
						amp = SkillFactory.getSkill(2210001);
					}
					int ampLevel = applyfrom.getSkillLevel(amp);
					if (ampLevel > 0) {
						MapleStatEffect ampStat = amp.getEffect(ampLevel);
						mod = ampStat.getX() / 100.0;
					}
				}
				mpchange -= mpCon * mod;
			}
		}
		return mpchange;
	}

	private int alchemistModifyVal (MapleCharacter chr, int val, boolean withX) {
		if (!skill && chr.getJob().isA(MapleJob.HERMIT)) {
			MapleStatEffect alchemistEffect = getAlchemistEffect(chr);
			if (alchemistEffect != null) {
				return (int) (val * ((withX ? alchemistEffect.getX() : alchemistEffect.getY()) / 100.0));
			}
		}
		return val;
	}
	
	private MapleStatEffect getAlchemistEffect (MapleCharacter chr) {
		ISkill alchemist = SkillFactory.getSkill(4110000);
		int alchemistLevel = chr.getSkillLevel(alchemist);
		if (alchemistLevel == 0) {
			return null;
		}
		return alchemist.getEffect(alchemistLevel);
	}
	
	private boolean isGmBuff() {
		switch (sourceid) {
			case 1005: // echo of hero acts like a gm buff
			case 5101000:
			case 5101001:
			case 5101002:
			case 5101003:
			case 5101005:
				return true;
			default:
				return false;
		}
	}
	
	private boolean isMonsterBuff() {
		if (!skill) {
			return false;
		}
		switch (sourceid) {
			case 1201006: // threaten
			case 2101003: // fp slow
			case 2201003: // il slow
			case 2211004: // il seal
			case 2111004: // fp seal
			case 2311005: // doom
			case 4111003: // shadow web
				return true;
		}
		return false;
	}

	private boolean isPartyBuff() {
		if (lt == null || rb == null) {
			return false;
		}
		if (sourceid >= 1211003 && sourceid <= 1211008) { // wk charges have lt and rb set but are neither player nor monster buffs
			return false;
		}
		return true;
	}
	
	public boolean isHeal() {
		return sourceid == 2301002;
	}

	public boolean isResurrection() {
		return sourceid == 5101005 || sourceid == 5001005 || sourceid == 2321006;
	}

	public short getWatk() {
		return watk;
	}

	public short getMatk() {
		return matk;
	}

	public short getWdef() {
		return wdef;
	}

	public short getMdef() {
		return mdef;
	}

	public short getAcc() {
		return acc;
	}

	public short getAvoid() {
		return avoid;
	}

	public short getHands() {
		return hands;
	}

	public short getSpeed() {
		return speed;
	}

	public short getJump() {
		return jump;
	}

	public int getDuration() {
		return duration;
	}

	public boolean isOverTime() {
		return overTime;
	}

	public List<Pair<MapleBuffStat, Integer>> getStatups() {
		return statups;
	}

	public boolean sameSource(MapleStatEffect effect) {
		return this.sourceid == effect.sourceid && this.skill == effect.skill;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getZ() {
		return z;
	}

	public int getDamage() {
		return damage;
	}

	public int getAttackCount() {
		return attackCount;
	}

	public int getBulletCount() {
		return bulletCount;
	}

	public int getBulletConsume() {
		return bulletConsume;
	}
	
	public int getMoneyCon() {
		return moneyCon;
	}
	
	public Map<MonsterStatus, Integer> getMonsterStati() {
		return monsterStatus;
	}

	public boolean isHide() {
		return skill && sourceid == 5101004;
	}
	
	public boolean isDragonBlood() {
		return skill && sourceid == 1311008;
	}

	private boolean isDs() {
		return skill && sourceid == 4001003;
	}

	private boolean isCombo() {
		return skill && sourceid == 1111002;
	}
	
	private boolean isEnrage() {
		return skill && sourceid == 1121010;
	}
	
	private boolean isShadowPartner() {
		return skill && sourceid == 4111002;
	}
	
	private boolean isChakra() {
		return skill && sourceid == 4211001;
	}
	
	private boolean isMonsterRiding() {
		return 	skill && sourceid == 1004;
	}
	
	public boolean isMagicDoor() {
		return skill && sourceid == 2311002;
	}

	public boolean isMesoGuard() {
		return skill && sourceid == 4211005;
	}
	
	public boolean isCharge() {
		return skill && sourceid >= 1211003 && sourceid <= 1211008;
	}
		
	public boolean isPoison() {
		return skill && (sourceid == 2111003 || sourceid == 2101005 || sourceid == 2111006);
	}
	
	private boolean isMist() {
		return skill && (sourceid == 2111003 || sourceid == 4221006); // poison mist and smokescreen
	}
	
	public SummonMovementType getSummonMovementType() {
		if (!skill) {
			return null;
		}
		switch (sourceid) {
			case 3211002: // puppet sniper
			case 3111002: // puppet ranger
				return SummonMovementType.STATIONARY;
			case 3211005: // golden eagle
			case 3111005: // golden hawk
			case 2311006: // summon dragon
			case 3221005: // frostprey
			case 3121006: // phoenix
				return SummonMovementType.CIRCLE_FOLLOW;
			case 2121005: // elquines
			case 2221005: // ifrit
			case 2321003: // bahamut
				return SummonMovementType.FOLLOW;
		}
		return null;
	}
	
	public boolean isSkill() {
		return skill;
	}
	
	public int getSourceId() {
		return sourceid;
	}
	
	/**
	 * 
	 * @return true if the effect should happen based on it's probablity, false otherwise
	 */
	public boolean makeChanceResult() {
		return prop == 1.0 || Math.random() < prop;
	}
	
	private static class CancelEffectAction implements Runnable {
		private MapleStatEffect effect;
		private WeakReference<MapleCharacter> target;
		private long startTime;

		public CancelEffectAction(MapleCharacter target, MapleStatEffect effect, long startTime) {
			this.effect = effect;
			this.target = new WeakReference<MapleCharacter>(target);
			this.startTime = startTime;
		}

		@Override
		public void run() {
			MapleCharacter realTarget = target.get();
			if (realTarget != null) {
				realTarget.cancelEffect(effect, false, startTime);
			}
		}
	}
}
