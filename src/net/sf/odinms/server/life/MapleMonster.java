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

package net.sf.odinms.server.life;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;

import net.sf.odinms.client.MapleBuffStat;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.status.MonsterStatus;
import net.sf.odinms.client.status.MonsterStatusEffect;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.net.world.MapleParty;
import net.sf.odinms.net.world.MaplePartyCharacter;
import net.sf.odinms.scripting.EventInstanceManager;
import net.sf.odinms.server.TimerManager;
import net.sf.odinms.server.life.MapleMonsterInformationProvider.DropEntry;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.server.maps.MapleMapObjectType;
import net.sf.odinms.tools.ArrayMap;
import net.sf.odinms.tools.MaplePacketCreator;

public class MapleMonster extends AbstractLoadedMapleLife {
	private MapleMonsterStats stats;
	private MapleMonsterStats overrideStats;
	private int hp;
	private int mp;
	private WeakReference<MapleCharacter> controller = new WeakReference<MapleCharacter>(null);
	private boolean controllerHasAggro, controllerKnowsAboutAggro;
	private Collection<AttackerEntry> attackers = new LinkedList<AttackerEntry>();
	private EventInstanceManager eventInstance = null;
	private Collection<MonsterListener> listeners = new LinkedList<MonsterListener>();
	private MapleCharacter highestDamageChar;
	private Map<MonsterStatus, MonsterStatusEffect> stati = new LinkedHashMap<MonsterStatus, MonsterStatusEffect>();
	private List<MonsterStatusEffect> activeEffects = new ArrayList<MonsterStatusEffect>();
	private MapleMap map;

	// private static Logger log = LoggerFactory.getLogger(MapleMonster.class);

	public MapleMonster(int id, MapleMonsterStats stats) {
		super(id);
		initWithStats(stats);
	}
	
	public MapleMonster(MapleMonster monster) {
		super(monster);
		initWithStats(monster.stats);
	}
	
	private void initWithStats (MapleMonsterStats stats) {
		setStance(5);
		this.stats = stats;
		hp = stats.getHp();
		mp = stats.getMp();
	}
	
	public void setMap(MapleMap map) {
		this.map = map;
	}

	public int getDrop() {
		MapleMonsterInformationProvider mi = MapleMonsterInformationProvider.getInstance();
		int lastAssigned = -1;
		int minChance = 1;
		List<DropEntry> dl = mi.retrieveDropChances(getId());
		for (DropEntry d : dl) {
			if (d.chance > minChance)
				minChance = d.chance;
		}
		for (DropEntry d : dl) {
			d.assignedRangeStart = lastAssigned + 1;
			d.assignedRangeLength = (int) (((double) 1 / (double) d.chance) * minChance);
			lastAssigned += d.assignedRangeLength;
		}
		// now produce the randomness o.o
		Random r = new Random();
		int c = r.nextInt(minChance);
		for (DropEntry d : dl) {
			if (c >= d.assignedRangeStart && c < (d.assignedRangeStart + d.assignedRangeLength))
				return d.itemId;
		}
		return -1;
	}

	public int getHp() {
		return hp;
	}

	public void setHp(int hp) {
		this.hp = hp;
	}

	public int getMaxHp() {
		if (overrideStats != null) {
			return overrideStats.getHp();
		}
		return stats.getHp();
	}

	public int getMp() {
		return mp;
	}

	public void setMp(int mp) {
		this.mp = mp;
	}

	public int getMaxMp() {
		if (overrideStats != null) {
			return overrideStats.getMp();
		}
		return stats.getMp();
	}

	public int getExp() {
		if (overrideStats != null) {
			return overrideStats.getExp();
		}
		return stats.getExp();
	}

	public int getLevel() {
		return stats.getLevel();
	}

	public boolean isBoss() {
		return stats.isBoss();
	}
	
	public int getAnimationTime(String name) {
		return stats.getAnimationTime(name);
	}
	
	public List<Integer> getRevives() {
		return stats.getRevives();
	}

	public void setOverrideStats(MapleMonsterStats overrideStats) {
		this.overrideStats = overrideStats;
	}

	/**
	 * 
	 * @param from the player that dealt the damage
	 * @param damage
	 */
	public void damage(MapleCharacter from, int damage, boolean updateAttackTime) {
		AttackerEntry attacker = null;

		if (from.getParty() != null) {
			attacker = new PartyAttackerEntry(from.getParty().getId(), from.getClient().getChannelServer());
		} else {
			attacker = new SingleAttackerEntry(from, from.getClient().getChannelServer());
		}

		boolean replaced = false;
		for (AttackerEntry aentry : attackers) {
			if (aentry.equals(attacker)) {
				attacker = aentry;
				replaced = true;
				break;
			}
		}
		if (!replaced) {
			attackers.add(attacker);
		}

		int rDamage = Math.max(0, Math.min(damage, this.hp));
		attacker.addDamage(from, rDamage, updateAttackTime);
		this.hp -= rDamage;
		int remhppercentage = (int) Math.ceil((this.hp * 100.0) / getMaxHp());
		if (remhppercentage < 1) {
			remhppercentage = 1;
		}
		long okTime = System.currentTimeMillis() - 4000;
		for (AttackerEntry mattacker : attackers) {
			for (AttackingMapleCharacter cattacker : mattacker.getAttackers()) {
				// current attacker is on the map of the monster
				if (cattacker.getAttacker().getMap() == from.getMap()) {
					if (cattacker.getLastAttackTime() >= okTime || isBoss()) {
						cattacker.getAttacker().getClient().getSession().write(MaplePacketCreator.showMonsterHP(getObjectId(), remhppercentage));
					}
				}
			}
		}
	}

	public boolean isAttackedBy(MapleCharacter chr) {
		for (AttackerEntry aentry : attackers) {
			if (aentry.contains(chr)) {
				return true;
			}
		}
		return false;
	}

	private void giveExpToCharacter(MapleCharacter attacker, int exp, boolean highestDamage, int numExpSharers) {
		if (highestDamage) {
			if (eventInstance != null) {
				eventInstance.monsterKilled(attacker, this);
			}
			highestDamageChar = attacker;
		}
		if (attacker.getHp() > 0) {
			if (exp > 0) {
				int personalExp = exp;
				Integer holySymbol = attacker.getBuffedValue(MapleBuffStat.HOLY_SYMBOL);
				if (holySymbol != null) {
					if (numExpSharers == 1) {
						personalExp *= 1.0 + (holySymbol.doubleValue() / 500.0);
					} else {
						personalExp *= 1.0 + (holySymbol.doubleValue() / 100.0);
					}
				}
				attacker.gainExp(personalExp, true, false, highestDamage);
			}
			attacker.mobKilled(this.getId());
		}
	}

	public MapleCharacter killBy(MapleCharacter killer) {
		// broadcastMessage(null, MaplePacketCreator.getPreKillthis(this.getObjectId()));

		// update exp
		int totalBaseExp = this.getExp() * ChannelServer.getInstance(killer.getClient().getChannel()).getExpRate();
		AttackerEntry highest = null;
		int highdamage = 0;
		for (AttackerEntry attackEntry : attackers) {
			if (attackEntry.getDamage() > highdamage) {
				highest = attackEntry;
				highdamage = attackEntry.getDamage();
			}
		}

		for (AttackerEntry attackEntry : attackers) {
			int baseExp = (int) Math.ceil(totalBaseExp * ((double) attackEntry.getDamage() / getMaxHp()));
			attackEntry.killedMob(killer.getMap(), baseExp, attackEntry == highest);
		}
		if (this.getController() != null) { // this can/should only happen when a hidden gm attacks the monster
			getController().getClient().getSession().write(
				MaplePacketCreator.stopControllingMonster(this.getObjectId()));
			getController().stopControllingMonster(this);
		}
		
		// maybe this isn't the best place to do it, fixme then
		final List<Integer> toSpawn = this.getRevives();

		if (toSpawn != null) {
			final MapleMap reviveMap = killer.getMap();
			
			TimerManager.getInstance().schedule(new Runnable() {
				public void run() {
					for(Integer mid : toSpawn) {
						MapleMonster mob = MapleLifeFactory.getMonster(mid);
						if (eventInstance != null) {
							eventInstance.registerMonster(mob);
						}
						mob.setPosition(getPosition());
						reviveMap.spawnMonster(mob);
					}
				}
			}, this.getAnimationTime("die1") - MapleMonsterInformationProvider.APPROX_FADE_DELAY);
		}
		if (eventInstance != null) {
			eventInstance.unregisterMonster(this);
		}
		for (MonsterListener listener : listeners.toArray(new MonsterListener[listeners.size()])) {
			listener.monsterKilled(this, highestDamageChar);
		}
		return highestDamageChar;
	}

	public boolean isAlive() {
		return this.hp > 0;
	}

	public MapleCharacter getController() {
		return controller.get();
	}

	public void setController(MapleCharacter controller) {
		this.controller = new WeakReference<MapleCharacter>(controller);
	}
	
	public void switchController (MapleCharacter newController, boolean immediateAggro) {
		MapleCharacter controller = getController();
		if (controller == newController) {
			return;
		}
		if (controller != null) {
			controller.stopControllingMonster(this);
			controller.getClient().getSession().write(MaplePacketCreator.stopControllingMonster(getObjectId()));
		}
		newController.controlMonster(this, immediateAggro);
		setController(newController);
		if (immediateAggro) {
			setControllerHasAggro(true);
		}
		setControllerKnowsAboutAggro(false);
	}
	
	public void addListener (MonsterListener listener) {
		listeners.add(listener);
	}
	
	public void removeListener (MonsterListener listener) {
		listeners.remove(listener);
	}

	public boolean isControllerHasAggro() {
		return controllerHasAggro;
	}

	public void setControllerHasAggro(boolean controllerHasAggro) {
		this.controllerHasAggro = controllerHasAggro;
	}

	public boolean isControllerKnowsAboutAggro() {
		return controllerKnowsAboutAggro;
	}

	public void setControllerKnowsAboutAggro(boolean controllerKnowsAboutAggro) {
		this.controllerKnowsAboutAggro = controllerKnowsAboutAggro;
	}

	@Override
	public void sendSpawnData(MapleClient client) {
		if (!isAlive()) {
			return;
		}
		client.getSession().write(MaplePacketCreator.spawnMonster(this, false));
		if (stati.size() > 0) {
			for (MonsterStatusEffect mse : activeEffects) {
				MaplePacket packet = MaplePacketCreator.applyMonsterStatus(getObjectId(), mse.getStati(), mse.getSkill().getId(), false, 0);
				client.getSession().write(packet);
			}
		}
	}

	@Override
	public void sendDestroyData(MapleClient client) {
		client.getSession().write(MaplePacketCreator.killMonster(getObjectId(), false));
	}

	@Override
	public String toString() {
		return getName() + "(" + getId() + ") at " + getPosition().x + "/" + getPosition().y + " with " + getHp() + "/" + getMaxHp() +
			"hp, " + getMp() + "/" + getMaxMp() + " mp (alive: " + isAlive() + " oid: " + getObjectId() + ")";
	}

	@Override
	public MapleMapObjectType getType() {
		return 	MapleMapObjectType.MONSTER;
	}

	public EventInstanceManager getEventInstance() {
		return eventInstance;
	}

	public void setEventInstance(EventInstanceManager eventInstance) {
		this.eventInstance = eventInstance;
	}

	public boolean isMobile() {
		return stats.isMobile();
	}

	public ElementalEffectiveness getEffectiveness (Element e) {
		if (activeEffects.size() > 0 && stati.get(MonsterStatus.DOOM) != null) {
			return ElementalEffectiveness.NORMAL; // like blue snails
		}
		return stats.getEffectiveness(e);
	}
	
	public boolean applyStatus (MapleCharacter from, final MonsterStatusEffect status, boolean poison, long duration) {
		switch (stats.getEffectiveness(status.getSkill().getElement())) {
			case IMMUNE:
			case STRONG:
				return false;
			case NORMAL:
			case WEAK:
				break;
			default:
				throw new RuntimeException("Unknown elemental effectiveness: " + stats.getEffectiveness(status.getSkill().getElement()));
		}
		// compos don't have an elemental (they have 2 - so we have to hack here...)
		if (status.getSkill().getId() == 2111006) { // fp compo
			ElementalEffectiveness effectiveness = stats.getEffectiveness(Element.POISON);
			if (effectiveness == ElementalEffectiveness.IMMUNE || effectiveness == ElementalEffectiveness.STRONG) {
				return false;
			}
		} else if (status.getSkill().getId() == 2211006) { // il compo
			ElementalEffectiveness effectiveness = stats.getEffectiveness(Element.ICE);
			if (effectiveness == ElementalEffectiveness.IMMUNE || effectiveness == ElementalEffectiveness.STRONG) {
				return false;
			}
		}
		if (poison && getHp() <= 1) {
			return false;
		}

		if (isBoss()) {
			return false;
		}
		
		for (MonsterStatus stat : status.getStati().keySet()) {
			MonsterStatusEffect oldEffect = stati.get(stat);
			if (oldEffect != null) {
				oldEffect.removeActiveStatus(stat);
				if (oldEffect.getStati().size() == 0) {
					oldEffect.getCancelTask().cancel(false);
					oldEffect.cancelPoisonSchedule();
					activeEffects.remove(oldEffect);
				}
			}
		}
		TimerManager timerManager = TimerManager.getInstance();
		final Runnable cancelTask = new Runnable() {
			@Override
			public void run() {
				if (isAlive()) {
					MaplePacket packet = MaplePacketCreator.cancelMonsterStatus(getObjectId(), status.getStati());
					map.broadcastMessage(packet, getPosition());
					if (getController() != null && !getController().isMapObjectVisible(MapleMonster.this)) {
						getController().getClient().getSession().write(packet);
					}
				}
				activeEffects.remove(status);
				for (MonsterStatus stat : status.getStati().keySet()) {
					stati.remove(stat);
				}
				status.cancelPoisonSchedule();
			}
		};
		if (poison) {
			int poisonLevel = from.getSkillLevel(status.getSkill());
			int poisonDamage = Math.min(Short.MAX_VALUE, (int)(getMaxHp() / (70.0 - poisonLevel) + 0.999));
			status.setValue(MonsterStatus.POISON, Integer.valueOf(poisonDamage));
			status.setPoisonSchedule(timerManager.register(new PoisonTask(poisonDamage, from, status, cancelTask, false), 1000, 1000));
		} else if (status.getSkill().getId() == 4111003) { // shadow web
			int webDamage = (int) (getMaxHp() / 50.0 + 0.999);
			// actually shadow web works different but similar...
			status.setPoisonSchedule(timerManager.schedule(new PoisonTask(webDamage, from, status, cancelTask, true), 3500));
		}
		for (MonsterStatus stat : status.getStati().keySet()) {
			stati.put(stat, status);
		}
		activeEffects.add(status);

		int animationTime = status.getSkill().getAnimationTime();
		MaplePacket packet = MaplePacketCreator.applyMonsterStatus(getObjectId(), status.getStati(), status.getSkill().getId(), false, 0);
		map.broadcastMessage(packet, getPosition());
		if (getController() != null && !getController().isMapObjectVisible(this)) {
			getController().getClient().getSession().write(packet);
		}
		ScheduledFuture<?> schedule = timerManager.schedule(cancelTask, duration + animationTime);
		status.setCancelTask(schedule);
		return true;
	}
	
	private final class PoisonTask implements Runnable {
		private final int poisonDamage;
		private final MapleCharacter chr;
		private final MonsterStatusEffect status;
		private final Runnable cancelTask;
		private final boolean shadowWeb;
		private final MapleMap map; 
		
		private PoisonTask(int poisonDamage, MapleCharacter chr, MonsterStatusEffect status, Runnable cancelTask, boolean shadowWeb) {
			this.poisonDamage = poisonDamage;
			this.chr = chr;
			this.status = status;
			this.cancelTask = cancelTask;
			this.shadowWeb = shadowWeb;
			this.map = chr.getMap();
		}
		
		@Override
		public void run() {
			int damage = poisonDamage;
			if (damage >= hp) {
				damage = hp - 1;
				if (!shadowWeb) {
					cancelTask.run();
					status.getCancelTask().cancel(false);
				}
			}
			if (hp > 1 && damage > 0) {
				damage(chr, damage, false);
				if (shadowWeb) {
					map.broadcastMessage(MaplePacketCreator.damageMonster(getObjectId(), damage), getPosition());
				}
			}
		}
	}
	
	public String getName() {
		return stats.getName();
	}

	private class AttackingMapleCharacter {
		private MapleCharacter attacker;
		private long lastAttackTime;
				
		public AttackingMapleCharacter(MapleCharacter attacker, long lastAttackTime) {
			super();
			this.attacker = attacker;
			this.lastAttackTime = lastAttackTime;
		}

		public long getLastAttackTime() {
			return lastAttackTime;
		}

		public void setLastAttackTime(long lastAttackTime) {
			this.lastAttackTime = lastAttackTime;
		}

		public MapleCharacter getAttacker() {
			return attacker;
		}
	}
	
	private interface AttackerEntry {
		List<AttackingMapleCharacter> getAttackers();

		public void addDamage(MapleCharacter from, int damage, boolean updateAttackTime);

		public int getDamage();

		public boolean contains(MapleCharacter chr);

		public void killedMob(MapleMap map, int baseExp, boolean mostDamage);
	}

	private class SingleAttackerEntry implements AttackerEntry {
		private int damage;
		private int chrid;
		private long lastAttackTime;
		private ChannelServer cserv;

		public SingleAttackerEntry(MapleCharacter from, ChannelServer cserv) {
			this.chrid = from.getId();
			this.cserv = cserv;
		}

		@Override
		public void addDamage(MapleCharacter from, int damage, boolean updateAttackTime) {
			if (chrid == from.getId()) {
				this.damage += damage;
			} else {
				throw new IllegalArgumentException("Not the attacker of this entry");
			}
			if (updateAttackTime) {
				lastAttackTime = System.currentTimeMillis();
			}
		}

		@Override
		public List<AttackingMapleCharacter> getAttackers() {
			MapleCharacter chr = cserv.getPlayerStorage().getCharacterById(chrid);
			if (chr != null) {
				return Collections.singletonList(new AttackingMapleCharacter(chr, lastAttackTime));
			} else {
				return Collections.emptyList();
			}
		}

		@Override
		public boolean contains(MapleCharacter chr) {
			return chrid == chr.getId();
		}

		@Override
		public int getDamage() {
			return damage;
		}

		@Override
		public void killedMob(MapleMap map, int baseExp, boolean mostDamage) {
			MapleCharacter chr = cserv.getPlayerStorage().getCharacterById(chrid);
			if (chr != null && chr.getMap() == map) {
				giveExpToCharacter(chr, baseExp, mostDamage, 1);
			}
		}

		@Override
		public int hashCode() {
			return chrid;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			final SingleAttackerEntry other = (SingleAttackerEntry) obj;
			return chrid == other.chrid;
		}
	}

	private static class OnePartyAttacker {
		public MapleParty lastKnownParty;
		public int damage;
		public long lastAttackTime;
		
		public OnePartyAttacker(MapleParty lastKnownParty, int damage) {
			super();
			this.lastKnownParty = lastKnownParty;
			this.damage = damage;
			this.lastAttackTime = System.currentTimeMillis();
		}
	}
	
	private class PartyAttackerEntry implements AttackerEntry {
		private int totDamage;
		//private Map<String, Pair<Integer, MapleParty>> attackers;
		private Map<Integer, OnePartyAttacker> attackers;
		private int partyid;
		private ChannelServer cserv;

		public PartyAttackerEntry(int partyid, ChannelServer cserv) {
			this.partyid = partyid;
			this.cserv = cserv;
			attackers = new HashMap<Integer, OnePartyAttacker>(6);
		}

		public List<AttackingMapleCharacter> getAttackers() {
			List<AttackingMapleCharacter> ret = new ArrayList<AttackingMapleCharacter>(attackers.size());
			for (Entry<Integer, OnePartyAttacker> entry : attackers.entrySet()) {
				MapleCharacter chr = cserv.getPlayerStorage().getCharacterById(entry.getKey());
				if (chr != null) {
					ret.add(new AttackingMapleCharacter(chr, entry.getValue().lastAttackTime));
				}
			}
			return ret;
		}

		private Map<MapleCharacter, OnePartyAttacker> resolveAttackers() {
			Map<MapleCharacter, OnePartyAttacker> ret = new HashMap<MapleCharacter, OnePartyAttacker>(attackers.size());
			for (Entry<Integer, OnePartyAttacker> aentry : attackers.entrySet()) {
				MapleCharacter chr = cserv.getPlayerStorage().getCharacterById(aentry.getKey());
				if (chr != null) {
					ret.put(chr, aentry.getValue());
				}
			}
			return ret;
		}

		@Override
		public boolean contains(MapleCharacter chr) {
			return attackers.containsKey(chr.getId());
		}

		@Override
		public int getDamage() {
			return totDamage;
		}

		public void addDamage(MapleCharacter from, int damage, boolean updateAttackTime) {
			OnePartyAttacker oldPartyAttacker = attackers.get(from.getId());
			if (oldPartyAttacker != null) {
				oldPartyAttacker.damage += damage;
				oldPartyAttacker.lastKnownParty = from.getParty();
				if (updateAttackTime) {
					oldPartyAttacker.lastAttackTime = System.currentTimeMillis();
				}
			} else {
				// TODO actually this causes wrong behaviour when the party changes between attacks
				// only the last setup will get exp - but otherwise we'd have to store the full party
				// constellation for every attack/everytime it changes, might be wanted/needed in the
				// future but not now
				OnePartyAttacker onePartyAttacker = new OnePartyAttacker(from.getParty(), damage);
				attackers.put(from.getId(), onePartyAttacker);
				if (!updateAttackTime) {
					onePartyAttacker.lastAttackTime = 0;
				}
			}
			totDamage += damage;
		}

		@Override
		public void killedMob(MapleMap map, int baseExp, boolean mostDamage) {
			Map<MapleCharacter, OnePartyAttacker> attackers = resolveAttackers();

			MapleCharacter highest = null;
			int highestDamage = 0;

			Map<MapleCharacter, Integer> expMap = new ArrayMap<MapleCharacter, Integer>(6);
			for (Entry<MapleCharacter, OnePartyAttacker> attacker : attackers.entrySet()) {
				MapleParty party = attacker.getValue().lastKnownParty;
				double averagePartyLevel = 0;

				List<MapleCharacter> expApplicable = new ArrayList<MapleCharacter>();
				for (MaplePartyCharacter partychar : party.getMembers()) {
					if (attacker.getKey().getLevel() - partychar.getLevel() <= 5 ||
						getLevel() - partychar.getLevel() <= 5) {
						MapleCharacter pchr = cserv.getPlayerStorage().getCharacterByName(partychar.getName());
						if (pchr != null) {
							if (pchr.isAlive() && pchr.getMap() == map) {
								expApplicable.add(pchr);
								averagePartyLevel += pchr.getLevel();
							}
						}
					}
				}
				double expBonus = 1.0;
				if (expApplicable.size() > 1) {
					expBonus = 1.10 + 0.05 * expApplicable.size();
					averagePartyLevel /= expApplicable.size();
				}

				int iDamage = attacker.getValue().damage;
				if (iDamage > highestDamage) {
					highest = attacker.getKey();
					highestDamage = iDamage;
				}
				double innerBaseExp = baseExp * ((double) iDamage / totDamage);
				double expFraction = (innerBaseExp * expBonus) / (expApplicable.size() + 1);

				for (MapleCharacter expReceiver : expApplicable) {
					Integer oexp = expMap.get(expReceiver);
					int iexp;
					if (oexp == null) {
						iexp = 0;
					} else {
						iexp = oexp.intValue();
					}
					double expWeight = (expReceiver == attacker.getKey() ? 2.0 : 1.0);
					double levelMod = expReceiver.getLevel() / averagePartyLevel;
					if (levelMod > 1.0 || this.attackers.containsKey(expReceiver.getId())) {
						levelMod = 1.0;
					}
					iexp += (int) Math.round(expFraction * expWeight * levelMod);
					expMap.put(expReceiver, Integer.valueOf(iexp));
				}
			}
			// FUCK we are done -.-
			for (Entry<MapleCharacter, Integer> expReceiver : expMap.entrySet()) {
				boolean white = mostDamage ? expReceiver.getKey() == highest : false;
				giveExpToCharacter(expReceiver.getKey(), expReceiver.getValue(), white, expMap.size());
			}
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + partyid;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			final PartyAttackerEntry other = (PartyAttackerEntry) obj;
			if (partyid != other.partyid)
				return false;
			return true;
		}
	}
}
