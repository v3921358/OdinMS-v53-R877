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

package net.sf.odinms.server.maps;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import net.sf.odinms.client.Equip;
import net.sf.odinms.client.IItem;
import net.sf.odinms.client.Item;
import net.sf.odinms.client.MapleBuffStat;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.client.messages.MessageCallback;
import net.sf.odinms.client.status.MonsterStatus;
import net.sf.odinms.client.status.MonsterStatusEffect;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.net.world.MaplePartyCharacter;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.MaplePortal;
import net.sf.odinms.server.MapleStatEffect;
import net.sf.odinms.server.TimerManager;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.server.life.MapleNPC;
import net.sf.odinms.server.life.SpawnPoint;
import net.sf.odinms.tools.MaplePacketCreator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapleMap {
	private static final int MAX_OID = 20000;
	private static final List<MapleMapObjectType> rangedMapobjectTypes = Arrays.asList(MapleMapObjectType.ITEM,
		MapleMapObjectType.MONSTER, MapleMapObjectType.DOOR, MapleMapObjectType.SUMMON);

	/**
	 * Holds a mapping of all oid -> MapleMapObject on this map. mapobjects is NOT a synchronized collection since it
	 * has to be synchronized together with runningOid that's why all access to mapobjects have to be done trough an
	 * explicit synchronized block
	 */
	private Map<Integer, MapleMapObject> mapobjects = new LinkedHashMap<Integer, MapleMapObject>();
	private Collection<SpawnPoint> monsterSpawn = new LinkedList<SpawnPoint>();
	private AtomicInteger spawnedMonstersOnMap = new AtomicInteger(0);
	private Collection<MapleCharacter> characters = new LinkedHashSet<MapleCharacter>();
	private Map<Integer, MaplePortal> portals = new HashMap<Integer, MaplePortal>();
	private MapleFootholdTree footholds = null;
	private int mapid;
	private int runningOid = 100;
	private int returnMapId;
	private int channel;
	private float monsterRate;
	private boolean dropsDisabled = false;
	private String mapName;
	private String streetName;
	private static Logger log = LoggerFactory.getLogger(MapleMap.class);

	public MapleMap(int mapid, int channel, int returnMapId, float monsterRate) {
		this.mapid = mapid;
		this.channel = channel;
		this.returnMapId = returnMapId;
		if (monsterRate > 0) {
			this.monsterRate = monsterRate;
			boolean greater1 = monsterRate > 1.0;
			this.monsterRate = (float) Math.abs(1.0 - this.monsterRate);
			this.monsterRate = this.monsterRate / 2.0f;
			if (greater1) {
				this.monsterRate = 1.0f + this.monsterRate;
			} else {
				this.monsterRate = 1.0f - this.monsterRate;
			}
			TimerManager.getInstance().register(new RespawnWorker(), 5001 + (int) (30.0 * Math.random()));
		}
	}

	public void toggleDrops() {
		dropsDisabled = !dropsDisabled;
	}

	public int getId() {
		return mapid;
	}

	public MapleMap getReturnMap() {
		return ChannelServer.getInstance(channel).getMapFactory().getMap(returnMapId);
	}

	public void addMapObject(MapleMapObject mapobject) {
		synchronized (this.mapobjects) {
			mapobject.setObjectId(runningOid);
			this.mapobjects.put(Integer.valueOf(runningOid), mapobject);
			incrementRunningOid();
		}
	}

	private void spawnAndAddRangedMapObject(MapleMapObject mapobject, DelayedPacketCreation packetbakery) {
		spawnAndAddRangedMapObject(mapobject, packetbakery, null);
	}
	
	private void spawnAndAddRangedMapObject(MapleMapObject mapobject, DelayedPacketCreation packetbakery, SpawnCondition condition) {
		synchronized (this.mapobjects) {
			mapobject.setObjectId(runningOid);

			synchronized (characters) {
				for (MapleCharacter chr : characters) {
					if (condition == null || condition.canSpawn(chr)) {
						if (chr.getPosition().distanceSq(mapobject.getPosition()) <= MapleCharacter.MAX_VIEW_RANGE_SQ) {
							packetbakery.sendPackets(chr.getClient());
							chr.addVisibleMapObject(mapobject);
						}
					}
				}
			}

			this.mapobjects.put(Integer.valueOf(runningOid), mapobject);
			incrementRunningOid();
		}
	}
	
	private void incrementRunningOid() {
		runningOid++;
		for (int numIncrements = 1; numIncrements < MAX_OID; numIncrements++) {
			if (runningOid > MAX_OID) {
				runningOid = 100;
			}
			if (this.mapobjects.containsKey(Integer.valueOf(runningOid))) {
				runningOid++;
			} else {
				return;
			}
		}
		throw new RuntimeException("Out of OIDs on map " + mapid + " (channel: " + channel + ")");
	}

	public void removeMapObject(int num) {
		synchronized (this.mapobjects) {
			/*if (!mapobjects.containsKey(Integer.valueOf(num))) {
				log.warn("Removing: mapobject {} does not exist on map {}", Integer.valueOf(num), Integer
					.valueOf(getId()));
			}*/
			this.mapobjects.remove(Integer.valueOf(num));
		}
	}

	public void removeMapObject(MapleMapObject obj) {
		removeMapObject(obj.getObjectId());
	}
	
	private Point calcPointBelow (Point initial) {
		MapleFoothold fh = footholds.findBelow(initial);
		if (fh == null) {
			return null;
		} 
		int dropY = fh.getY1();
		if (!fh.isWall() && fh.getY1() != fh.getY2()) {
			double s1 = Math.abs(fh.getY2() - fh.getY1());
			double s2 = Math.abs(fh.getX2() - fh.getX1());
			double s4 = Math.abs(initial.x - fh.getX1());
			double alpha = Math.atan(s2 / s1);
			double beta = Math.atan(s1 / s2);
			double s5 = Math.cos(alpha) * (s4 / Math.cos(beta));
			if (fh.getY2() < fh.getY1()) {
				dropY = fh.getY1() - (int) s5;
			} else {
				dropY = fh.getY1() + (int) s5;
			}
		}
		return new Point(initial.x, dropY);
	}

	private Point calcDropPos(Point initial, Point fallback) {
		Point ret = calcPointBelow(new Point(initial.x, initial.y - 99));
		if (ret == null) return fallback;
		return ret;
	}

	private void dropFromMonster(MapleCharacter dropOwner, MapleMonster monster) {
		if (dropsDisabled)
			return;
		/*
		 * drop logic: decide based on monster what the max drop count is get drops (not allowed: multiple mesos,
		 * multiple items of same type exception: event drops) calculate positions
		 */
		int maxDrops;
		MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
		final boolean isBoss = monster.isBoss();
		if (isBoss) {
			maxDrops = 10;
		} else {
			maxDrops = 4;
		}

		List<Integer> toDrop = new ArrayList<Integer>();
		for (int i = 0; i < maxDrops; i++) {
			toDrop.add(monster.getDrop());
		}

		Set<Integer> alreadyDropped = new HashSet<Integer>();
		for (int i = 0; i < toDrop.size(); i++) {
			if (toDrop.get(i) == -1) {
				if (alreadyDropped.contains(-1) && !isBoss) {
					toDrop.remove(i);
					i--;
				} else {
					alreadyDropped.add(-1);
				}
			} else {
				MapleInventoryType type = ii.getInventoryType(toDrop.get(i));
				if (alreadyDropped.contains((int) type.getType()) && !isBoss) {
					toDrop.remove(i);
					i--;
				} else {
					alreadyDropped.add((int) type.getType());
				}
			}
		}

		if (toDrop.size() > maxDrops) {
			toDrop = toDrop.subList(0, maxDrops);
		}
		Point[] toPoint = new Point[toDrop.size()];
		int shiftDirection = 0;
		int shiftCount = 0;
		
		int curX = Math.min(Math.max(monster.getPosition().x - 25 * (toDrop.size() / 2), footholds.getMinDropX() + 25),
			footholds.getMaxDropX() - toDrop.size() * 25);
		int curY = Math.max(monster.getPosition().y, footholds.getY1());
		//int monsterShift = curX - 
		while (shiftDirection < 3 && shiftCount < 1000) {
			// TODO for real center drop the monster width is needed o.o"
			if (shiftDirection == 1)
				curX += 25;
			else if (shiftDirection == 2)
				curX -= 25;
			// now do it
			for (int i = 0; i < toDrop.size(); i++) {
				MapleFoothold wall = footholds.findWall(new Point(curX, curY), new Point(curX + toDrop.size() * 25, curY));
				if (wall != null) {						
					//System.out.println("found a wall. wallX " + wall.getX1() + " curX " + curX);
					if (wall.getX1() < curX) {
						shiftDirection = 1;
						shiftCount++;
						break;
					} else if (wall.getX1() == curX) {
						if (shiftDirection == 0)
							shiftDirection = 1;
						shiftCount++;
						break;
					} else {
						shiftDirection = 2;
						shiftCount++;
						break;
					}
				} else if (i == toDrop.size() - 1) {
					//System.out.println("ok " + curX);
					shiftDirection = 3;				
				}
				final Point dropPos = calcDropPos(new Point(curX + i * 25, curY), new Point(monster.getPosition()));
				toPoint[i] = new Point(curX + i * 25, curY);
				final int drop = toDrop.get(i);

				if (drop == -1) { // meso
					final int mesoRate = ChannelServer.getInstance(dropOwner.getClient().getChannel()).getMesoRate();
					Random r = new Random();
					double mesoDecrease = Math.pow(0.93, monster.getExp() / 300.0);
					if (mesoDecrease > 1.0) {
						mesoDecrease = 1.0;
					}
					int tempmeso = Math.min(30000, (int) (mesoDecrease * (monster.getExp()) *
						(1.0 + r.nextInt(20)) / 10.0));
					if(dropOwner.getBuffedValue(MapleBuffStat.MESOUP) != null) {
						tempmeso = (int) (tempmeso * dropOwner.getBuffedValue(MapleBuffStat.MESOUP).doubleValue() / 100.0);
					}
					
					final int meso = tempmeso;
					
					if (meso > 0) {
						final MapleMonster dropMonster = monster;
						final MapleCharacter dropChar = dropOwner;
						TimerManager.getInstance().schedule(new Runnable() {
							public void run() {
								spawnMesoDrop(meso * mesoRate, meso, dropPos, dropMonster, dropChar, isBoss);
							}
						}, monster.getAnimationTime("die1"));
					}
				} else {
					IItem idrop;
					MapleInventoryType type = ii.getInventoryType(drop);
					if (type.equals(MapleInventoryType.EQUIP)) {
						Equip nEquip = ii.randomizeStats((Equip) ii.getEquipById(drop));
						idrop = nEquip;
					} else {
						idrop = new Item(drop, (byte) 0, (short) 1);
						// Randomize quantity for certain items
						if (ii.isArrowForBow(drop) || ii.isArrowForCrossBow(drop) || ii.isThrowingStar(drop))
							idrop.setQuantity((short) (1 + ii.getSlotMax(drop) * Math.random()));
					}

					StringBuilder logMsg = new StringBuilder("Created as a drop from monster ");
					logMsg.append(monster.getObjectId());
					logMsg.append(" (");
					logMsg.append(monster.getId());
					logMsg.append(") at ");
					logMsg.append(dropPos.toString());
					logMsg.append(" on map ");
					logMsg.append(mapid);
					idrop.log(logMsg.toString(),false);

					final MapleMapItem mdrop = new MapleMapItem(idrop, dropPos, monster, dropOwner);
					final MapleMapObject dropMonster = monster;
					final MapleCharacter dropChar = dropOwner;
					final TimerManager tMan = TimerManager.getInstance();

					tMan.schedule(new Runnable() {
						public void run() {
							spawnAndAddRangedMapObject(mdrop, new DelayedPacketCreation() {
								public void sendPackets(MapleClient c) {
									c.getSession().write(MaplePacketCreator.dropItemFromMapObject(drop, mdrop.getObjectId(), dropMonster
										.getObjectId(), isBoss ? 0 : dropChar.getId(), dropMonster.getPosition(), dropPos, (byte) 1));
								}
							});

							tMan.schedule(new ExpireMapItemJob(mdrop), 60000);
						}
					}, monster.getAnimationTime("die1"));

				}
			}
		}
	}

	public boolean damageMonster(MapleCharacter chr, MapleMonster monster, int damage) {
		// double checking to potentially avoid synchronisation overhead
		if (monster.isAlive()) {
			boolean killMonster = false;

			synchronized (monster) {
				if (!monster.isAlive())
					return false;
				if (damage > 0) {
					monster.damage(chr, damage, true);
					if (!monster.isAlive()) { // monster just died
						killMonster = true;
					}
				}
			}
			// the monster is dead, as damageMonster returns immediately for dead monsters this makes
			// this block implicitly synchronized for ONE monster
			if (killMonster) {
				killMonster (monster, chr, true);
			}
			return true;
		}
		return false;
	}
	
	public void killMonster (MapleMonster monster, MapleCharacter chr, boolean withDrops) {
		spawnedMonstersOnMap.decrementAndGet();
		monster.setHp(0);
		broadcastMessage(MaplePacketCreator.killMonster(monster.getObjectId(), true), monster.getPosition());
		removeMapObject(monster);
		MapleCharacter dropOwner = monster.killBy(chr);
		if (withDrops) {
			if (dropOwner == null) {
				dropOwner = chr;
			}
			dropFromMonster(dropOwner, monster);
		}
	}

	/**
	 * Automagically finds a new controller for the given monster from the chars on the map...
	 * 
	 * @param monster
	 */
	private void updateMonsterController(MapleMonster monster) {
		synchronized (monster) {
			if (!monster.isAlive()) {
				return;
			}
			if (monster.getController() != null) {
				// monster has a controller already, check if he's still on this map
				if (monster.getController().getMap() != this) {
					log.warn("Monstercontroller wasn't on same map");
					monster.getController().stopControllingMonster(monster);
				} else {
					// controller is on the map, monster has an controller, everything is fine
					return;
				}
			}
			int mincontrolled = -1;
			MapleCharacter newController = null;
			synchronized (characters) {
				for (MapleCharacter chr : characters) {
					if (!chr.isHidden() && (chr.getControlledMonsters().size() < mincontrolled || mincontrolled == -1)) {
						if (!chr.getName().equals("FaekChar")) { // TODO remove me for production release
							mincontrolled = chr.getControlledMonsters().size();
							newController = chr;
						}
					}
				}
			}
			if (newController != null) // was a new controller found? (if not no one is on the map)
			{
				newController.controlMonster(monster, false);
			}
		}
	}

	public Collection<MapleMapObject> getMapObjects() {
		return Collections.unmodifiableCollection(mapobjects.values());
	}
	
	public boolean containsNPC(int npcid) {
		synchronized (mapobjects) {
			for (MapleMapObject obj : mapobjects.values()) {
				if (obj.getType() == MapleMapObjectType.NPC) {
					if (((MapleNPC) obj).getId() == npcid) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public MapleMapObject getMapObject(int oid) {
		return mapobjects.get(oid);
	}

	/**
	 * returns a monster with the given oid, if no such monster exists returns null
	 * 
	 * @param oid
	 * @return
	 */
	public MapleMonster getMonsterByOid(int oid) {
		MapleMapObject mmo = getMapObject(oid);
		if (mmo == null) {
			return null;
		}
		if (mmo.getType() == MapleMapObjectType.MONSTER) {
			return (MapleMonster) mmo;
		}
		return null;
	}

	public void spawnMonsterOnGroudBelow(MapleMonster mob, Point pos) {
		Point spos = new Point(pos.x, pos.y - 1);
		spos = calcPointBelow(spos);
		spos.y -= 1;
		mob.setPosition(spos);
		spawnMonster(mob);
	}	

	public void spawnMonster(final MapleMonster monster) {
		monster.setMap(this);
		synchronized (this.mapobjects) {
			spawnAndAddRangedMapObject(monster, new DelayedPacketCreation() {
				public void sendPackets(MapleClient c) {
					c.getSession().write(MaplePacketCreator.spawnMonster(monster, true));
				}
			});
			updateMonsterController(monster);
		}
		spawnedMonstersOnMap.incrementAndGet();
	}
	
	public void spawnDoor(final MapleDoor door) {
		synchronized (this.mapobjects) {
			spawnAndAddRangedMapObject(door, new DelayedPacketCreation() {
				public void sendPackets(MapleClient c) {
					c.getSession().write(MaplePacketCreator.spawnDoor(door.getOwner().getId(), door.getTargetPosition(), false));
					if (door.getOwner().getParty() != null && (door.getOwner() == c.getPlayer() || door.getOwner().getParty().containsMembers(new MaplePartyCharacter(c.getPlayer())))) {
						c.getSession().write(MaplePacketCreator.partyPortal(door.getTown().getId(), door.getTarget().getId(), door.getTargetPosition()));
					}
					c.getSession().write(MaplePacketCreator.spawnPortal(door.getTown().getId(), door.getTarget().getId(), door.getTargetPosition()));
					c.getSession().write(MaplePacketCreator.enableActions());
				}
			}, new SpawnCondition() {

				public boolean canSpawn(MapleCharacter chr) {
					return chr.getMapId() == door.getTarget().getId() || 
						chr == door.getOwner() && chr.getParty() == null;
				}
				
			});
		}
	}
	
	public void spawnSummon(final MapleSummon summon) {
		spawnAndAddRangedMapObject(summon, new DelayedPacketCreation() {
			public void sendPackets(MapleClient c) {
				int skilLlevel = summon.getOwner().getSkillLevel(SkillFactory.getSkill(summon.getSkill()));
				c.getSession().write(MaplePacketCreator.spawnSpecialMapObject(summon.getOwner(), summon.getSkill(), skilLlevel, summon.getPosition(), summon.getMovementType(), true));
			}
		});
	}
	
	public void spawnMist (final MapleMist mist, final int duration, boolean poison) {
		addMapObject(mist);
		broadcastMessage(mist.makeSpawnData());
		TimerManager tMan = TimerManager.getInstance();
		final ScheduledFuture<?> poisonSchedule;
		if (poison) {
			Runnable poisonTask = new Runnable() {
				@Override
				public void run() {
					List<MapleMapObject> affectedMonsters = getMapObjectsInBox(mist.getBox(), Collections.singletonList(MapleMapObjectType.MONSTER));
					for (MapleMapObject mo : affectedMonsters) {
						if (mist.makeChanceResult()) {
							MonsterStatusEffect poisonEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.POISON, 1), mist.getSourceSkill(), false);
							((MapleMonster) mo).applyStatus(mist.getOwner(), poisonEffect, true, duration);
						}
					}
				}
			};
			poisonSchedule = tMan.register(poisonTask, 2000, 2500);
		} else {
			poisonSchedule = null;
		}

		tMan.schedule(new Runnable() {
			@Override
			public void run() {
				removeMapObject(mist);
				if (poisonSchedule != null) {
					poisonSchedule.cancel(false);
				}
				broadcastMessage(mist.makeDestroyData());
			}
		}, duration);
	}

	public void spawnItemDrop(final MapleMapObject dropper, final MapleCharacter owner, final IItem item, Point pos, final boolean ffaDrop, final boolean expire) {
		TimerManager tMan = TimerManager.getInstance();
		final Point droppos = calcDropPos(pos, pos);
		final MapleMapItem drop = new MapleMapItem(item, droppos, dropper, owner);
		spawnAndAddRangedMapObject(drop, new DelayedPacketCreation() {
			public void sendPackets(MapleClient c) {
				c.getSession().write(MaplePacketCreator.dropItemFromMapObject(item.getItemId(), drop.getObjectId(), 0, ffaDrop ? 0 : owner.getId(),
					dropper.getPosition(), droppos, (byte) 1));
			}
		});
		broadcastMessage(MaplePacketCreator.dropItemFromMapObject(item.getItemId(), drop.getObjectId(), 0, ffaDrop ? 0
			: owner.getId(), dropper.getPosition(), droppos, (byte) 0), drop.getPosition());

		if (expire) {
			tMan.schedule(new ExpireMapItemJob(drop), 60000);
		}
	}

	public void spawnMesoDrop(final int meso, final int displayMeso, Point position, final MapleMapObject dropper, final MapleCharacter owner, final boolean ffaLoot) {
		TimerManager tMan = TimerManager.getInstance();
		final Point droppos = calcDropPos(position, position);
		final MapleMapItem mdrop = new MapleMapItem(meso, displayMeso, droppos, dropper, owner);
		spawnAndAddRangedMapObject(mdrop, new DelayedPacketCreation() {
			public void sendPackets(MapleClient c) {
				c.getSession().write(MaplePacketCreator.dropMesoFromMapObject(displayMeso, mdrop.getObjectId(), dropper.getObjectId(),
					ffaLoot ? 0 : owner.getId(), dropper.getPosition(), droppos, (byte) 1));
			}
		});
		tMan.schedule(new ExpireMapItemJob(mdrop), 60000);
	}

	/**
	 * Adds a player to this map and sends nescessary data
	 * 
	 * @param chr
	 */
	public void addPlayer(MapleCharacter chr) {
		//log.warn("[dc] [level2] Player {} enters map {}", new Object[] { chr.getName(), mapid });
		synchronized (characters) {
			this.characters.add(chr);
		}
		synchronized (this.mapobjects) {
			if (!chr.isHidden()) {
				broadcastMessage(chr, (MaplePacketCreator.spawnPlayerMapobject(chr)), false);
			}
			sendObjectPlacement(chr.getClient());
			// spawn self
			chr.getClient().getSession().write(MaplePacketCreator.spawnPlayerMapobject(chr));
			this.mapobjects.put(Integer.valueOf(chr.getObjectId()), chr);
		}
		if (chr.getPlayerShop() != null) {
			addMapObject(chr.getPlayerShop());
		}
		MapleStatEffect summonStat = chr.getStatForBuff(MapleBuffStat.SUMMON);
		if (summonStat != null) {
			MapleSummon summon = chr.getSummons().get(summonStat.getSourceId());
			summon.setPosition(chr.getPosition());
			summon.sendSpawnData(chr.getClient());
			chr.addVisibleMapObject(summon);
			addMapObject(summon);
		}
		chr.receivePartyMemberHP();
	}

	public void removePlayer(MapleCharacter chr) {
		//log.warn("[dc] [level2] Player {} leaves map {}", new Object[] { chr.getName(), mapid });
		synchronized (characters) {
			characters.remove(chr);
		}
		removeMapObject(Integer.valueOf(chr.getObjectId()));
		broadcastMessage(MaplePacketCreator.removePlayerFromMap(chr.getId()));
		for (MapleMonster monster : chr.getControlledMonsters()) {
			monster.setController(null);
			monster.setControllerHasAggro(false);
			monster.setControllerKnowsAboutAggro(false);
			updateMonsterController(monster);
		}
		for (MapleSummon summon : chr.getSummons().values()) {
			if (summon.isPuppet()) {
				chr.cancelBuffStats(MapleBuffStat.PUPPET);
			} else {
				removeMapObject(summon);
			}
		}
	}

	/**
	 * Broadcasts the given packet to everyone on the map but the source. source = null Broadcasts to everyone
	 * 
	 * @param source
	 * @param packet
	 */
	// public void broadcastMessage(MapleCharacter source, MaplePacket packet) {
	// synchronized (characters) {
	// for (MapleCharacter chr : characters) {
	// if (chr != source) {
	// chr.getClient().getSession().write(packet);
	// }
	// }
	// }
	// }
	/**
	 * Broadcast a message to everyone in the map
	 * 
	 * @param packet
	 */
	public void broadcastMessage(MaplePacket packet) {
		broadcastMessage(null, packet, Double.POSITIVE_INFINITY, null);
	}

	/**
	 * Nonranged. Repeat to source according to parameter.
	 * 
	 * @param source
	 * @param packet
	 * @param repeatToSource
	 */
	public void broadcastMessage(MapleCharacter source, MaplePacket packet, boolean repeatToSource) {
		broadcastMessage(repeatToSource ? null : source, packet, Double.POSITIVE_INFINITY, source.getPosition());
	}

	/**
	 * Ranged and repeat according to parameters.
	 * 
	 * @param source
	 * @param packet
	 * @param repeatToSource
	 * @param ranged
	 */
	public void broadcastMessage(MapleCharacter source, MaplePacket packet, boolean repeatToSource, boolean ranged) {
		broadcastMessage(repeatToSource ? null : source, packet, ranged ? MapleCharacter.MAX_VIEW_RANGE_SQ
			: Double.POSITIVE_INFINITY, source.getPosition());
	}

	/**
	 * Always ranged from Point.
	 * 
	 * @param packet
	 * @param rangedFrom
	 */
	public void broadcastMessage(MaplePacket packet, Point rangedFrom) {
		broadcastMessage(null, packet, MapleCharacter.MAX_VIEW_RANGE_SQ, rangedFrom);
	}

	/**
	 * Always ranged from point. Does not repeat to source.
	 * 
	 * @param source
	 * @param packet
	 * @param rangedFrom
	 */
	public void broadcastMessage(MapleCharacter source, MaplePacket packet, Point rangedFrom) {
		broadcastMessage(source, packet, MapleCharacter.MAX_VIEW_RANGE_SQ, rangedFrom);
	}

	private void broadcastMessage(MapleCharacter source, MaplePacket packet, double rangeSq, Point rangedFrom) {
		synchronized (characters) {
			for (MapleCharacter chr : characters) {
				if (chr != source) {
					if (rangeSq < Double.POSITIVE_INFINITY) {
						if (rangedFrom.distanceSq(chr.getPosition()) <= rangeSq) {
							chr.getClient().getSession().write(packet);
						}
					} else {
						chr.getClient().getSession().write(packet);
					}
				}
			}
		}
	}
	
	private boolean isNonRangedType (MapleMapObjectType type) {
		switch (type) {
			case NPC:
			case PLAYER:
			case MIST:
				return true;
		}
		return false;
	}

	private void sendObjectPlacement(MapleClient mapleClient) {
		for (MapleMapObject o : mapobjects.values()) {
			if (isNonRangedType(o.getType())) {
				o.sendSpawnData(mapleClient);
			} else if (o.getType() == MapleMapObjectType.MONSTER) {
				updateMonsterController((MapleMonster) o);
			}
		}
		MapleCharacter chr = mapleClient.getPlayer();

		if (chr != null) {
			for (MapleMapObject o : getMapObjectsInRange(chr.getPosition(), MapleCharacter.MAX_VIEW_RANGE_SQ,
				rangedMapobjectTypes)) {
				o.sendSpawnData(chr.getClient());
				chr.addVisibleMapObject(o);
			}
		} else {
			log.info("sendObjectPlacement invoked with null char");
		}
	}

	public List<MapleMapObject> getMapObjectsInRange(Point from, double rangeSq, List<MapleMapObjectType> types) {
		List<MapleMapObject> ret = new LinkedList<MapleMapObject>();
		for (MapleMapObject l : mapobjects.values()) {
			if (types.contains(l.getType())) {
				if (from.distanceSq(l.getPosition()) <= rangeSq) {
					ret.add(l);
				}
			}
		}
		return ret;
	}
	
	public List<MapleMapObject> getMapObjectsInBox(Rectangle box, List<MapleMapObjectType> types) {
		List<MapleMapObject> ret = new LinkedList<MapleMapObject>();
		synchronized (mapobjects) {
			for (MapleMapObject l : mapobjects.values()) {
				if (types.contains(l.getType())) {
					if (box.contains(l.getPosition())) {
						ret.add(l);
					}
				}
			}
		}
		return ret;
	}

	public void addPortal(MaplePortal myPortal) {
		portals.put(myPortal.getId(), myPortal);
	}

	public MaplePortal getPortal(String portalname) {
		for (MaplePortal port : portals.values()) {
			if (port.getName().equals(portalname)) {
				return port;
			}
		}
		return null;
	}

	public MaplePortal getPortal(int portalid) {
		return portals.get(portalid);
	}

	public void setFootholds(MapleFootholdTree footholds) {
		this.footholds = footholds;
	}

	public MapleFootholdTree getFootholds() {
		return footholds;
	}

	/**
	 * not threadsafe, please synchronize yourself
	 * 
	 * @param monster
	 */
	public void addMonsterSpawn(MapleMonster monster, int mobTime) {
		Point newpos = calcPointBelow(monster.getPosition());
		newpos.y -= 1;
		SpawnPoint sp = new SpawnPoint(monster, newpos, mobTime);

		monsterSpawn.add(sp);
		if (sp.shouldSpawn()) {
			sp.spawnMonster(this);
		}
	}

	public float getMonsterRate() {
		return monsterRate;
	}

	public Collection<MapleCharacter> getCharacters() {
		return Collections.unmodifiableCollection(this.characters);
	}
	
	public MapleCharacter getCharacterById(int id) {
		for (MapleCharacter c : this.characters) {
			if (c.getId() == id)
				return c;
		}
		return null;
	}

	private void updateMapObjectVisibility(MapleCharacter chr, MapleMapObject mo) {
		if (!chr.isMapObjectVisible(mo)) { // monster entered view range
			if (mo.getPosition().distanceSq(chr.getPosition()) <= MapleCharacter.MAX_VIEW_RANGE_SQ) {
				chr.addVisibleMapObject(mo);
				mo.sendSpawnData(chr.getClient());
			}
		} else { // monster left view range
			if (mo.getPosition().distanceSq(chr.getPosition()) > MapleCharacter.MAX_VIEW_RANGE_SQ) {
				chr.removeVisibleMapObject(mo);
				mo.sendDestroyData(chr.getClient());
			}
		}
	}

	public void moveMonster(MapleMonster monster, Point reportedPos) {
		monster.setPosition(reportedPos);
		synchronized (characters) {
			for (MapleCharacter chr : characters) {
				updateMapObjectVisibility(chr, monster);
			}
		}
	}

	public void movePlayer(MapleCharacter player, Point newPosition) {
		player.setPosition(newPosition);
		Collection<MapleMapObject> visibleObjects = player.getVisibleMapObjects();
		MapleMapObject[] visibleObjectsNow = visibleObjects.toArray(new MapleMapObject[visibleObjects.size()]);
		for (MapleMapObject mo : visibleObjectsNow) {
			if (mapobjects.get(mo.getObjectId()) == mo) {
				updateMapObjectVisibility(player, mo);
			} else {
				player.removeVisibleMapObject(mo);
			}
		}
		for (MapleMapObject mo : getMapObjectsInRange(player.getPosition(), MapleCharacter.MAX_VIEW_RANGE_SQ,
			rangedMapobjectTypes)) {
			if (!player.isMapObjectVisible(mo)) {
				mo.sendSpawnData(player.getClient());
				player.addVisibleMapObject(mo);
			}
		}
	}

	public MaplePortal findClosestSpawnpoint(Point from) {
		MaplePortal closest = null;
		double shortestDistance = Double.POSITIVE_INFINITY;
		for (MaplePortal portal : portals.values()) {
			double distance = portal.getPosition().distanceSq(from);
			if (portal.getType() >= 0 && portal.getType() <= 2 && distance < shortestDistance) {
				closest = portal;
				shortestDistance = distance;
			}
		}
		return closest;
	}
	
	public void spawnDebug(MessageCallback mc) {
		mc.dropMessage("Spawndebug...");
		synchronized (mapobjects) {
			mc.dropMessage("Mapobjects in map: " + mapobjects.size() + " \"spawnedMonstersOnMap\": " +
				spawnedMonstersOnMap + " spawnpoints: " + monsterSpawn.size() +
				" maxRegularSpawn: " + getMaxRegularSpawn());
			int numMonsters = 0;
			for (MapleMapObject mo : mapobjects.values()) {
				if (mo instanceof MapleMonster) {
					numMonsters++;
				}
			}
			mc.dropMessage("actual monsters: " + numMonsters);
		}
	}
	
	private int getMaxRegularSpawn() {
		return (int) (monsterSpawn.size() / monsterRate);
	}

	public Collection<MaplePortal> getPortals() {
		return Collections.unmodifiableCollection(portals.values());
	}

	public String getMapName() {
		return mapName;
	}

	public void setMapName(String mapName) {
		this.mapName = mapName;
	}

	public String getStreetName() {
		return streetName;
	}

	public void setStreetName(String streetName) {
		this.streetName = streetName;
	}

	private class ExpireMapItemJob implements Runnable {
		private MapleMapItem mapitem;

		public ExpireMapItemJob(MapleMapItem mapitem) {
			this.mapitem = mapitem;
		}

		@Override
		public void run() {
			if (mapitem != null && mapitem == getMapObject(mapitem.getObjectId())) {
				synchronized (mapitem) {
					if (mapitem.isPickedUp())
						return;
					MapleMap.this.broadcastMessage(MaplePacketCreator.removeItemFromMap(mapitem.getObjectId(), 0, 0),
						mapitem.getPosition());
					MapleMap.this.removeMapObject(mapitem);
					mapitem.setPickedUp(true);
				}
			}
		}
	}

	private class RespawnWorker implements Runnable {
		@Override
		public void run() {
			int playersOnMap = characters.size();

			if (playersOnMap == 0) {
				return;
			}

			int ispawnedMonstersOnMap = spawnedMonstersOnMap.get();
			int numShouldSpawn = (int) Math.round(Math.random() * ((2 + playersOnMap / 1.5 + (getMaxRegularSpawn() - ispawnedMonstersOnMap) / 4.0)));
			if (numShouldSpawn + ispawnedMonstersOnMap > getMaxRegularSpawn()) {
				numShouldSpawn = getMaxRegularSpawn() - ispawnedMonstersOnMap;
			}
			
			if (numShouldSpawn <= 0) {
				return;
			}
			
			// k find that many monsters that need respawning and respawn them �.o
 			List<SpawnPoint> randomSpawn = new ArrayList<SpawnPoint>(monsterSpawn);
			Collections.shuffle(randomSpawn);
			int spawned = 0;
			for (SpawnPoint spawnPoint : randomSpawn) {
				if (spawnPoint.shouldSpawn()) {
					spawnPoint.spawnMonster(MapleMap.this);
					spawned++;
				}
				if (spawned >= numShouldSpawn) {
					break;
				}
			}
		}
	}

	private static interface DelayedPacketCreation {
		void sendPackets(MapleClient c);
	}
	
	private static interface SpawnCondition {
		boolean canSpawn(MapleCharacter chr);
	}
}
