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

package net.sf.odinms.client.messages;

import java.awt.Point;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import net.sf.odinms.client.IItem;
import net.sf.odinms.client.Item;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleCharacterUtil;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.client.MapleJob;
import net.sf.odinms.client.MapleStat;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.client.anticheat.CheatingOffense;
import net.sf.odinms.database.DatabaseConnection;
import net.sf.odinms.net.ExternalCodeTableGetter;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.net.PacketProcessor;
import net.sf.odinms.net.RecvPacketOpcode;
import net.sf.odinms.net.SendPacketOpcode;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.net.channel.handler.GeneralchatHandler;
import net.sf.odinms.net.world.remote.CheaterData;
import net.sf.odinms.net.world.remote.WorldLocation;
import net.sf.odinms.server.MapleInventoryManipulator;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.MaplePortal;
import net.sf.odinms.server.MapleShop;
import net.sf.odinms.server.MapleShopFactory;
import net.sf.odinms.server.MapleTrade;
import net.sf.odinms.server.ShutdownServer;
import net.sf.odinms.server.TimerManager;
import net.sf.odinms.server.life.MapleLifeFactory;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.server.life.MapleMonsterInformationProvider;
import net.sf.odinms.server.life.MapleMonsterStats;
import net.sf.odinms.server.maps.MapleDoor;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.server.maps.MapleMapObject;
import net.sf.odinms.server.maps.MapleMapObjectType;
import net.sf.odinms.server.quest.MapleQuest;
import net.sf.odinms.tools.HexTool;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.MockIOSession;
import net.sf.odinms.tools.Pair;
import net.sf.odinms.tools.StringUtil;
import net.sf.odinms.tools.data.output.MaplePacketLittleEndianWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandProcessor implements CommandProcessorMBean {
	private static CommandProcessor instance = new CommandProcessor();
	private static final Logger log = LoggerFactory.getLogger(GeneralchatHandler.class);
	private static List<Pair<MapleCharacter,String>> gmlog = new LinkedList<Pair<MapleCharacter,String>>();
	private static Runnable persister;
	
	static {
		persister = new PersistingTask();
		TimerManager.getInstance().register(persister, 62000);
	}
	
	private CommandProcessor() {
		// hidden singleton so we can become managable
	}
	
	public static class PersistingTask implements Runnable {
		@Override
		public void run() {
			synchronized (gmlog) {
				Connection con = DatabaseConnection.getConnection();
				try {
					PreparedStatement ps = con.prepareStatement("INSERT INTO gmlog (cid, command) VALUES (?, ?)");
					for (Pair<MapleCharacter,String> logentry : gmlog) {
						ps.setInt(1, logentry.getLeft().getId());
						ps.setString(2, logentry.getRight());
						ps.executeUpdate();
					}
					ps.close();
				} catch (SQLException e) {
					log.error("error persisting cheatlog", e);
				}
				gmlog.clear();
			}
		}
	}
	
	public static void registerMBean() {
		MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
		try {
			mBeanServer.registerMBean(instance, new ObjectName("net.sf.odinms.client.messages:name=CommandProcessor"));
		} catch (Exception e) {
			log.error("Error registering CommandProcessor MBean");
		}
	}
	
	private static int getNoticeType(String typestring) {
		if (typestring.equals("n")) {
			return 0;
		} else if (typestring.equals("p")) {
			return 1;
		} else if (typestring.equals("l")) {
			return 2;
		} else if (typestring.equals("nv")) {
			return 5;
		} else if (typestring.equals("v")) {
			return 5;
		} else if (typestring.equals("b")) {
			return 6;
		}
		return -1;
	}

	private static int getOptionalIntArg(String splitted[], int position, int def) {
		if (splitted.length > position) {
			try {
				return Integer.parseInt(splitted[position]);
			} catch (NumberFormatException nfe) {
				return def;
			}
		}
		return def;
	}

	private static String getNamedArg(String splitted[], int startpos, String name) {
		for (int i = startpos; i < splitted.length; i++) {
			if (splitted[i].equalsIgnoreCase(name) && i + 1 < splitted.length) {
				return splitted[i + 1];
			}
		}
		return null;
	}

	private static Integer getNamedIntArg(String splitted[], int startpos, String name) {
		String arg = getNamedArg(splitted, startpos, name);
		if (arg != null) {
			try {
				return Integer.parseInt(arg);
			} catch (NumberFormatException nfe) {
				// swallow - we don't really care
			}
		}
		return null;
	}

	private static Double getNamedDoubleArg(String splitted[], int startpos, String name) {
		String arg = getNamedArg(splitted, startpos, name);
		if (arg != null) {
			try {
				return Double.parseDouble(arg);
			} catch (NumberFormatException nfe) {
				// swallow - we don't really care
			}
		}
		return null;
	}

	public static boolean processCommand(MapleClient c, String line) {
		return processCommandInternal(c, new ServernoticeMapleClientMessageCallback(c), c.getPlayer().isGM(), line);
	}
	
	/* (non-Javadoc)
	 * @see net.sf.odinms.client.messages.CommandProcessorMBean#processCommandJMX(int, int, java.lang.String)
	 */
	public String processCommandJMX(int cserver, int mapid, String command) {
		ChannelServer cserv = ChannelServer.getInstance(cserver);
		if (cserv == null) {
			return "The specified channel Server does not exist in this serverprocess";
		}
		MapleClient c = new MapleClient(null, null, new MockIOSession());
		MapleCharacter chr = MapleCharacter.getDefault(c, 26023);
		c.setPlayer(chr);
		chr.setName("/---------jmxuser-------------\\"); // (name longer than maxmimum length)
		MapleMap map = cserv.getMapFactory().getMap(mapid);
		if (map != null) {
			chr.setMap(map);
			SkillFactory.getSkill(5101004).getEffect(1).applyTo(chr);
			map.addPlayer(chr);
		}
		cserv.addPlayer(chr);
		MessageCallback mc = new StringMessageCallback();
		try {
			processCommandInternal(c, mc, true, command);
		} finally {
			if (map != null) {
				map.removePlayer(chr);
			}
			cserv.removePlayer(chr);
		}
		return mc.toString();
	}
	
	/* (non-Javadoc)
	 * @see net.sf.odinms.client.messages.CommandProcessorMBean#processCommandInstance(net.sf.odinms.client.MapleClient, java.lang.String)
	 */
	private static boolean processCommandInternal(MapleClient c, MessageCallback mc, boolean isGM, String line) {
		MapleCharacter player = c.getPlayer();
		ChannelServer cserv = c.getChannelServer();
		if (line.charAt(0) == '!' && isGM) {
			synchronized (gmlog) {
				gmlog.add(new Pair<MapleCharacter, String>(player, line));
			}
			log.warn("{} used a GM command: {}", c.getPlayer().getName(), line);
			String[] splitted = line.split(" ");
			if (splitted[0].equals("!map")) {
				int mapid = Integer.parseInt(splitted[1]);
				MapleMap target = cserv.getMapFactory().getMap(mapid);
				MaplePortal targetPortal = null;
				if (splitted.length > 2) {
					try {
						targetPortal = target.getPortal(Integer.parseInt(splitted[2]));
					} catch (IndexOutOfBoundsException ioobe) {
						// noop, assume the gm didn't know how many portals there are
					} catch (NumberFormatException nfe) {
						// noop, assume that the gm is drunk
					}
				}
				if (targetPortal == null) {
					targetPortal = target.getPortal(0);
				}
				player.changeMap(target, targetPortal);
			} else if (splitted[0].equals("!jail")) {
				MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
				int mapid = 200090300; // mulung ride
				if (splitted.length > 2 && splitted[1].equals("2")) {
					mapid = 280090000; // room of tragedy
					victim = cserv.getPlayerStorage().getCharacterByName(splitted[2]);
				}
				if (victim != null) {
					MapleMap target = cserv.getMapFactory().getMap(mapid);
					MaplePortal targetPortal = target.getPortal(0);
					victim.changeMap(target, targetPortal);
					mc.dropMessage(victim.getName() + " was jailed!");
				} else {
					mc.dropMessage(victim.getName() + " not found!");
				}
			} else if (splitted[0].equals("!lolcastle")) {
				if (splitted.length != 2) {
					mc.dropMessage("Syntax: !lolcastle level (level = 1-5)");
				}
				MapleMap target = c.getChannelServer().getEventSM().getEventManager("lolcastle").getInstance("lolcastle" + splitted[1]).getMapFactory().getMap(990000300, false, false);
				player.changeMap(target, target.getPortal(0));
			} else if (splitted[0].equals("!warp")) {
				MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
				if (victim != null) {
					if (splitted.length == 2) {
						MapleMap target = victim.getMap();
						c.getPlayer().changeMap(target, target.findClosestSpawnpoint(victim.getPosition()));
					} else {
						int mapid = Integer.parseInt(splitted[2]);
						MapleMap target = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(mapid);
						victim.changeMap(target, target.getPortal(0));
					}
				} else {
					try {
						victim = c.getPlayer();
						WorldLocation loc = c.getChannelServer().getWorldInterface().getLocation(splitted[1]);
						if (loc != null) {
							mc.dropMessage("You will be cross-channel warped. This may take a few seconds.");
							//WorldLocation loc = new WorldLocation(40000, 2);
							MapleMap target = c.getChannelServer().getMapFactory().getMap(loc.map);
							c.getPlayer().cancelAllBuffs();
							String ip = c.getChannelServer().getIP(loc.channel);
							c.getPlayer().getMap().removePlayer(c.getPlayer());
							victim.setMap(target);
							String[] socket = ip.split(":");
							if (c.getPlayer().getTrade() != null) {
								MapleTrade.cancelTrade(c.getPlayer());
							}
							c.getPlayer().saveToDB(true);
							if (c.getPlayer().getCheatTracker() != null)
								c.getPlayer().getCheatTracker().dispose();
							ChannelServer.getInstance(c.getChannel()).removePlayer(c.getPlayer());
							c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION);
							try {
								MaplePacket packet = MaplePacketCreator.getChannelChange(
									InetAddress.getByName(socket[0]), Integer.parseInt(socket[1]));
								c.getSession().write(packet);
							} catch (Exception e) {
								throw new RuntimeException(e);
							}
						} else {
							int map = Integer.parseInt(splitted[1]);
							MapleMap target = cserv.getMapFactory().getMap(map);
							player.changeMap(target, target.getPortal(0));
						}
					} catch (/*Remote*/Exception e) {
						mc.dropMessage("Something went wrong " + e.getMessage());
					}
				}
			}  else if (splitted[0].equals("!toggleoffense")) {
				try {
					CheatingOffense co = CheatingOffense.valueOf(splitted[1]);
					co.setEnabled(!co.isEnabled());
				} catch (IllegalArgumentException iae) {
					mc.dropMessage("Offense " + splitted[1] + " not found");
				}
			} else if (splitted[0].equals("!warphere")) {
				MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
				victim.changeMap(c.getPlayer().getMap(), c.getPlayer().getMap().findClosestSpawnpoint(
					c.getPlayer().getPosition()));
			} else if (splitted[0].equals("!spawn")) {
				int mid = Integer.parseInt(splitted[1]);
				int num = Math.min(getOptionalIntArg(splitted, 2, 1), 500);

				if (mid == 9400203) {
					log.info(MapleClient.getLogMessage(player, "Trying to spawn a silver slime"));
					return true;
				}

				Integer hp = getNamedIntArg(splitted, 1, "hp");
				Integer exp = getNamedIntArg(splitted, 1, "exp");
				Double php = getNamedDoubleArg(splitted, 1, "php");
				Double pexp = getNamedDoubleArg(splitted, 1, "pexp");

				MapleMonster onemob = MapleLifeFactory.getMonster(mid);

				int newhp = 0;
				int newexp = 0;

				double oldExpRatio = ((double) onemob.getHp() / onemob.getExp());

				if (hp != null) {
					newhp = hp.intValue();
				} else if (php != null) {
					newhp = (int) (onemob.getMaxHp() * (php.doubleValue() / 100));
				} else {
					newhp = onemob.getMaxHp();
				}
				if (exp != null) {
					newexp = exp.intValue();
				} else if (pexp != null) {
					newexp = (int) (onemob.getExp() * (pexp.doubleValue() / 100));
				} else {
					newexp = onemob.getExp();
				}

				if (newhp < 1) {
					newhp = 1;
				}
				double newExpRatio = ((double) newhp / newexp);
				if (newExpRatio < oldExpRatio && newexp > 0) {
					mc.dropMessage("The new hp/exp ratio is better than the old one. (" + newExpRatio + " < " +
						oldExpRatio + ") Please don't do this");
					return true;
				}
				
				MapleMonsterStats overrideStats = new MapleMonsterStats();
				overrideStats.setHp(newhp);
				overrideStats.setExp(newexp);
				overrideStats.setMp(onemob.getMaxMp());
				
				for (int i = 0; i < num; i++) {
					MapleMonster mob = MapleLifeFactory.getMonster(mid);
					mob.setHp(newhp);
					mob.setOverrideStats(overrideStats);
					c.getPlayer().getMap().spawnMonsterOnGroudBelow(mob, c.getPlayer().getPosition());
				}
			} else if (splitted[0].equals("!servermessage")) {
				ChannelServer.getInstance(c.getChannel()).setServerMessage(StringUtil.joinStringFrom(splitted, 1));
			} else if (splitted[0].equals("!array")) {
				mc.dropMessage("Array");
			} else if (splitted[0].equals("!notice")) {
				int joinmod = 1;

				int range = -1;
				if (splitted[1].equals("m")) {
					range = 0;
				} else if (splitted[1].equals("c")) {
					range = 1;
				} else if (splitted[1].equals("w")) {
					range = 2;
				}

				int tfrom = 2;
				if (range == -1) {
					range = 2;
					tfrom = 1;
				}
				int type = getNoticeType(splitted[tfrom]);
				if (type == -1) {
					type = 0;
					joinmod = 0;
				}
				String prefix = "";
				if (splitted[tfrom].equals("nv")) {
					prefix = "[Notice] ";
				}
				joinmod += tfrom;
				MaplePacket packet = MaplePacketCreator.serverNotice(type, prefix +
					StringUtil.joinStringFrom(splitted, joinmod));
				if (range == 0) {
					c.getPlayer().getMap().broadcastMessage(packet);
				} else if (range == 1) {
					ChannelServer.getInstance(c.getChannel()).broadcastPacket(packet);
				} else if (range == 2) {
					try {
						ChannelServer.getInstance(c.getChannel()).getWorldInterface().broadcastMessage(
							c.getPlayer().getName(), packet.getBytes());
					} catch (RemoteException e) {
						c.getChannelServer().reconnectWorld();
					}
				}
			} else if (splitted[0].equals("!job")) {
				c.getPlayer().changeJob(MapleJob.getById(Integer.parseInt(splitted[1])));
			} else if (splitted[0].equals("!clock")) {
				player.getMap().broadcastMessage(MaplePacketCreator.getClock(getOptionalIntArg(splitted, 1, 60)));
			} else if (splitted[0].equals("!pill")) {
				MapleInventoryManipulator.addById(c, 2002009, (short) 5, c.getPlayer().getName() + " used !pill");
			} else if (splitted[0].equals("!item")) {
				short quantity = (short) getOptionalIntArg(splitted, 2, 1);
				MapleInventoryManipulator.addById(c, Integer.parseInt(splitted[1]), quantity, c.getPlayer().getName() +
					"used !item with quantity " + quantity);
			} else if (splitted[0].equals("!drop")) {
				MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
				int itemId = Integer.parseInt(splitted[1]);
				short quantity = (short) (short) getOptionalIntArg(splitted, 2, 1);
				IItem toDrop;
				if (ii.getInventoryType(itemId) == MapleInventoryType.EQUIP)
					toDrop = ii.getEquipById(itemId);
				else
					toDrop = new Item(itemId, (byte) 0, (short) quantity);
				StringBuilder logMsg = new StringBuilder("Created by ");
				logMsg.append(c.getPlayer().getName());
				logMsg.append(" using !drop. Quantity: ");
				logMsg.append(quantity);
				toDrop.log(logMsg.toString(), false);
				c.getPlayer().getMap().spawnItemDrop(c.getPlayer(), c.getPlayer(), toDrop, c.getPlayer().getPosition(), true,true);
			} else if (splitted[0].equals("!shop")) {
				MapleShopFactory sfact = MapleShopFactory.getInstance();
				MapleShop shop = sfact.getShop(1);
				shop.sendShop(c);
			} else if (splitted[0].equals("!equip")) {
				MapleShopFactory sfact = MapleShopFactory.getInstance();
				MapleShop shop = sfact.getShop(2);
				shop.sendShop(c);
			} else if (splitted[0].equals("!cleardrops")) {
				MapleMonsterInformationProvider.getInstance().clearDrops();
			} else if (splitted[0].equals("!clearshops")) {
				MapleShopFactory.getInstance().clear();
			} else if (splitted[0].equals("!clearevents")) {
				for (ChannelServer instance : ChannelServer.getAllInstances()) {
					instance.reloadEvents();
				}
			} else if (splitted[0].equals("!resetquest")) {
				MapleQuest.getInstance(Integer.parseInt(splitted[1])).forfeit(c.getPlayer());
			} else if (splitted[0].equals("!gps")) {
				// c.getSession().write(MaplePacketCreator.getPlayerShop(c.getPlayer(), 0, null));
			} else if (splitted[0].equals("!sp")) {
				player.setRemainingSp(getOptionalIntArg(splitted, 1, 1));
				player.updateSingleStat(MapleStat.AVAILABLESP, player.getRemainingSp());
			} else if (splitted[0].equals("!fakerelog")) {
				c.getSession().write(MaplePacketCreator.getCharInfo(player));
				player.getMap().removePlayer(player);
				player.getMap().addPlayer(player);
			} else if (splitted[0].equals("!test")) {
				// faeks id is 30000 (30 75 00 00)
				// MapleCharacter faek = ((MapleCharacter) c.getPlayer().getMap().getMapObject(30000));
				
				//List<BuddylistEntry> buddylist = Arrays.asList(new BuddylistEntry("derGuteBuddy", 30000, 1, true));
//				c.getSession().write(MaplePacketCreator.updateBuddylist(buddylist));
				// c.getSession().write(MaplePacketCreator.updateBuddyChannel(30000, 1));
				// c.getSession().write(MaplePacketCreator.updateBuddyChannel(30000, 0));
				//c.getSession().write(MaplePacketCreator.requestBuddylistAdd(30000, "FaekChar"));
				//c.getSession().write(MaplePacketCreator.requestBuddylistAdd(30001, "FaekChar2"));
				//c.getSession().write(MaplePacketCreator.multiChat("lulu", line, 0));
				// c.getSession().write(MaplePacketCreator.showOwnBuffEffect(1311008, 5));
				// c.getSession().write(MaplePacketCreator.showBuffeffect(30000, 1311008, 5));
				//c.getSession().write(MaplePacketCreator.getPacketFromHexString("2B 00 07 22 64 1F 23 00 57 69 6E 64 53 63 61 72 73 00 FF FF 2C 02 56 0A 35 B7 34 A9 17 00 78 4D 41 55 53 49 78 00 73 00 FF FF 2C 00 FF FF FF FF 6A 3A 0D 00 6F 31 56 69 45 54 78 47 69 52 4C 00 2C 02 56 0A 35 B7 7D 3C 05 00 69 74 7A 78 65 6D 69 6C 79 79 00 00 2C 02 56 0A 35 B7 00 ED 19 00 31 39 39 52 61 6E 64 6F 6D 67 75 79 00 02 56 0A 35 B7 69 7D 00 00 64 61 76 74 73 61 69 00 6D 67 75 79 00 02 56 0A 35 B7 46 85 17 00 44 72 61 6B 65 58 6B 69 6C 6C 65 72 00 00 FF FF FF FF AD 78 00 00 42 61 74 6F 73 69 61 00 6C 6C 65 72 00 02 56 0A 35 B7 A7 B1 02 00 53 65 63 6E 69 6E 00 00 6C 6C 65 72 00 00 FF FF FF FF 05 50 00 00 48 61 6E 64 4F 66 47 6F 64 00 65 72 00 02 56 0A 35 B7 29 21 41 00 53 61 65 61 00 66 47 6F 64 00 65 72 00 00 FF FF FF FF 79 00 01 00 62 75 74 74 77 61 78 00 64 00 65 72 00 02 56 0A 35 B7 B9 01 02 00 48 65 72 6F 53 6F 50 72 6F 00 65 72 00 02 56 0A 35 B7 63 0F 23 00 4D 53 43 42 00 6F 50 72 6F 00 65 72 00 02 56 0A 35 B7 63 40 0F 00 44 65 6D 30 6E 7A 61 62 75 7A 61 00 00 02 56 0A 35 B7 B2 C8 00 00 41 73 69 61 6E 4D 49 63 6B 65 79 00 00 00 FF FF FF FF E1 6D 13 00 54 52 44 52 6F 6C 6C 61 00 65 79 00 00 00 FF FF FF FF 0D 35 00 00 53 65 63 72 61 6E 6F 00 00 65 79 00 00 00 FF FF FF FF DF E3 01 00 62 69 7A 7A 00 6E 6F 00 00 65 79 00 00 00 FF FF FF FF 56 93 2F 00 54 65 72 70 65 00 6F 00 00 65 79 00 00 00 FF FF FF FF 69 EB 14 00 53 6B 79 64 72 65 61 6D 00 65 79 00 00 00 FF FF FF FF 1B 04 02 00 4E 61 67 6C 66 61 72 00 00 65 79 00 00 00 FF FF FF FF FA 6F 00 00 53 68 6D 75 66 66 00 67 6F 6E 00 00 00 00 FF FF FF FF 09 E2 00 00 44 65 70 74 69 63 00 67 6F 6E 00 00 00 00 FF FF FF FF 85 49 15 00 54 79 73 74 6F 00 00 67 6F 6E 00 00 00 02 56 0A 35 B7 F8 9A 17 00 46 6F 68 6E 7A 00 00 67 6F 6E 00 00 00 02 56 0A 35 B7 86 B2 0F 00 41 62 79 73 61 6C 43 6C 65 72 69 63 00 02 56 0A 35 B7 1A 88 1D 00 78 73 63 72 69 62 62 6C 65 73 7A 00 00 00 FF FF FF FF D5 5C 1E 00 46 6A 6F 65 72 67 79 6E 6E 00 7A 00 00 00 FF FF FF FF 4B CE 03 00 41 72 72 6F 77 68 65 61 64 31 33 35 00 02 56 0A 35 B7 8F 2F 20 00 4E 61 77 75 74 6F 00 61 64 31 33 35 00 00 FF FF FF FF D5 8E 1E 00 4C 61 72 69 6C 79 00 61 64 31 33 35 00 00 FF FF FF FF 9B 85 0F 00 53 68 65 65 70 68 65 72 64 00 33 35 00 00 FF FF FF FF 30 C0 23 00 46 6A 6F 65 72 00 6E 61 6C 20 66 61 69 00 09 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"));
				c.getSession().write(MaplePacketCreator.getPacketFromHexString("2B 00 14 30 C0 23 00 00 11 00 00 00"));
			} else if (splitted[0].equals("!dc")) {
				int level = 0;
				MapleCharacter victim;
				if (splitted[1].charAt(0) == '-') {
					level = StringUtil.countCharacters(splitted[1], 'f');
					victim = cserv.getPlayerStorage().getCharacterByName(splitted[2]);
				} else {
					victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
				}
				victim.getClient().getSession().close();
				if (level >= 1) {
					victim.getClient().disconnect();
				}
				if (level >= 2) {
					victim.saveToDB(true);
					cserv.removePlayer(victim);
				}
			} else if (splitted[0].equals("!charinfo")) {
				StringBuilder builder = new StringBuilder();
				MapleCharacter other = cserv.getPlayerStorage().getCharacterByName(splitted[1]);

				builder.append(MapleClient.getLogMessage(other, ""));
				builder.append(" at ");
				builder.append(other.getPosition().x);
				builder.append("/");
				builder.append(other.getPosition().y);
				builder.append(" ");
				builder.append(other.getHp());
				builder.append("/");
				builder.append(other.getCurrentMaxHp());
				builder.append("hp ");
				builder.append(other.getMp());
				builder.append("/");
				builder.append(other.getCurrentMaxMp());
				builder.append("mp ");
				builder.append(other.getExp());
				builder.append("exp hasParty: ");
				builder.append(other.getParty() != null);
				builder.append(" hasTrade: ");
				builder.append(other.getTrade() != null);
				mc.dropMessage(builder.toString());
				other.getClient().dropDebugMessage(mc);
			} else if (splitted[0].equals("!ban")) {
				if (splitted.length < 3) {
					new ServernoticeMapleClientMessageCallback(2, c).dropMessage("Syntaxhelper : Syntax: !ban charname reason");
					return true;
				}
				String originalReason = StringUtil.joinStringFrom(splitted, 2);
				String reason = c.getPlayer().getName() + " banned " + splitted[1] + ": " +
				originalReason;
				MapleCharacter target = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
				if (target != null) {
					String readableTargetName = MapleCharacterUtil.makeMapleReadable(target.getName());
					String ip = target.getClient().getSession().getRemoteAddress().toString().split(":")[0];
					reason += " (IP: " + ip + ")";
					target.ban(reason);
					mc.dropMessage("Banned " + readableTargetName + " ipban for " + ip + " reason: " + originalReason);
				} else {
					if (MapleCharacter.ban(splitted[1], reason, false)) {
						mc.dropMessage("Offline Banned " + splitted[1]);
					} else {
						mc.dropMessage("Failed to ban " + splitted[1]);
					}
				}
			} else if (splitted[0].equals("!levelup")) {
				c.getPlayer().levelUp();
				int newexp = c.getPlayer().getExp();
				if (newexp < 0) {
					c.getPlayer().gainExp(-newexp, false, false);
				}
			} else if (splitted[0].equals("!whereami")) {
				new ServernoticeMapleClientMessageCallback(c).dropMessage("You are on map " +
					c.getPlayer().getMap().getId());
			} else if (splitted[0].equals("!version")) {
				new ServernoticeMapleClientMessageCallback(c)
					.dropMessage("Rev $Revision: 867 $ built $LastChangedDate: 2008-04-26 14:30:40 +0200 (Sa, 26 Apr 2008) $");
			} else if (splitted[0].equals("!connected")) {
				try {
					Map<Integer, Integer> connected = cserv.getWorldInterface().getConnected();
					StringBuilder conStr = new StringBuilder("Connected Clients: ");
					boolean first = true;
					for (int i : connected.keySet()) {
						if (!first) {
							conStr.append(", ");
						} else {
							first = false;
						}
						if (i == 0) {
							conStr.append("Total: ");
							conStr.append(connected.get(i));
						} else {
							conStr.append("Ch");
							conStr.append(i);
							conStr.append(": ");
							conStr.append(connected.get(i));
						}
					}
					new ServernoticeMapleClientMessageCallback(c).dropMessage(conStr.toString());
				} catch (RemoteException e) {
					c.getChannelServer().reconnectWorld();
				}
			} else if (splitted[0].equals("!whosthere")) {
				MessageCallback callback = new ServernoticeMapleClientMessageCallback(c);
				StringBuilder builder = new StringBuilder("Players on Map: ");
				for (MapleCharacter chr : c.getPlayer().getMap().getCharacters()) {
					if (builder.length() > 150) { // wild guess :o
						builder.setLength(builder.length() - 2);
						callback.dropMessage(builder.toString());
						builder = new StringBuilder();
					}
					builder.append(MapleCharacterUtil.makeMapleReadable(chr.getName()));
					builder.append(", ");
				}
				builder.setLength(builder.length() - 2);
				c.getSession().write(MaplePacketCreator.serverNotice(6, builder.toString()));
			} else if (splitted[0].equals("!shutdown")) {
				int time = 60000;
				if (splitted.length > 1) {
					time = Integer.parseInt(splitted[1]) * 60000;
				}
				persister.run();
				c.getChannelServer().shutdown(time);
			} else if (splitted[0].equals("!shutdownworld")) {
				int time = 60000;
				if (splitted.length > 1) {
					time = Integer.parseInt(splitted[1]) * 60000;
				}
				persister.run();
				c.getChannelServer().shutdownWorld(time);
				// shutdown
			} else if (splitted[0].equals("!shutdownnow")) {
				persister.run();
				new ShutdownServer(c.getChannel()).run();
			} else if (splitted[0].equals("!timerdebug")) {
				TimerManager.getInstance().dropDebugInfo(mc);
			} else if (splitted[0].equals("!threads")) {
				Thread[] threads = new Thread[Thread.activeCount()];
				Thread.enumerate(threads);
				String filter = "";
				if (splitted.length > 1) {
					filter = splitted[1];
				}
				for (int i = 0; i < threads.length; i++) {
					String tstring = threads[i].toString();
					if (tstring.toLowerCase().indexOf(filter.toLowerCase()) > -1) {
						mc.dropMessage(i + ": " + tstring);
					}
				}
			} else if (splitted[0].equals("!showtrace")) {
				if (splitted.length < 2) {
					return true;
				}
				Thread[] threads = new Thread[Thread.activeCount()];
				Thread.enumerate(threads);
				Thread t = threads[Integer.parseInt(splitted[1])];
				mc.dropMessage(t.toString() + ":");
				for (StackTraceElement elem : t.getStackTrace()) {
					mc.dropMessage(elem.toString());
				}
			} else if (splitted[0].equals("!dumpthreads")) {
				Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
				try {
					PrintWriter pw = new PrintWriter(new File("threaddump.txt"));
					for (Entry<Thread, StackTraceElement[]> t : traces.entrySet()) {
						pw.println(t.getKey().toString());
						for (StackTraceElement elem : t.getValue()) {
							pw.println(elem.toString());
						}
						pw.println();
					}
					pw.close();
				} catch (FileNotFoundException e) {
					log.error("ERROR", e);
				}
			} else if (splitted[0].equals("!reloadops")) {
				try {
					ExternalCodeTableGetter.populateValues(SendPacketOpcode.getDefaultProperties(), SendPacketOpcode.values());
					ExternalCodeTableGetter.populateValues(RecvPacketOpcode.getDefaultProperties(), RecvPacketOpcode.values());
				} catch (Exception e) {
					log.error("Failed to reload props", e);
				}
				PacketProcessor.getProcessor(PacketProcessor.Mode.CHANNELSERVER).reset(PacketProcessor.Mode.CHANNELSERVER);
				PacketProcessor.getProcessor(PacketProcessor.Mode.CHANNELSERVER).reset(PacketProcessor.Mode.CHANNELSERVER);
			} else if (splitted[0].equals("!killall") || splitted[0].equals("!monsterdebug")) {
				MapleMap map = c.getPlayer().getMap();
				double range = Double.POSITIVE_INFINITY;
				if (splitted.length > 1) {
					int irange = Integer.parseInt(splitted[1]);
					range = irange * irange;
				}
				List<MapleMapObject> monsters = map.getMapObjectsInRange(c.getPlayer().getPosition(), range, Arrays
					.asList(MapleMapObjectType.MONSTER));
				boolean kill = splitted[0].equals("!killall");
				for (MapleMapObject monstermo : monsters) {
					MapleMonster monster = (MapleMonster) monstermo;
					if (kill) {
						map.killMonster(monster, c.getPlayer(), false);
					} else {
						mc.dropMessage("Monster " + monster.toString());
					}
				}
				if (kill) {
					mc.dropMessage("Killed " + monsters.size() + " monsters <3");
				}
			} else if (splitted[0].equals("!skill")) {
				int skill = Integer.parseInt(splitted[1]);
				int level = getOptionalIntArg(splitted, 2, 1);
				int masterlevel = getOptionalIntArg(splitted, 3, 1);
				c.getPlayer().changeSkillLevel(SkillFactory.getSkill(skill), level, masterlevel);
			} else if (splitted[0].equals("!spawndebug")) {
				c.getPlayer().getMap().spawnDebug(mc);
			} else if (splitted[0].equals("!door")) {
				Point doorPos = new Point(player.getPosition());
				doorPos.y -= 270;
				MapleDoor door = new MapleDoor(c.getPlayer(), doorPos);
				door.getTarget().addMapObject(door);
				//c.getSession().write(MaplePacketCreator.spawnDoor(/*c.getPlayer().getId()*/ 0x1E47, door.getPosition(), false));
				/*c.getSession().write(MaplePacketCreator.saveSpawnPosition(door.getPosition()));*/
				MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
				mplew.write(HexTool.getByteArrayFromHexString("B9 00 00 47 1E 00 00 0A 04 76 FF"));
				c.getSession().write(mplew.getPacket());
				mplew = new MaplePacketLittleEndianWriter();
				mplew.write(HexTool.getByteArrayFromHexString("36 00 00 EF 1C 0D 4C 3E 1D 0D 0A 04 76 FF"));
				c.getSession().write(mplew.getPacket());
				c.getSession().write(MaplePacketCreator.enableActions());
				door = new MapleDoor(door);
				door.getTown().addMapObject(door);
			} else if (splitted[0].equals("!tdrops")) {
				player.getMap().toggleDrops();
			} else if (splitted[0].equals("!lowhp")) {
				player.setHp(1);
				player.setMp(500);
				player.updateSingleStat(MapleStat.HP, 1);
				player.updateSingleStat(MapleStat.MP, 500);
			}  else if (splitted[0].equals("!fullhp")) {
				player.setHp(player.getMaxHp());
				player.updateSingleStat(MapleStat.HP, player.getMaxHp());
			} else if (splitted[0].equals("!cheaters")) {
				try {
					List<CheaterData> cheaters = c.getChannelServer().getWorldInterface().getCheaters();
					for (int x = cheaters.size() - 1; x >= 0; x--) {
						CheaterData cheater = cheaters.get(x);
						mc.dropMessage(cheater.getInfo());
					}
				} catch (RemoteException e) {
					c.getChannelServer().reconnectWorld();
				}
			} else {
				mc.dropMessage("GM Command " + splitted[0] + " does not exist");
			}
			return true;
		}
		return false;
	}
}
