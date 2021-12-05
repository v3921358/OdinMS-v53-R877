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

package net.sf.odinms.tools;

import java.awt.Point;
import java.awt.Rectangle;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;

import net.sf.odinms.client.BuddylistEntry;
import net.sf.odinms.client.IEquip;
import net.sf.odinms.client.IItem;
import net.sf.odinms.client.ISkill;
import net.sf.odinms.client.Item;
import net.sf.odinms.client.MapleBuffStat;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventory;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.client.MapleKeyBinding;
import net.sf.odinms.client.MapleQuestStatus;
import net.sf.odinms.client.MapleStat;
import net.sf.odinms.client.IEquip.ScrollResult;
import net.sf.odinms.client.status.MonsterStatus;
import net.sf.odinms.net.ByteArrayMaplePacket;
import net.sf.odinms.net.LongValueHolder;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.net.SendPacketOpcode;
import net.sf.odinms.net.channel.handler.SummonDamageHandler.SummonAttackEntry;
import net.sf.odinms.net.world.MapleParty;
import net.sf.odinms.net.world.MaplePartyCharacter;
import net.sf.odinms.net.world.PartyOperation;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.MaplePlayerShop;
import net.sf.odinms.server.MaplePlayerShopItem;
import net.sf.odinms.server.MapleShopItem;
import net.sf.odinms.server.MapleTrade;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.server.life.MapleNPC;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.server.maps.SummonMovementType;
import net.sf.odinms.server.movement.LifeMovementFragment;
import net.sf.odinms.tools.data.output.LittleEndianWriter;
import net.sf.odinms.tools.data.output.MaplePacketLittleEndianWriter;

public class MaplePacketCreator {
	private final static byte[] CHAR_INFO_MAGIC = new byte[] { (byte) 0xff, (byte) 0xc9, (byte) 0x9a, 0x3b };
	private final static byte[] ITEM_MAGIC = new byte[] { (byte) 0x80, 5 };
	public static final List<Pair<MapleStat, Integer>> EMPTY_STATUPDATE = Collections.emptyList();

	// private static Logger log =
	// LoggerFactory.getLogger(MaplePacketCreator.class);
	/**
	 * Sends a hello packet.
	 * 
	 * @param mapleVersion the maple version
	 * @param sendIv the IV used by the server for sending
	 * @param recvIv the IV used by the server for receiving
	 */
	public static MaplePacket getHello(short mapleVersion, byte[] sendIv, byte[] recvIv, boolean testServer) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);
		mplew.writeShort(0x0d);
		mplew.writeShort(mapleVersion);
		mplew.write(new byte[] { 0, 0 });
		mplew.write(recvIv);
		mplew.write(sendIv);
		mplew.write(testServer ? 5 : 8);
		return mplew.getPacket();
	}

	public static MaplePacket getPing() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);
		mplew.writeShort(SendPacketOpcode.PING.getValue());
		return mplew.getPacket();
	}

	/**
	 * Possible reasons:<br>
	 * 3: id deleted or blocked<br>
	 * 4: incorrect pwd<br>
	 * 5: not a registered id<br>
	 * 6: system error<br>
	 * 7: alread loggedin<br>
	 * 8: system error<br>
	 * 9: system error<br>
	 * 10: could not process so many connection<br>
	 * 11: only users older than 20 can use this channel
	 * 
	 * @param reason
	 * @return
	 */
	public static MaplePacket getLoginFailed(int reason) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);
		mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
		mplew.writeInt(reason);
		mplew.writeShort(0);

		return mplew.getPacket();
	}

	public static MaplePacket getAuthSuccessRequestPin(String account) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
		mplew.write(new byte[] { 0, 0, 0, 0, 0, 0, (byte) 0xFF, 0x6A, 1, 0, 0, 0, 0x4E });
		mplew.writeMapleAsciiString(account);
		mplew
			.write(new byte[] { 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xDC, 0x3D, 0x0B, 0x28, 0x64, (byte) 0xC5, 1 });
		return mplew.getPacket();
	}

	/**
	 * Mode can be: 0 - pin was accepted<br>
	 * 1 - register a new pin<br>
	 * 2 - invalid pin / reenter<br>
	 * 3 - connection failed due to system error<br>
	 * 4 - enter the pin
	 * 
	 * @param mode the mode
	 */
	public static MaplePacket pinOperation(byte mode) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);
		mplew.writeShort(SendPacketOpcode.PIN_OPERATION.getValue());
		mplew.write(mode);
		return mplew.getPacket();
	}

	public static MaplePacket requestPin() {
		return pinOperation((byte) 4);
	}

	public static MaplePacket requestPinAfterFailure() {
		return pinOperation((byte) 2);
	}

	public static MaplePacket pinAccepted() {
		return pinOperation((byte) 0);
	}

	/**
	 * 
	 * @param serverIndex
	 * @param serverName
	 * @param numChannels
	 * @param load 1200 seems to be max
	 * @return
	 */
	public static MaplePacket getServerList(int serverIndex, String serverName, Set<Integer> channels, int load) {
		/*
		 * 0B 00 00 06 00 53 63 61 6E 69 61 00 00 00 64 00 64 00 00 13 08 00 53 63 61 6E 69 61 2D 31 5E 04 00 00 00 00
		 * 00 08 00 53 63 61 6E 69 61 2D 32 25 01 00 00 00 01 00 08 00 53 63 61 6E 69 61 2D 33 F6 00 00 00 00 02 00 08
		 * 00 53 63 61 6E 69 61 2D 34 BC 00 00 00 00 03 00 08 00 53 63 61 6E 69 61 2D 35 E7 00 00 00 00 04 00 08 00 53
		 * 63 61 6E 69 61 2D 36 BC 00 00 00 00 05 00 08 00 53 63 61 6E 69 61 2D 37 C2 00 00 00 00 06 00 08 00 53 63 61
		 * 6E 69 61 2D 38 BB 00 00 00 00 07 00 08 00 53 63 61 6E 69 61 2D 39 C0 00 00 00 00 08 00 09 00 53 63 61 6E 69
		 * 61 2D 31 30 C3 00 00 00 00 09 00 09 00 53 63 61 6E 69 61 2D 31 31 BB 00 00 00 00 0A 00 09 00 53 63 61 6E 69
		 * 61 2D 31 32 AB 00 00 00 00 0B 00 09 00 53 63 61 6E 69 61 2D 31 33 C7 00 00 00 00 0C 00 09 00 53 63 61 6E 69
		 * 61 2D 31 34 B9 00 00 00 00 0D 00 09 00 53 63 61 6E 69 61 2D 31 35 AE 00 00 00 00 0E 00 09 00 53 63 61 6E 69
		 * 61 2D 31 36 B6 00 00 00 00 0F 00 09 00 53 63 61 6E 69 61 2D 31 37 DB 00 00 00 00 10 00 09 00 53 63 61 6E 69
		 * 61 2D 31 38 C7 00 00 00 00 11 00 09 00 53 63 61 6E 69 61 2D 31 39 EF 00 00 00 00 12 00
		 */

		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SERVERLIST.getValue());
		mplew.write(serverIndex);
		mplew.writeMapleAsciiString(serverName);
		mplew.write(2); // 1: E 2: N 3: H
		//mplew.writeShort(0);
		mplew.writeMapleAsciiString("");
		mplew.write(0x64); // rate modifier, don't ask O.O!
		mplew.write(0x0); // event xp * 2.6 O.O!
		mplew.write(0x64); // rate modifier, don't ask O.O!
		mplew.write(0x0); // drop rate * 2.6
		mplew.write(0x0);
		int lastChannel = 1;
		for (int i = 30; i > 0; i--)
			if (channels.contains(i)) {
				lastChannel = i;
				break;
			}

		mplew.write(lastChannel);
		
		for (int i = 1; i <= lastChannel; i++) {
			if (channels.contains(i)) {
				load = 150;
			} else {
				load = 1200;
			}
			mplew.writeMapleAsciiString(serverName + "-" + i);
			mplew.writeInt(load);
			mplew.write(serverIndex);
			mplew.writeShort(i - 1);
		}

		return mplew.getPacket();
	}

	public static MaplePacket getEndOfServerList() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SERVERLIST.getValue());
		mplew.write(0xFF);
		return mplew.getPacket();
	}

	/**
	 * Returns a server status message - status 0 = normal, status 1 = highly populated, 2 = full
	 * 
	 * @param status the server status
	 * @return the status message
	 */
	public static MaplePacket getServerStatus(int status) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SERVERSTATUS.getValue());
		mplew.writeShort(status);
		return mplew.getPacket();
	}

	public static MaplePacket getServerIP(InetAddress inetAddr, int port, int clientId) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SERVER_IP.getValue());
		mplew.writeShort(0);
		byte[] addr = inetAddr.getAddress();
		mplew.write(addr);
		mplew.writeShort(port);
		// 0x13 = numchannels?
		mplew.writeInt(clientId); // this gets repeated to the channel server
		// leos.write(new byte[] { (byte) 0x13, (byte) 0x37, 0x42, 1, 0, 0, 0,
		// 0, 0 });
		mplew.write(new byte[] { 0, 0, 0, 0, 0 });
		// 0D 00 00 00 3F FB D9 0D 8A 21 CB A8 13 00 00 00 00 00 00
		// ....?....!.........
		return mplew.getPacket();
	}
	
	public static MaplePacket getChannelChange(InetAddress inetAddr, int port) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CHANGE_CHANNEL.getValue());
		mplew.write(1);
		byte[] addr = inetAddr.getAddress();
		mplew.write(addr);
		mplew.writeShort(port);
		return mplew.getPacket();
	}

	public static MaplePacket getCharList(MapleClient c, int serverId) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CHARLIST.getValue());
		mplew.write(0);
		List<MapleCharacter> chars = c.loadCharacters(serverId);
		mplew.write((byte) chars.size());

		for (MapleCharacter chr : chars) {
			addCharEntry(mplew, chr);
		}

		return mplew.getPacket();
	}

	private static void addCharStats(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
		mplew.writeInt(chr.getId()); // character id
		mplew.writeAsciiString(chr.getName());
		for (int x = chr.getName().length(); x < 13; x++) { // fill to maximum
			// name length
			mplew.write(0);
		}

		mplew.write(chr.getGender()); // gender (0 = male, 1 = female)
		mplew.write(chr.getSkinColor().getId()); // skin color
		mplew.writeInt(chr.getFace()); // face
		mplew.writeInt(chr.getHair()); // hair
		mplew.writeInt(0);
		mplew.writeInt(0);

		mplew.write(chr.getLevel()); // level
		mplew.writeShort(chr.getJob().getId()); // job
		// mplew.writeShort(422);
		mplew.writeShort(chr.getStr()); // str
		mplew.writeShort(chr.getDex()); // dex
		mplew.writeShort(chr.getInt()); // int
		mplew.writeShort(chr.getLuk()); // luk
		mplew.writeShort(chr.getHp()); // hp (?)
		mplew.writeShort(chr.getMaxHp()); // maxhp
		mplew.writeShort(chr.getMp()); // mp (?)
		mplew.writeShort(chr.getMaxMp()); // maxmp
		mplew.writeShort(chr.getRemainingAp()); // remaining ap
		mplew.writeShort(chr.getRemainingSp()); // remaining sp
		mplew.writeInt(chr.getExp()); // current exp
		mplew.writeShort(chr.getFame()); // fame
		mplew.writeInt(chr.getMapId()); // current map id
		mplew.write(chr.getInitialSpawnpoint()); // spawnpoint
	}

	private static void addCharLook(MaplePacketLittleEndianWriter mplew, MapleCharacter chr,boolean mega) {
		mplew.write(chr.getGender());
		mplew.write(chr.getSkinColor().getId()); // skin color
		mplew.writeInt(chr.getFace()); // face
		// variable length
		mplew.write(mega ? 0 : 1);
		mplew.writeInt(chr.getHair()); // hair
		MapleInventory equip = chr.getInventory(MapleInventoryType.EQUIPPED);
		// Map<Integer, Integer> equipped = new LinkedHashMap<Integer,
		// Integer>();
		Map<Byte, Integer> myEquip = new LinkedHashMap<Byte, Integer>();
		Map<Byte, Integer> maskedEquip = new LinkedHashMap<Byte, Integer>();
		for (IItem item : equip.list()) {
			byte pos = (byte) (item.getPosition() * -1);
			if (pos < 100 && myEquip.get(pos) == null) {
				myEquip.put(pos, item.getItemId());
			} else if (pos > 100 && pos != 111) { // don't ask. o.o
				pos -= 100;
				if (myEquip.get(pos) != null) {
					maskedEquip.put(pos, myEquip.get(pos));
				}
				myEquip.put(pos, item.getItemId());
			} else if (myEquip.get(pos) != null) {
				maskedEquip.put(pos, item.getItemId());
			}
		}
		for (Entry<Byte,Integer> entry  : myEquip.entrySet()) {
			mplew.write(entry.getKey());
			mplew.writeInt(entry.getValue());
		}
		mplew.write(0xFF); // end of visible itens
		// masked itens
		for (Entry<Byte,Integer> entry  : maskedEquip.entrySet()) {
			mplew.write(entry.getKey());
			mplew.writeInt(entry.getValue());
		}
		/*
		 * for (IItem item : equip.list()) { byte pos = (byte)(item.getPosition() * -1); if (pos > 100) {
		 * mplew.write(pos - 100); mplew.writeInt(item.getItemId()); } }
		 */
		// ending markers
		mplew.write(0xFF);
		IItem cWeapon = equip.getItem((byte) -111);
		if (cWeapon != null)
			mplew.writeInt(cWeapon.getItemId());
		else
			mplew.writeInt(0); // cashweapon
		if (chr.getPet() != null) {
			mplew.writeInt(chr.getPet().getItemId());
		} else {
			mplew.writeInt(0); // pet
		}
	}

	private static void addCharEntry(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
		addCharStats(mplew, chr);
		addCharLook(mplew, chr,false);

		mplew.write(1); // world rank enabled (next 4 ints are not sent if disabled)
		mplew.writeInt(1); // world rank
		mplew.writeInt(0); // move (negative is downwards)
		mplew.writeInt(1); // job rank
		mplew.writeInt(0); // move (negative is downwards)
	}

	private static void addQuestInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
		List<MapleQuestStatus> started = chr.getStartedQuests();
		mplew.writeShort(started.size());
		for (MapleQuestStatus q : started) {
			mplew.writeInt(q.getQuest().getId());
		}
		List<MapleQuestStatus> completed = chr.getCompletedQuests();
		mplew.writeShort(completed.size());
		for (MapleQuestStatus q : completed) {
			mplew.writeShort(q.getQuest().getId());
			mplew.write(HexTool.getByteArrayFromHexString("80 DF BA C5 8B F3 C6 01"));
		}
	}

	public static MaplePacket getCharInfo(MapleCharacter chr) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.WARP_TO_MAP.getValue()); // 0x49
		mplew.writeInt(chr.getClient().getChannel() - 1);
		mplew.write(1);
		mplew.write(1);
		mplew.writeInt(new Random().nextInt()); // seed the maplestory rng with a random number <3
		mplew.write(HexTool.getByteArrayFromHexString("F4 83 6B 3D BA 9A 4F A1 FF FF"));
		addCharStats(mplew, chr);

		mplew.write(0x14); //???
		mplew.writeInt(chr.getMeso()); // mesos
		mplew.write(100); // equip slots
		mplew.write(100); // use slots
		mplew.write(100); // set-up slots
		mplew.write(100); // etc slots
		mplew.write(100); // cash slots

		MapleInventory iv = chr.getInventory(MapleInventoryType.EQUIPPED);
		Collection<IItem> equippedC = iv.list();
		List<Item> equipped = new ArrayList<Item>(equippedC.size());
		for (IItem item : equippedC) {
			equipped.add((Item) item);
		}
		Collections.sort(equipped);

		for (Item item : equipped) {
			addItemInfo(mplew, item);
		}
		mplew.writeShort(0); // start of equip inventory
		iv = chr.getInventory(MapleInventoryType.EQUIP);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0); // start of use inventory
		// addItemInfo(mplew, new Item(2020028, (byte) 8, (short) 1));
		iv = chr.getInventory(MapleInventoryType.USE);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0); // start of set-up inventory
		iv = chr.getInventory(MapleInventoryType.SETUP);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0); // start of etc inventory
		iv = chr.getInventory(MapleInventoryType.ETC);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0); // start of cash inventory
		iv = chr.getInventory(MapleInventoryType.CASH);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0); // start of skills

		Map<ISkill, MapleCharacter.SkillEntry> skills = chr.getSkills();
		mplew.writeShort(skills.size());
		for (Entry<ISkill, MapleCharacter.SkillEntry> skill : skills.entrySet()) {
			mplew.writeInt(skill.getKey().getId());
			mplew.writeInt(skill.getValue().skillevel);
			if (skill.getKey().isFourthJob()) {
				mplew.writeInt(skill.getValue().masterlevel);
			}
		}

		mplew.writeShort(0);
		addQuestInfo(mplew, chr);

		mplew.write(new byte[8]);
		for (int x = 0; x < 15; x++)
			mplew.write(CHAR_INFO_MAGIC);
		mplew.write(HexTool.getByteArrayFromHexString("90 63 3A 0D C5 5D C8 01"));

		return mplew.getPacket();
	}
	
	/**
	 * Sends an empty statupdate
	 * 
	 * @return
	 */
	public static MaplePacket enableActions() {
		return updatePlayerStats(EMPTY_STATUPDATE, true);
	}


	public static MaplePacket updatePlayerStats(List<Pair<MapleStat, Integer>> stats) {
		return updatePlayerStats(stats, false);
	}

	public static MaplePacket updatePlayerStats(List<Pair<MapleStat, Integer>> stats, boolean itemReaction) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.UPDATE_STATS.getValue());
		if (itemReaction) {
			mplew.write(1);
		} else {
			mplew.write(0);
		}
		mplew.write(0);
		int updateMask = 0;
		for (Pair<MapleStat, Integer> statupdate : stats) {
			updateMask |= statupdate.getLeft().getValue();
		}
		List<Pair<MapleStat, Integer>> mystats = stats;
		if (mystats.size() > 1) {
			Collections.sort(mystats, new Comparator<Pair<MapleStat, Integer>>() {
				@Override
				public int compare(Pair<MapleStat, Integer> o1, Pair<MapleStat, Integer> o2) {
					int val1 = o1.getLeft().getValue();
					int val2 = o2.getLeft().getValue();
					return (val1 < val2 ? -1 : (val1 == val2 ? 0 : 1));
				}
			});
		}
		mplew.writeInt(updateMask);
		for (Pair<MapleStat, Integer> statupdate : mystats) {
			if (statupdate.getLeft().getValue() > 1) {
				if (statupdate.getLeft().getValue() < 0x20) {
					mplew.write(statupdate.getRight().shortValue());
				} else if (statupdate.getLeft().getValue() < 0xFFFF) {
					mplew.writeShort(statupdate.getRight().shortValue());
				} else {
					mplew.writeInt(statupdate.getRight().intValue());
				}
			}
		}

		return mplew.getPacket();
	}

	public static MaplePacket getWarpToMap(MapleMap to, int spawnPoint, MapleCharacter chr) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.WARP_TO_MAP.getValue()); // 0x49
		mplew.writeInt(chr.getClient().getChannel() - 1);
		mplew.writeShort(0x2);
		mplew.writeInt(to.getId());
		mplew.write(spawnPoint);
		mplew.writeShort(chr.getHp()); // hp (???)
		mplew.write(0);
		long questMask = 0x1ffffffffffffffL;
		mplew.writeLong(questMask);

		return mplew.getPacket();
	}
	
	public static MaplePacket spawnPortal(int townId, int targetId, Point pos) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SPAWN_PORTAL.getValue());
		mplew.writeInt(townId);
		mplew.writeInt(targetId);
		if (pos != null) {
			mplew.writeShort(pos.x);
			mplew.writeShort(pos.y);
		}
		//System.out.println("ssp: " + HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}
	
	public static MaplePacket spawnDoor(int oid, Point pos, boolean town) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SPAWN_DOOR.getValue());
		// B9 00 00 47 1E 00 00
		mplew.write(town ? 1 : 0);
		mplew.writeInt(oid);
		mplew.writeShort(pos.x);
		mplew.writeShort(pos.y);
		// System.out.println("doorspawn: " + HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}
	
	public static MaplePacket removeDoor(int oid, boolean town) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		if (town) {
			mplew.writeShort(SendPacketOpcode.SPAWN_PORTAL.getValue());
			mplew.writeInt(999999999);
			mplew.writeInt(999999999);
		} else {
			mplew.writeShort(SendPacketOpcode.REMOVE_DOOR.getValue());		
			mplew.write(/*town ? 1 : */0);
			mplew.writeInt(oid);
		}
		//System.out.println("doorremove: " + HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}
	
	public static MaplePacket spawnSpecialMapObject(MapleCharacter chr, int skill, int skillLevel, Point pos, SummonMovementType movementType, boolean animated) {
		//72 00 29 1D 02 00 FD FE 30 00 19 7D FF BA 00 04 01 00 03 01 00

		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SPAWN_SPECIAL_MAPOBJECT.getValue());
		
		mplew.writeInt(chr.getId());
		mplew.writeInt(skill);
		mplew.write(skillLevel);
		mplew.writeShort(pos.x);
		mplew.writeShort(pos.y);
		//mplew.writeInt(oid);
		mplew.write(0); //?
		mplew.write(0); //?
		mplew.write(0);
		mplew.write(movementType.getValue()); // 0 = don't move, 1 = follow (4th mage summons?), 2/4 = only tele follow, 3 = bird follow
		
		mplew.write(1); // 0 and the summon can't attack - but puppets don't attack with 1 either ^.-
		mplew.write(animated ? 0 : 1);
		
		//System.out.println(HexTool.toString(mplew.getPacket().getBytes()));
		// 72 00 B3 94 00 00 FD FE 30 00 19 FC 00 B4 00 00 00 00 03 01 00 - fukos bird
		// 72 00 30 75 00 00 FD FE 30 00 00 FC 00 B4 00 00 00 00 03 01 00 - faeks bird
		return mplew.getPacket();
	}
	
	public static MaplePacket removeSpecialMapObject(MapleCharacter chr, int skill, boolean animated) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.REMOVE_SPECIAL_MAPOBJECT.getValue());
		
		mplew.writeInt(chr.getId());
		mplew.writeInt(skill);

		mplew.write(animated ? 4 : 1); //?
		
		//System.out.println(HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}
	
	protected static void addItemInfo(MaplePacketLittleEndianWriter mplew, IItem item) {
		addItemInfo(mplew, item, false, false);
	}
	
	private static void addExpirationTime(MaplePacketLittleEndianWriter mplew, long time, boolean showexpirationtime) {
		mplew.writeInt(KoreanDateUtil.getKoreanTimestamp(time));
		mplew.write(showexpirationtime ? 1 : 2);
	}

	private static void addItemInfo(MaplePacketLittleEndianWriter mplew, IItem item, boolean zeroPosition,
										boolean leaveOut) {
		MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
		byte pos = item.getPosition();
		boolean masking = false;
		if (zeroPosition) {
			if (!leaveOut)
				mplew.write(0);
		} else if (pos <= (byte) -1) {
			pos *= -1;
			if (pos > 100) {
				masking = true;
				mplew.write(0);
				mplew.write(pos - 100);
			} else {
				mplew.write(pos);
			}
		} else {
			mplew.write(item.getPosition());
		}

		mplew.write(item.getType());
		mplew.writeInt(item.getItemId());
		if (masking) {
			// 07.03.2008 06:49... o.o
			mplew.write(HexTool.getByteArrayFromHexString("01 41 B4 38 00 00 00 00 00 80 20 6F"));
		} else {
			mplew.writeShort(0);
			mplew.write(ITEM_MAGIC);
		}
		//TODO: Item.getExpirationTime
		addExpirationTime(mplew, 0, false);
		
		if (item.getType() == IItem.EQUIP) {
			IEquip equip = (IEquip) item;
			mplew.write(equip.getUpgradeSlots());
			mplew.write(equip.getLevel());
			mplew.writeShort(equip.getStr()); // str
			mplew.writeShort(equip.getDex()); // dex
			mplew.writeShort(equip.getInt()); // int
			mplew.writeShort(equip.getLuk()); // luk
			mplew.writeShort(equip.getHp()); // hp
			mplew.writeShort(equip.getMp()); // mp
			mplew.writeShort(equip.getWatk()); // watk
			mplew.writeShort(equip.getMatk()); // matk
			mplew.writeShort(equip.getWdef()); // wdef
			mplew.writeShort(equip.getMdef()); // mdef
			mplew.writeShort(equip.getAcc()); // accuracy
			mplew.writeShort(equip.getAvoid()); // avoid
			mplew.writeShort(equip.getHands()); // hands
			mplew.writeShort(equip.getSpeed()); // speed
			mplew.writeShort(equip.getJump()); // jump
			mplew.writeMapleAsciiString(equip.getOwner());
			// 0 normal; 1 locked
			mplew.write(0);
			if (!masking) {
				mplew.write(0);
				mplew.writeInt(0); // values of these don't seem to matter at all
				mplew.writeInt(0);
			}
		} else {
			mplew.writeShort(item.getQuantity());
			mplew.writeMapleAsciiString(item.getOwner());
			mplew.writeShort(0); // this seems to end the item entry
			// but only if its not a THROWING STAR :))9 O.O!
			if (ii.isThrowingStar(item.getItemId())) {
				// mplew.write(HexTool.getByteArrayFromHexString("A8 3A 00 00 41 00 00 20"));
				mplew.write(HexTool.getByteArrayFromHexString("A1 6D 05 01 00 00 00 7D"));
			}
		}
	}

	public static MaplePacket getRelogResponse() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);
		mplew.writeShort(SendPacketOpcode.RELOG_RESPONSE.getValue());
		mplew.write(1);
		return mplew.getPacket();
	}

	public static MaplePacket serverMessage(String message) {
		return serverMessage(4, 0, message, true);
	}

	/**
	 * type 0: [Notice]<br>
	 * type 1: Popup<br>
	 * type 2: Light blue background and lolwhut<br>
	 * type 4: Scrolling message at top<br>
	 * type 5: Pink Text<br>
	 * type 6: Lightblue Text
	 * 
	 * @param type
	 * @param message
	 * @return
	 */
	public static MaplePacket serverNotice(int type, String message) {
		return serverMessage(type, 0, message, false);
	}
	
	public static MaplePacket serverNotice(int type, int channel, String message) {
		return serverMessage(type, channel, message, false);
	}

	private static MaplePacket serverMessage(int type, int channel, String message, boolean servermessage) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SERVERMESSAGE.getValue()); // 0.47: 0x37, unchanged
		mplew.write(type);
		if (servermessage) {
			mplew.write(1);
		}
		mplew.writeMapleAsciiString(message);
		
		if (type == 3) {
			mplew.write(channel - 1); // channel
			mplew.write(0); // 0 = graues ohr, 1 = lulz?
		}
			

		return mplew.getPacket();
	}
	
	public static MaplePacket getAvatarMega(MapleCharacter chr, int channel, int itemId, List<String> message) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.AVATAR_MEGA.getValue());
		mplew.writeInt(itemId);
		mplew.writeMapleAsciiString(chr.getName());
		for (String s : message)
			mplew.writeMapleAsciiString(s);
		mplew.writeInt(channel - 1); // channel
		mplew.write(0);
		addCharLook(mplew, chr,true);
	
		return mplew.getPacket();
	}

	public static MaplePacket spawnNPC(MapleNPC life, boolean requestController) {
		// B1 00 01 04 00 00 00 34 08 00 00 99 FF 35 00 01 0B 00 67 FF CB FF
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		if (requestController) {
			mplew.writeShort(SendPacketOpcode.SPAWN_NPC_REQUEST_CONTROLLER.getValue());
			mplew.write(1); // ?
		} else {
			mplew.writeShort(SendPacketOpcode.SPAWN_NPC.getValue());
		}
		mplew.writeInt(life.getObjectId());
		mplew.writeInt(life.getId());
		mplew.writeShort(life.getPosition().x);
		mplew.writeShort(life.getCy());
		mplew.write(1); // type ?
		mplew.writeShort(life.getFh());
		mplew.writeShort(life.getRx0());
		mplew.writeShort(life.getRx1());

		return mplew.getPacket();
	}
	
	public static MaplePacket spawnMonster(MapleMonster life, boolean newSpawn) {
		return spawnMonsterInternal(life, false, newSpawn, false, 0);
	}
	
	public static MaplePacket spawnMonster(MapleMonster life, boolean newSpawn, int effect) {
		return spawnMonsterInternal(life, false, newSpawn, false, effect);
	}
	
	public static MaplePacket controlMonster (MapleMonster life, boolean newSpawn, boolean aggro) {
		return spawnMonsterInternal(life, true, newSpawn, aggro, 0);
	}

	private static MaplePacket spawnMonsterInternal(MapleMonster life, boolean requestController, boolean newSpawn, boolean aggro, int effect) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		// 95 00 DA 33 37 00 01 58 CC 6C 00 00 00 00 00 B7 FF F3 FB 02 1A 00 1A 00 02 0E 06 00 00 FF
		// OP    OBJID          MOBID       NULL        PX    PY    ST 00 00 FH                       
		// 95 00 7A 00 00 00 01 58 CC 6C 00 00 00 00 00 56 FF 3D FA 05 00 00 00 00             FE FF

		if (requestController) {
			mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());
			// mplew.writeShort(0xA0); // 47 9e
			if (aggro) {
				mplew.write(2);
			} else {
				mplew.write(1);
			}
		} else {
			mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER.getValue());
			// mplew.writeShort(0x9E); // 47 9c
		}
		mplew.writeInt(life.getObjectId());
		mplew.write(5); // ????!? either 5 or 1?
		mplew.writeInt(life.getId());
		mplew.writeInt(0); // if nonnull client crashes (?)

		mplew.writeShort(life.getPosition().x);
		mplew.writeShort(life.getPosition().y);
		// System.out.println(life.getPosition().x);
		// System.out.println(life.getPosition().y);
		// mplew.writeShort(life.getCy());
		mplew.write(life.getStance()); // or 5? o.O"
		mplew.writeShort(0); // ??
		mplew.writeShort(life.getFh()); // seems to be left and right restriction...
		
		if(effect > 0) {
		    mplew.write(effect);
		    mplew.write(0x00);
		    mplew.writeShort(0x00); // ?
		}
		
		if (newSpawn) {
			mplew.writeShort(-2);
		} else {
			mplew.writeShort(-1);
		}

		//System.out.println(mplew.toString());
		return mplew.getPacket();
	}
	
	public static MaplePacket stopControllingMonster (int oid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());
		mplew.write(0);
		mplew.writeInt(oid);
		
		return mplew.getPacket();
	}

	public static MaplePacket moveMonsterResponse(int objectid, short moveid, int currentMp, boolean useSkills) {
		// A1 00 18 DC 41 00 01 00 00 1E 00 00 00
		// A1 00 22 22 22 22 01 00 00 00 00 00 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MOVE_MONSTER_RESPONSE.getValue());
		mplew.writeInt(objectid);
		mplew.writeShort(moveid);
		mplew.write(useSkills ? 1 : 0);
		mplew.writeShort(currentMp);
		mplew.writeShort(0);

		return mplew.getPacket();
	}

	public static MaplePacket getChatText(int cidfrom, String text) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CHATTEXT.getValue());
		// mplew.writeShort(0x67); // 47 65
		mplew.writeInt(cidfrom);
		mplew.write(0); // gms have this set to != 0, gives them white
		// background text

		mplew.writeMapleAsciiString(text);

		return mplew.getPacket();
	}

	/**
	 * For testing only /!\
	 * 
	 * @param hex
	 * @return
	 */
	public static MaplePacket getPacketFromHexString(String hex) {
		byte[] b = HexTool.getByteArrayFromHexString(hex);
		return new ByteArrayMaplePacket(b);
	}

	public static MaplePacket getShowExpGain(int gain, boolean inChat, boolean white) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		// 20 00 03 01 0A 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(3); //3 = exp, 4 = fame, 5 = mesos, 6 = guildpoints
		mplew.write(white ? 1 : 0);
		mplew.writeInt(gain);
		mplew.write(inChat ? 1 : 0);
		mplew.writeInt(0);
		mplew.writeInt(0);
		mplew.writeInt(0);

		return mplew.getPacket();
	}
	
	public static MaplePacket getShowMesoGain(int gain) {
		return getShowMesoGain(gain, false);
	}

	public static MaplePacket getShowMesoGain(int gain, boolean inChat) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		if (!inChat) {
			mplew.write(0);
			mplew.write(1);
		} else {
			mplew.write(5);
		}
		mplew.writeInt(gain);
		mplew.writeShort(0); //inet cafe meso gain �.o

		return mplew.getPacket();
	}
	
	public static MaplePacket getShowItemGain(int itemId, short quantity) {
		return getShowItemGain(itemId, quantity, false);
	}

	public static MaplePacket getShowItemGain(int itemId, short quantity, boolean inChat) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		if (inChat) {
			// mplew.writeShort(0x92); // 47 90
			mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
			mplew.write(3);
			mplew.write(1);
			mplew.writeInt(itemId);
			mplew.writeInt(quantity);
		} else {
			mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
			// mplew.writeShort(0x21);
			mplew.writeShort(0);
			mplew.writeInt(itemId);
			mplew.writeInt(quantity);
			mplew.writeInt(0);
			mplew.writeInt(0);
		}
		return mplew.getPacket();
	}

	public static MaplePacket killMonster(int oid, boolean animation) {
		// 9D 00 45 2B 67 00 01
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.KILL_MONSTER.getValue());
		// mplew.writeShort(0x9f); // 47 9d
		mplew.writeInt(oid);
		if (animation) {
			mplew.write(1);
		} else {
			mplew.write(0);
		}

		return mplew.getPacket();
	}

	public static MaplePacket dropMesoFromMapObject(int amount, int itemoid, int dropperoid, int ownerid,
													Point dropfrom, Point dropto, byte mod) {
		return dropItemFromMapObjectInternal(amount, itemoid, dropperoid, ownerid, dropfrom, dropto, mod, true);
	}

	public static MaplePacket dropItemFromMapObject(int itemid, int itemoid, int dropperoid, int ownerid,
													Point dropfrom, Point dropto, byte mod) {
		return dropItemFromMapObjectInternal(itemid, itemoid, dropperoid, ownerid, dropfrom, dropto, mod, false);
	}
	
	public static MaplePacket dropItemFromMapObjectInternal(int itemid, int itemoid, int dropperoid, int ownerid,
													Point dropfrom, Point dropto, byte mod, boolean mesos) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		// dropping mesos
		// BF 00 01 01 00 00 00 01 0A 00 00 00 24 46 32 00 00 84 FF 70 00 00 00
		// 00 00 84 FF 70 00 00 00 00
		// dropping maple stars
		// BF 00 00 02 00 00 00 00 FB 95 1F 00 24 46 32 00 00 84 FF 70 00 00 00
		// 00 00 84 FF 70 00 00 00 00 80 05 BB 46 E6 17 02 00
		// killing monster (0F 2C 67 00)
		// BF 00 01 2C 03 00 00 00 6D 09 3D 00 24 46 32 00 00 A3 02 6C FF 0F 2C
		// 67 00 A3 02 94 FF 89 01 00 80 05 BB 46 E6 17 02 01

		// 4000109
		mplew.writeShort(SendPacketOpcode.DROP_ITEM_FROM_MAPOBJECT.getValue());
		// mplew.writeShort(0xC1); // 47 bf
		// mplew.write(1); // 1 with animation, 2 without o.o
		mplew.write(mod);
		mplew.writeInt(itemoid);
		mplew.write(mesos ? 1 : 0); // 1 = mesos, 0 =item
		mplew.writeInt(itemid);
		mplew.writeInt(ownerid); // owner charid
		mplew.write(0);
		mplew.writeShort(dropto.x);
		mplew.writeShort(dropto.y);
		if (mod != 2) {
			mplew.writeInt(ownerid);
			mplew.writeShort(dropfrom.x);
			mplew.writeShort(dropfrom.y);
		} else {
			mplew.writeInt(dropperoid);
		}
		mplew.write(0);
		if (mod != 2) {
			mplew.writeShort(0);
		}
		if (!mesos) {
			mplew.write(ITEM_MAGIC);
			//TODO getTheExpirationTimeFromSomewhere o.o
			addExpirationTime(mplew, System.currentTimeMillis(), false);
			// mplew.write(1);
			mplew.write(0);
		}

		return mplew.getPacket();
	}

	// TODO: make MapleCharacter a mapobject, remove the need for passing oid
	// here
	public static MaplePacket spawnPlayerMapobject(MapleCharacter chr) {
		// 62 00 24 46 32 00 05 00 42 65 79 61 6E 00 00 00 00 00 00 00 00 00 00
		// 00 00 00 00 00 00 00 00 20 4E 00 00 00 44 75 00 00 01 2A 4A 0F 00 04
		// 60 BF 0F 00 05 A2 05 10 00 07 2B 5C 10 00 09 E7 D0 10 00 0B 39 53 14
		// 00 FF FF 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
		// DE 01 73 FF 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00
		// 00 00 00 00

		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SPAWN_PLAYER.getValue());
		// mplew.writeInt(chr.getId());
		mplew.writeInt(chr.getId());
		mplew.writeMapleAsciiString(chr.getName());
		mplew.writeMapleAsciiString(""); // guild
		mplew.write(new byte[6]);

		long buffmask = 0;
		Integer buffvalue = null;

		if (chr.getBuffedValue(MapleBuffStat.DARKSIGHT) != null && !chr.isHidden()) {
			buffmask |= MapleBuffStat.DARKSIGHT.getValue();
		}
		if (chr.getBuffedValue(MapleBuffStat.COMBO) != null) {
			buffmask |= MapleBuffStat.COMBO.getValue();
			buffvalue = Integer.valueOf(chr.getBuffedValue(MapleBuffStat.COMBO).intValue());
		}
		if (chr.getBuffedValue(MapleBuffStat.MONSTER_RIDING) != null) {
			buffmask |= MapleBuffStat.MONSTER_RIDING.getValue();
		}
		if (chr.getBuffedValue(MapleBuffStat.SHADOWPARTNER) != null) {
			buffmask |= MapleBuffStat.SHADOWPARTNER.getValue();
		}
		mplew.writeLong(buffmask);

		if (buffvalue != null)
			mplew.write(buffvalue.byteValue());

		addCharLook(mplew, chr, false);
		mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 00 00 00 00 00 00 00 00"));
		mplew.writeShort(chr.getPosition().x);
		mplew.writeShort(chr.getPosition().y);
		mplew.write(chr.getStance());
		// 04 34 00 00
		// mplew.writeInt(1); // dunno p00 (?)
		if (chr.getPet() != null) {
			mplew.writeInt(0x01000000);
			mplew.writeInt(chr.getPet().getItemId());
			mplew.writeMapleAsciiString(chr.getPet().getName());
			// 38 EVTL. Y
			// mplew.write(HexTool.getByteArrayFromHexString("72 FB 38 00 00 00 00 00 09 03 04 00 18 34 00 00 00 01 00
			// 00 00"));
			mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 00 00 00 00"));
			mplew.writeShort(0);
			mplew.writeShort(chr.getPosition().y);
			mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 00 00 00 00 00"));
		} else {
			mplew.writeInt(0);
			mplew.writeInt(1);
		}

		mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 00 00 00 00"));
		if (chr.getPlayerShop() != null && chr.getPlayerShop().isOwner(chr)) {
			addAnnounceBox(mplew, chr.getPlayerShop());
		} else {
			mplew.write(0);
		}
		mplew.write(new byte[5]);
		// System.out.println(HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}

	private static void addAnnounceBox(MaplePacketLittleEndianWriter mplew, MaplePlayerShop shop) {
		// 00: no game
		// 01: omok game
		// 02: card game
		// 04: shop
		mplew.write(4);
		mplew.writeInt(shop.getObjectId()); // gameid/shopid
		mplew.writeMapleAsciiString(shop.getDescription()); // desc
		// 00: public
		// 01: private
		mplew.write(0);
		// 00: red 4x3
		// 01: green 5x4
		// 02: blue 6x5
		// omok:
		// 00: normal
		mplew.write(0);
		// first slot: 1/2/3/4
		// second slot: 1/2/3/4
		mplew.write(1);
		mplew.write(4);
		// 0: open
		// 1: in progress
		mplew.write(0);
	}

	public static MaplePacket facialExpression(MapleCharacter from, int expression) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.FACIAL_EXPRESSION.getValue());
		// mplew.writeShort(0x85); // 47 83
		mplew.writeInt(from.getId());
		mplew.writeInt(expression);
		return mplew.getPacket();
	}
	
	private static void serializeMovementList(LittleEndianWriter lew, List<LifeMovementFragment> moves) {
		lew.write(moves.size());
		for (LifeMovementFragment move : moves) {
			move.serialize(lew);
		}
	}
	
	public static MaplePacket movePlayer(int cid, List<LifeMovementFragment> moves) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		/*
		 * 7C 00 #10 27 00 00# 24 00# 3F FD 03 00# 00 00 Y#00 00 AF 00 00 00 C2 00 01 B4 00 01 AF 00 56 FD 06 00 00 00
		 * X00 00 Y00 00 AF 00 E9 FF 00 00 06 4A 01
		 */

		mplew.writeShort(SendPacketOpcode.MOVE_PLAYER.getValue());
		mplew.writeInt(cid);
		// mplew.write(HexTool.getByteArrayFromHexString("24 00 3F FD")); //?
		mplew.writeInt(0);

		serializeMovementList(mplew, moves);
		// dance ;)
		/*
		 * mplew .write(HexTool .getByteArrayFromHexString("0B 00 EC 00 4F 01 12 00 00 00 42 00 03 1E 00 00 EA 00 4F 01
		 * BE FF 00 00 42 00 03 3C 00 00 E9 00 4F 01 E8 FF 00 00 42 00 02 1E 00 00 EA 00 4F 01 3C 00 00 00 42 00 02 3C
		 * 00 00 EB 00 4F 01 12 00 00 00 42 00 03 1E 00 00 EB 00 4F 01 E8 FF 00 00 42 00 03 1E 00 00 EF 00 4F 01 66 00
		 * 00 00 42 00 02 5A 00 00 F2 00 4F 01 12 00 00 00 42 00 03 3C 00 00 F2 00 4F 01 00 00 00 00 42 00 0B 1E 00 00
		 * F5 00 4F 01 54 00 00 00 42 00 02 3C 00 00 F8 00 4F 01 3C 00 00 00 42 00 04 1E 00 11 88 58 55 88 55 85 18 55
		 * 00 E9 00 4F 01 F8 00 4F 01"));
		 */
		// mplew.write(1); //num commands (?) (alternative: command type)
		// mplew.write(0); //action to perform (0 = walk, 1 = jump, 4 = tele, 6 = fj)
		// mplew.writeShort(0); //x target
		// mplew.writeShort(0); //y target
		// mplew.writeShort(100); //x wobbliness
		// mplew.writeShort(100); //y wobbliness
		// mplew.writeShort(0);
		// mplew.write(1); //state after command
		// mplew.writeShort(1000); //time for this command in ms
		return mplew.getPacket();
	}
	
	public static MaplePacket moveSummon (int cid, int summonSkill, Point startPos, List<LifeMovementFragment> moves) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MOVE_SUMMON.getValue());
		mplew.writeInt(cid);
		mplew.writeInt(summonSkill);
		mplew.writeShort(startPos.x);
		mplew.writeShort(startPos.y);
		
		serializeMovementList(mplew, moves);
		
		return mplew.getPacket();
	}

	public static MaplePacket moveMonster(int useskill, int skill, int oid, Point startPos, List<LifeMovementFragment> moves) {
		/*
		 * A0 00 C8 00 00 00 00 FF 00 00 00 00 48 02 7D FE 02 00 1C 02 7D FE 9C FF 00 00 2A 00 03 BD 01 00 DC 01 7D FE
		 * 9C FF 00 00 2B 00 03 7B 02
		 */
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MOVE_MONSTER.getValue());
		// mplew.writeShort(0xA2); // 47 a0
		mplew.writeInt(oid);
		mplew.write(useskill);
		mplew.writeInt(skill);
		mplew.write(0);
		mplew.writeShort(startPos.x);
		mplew.writeShort(startPos.y);

		serializeMovementList(mplew, moves);

		return mplew.getPacket();
	}
	
	public static MaplePacket summonAttack(int cid, int summonSkillId, int newStance, List<SummonAttackEntry> allDamage) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.SUMMON_ATTACK.getValue());
		mplew.writeInt(cid);
		mplew.writeInt(summonSkillId);
		mplew.write(newStance);
		mplew.write(allDamage.size());
		for (SummonAttackEntry attackEntry : allDamage) {
			mplew.writeInt(attackEntry.getMonsterOid()); // oid
			mplew.write(6); // who knows
			mplew.writeInt(attackEntry.getDamage()); // damage
		}

		return mplew.getPacket();
	}

	public static MaplePacket closeRangeAttack(int cid, int skill, int stance, int numAttackedAndDamage,
												List<Pair<Integer, List<Integer>>> damage) {
		// 7D 00 #30 75 00 00# 12 00 06 02 0A 00 00 00 00 01 00 00 00 00 97 02
		// 00 00 97 02 00 00
		// 7D 00 #30 75 00 00# 11 00 06 02 0A 00 00 00 00 20 00 00 00 49 06 00
		// 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CLOSE_RANGE_ATTACK.getValue());
		// mplew.writeShort(0x7F); // 47 7D
		if(skill == 4211006) // meso explosion
		    addMesoExplosion(mplew, cid, skill, stance, numAttackedAndDamage, 0, damage);
		else
		    addAttackBody(mplew, cid, skill, stance, numAttackedAndDamage, 0, damage);

		return mplew.getPacket();
	}

	public static MaplePacket rangedAttack(int cid, int skill, int stance, int numAttackedAndDamage, int projectile,
											List<Pair<Integer, List<Integer>>> damage) {
		// 7E 00 30 75 00 00 01 00 97 04 0A CB 72 1F 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.RANGED_ATTACK.getValue());
		// mplew.writeShort(0x80); // 47 7E
		addAttackBody(mplew, cid, skill, stance, numAttackedAndDamage, projectile, damage);

		return mplew.getPacket();
	}

	public static MaplePacket magicAttack(int cid, int skill, int stance, int numAttackedAndDamage,
											List<Pair<Integer, List<Integer>>> damage) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MAGIC_ATTACK.getValue());
		// mplew.writeShort(0x81);
		addAttackBody(mplew, cid, skill, stance, numAttackedAndDamage, 0, damage);

		return mplew.getPacket();
	}

	private static void addAttackBody(LittleEndianWriter lew, int cid, int skill, int stance, int numAttackedAndDamage,
										int projectile, List<Pair<Integer, List<Integer>>> damage) {
		lew.writeInt(cid);
		lew.write(numAttackedAndDamage);
		if (skill > 0) {
			lew.write(0xFF); // too low and some skills don't work (?)
			lew.writeInt(skill);
		} else {
			lew.write(0);
		}
		lew.write(stance);
		lew.write(HexTool.getByteArrayFromHexString("02 0A"));
		lew.writeInt(projectile);

		for (Pair<Integer, List<Integer>> oned : damage) {
			if (oned.getRight() != null) {
				lew.writeInt(oned.getLeft().intValue());
				lew.write(0xFF);
				for (Integer eachd : oned.getRight()) {
					// highest bit set = crit
					lew.writeInt(eachd.intValue());
				}
			}
		}
	}
	
	private static void addMesoExplosion(LittleEndianWriter lew, int cid, int skill, int stance, int numAttackedAndDamage,
										int projectile, List<Pair<Integer, List<Integer>>> damage) {
		// 7A 00 6B F4 0C 00 22 1E 3E 41 40 00 38 04 0A 00 00 00 00 44 B0 04 00 06 02 E6 00 00 00 D0 00 00 00 F2 46 0E 00 06 02 D3 00 00 00 3B 01 00 00
		// 7A 00 6B F4 0C 00 00 1E 3E 41 40 00 38 04 0A 00 00 00 00 
		lew.writeInt(cid);
		lew.write(numAttackedAndDamage);
		lew.write(0x1E);
		lew.writeInt(skill);
		lew.write(stance);
		lew.write(HexTool.getByteArrayFromHexString("04 0A"));
		lew.writeInt(projectile);
		
		for (Pair<Integer, List<Integer>> oned : damage) {
			if(oned.getRight() != null) {
			    lew.writeInt(oned.getLeft().intValue());
			    lew.write(0xFF);
			    lew.write(oned.getRight().size());
			    for (Integer eachd : oned.getRight()) {
				    lew.writeInt(eachd.intValue());
			    }
			}
		}
		
	}
	
	public static MaplePacket getNPCShop(int sid, List<MapleShopItem> items) {
		MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_NPC_SHOP.getValue());
		mplew.writeInt(sid);
		mplew.writeShort(items.size()); // item count
		for (MapleShopItem item : items) {
			mplew.writeInt(item.getItemId());
			mplew.writeInt(item.getPrice());
			if (!ii.isThrowingStar(item.getItemId())) {
				mplew.writeShort(1); // stacksize o.o
				mplew.writeShort(item.getBuyable());
			} else {
				mplew.writeShort(0);
				mplew.writeInt(0);
				// o.O getPrice sometimes returns the unitPrice not the price
				mplew.writeShort(BitTools.doubleToShortBits(ii.getPrice(item.getItemId())));
				mplew.writeShort(ii.getSlotMax(item.getItemId()));
			}
		}
		
		return mplew.getPacket();
	}

	/**
	 * code (8 = sell, 0 = buy, 0x20 = due to an error the trade did not happen o.o)
	 * @param code
	 * @return
	 */
	public static MaplePacket confirmShopTransaction(byte code) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CONFIRM_SHOP_TRANSACTION.getValue());
		// mplew.writeShort(0xE6); // 47 E4
		mplew.write(code); // recharge == 8?
		return mplew.getPacket();
	}

	/*
	 * 19 reference 00 01 00 = new while adding 01 01 00 = add from drop 00 01 01 = update count 00 01 03 = clear slot
	 * 01 01 02 = move to empty slot 01 02 03 = move and merge 01 02 01 = move and merge with rest
	 */
	public static MaplePacket addInventorySlot(MapleInventoryType type, IItem item) {
		return addInventorySlot(type, item, false);
	}

	public static MaplePacket addInventorySlot(MapleInventoryType type, IItem item, boolean fromDrop) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		// mplew.writeShort(0x19);
		if (fromDrop) {
			mplew.write(1);
		} else {
			mplew.write(0);
		}
		mplew.write(HexTool.getByteArrayFromHexString("01 00")); // add mode
		mplew.write(type.getType()); // iv type
		mplew.write(item.getPosition()); // slot id
		addItemInfo(mplew, item, true, false);
		return mplew.getPacket();
	}

	public static MaplePacket updateInventorySlot(MapleInventoryType type, Item item) {
		return updateInventorySlot(type, item, false);
	}

	public static MaplePacket updateInventorySlot(MapleInventoryType type, Item item, boolean fromDrop) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		if (fromDrop) {
			mplew.write(1);
		} else {
			mplew.write(0);
		}
		mplew.write(HexTool.getByteArrayFromHexString("01 01")); // update
		// mode
		mplew.write(type.getType()); // iv type
		mplew.write(item.getPosition()); // slot id
		mplew.write(0); // ?
		mplew.writeShort(item.getQuantity());
		return mplew.getPacket();
	}

	public static MaplePacket moveInventoryItem(MapleInventoryType type, byte src, byte dst) {
		return moveInventoryItem(type, src, dst, (byte) -1);
	}

	public static MaplePacket moveInventoryItem(MapleInventoryType type, byte src, byte dst, byte equipIndicator) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("01 01 02"));
		mplew.write(type.getType());
		mplew.writeShort(src);
		mplew.writeShort(dst);
		if (equipIndicator != -1) {
			mplew.write(equipIndicator);
		}
		return mplew.getPacket();
	}

	public static MaplePacket moveAndMergeInventoryItem(MapleInventoryType type, byte src, byte dst, short total) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("01 02 03"));
		mplew.write(type.getType());
		mplew.writeShort(src);
		mplew.write(1); // merge mode?
		mplew.write(type.getType());
		mplew.writeShort(dst);
		mplew.writeShort(total);
		return mplew.getPacket();
	}

	public static MaplePacket moveAndMergeWithRestInventoryItem(MapleInventoryType type, byte src, byte dst,
																short srcQ, short dstQ) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("01 02 01"));
		mplew.write(type.getType());
		mplew.writeShort(src);
		mplew.writeShort(srcQ);
		mplew.write(HexTool.getByteArrayFromHexString("01"));
		mplew.write(type.getType());
		mplew.writeShort(dst);
		mplew.writeShort(dstQ);
		return mplew.getPacket();
	}

	public static MaplePacket clearInventoryItem(MapleInventoryType type, byte slot, boolean fromDrop) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(fromDrop ? 1 : 0);
		mplew.write(HexTool.getByteArrayFromHexString("01 03"));
		mplew.write(type.getType());
		mplew.writeShort(slot);
		return mplew.getPacket();
	}
	
	public static MaplePacket scrolledItem(IItem scroll, IItem item, boolean destroyed) {
		// 18 00 01 02 03 02 08 00 03 01 F7 FF 01
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(1); // fromdrop always true
		if (destroyed) {
			mplew.write(2);
		} else {
			mplew.write(3);
		}
		if (scroll.getQuantity() > 0) {
			mplew.write(1);
		} else {
			mplew.write(3);
		}
		mplew.write(MapleInventoryType.USE.getType());
		mplew.writeShort(scroll.getPosition());
		if (scroll.getQuantity() > 0) {
			mplew.writeShort(scroll.getQuantity());
		}
		mplew.write(3);
		if (!destroyed) {
			mplew.write(MapleInventoryType.EQUIP.getType());
			mplew.writeShort(item.getPosition());
			mplew.write(0);
		}
		mplew.write(MapleInventoryType.EQUIP.getType());
		mplew.writeShort(item.getPosition());
		if (!destroyed) {
			addItemInfo(mplew, item, true, true);
		}
		mplew.write(1);
		return mplew.getPacket();
	}
	
	public static MaplePacket getScrollEffect(int chr, ScrollResult scrollSuccess) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_SCROLL_EFFECT.getValue());
		mplew.writeInt(chr);
		switch (scrollSuccess) {
			case SUCCESS:
				mplew.writeInt(1);
				break;
			case FAIL:
				mplew.writeInt(0);
				break;
			case CURSE:
				mplew.write(0);
				mplew.write(1);
				mplew.writeShort(0);
				break;
			default:
				throw new IllegalArgumentException("effect in illegal range");
		}

		return mplew.getPacket();
	}

	public static MaplePacket removePlayerFromMap(int cid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.REMOVE_PLAYER_FROM_MAP.getValue());
		// mplew.writeShort(0x65); // 47 63
		mplew.writeInt(cid);
		return mplew.getPacket();
	}
	
	/**
	 * animation:
	 * 0 - expire<br/>
	 * 1 - without animation<br/>
	 * 2 - pickup<br/>
	 * 4 - explode<br/> 
	 * cid is ignored for 0 and 1
	 * @param oid
	 * @param animation
	 * @param cid
	 * @return
	 */
	public static MaplePacket removeItemFromMap(int oid, int animation, int cid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.REMOVE_ITEM_FROM_MAP.getValue());
		mplew.write(animation); // expire
		mplew.writeInt(oid);
		if (animation >= 2) {
			mplew.writeInt(cid);
		}
		return mplew.getPacket();
	}
	
	public static MaplePacket updateCharLook(MapleCharacter chr) {
		// 88 00 80 74 03 00 01 00 00 19 50 00 00 00 67 75 00 00 02 34 71 0F 00 04 59 BF 0F 00 05 AB 05 10 00 07 8C 5B
		// 10 00 08 F4 82 10 00 09 E7 D0 10 00 0A BE A9 10 00 0B 0C 05 14 00 FF FF 00 00 00 00 00 00 00 00 00 00 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_LOOK.getValue());
		mplew.writeInt(chr.getId());
		mplew.write(1);
		addCharLook(mplew, chr,false);
		mplew.writeShort(0);
		mplew.write(0);
		return mplew.getPacket();
	}

	public static MaplePacket dropInventoryItem(MapleInventoryType type, short src) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		// mplew.writeShort(0x19);
		mplew.write(HexTool.getByteArrayFromHexString("01 01 03"));
		mplew.write(type.getType());
		mplew.writeShort(src);
		if (src < 0) {
			mplew.write(1);
		}
		return mplew.getPacket();
	}

	public static MaplePacket dropInventoryItemUpdate(MapleInventoryType type, IItem item) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("01 01 01"));
		mplew.write(type.getType());
		mplew.writeShort(item.getPosition());
		mplew.writeShort(item.getQuantity());
		return mplew.getPacket();
	}

	public static MaplePacket damagePlayer(int skill, int monsteridfrom, int cid, int damage) {
		// 82 00 30 C0 23 00 FF 00 00 00 00 B4 34 03 00 01 00 00 00 00 00 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.DAMAGE_PLAYER.getValue());
		// mplew.writeShort(0x84); // 47 82
		mplew.writeInt(cid);
		mplew.write(skill);
		mplew.writeInt(0);
		mplew.writeInt(monsteridfrom);
		mplew.write(1);
		mplew.write(0);
		mplew.write(0); // > 0 = heros will effect
		mplew.writeInt(damage);

		return mplew.getPacket();
	}

	public static MaplePacket charNameResponse(String charname, boolean nameUsed) {
		// 0D 00 0C 00 42 6C 61 62 6C 75 62 62 31 32 33 34 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CHAR_NAME_RESPONSE.getValue());
		// mplew.writeShort(0xd);
		mplew.writeMapleAsciiString(charname);
		mplew.write(nameUsed ? 1 : 0);

		return mplew.getPacket();
	}

	public static MaplePacket addNewCharEntry(MapleCharacter chr, boolean worked) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.ADD_NEW_CHAR_ENTRY.getValue());

		mplew.write(worked ? 0 : 1);

		addCharEntry(mplew, chr);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param quest
	 * @return
	 */
	public static MaplePacket startQuest(MapleCharacter c, short quest) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		// mplew.writeShort(0x21);
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(1);
		mplew.writeShort(quest);
		mplew.writeShort(1);
		mplew.write(0);
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	/**
	 * state 0 = del ok state 12 = invalid bday
	 * 
	 * @param cid
	 * @param state
	 * @return
	 */
	public static MaplePacket deleteCharResponse(int cid, int state) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.DELETE_CHAR_RESPONSE.getValue());
		mplew.writeInt(cid);
		mplew.write(state);
		return mplew.getPacket();
	}

	public static MaplePacket charInfo(MapleCharacter chr) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CHAR_INFO.getValue());
		// mplew.writeShort(0x31);
		mplew.writeInt(chr.getId());
		mplew.write(chr.getLevel());
		mplew.writeShort(chr.getJob().getId());
		mplew.writeShort(chr.getFame());
		mplew.write(0); // heart red or gray

		mplew.writeMapleAsciiString(""); // guild

		if (false) { // got pet
			mplew.write(1);
			mplew.writeInt(5000036); // petid
			mplew.writeMapleAsciiString("Der TOOOOD");
			mplew.write(1); // pet level
			mplew.writeShort(1337); // pet closeness
			mplew.write(200); // pet fullness
			mplew.write(0x30); // ??
			mplew.write(new byte[5]); // probably pet equip and/or mount
			// and/or wishlist
		} 
		// no pet
		mplew.write(new byte[3]);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param quest
	 * @return
	 */
	public static MaplePacket forfeitQuest(MapleCharacter c, short quest) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(1);
		mplew.writeShort(quest);
		mplew.writeShort(0);
		mplew.write(0);
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param quest
	 * @return
	 */
	public static MaplePacket completeQuest(MapleCharacter c, short quest) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(1);
		mplew.writeShort(quest);
		mplew.write(HexTool.getByteArrayFromHexString("02 A0 67 B9 DA 69 3A C8 01"));
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param quest
	 * @param npc
	 * @param progress
	 * @return
	 */
	// frz note, 0.52 transition: this is only used when starting a quest and seems to have no effect, is it needed?
	public static MaplePacket updateQuestInfo(MapleCharacter c, short quest, int npc, byte progress) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.UPDATE_QUEST_INFO.getValue());
		mplew.write(progress);
		mplew.writeShort(quest);
		mplew.writeInt(npc);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	private static <E extends LongValueHolder> long getLongMask(List<Pair<E, Integer>> statups) {
		long mask = 0;
		for (Pair<E, Integer> statup : statups) {
			mask |= statup.getLeft().getValue();
		}
		return mask;
	}

	private static <E extends LongValueHolder> long getLongMaskFromList(List<E> statups) {
		long mask = 0;
		for (E statup : statups) {
			mask |= statup.getValue();
		}
		return mask;
	}

	/**
	 * It is important that statups is in the correct order (see decleration order in MapleBuffStat) since this method
	 * doesn't do automagical reordering.
	 * 
	 * @param buffid
	 * @param bufflength
	 * @param statups
	 * @return
	 */
	public static MaplePacket giveBuff(int buffid, int bufflength, List<Pair<MapleBuffStat, Integer>> statups) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());

		// darksight
		// 1C 00 80 04 00 00 00 00 00 00 F4 FF EB 0C 3D 00 C8 00 01 00 EB 0C 3D 00 C8 00 00 00 01
		// fire charge
		// 1C 00 04 00 40 00 00 00 00 00 26 00 7B 7A 12 00 90 01 01 00 7B 7A 12 00 90 01 58 02
		// ice charge
		// 1C 00 04 00 40 00 00 00 00 00 07 00 7D 7A 12 00 26 00 01 00 7D 7A 12 00 26 00 58 02
		// thunder charge
		// 1C 00 04 00 40 00 00 00 00 00 0B 00 7F 7A 12 00 18 00 01 00 7F 7A 12 00 18 00 58 02

		// incincible 0.49
		// 1B 00 00 80 00 00 00 00 00 00 0F 00 4B 1C 23 00 F8 24 01 00 00 00
		// mguard 0.49
		// 1B 00 00 02 00 00 00 00 00 00 50 00 6A 88 1E 00 C0 27 09 00 00 00
		// bless 0.49
		// 1B 00 3A 00 00 00 00 00 00 00 14 00 4C 1C 23 00 3F 0D 03 00 14 00 4C 1C 23 00 3F 0D 03 00 14 00 4C 1C 23 00 3F 0D 03 00 14 00 4C 1C 23 00 3F 0D 03 00 00 00
		
		// combo
		// 1B 00 00 00 20 00 00 00 00 00 01 00 DA F3 10 00 C0 D4 01 00 58 02
		// 1B 00 00 00 20 00 00 00 00 00 02 00 DA F3 10 00 57 B7 01 00 00 00
		// 1B 00 00 00 20 00 00 00 00 00 03 00 DA F3 10 00 51 A7 01 00 00 00
		
		long mask = getLongMask(statups);

		mplew.writeLong(mask);
		for (Pair<MapleBuffStat, Integer> statup : statups) {
			mplew.writeShort(statup.getRight().shortValue());
			mplew.writeInt(buffid);
			mplew.writeInt(bufflength);
		}
		
		
		mplew.writeShort(0); // ??? wk charges have 600 here �.o
		mplew.write(0);         // combo 600, too
		
		return mplew.getPacket();	
	}

	public static MaplePacket giveForeignBuff(int cid, List<Pair<MapleBuffStat, Integer>> statups) {
		// 8A 00 24 46 32 00 80 04 00 00 00 00 00 00 F4 00 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());

		mplew.writeInt(cid);
		long mask = getLongMask(statups);
		mplew.writeLong(mask);
		// TODO write the values somehow? only one byte per value?
                // seems to work
                for (Pair<MapleBuffStat, Integer> statup : statups) {
			mplew.writeShort(statup.getRight().byteValue());
		}
		mplew.writeShort(0); // same as give_buff

		return mplew.getPacket();
	}

	public static MaplePacket cancelForeignBuff(int cid, List<MapleBuffStat> statups) {
		// 8A 00 24 46 32 00 80 04 00 00 00 00 00 00 F4 00 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CANCEL_FOREIGN_BUFF.getValue());

		mplew.writeInt(cid);
		mplew.writeLong(getLongMaskFromList(statups));

		return mplew.getPacket();
	}

	public static MaplePacket cancelBuff(List<MapleBuffStat> statups) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CANCEL_BUFF.getValue());
		mplew.writeLong(getLongMaskFromList(statups));
		mplew.write(0);
		return mplew.getPacket();
	}

	public static MaplePacket getPlayerShopChat(MapleCharacter c, String chat, boolean owner) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("06 08"));
		mplew.write(owner ? 0 : 1);
		mplew.writeMapleAsciiString(c.getName() + " : " + chat);
		return mplew.getPacket();
	}

	public static MaplePacket getPlayerShopNewVisitor(MapleCharacter c) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("04 02"));
		addCharLook(mplew, c,false);
		mplew.writeMapleAsciiString(c.getName());
		return mplew.getPacket();
	}
	
	public static MaplePacket getTradePartnerAdd(MapleCharacter c) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("04 01"));// 00 04 88 4E 00"));
		addCharLook(mplew, c,false);
		mplew.writeMapleAsciiString(c.getName());
		return mplew.getPacket();
	}
	
	public static MaplePacket getTradeInvite(MapleCharacter c) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("02 03"));
		mplew.writeMapleAsciiString(c.getName());
		mplew.write(HexTool.getByteArrayFromHexString("B7 50 00 00"));
		return mplew.getPacket();
	}
	
	public static MaplePacket getTradeMesoSet(byte number, int meso) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0xE);
		mplew.write(number);
		mplew.writeInt(meso);
		return mplew.getPacket();
	}
	
	public static MaplePacket getTradeItemAdd(byte number, IItem item) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0xD);
		mplew.write(number);
		//mplew.write(1);
		addItemInfo(mplew, item);
		return mplew.getPacket();
	}
	
	public static MaplePacket getPlayerShopItemUpdate(MaplePlayerShop shop) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0x16);
		mplew.write(shop.getItems().size());
		for (MaplePlayerShopItem item : shop.getItems()) {
			mplew.writeShort(item.getBundles());
			mplew.writeShort(item.getItem().getQuantity());
			mplew.writeInt(item.getPrice());
			addItemInfo(mplew, item.getItem(), true, true);
		}
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param shop
	 * @param owner
	 * @return
	 */
	public static MaplePacket getPlayerShop(MapleClient c, MaplePlayerShop shop, boolean owner) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("05 04 04"));
		mplew.write(owner ? 0 : 1);
		mplew.write(0);
		addCharLook(mplew, shop.getOwner(),false);
		mplew.writeMapleAsciiString(shop.getOwner().getName());

		MapleCharacter[] visitors = shop.getVisitors();
		for (int i = 0; i < visitors.length; i++) {
			if (visitors[i] != null) {
				mplew.write(i + 1);
				addCharLook(mplew, visitors[i],false);
				mplew.writeMapleAsciiString(visitors[i].getName());
			}
		}
		mplew.write(0xFF);
		mplew.writeMapleAsciiString(shop.getDescription());
		List<MaplePlayerShopItem> items = shop.getItems();
		mplew.write(0x10);
		mplew.write(items.size());
		for (MaplePlayerShopItem item : items) {
			mplew.writeShort(item.getBundles());
			mplew.writeShort(item.getItem().getQuantity());
			mplew.writeInt(item.getPrice());
			addItemInfo(mplew, item.getItem(), true, true);
		}
		// mplew.write(HexTool.getByteArrayFromHexString("01 60 BF 0F 00 00 00 80 05 BB 46 E6 17 02 05 00 00 00 00 00 00
		// 00 00 00 1D 00 16 00 00 00 00 00 00 00 02 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 1B 7F 00 00 0D 00 00
		// 40 01 00 01 00 FF 34 0C 00 01 E6 D0 10 00 00 00 80 05 BB 46 E6 17 02 04 01 00 00 00 00 00 00 00 00 0A 00 00
		// 00 00 00 00 00 00 00 00 00 00 00 0F 00 00 00 00 00 00 00 00 00 00 00 63 CF 07 01 00 00 00 7C 01 00 01 00 5F
		// AE 0A 00 01 79 16 15 00 00 00 80 05 BB 46 E6 17 02 07 00 00 00 00 00 00 00 00 00 66 00 00 00 21 00 2F 00 00
		// 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 A4 82 7A 01 00 00 00 7C 01 00 01 00 5F AE 0A 00 01 79 16
		// 15 00 00 00 80 05 BB 46 E6 17 02 07 00 00 00 00 00 00 00 00 00 66 00 00 00 23 00 2C 00 00 00 00 00 00 00 00
		// 00 00 00 00 00 00 00 00 00 00 00 FE AD 88 01 00 00 00 7C 01 00 01 00 DF 67 35 00 01 E5 D0 10 00 00 00 80 05
		// BB 46 E6 17 02 01 03 00 00 00 00 07 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0A 00 00 00 00 00 00
		// 00 00 00 00 00 CE D4 F1 00 00 00 00 7C 01 00 01 00 7F 1A 06 00 01 4C BF 0F 00 00 00 80 05 BB 46 E6 17 02 05
		// 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 1D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 38
		// CE AF 00 00 00 00 7C 01 00 01 00 BF 27 09 00 01 07 76 16 00 00 00 80 05 BB 46 E6 17 02 00 07 00 00 00 00 00
		// 00 00 00 00 00 00 00 17 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 7C 02 00 00 1E 00 00
		// 48 01 00 01 00 5F E3 16 00 01 11 05 14 00 00 00 80 05 BB 46 E6 17 02 07 00 00 00 00 00 00 00 00 00 00 00 00
		// 00 21 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 1C 8A 00 00 39 00 00 10 01 00 01 00 7F
		// 84 1E 00 01 05 DE 13 00 00 00 80 05 BB 46 E6 17 02 07 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
		// 00 00 00 00 00 00 00 00 00 0C 00 00 00 00 00 00 00 00 E5 07 01 00 00 00 7C 2B 00 01 00 AF B3 00 00 02 FC 0C
		// 3D 00 00 00 80 05 BB 46 E6 17 02 2B 00 00 00 00 00 00 00 01 00 0F 27 00 00 02 D1 ED 2D 00 00 00 80 05 BB 46
		// E6 17 02 01 00 00 00 00 00 0A 00 01 00 9F 0F 00 00 02 84 84 1E 00 00 00 80 05 BB 46 E6 17 02 0A 00 00 00 00
		// 00 01 00 01 00 FF 08 3D 00 01 02 05 14 00 00 00 80 05 BB 46 E6 17 02 07 00 00 00 00 00 00 00 00 00 00 00 00
		// 00 25 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 78 36 00 00 1D 00 00 14 01 00 01 00 9F
		// 25 26 00 01 2B 2C 14 00 00 00 80 05 BB 46 E6 17 02 07 00 00 00 00 00 00 00 00 00 00 00 00 00 34 00 00 00 06
		// 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 E8 76 00 00 1F 00 00 24 01 00 01 00 BF 0E 16 02 01 D9 D0
		// 10 00 00 00 80 05 BB 46 E6 17 02 00 04 00 00 00 00 00 00 07 00 00 00 00 00 02 00 00 00 06 00 08 00 00 00 00
		// 00 00 00 00 00 00 00 00 00 00 00 23 02 00 00 1C 00 00 1C 5A 00 01 00 0F 27 00 00 02 B8 14 3D 00 00 00 80 05
		// BB 46 E6 17 02 5A 00 00 00 00 00"));
		/*
		 * 10 10 01 00 01 00 3F 42 0F 00 01 60 BF 0F 00 00 00 80 05 BB /* ||||||||||| OMG ITS THE PRICE ||||| PROBABLY
		 * THA QUANTITY ||||||||||| itemid
		 * 
		 */
		// mplew.writeInt(0);
		return mplew.getPacket();
	}
	
	public static MaplePacket getTradeStart(MapleClient c, MapleTrade trade, byte number) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("05 03 02"));
		mplew.write(number);
		if (number == 1) {
			mplew.write(0);
			addCharLook(mplew, trade.getPartner().getChr(), false);
			mplew.writeMapleAsciiString(trade.getPartner().getChr().getName());
		}
		mplew.write(number);
		/*if (number == 1) {
			mplew.write(0);
			mplew.writeInt(c.getPlayer().getId());
		}*/
		addCharLook(mplew, c.getPlayer(), false);
		mplew.writeMapleAsciiString(c.getPlayer().getName());
		mplew.write(0xFF);
		return mplew.getPacket();
	}
	
	public static MaplePacket getTradeConfirmation() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());		
		mplew.write(0xF);
		return mplew.getPacket();
	}
	
	public static MaplePacket getTradeCompletion(byte number) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());		
		mplew.write(0xA);
		mplew.write(number);
		mplew.write(6);
		return mplew.getPacket();
	}
	
	public static MaplePacket getTradeCancel(byte number) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());		
		mplew.write(0xA);
		mplew.write(number);
		mplew.write(2);
		return mplew.getPacket();
	}

	public static MaplePacket updateCharBox(MapleCharacter c) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_BOX.getValue());
		mplew.writeInt(c.getId());
		if (c.getPlayerShop() != null) {
			addAnnounceBox(mplew, c.getPlayerShop());
		} else {
			mplew.write(0);
		}
		return mplew.getPacket();
	}

	public static MaplePacket getNPCTalk(int npc, byte msgType, String talk, String endBytes) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
		mplew.write(4); // ?
		mplew.writeInt(npc);
		mplew.write(msgType);
		mplew.writeMapleAsciiString(talk);
		mplew.write(HexTool.getByteArrayFromHexString(endBytes));
		return mplew.getPacket();
	}

	public static MaplePacket showLevelup(int cid) {
		return showForeignEffect(cid, 0);
	}
	
	public static MaplePacket showJobChange(int cid) {
		return showForeignEffect(cid, 8);
	}
	
	public static MaplePacket showForeignEffect(int cid, int effect) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
		mplew.writeInt(cid); // ?
		mplew.write(effect);
		return mplew.getPacket();
	}

	public static MaplePacket showBuffeffect(int cid, int skillid, int effectid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
		mplew.writeInt(cid); // ?
		mplew.write(effectid);
		mplew.writeInt(skillid);
		mplew.write(1); // probably buff level but we don't know it and it doesn't really matter
		return mplew.getPacket();
	}
	
	public static MaplePacket showOwnBuffEffect(int skillid, int effectid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
		mplew.write(effectid);
		mplew.writeInt(skillid);
		mplew.write(1);  // probably buff level but we don't know it and it doesn't really matter
		return mplew.getPacket();
	}

	public static MaplePacket updateSkill(int skillid, int level, int masterlevel) {
		// 1E 00 01 01 00 E9 03 00 00 01 00 00 00 00 00 00 00 01
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.UPDATE_SKILLS.getValue());
		mplew.write(1);
		mplew.writeShort(1);
		mplew.writeInt(skillid);
		mplew.writeInt(level);
		mplew.writeInt(masterlevel);
		mplew.write(1);
		return mplew.getPacket();
	}

	public static MaplePacket updateQuestMobKills(MapleQuestStatus status) {
		// 21 00 01 FB 03 01 03 00 30 30 31
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(1);
		mplew.writeShort(status.getQuest().getId());
		mplew.write(1);
		String killStr = "";
		for (int kills : status.getMobKills().values()) {
			killStr += StringUtil.getLeftPaddedStr(String.valueOf(kills), '0', 3);
		}
		mplew.writeMapleAsciiString(killStr);
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	public static MaplePacket getShowQuestCompletion(int id) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_QUEST_COMPLETION.getValue());
		mplew.writeShort(id);
		return mplew.getPacket();
	}

	public static MaplePacket getKeymap(Map<Integer, MapleKeyBinding> keybindings) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.KEYMAP.getValue());
		mplew.write(0);

		for (int x = 0; x < 90; x++) {
			MapleKeyBinding binding = keybindings.get(Integer.valueOf(x));
			if (binding != null) {
				mplew.write(binding.getType());
				mplew.writeInt(binding.getAction());
			} else {
				mplew.write(0);
				mplew.writeInt(0);
			}
		}

		return mplew.getPacket();
	}

	public static MaplePacket getWhisper(String sender, int channel, String text) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
		mplew.write(0x12);
		mplew.writeMapleAsciiString(sender);
		mplew.writeShort(channel - 1); // I guess this is the channel
		mplew.writeMapleAsciiString(text);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param target name of the target character
	 * @param reply error code: 0x0 = cannot find char, 0x1 = success
	 * @return the MaplePacket
	 */
	public static MaplePacket getWhisperReply(String target, byte reply) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
		mplew.write(0x0A); // whisper?
		mplew.writeMapleAsciiString(target);
		mplew.write(reply);
		// System.out.println(HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}

	public static MaplePacket getFindReplyWithMap(String target, int mapid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
		mplew.write(9);
		mplew.writeMapleAsciiString(target);
		mplew.write(1);
		mplew.writeInt(mapid);
		// ?? official doesn't send zeros here but whatever
		mplew.write(new byte[8]);
		return mplew.getPacket();
	}
	
	public static MaplePacket getFindReply(String target, int channel) {
		//Received UNKNOWN (1205941596.79689):  (25) 
		//54 00 09 07 00 64 61 76 74 73 61 69 01 86 7F 3D 36 D5 02 00 00 22 00 00 00
		//T....davtsai..=6...."...
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
		mplew.write(9);
		mplew.writeMapleAsciiString(target);
		mplew.write(3);
		mplew.writeInt(channel - 1);
		return mplew.getPacket();		
	}

	public static MaplePacket getInventoryFull() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(1);
		mplew.write(0);
		return mplew.getPacket();
	}

	public static MaplePacket getShowInventoryFull() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(0);
		mplew.write(0xFF);
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	public static MaplePacket getStorage(int npcId, byte slots, Collection<IItem> items, int meso) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0x13);
		mplew.writeInt(npcId);
		mplew.write(slots);
		mplew.writeShort(0x7E);
		mplew.writeInt(meso);
		mplew.write(HexTool.getByteArrayFromHexString("00 00 00"));
		mplew.write((byte) items.size());
		for (IItem item : items) {
			addItemInfo(mplew, item, true, true);
		}
		mplew.write(0);
		return mplew.getPacket();
	}
	
	public static MaplePacket getStorageFull() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0xE);
		return mplew.getPacket();
	}
	
	public static MaplePacket mesoStorage(byte slots, int meso) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0x10);
		mplew.write(slots);
		mplew.writeShort(2);
		mplew.writeInt(meso);
		return mplew.getPacket();
	}

	public static MaplePacket storeStorage(byte slots, MapleInventoryType type, Collection<IItem> items) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0xB);
		mplew.write(slots);
		mplew.writeShort(type.getBitfieldEncoding());
		mplew.write(items.size());
		for (IItem item : items) {
			addItemInfo(mplew, item, true, true);
			//mplew.write(0);
		}
		
		
		return mplew.getPacket();
	}

	/*public static MaplePacket takeOutStorage(byte slots, byte slot) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(8);
		mplew.write(slots);
		mplew.write(4 * slot);
		mplew.writeShort(0);
		return mplew.getPacket();
	}*/

	public static MaplePacket takeOutStorage(byte slots, MapleInventoryType type, Collection<IItem> items) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0x8);
		mplew.write(slots);
		mplew.writeShort(type.getBitfieldEncoding());
		mplew.write(items.size());
		for (IItem item : items) {
			addItemInfo(mplew, item, true, true);
			//mplew.write(0);
		}

		return mplew.getPacket();
	}
	
	/**
	 * 
	 * @param oid
	 * @param remhp in %
	 * @return
	 */
	public static MaplePacket showMonsterHP (int oid, int remhppercentage) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_MONSTER_HP.getValue());
		mplew.writeInt(oid);
		mplew.write(remhppercentage);
		
		return mplew.getPacket();
	}
	
	public static MaplePacket giveFameResponse(int mode, String charname, int newfame) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());

		mplew.write(0);
		mplew.writeMapleAsciiString(charname);
		mplew.write(mode);
		mplew.writeShort(newfame);
		mplew.writeShort(0);

		return mplew.getPacket();
	}
	
	/**
	 * status can be: <br>
	 * 0: ok, use giveFameResponse<br>
	 * 1: the username is incorrectly entered<br>
	 * 2: users under level 15 are unable to toggle with fame.<br>
	 * 3: can't raise or drop fame anymore today.<br>
	 * 4: can't raise or drop fame for this character for this month anymore.<br>
	 * 5: received fame, use receiveFame()<br>
	 * 6: level of fame neither has been raised nor dropped due to an unexpected error
	 * 
	 * @param status
	 * @param mode
	 * @param charname
	 * @param newfame
	 * @return
	 */
	public static MaplePacket giveFameErrorResponse(int status) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());

		mplew.write(status);
			
		return mplew.getPacket();
	}
	
	
	public static MaplePacket receiveFame(int mode, String charnameFrom) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());
		mplew.write(5);
		mplew.writeMapleAsciiString(charnameFrom);
		mplew.write(mode);

		return mplew.getPacket();
	}
	
	public static MaplePacket partyCreated() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.write(8);
		mplew.writeShort(0x8b);
		mplew.writeShort(2);
		mplew.write(CHAR_INFO_MAGIC);
		mplew.write(CHAR_INFO_MAGIC);
		mplew.writeInt(0);
		
		return mplew.getPacket();
	}
	
	public static MaplePacket partyInvite (MapleCharacter from) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.write(4);
		mplew.writeInt(from.getParty().getId());
		mplew.writeMapleAsciiString(from.getName());
		
		return mplew.getPacket();
	}
	
	/**
	 * 10: a beginner can't create a party<br>
	 * 11/14/19: your request for a party didn't work due to an unexpected error<br>
	 * 13: you have yet to join a party<br>
	 * 16: already have joined a party<br>
	 * 17: the party you are trying to join is already at full capacity<br>
	 * 18: unable to find the requested character in this channel<br>
	 * 
	 * @param message
	 * @return
	 */
	public static MaplePacket partyStatusMessage(int message) {
		//32 00 08 DA 14 00 00 FF C9 9A 3B FF C9 9A 3B 22 03 6E 67
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.write(message);

		return mplew.getPacket();
	}
	
	/**
	 * 22: has denied the invitation<br>
	 * 
	 * @param message
	 * @param charname
	 * @return
	 */
	public static MaplePacket partyStatusMessage(int message, String charname) {
		//32 00 08 DA 14 00 00 FF C9 9A 3B FF C9 9A 3B 22 03 6E 67
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.write(message);
		mplew.writeMapleAsciiString(charname);

		return mplew.getPacket();
	}
	
	private static void addPartyStatus (int forchannel, MapleParty party, LittleEndianWriter lew, boolean leaving) {
		List<MaplePartyCharacter> partymembers = new ArrayList<MaplePartyCharacter>(party.getMembers());
		while (partymembers.size() < 6) {
			partymembers.add(new MaplePartyCharacter());
		}
		for (MaplePartyCharacter partychar : partymembers) {
			lew.writeInt(partychar.getId());
		}
		for (MaplePartyCharacter partychar : partymembers) {
			lew.writeAsciiString(StringUtil.getRightPaddedStr(partychar.getName(), '\0', 13));
		}
		for (MaplePartyCharacter partychar : partymembers) {
			lew.writeInt(partychar.getJobId());
		}
		for (MaplePartyCharacter partychar : partymembers) {
			lew.writeInt(partychar.getLevel());
		}
		for (MaplePartyCharacter partychar : partymembers) {
			if (partychar.isOnline()) {
				lew.writeInt(partychar.getChannel() - 1);
			} else {
				lew.writeInt(-2);
			}
		}
		lew.writeInt(party.getLeader().getId());
		for (MaplePartyCharacter partychar : partymembers) {
			if (partychar.getChannel() == forchannel) {
				lew.writeInt(partychar.getMapid());
			} else {
				lew.writeInt(-2);
			}
		}
		for (MaplePartyCharacter partychar : partymembers) {
			if (partychar.getChannel() == forchannel && !leaving) {
				lew.writeInt(partychar.getDoorTown());
				lew.writeInt(partychar.getDoorTarget());
				lew.writeInt(partychar.getDoorPosition().x);
				lew.writeInt(partychar.getDoorPosition().y);
			} else {
				lew.writeInt(999999999);
				lew.writeInt(999999999);
				lew.writeInt(-1);
				lew.writeInt(-1);
			}
		}
	}

	public static MaplePacket updateParty (int forChannel, MapleParty party, PartyOperation op, MaplePartyCharacter target)
	{
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		switch (op) {
			case DISBAND:
			case EXPEL:
			case LEAVE:
				mplew.write(0xC);
				mplew.writeShort(0x8b);
				mplew.writeShort(2);
				mplew.writeInt(target.getId());

				if (op == PartyOperation.DISBAND) {
					mplew.write(0);
					mplew.writeInt(party.getId());
				} else {
					mplew.write(1);
					if (op == PartyOperation.EXPEL) {
						mplew.write(1);
					} else {
						mplew.write(0);
					}
					mplew.writeMapleAsciiString(target.getName());
					addPartyStatus(forChannel, party, mplew, false);
					//addLeavePartyTail(mplew);
				}
				
				break;
			case JOIN:
				mplew.write(0xF);
				mplew.writeShort(0x8b);
				mplew.writeShort(2);
				mplew.writeMapleAsciiString(target.getName());
				addPartyStatus(forChannel, party, mplew, false);
				//addJoinPartyTail(mplew);
				break;
			case SILENT_UPDATE:
			case LOG_ONOFF:
				if (op == PartyOperation.LOG_ONOFF) {
					mplew.write(0x1F); //actually this is silent too
				} else {
					mplew.write(0x7);
				}
				mplew.write(0xdd);
				mplew.write(0x14);
				mplew.writeShort(0);
				addPartyStatus(forChannel, party, mplew, false);
				//addJoinPartyTail(mplew);
				//addDoorPartyTail(mplew);
				break;

		}
		//System.out.println("partyupdate: " + HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}
	
	public static MaplePacket partyPortal(int townId, int targetId, Point position) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.writeShort(0x22);
		mplew.writeInt(townId);
		mplew.writeInt(targetId);
		mplew.writeShort(position.x);
		mplew.writeShort(position.y);
		//System.out.println("partyportal: " + HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}
	
	// 87 00 30 75 00 00# 00 02 00 00 00 03 00 00
	public static MaplePacket updatePartyMemberHP (int cid, int curhp, int maxhp) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.UPDATE_PARTYMEMBER_HP.getValue());
		mplew.writeInt(cid);
		mplew.writeInt(curhp);
		mplew.writeInt(maxhp);
		return mplew.getPacket();
	}

	/**
	 * mode: 0 buddychat; 1 partychat; 2 guildchat
	 * @param name
	 * @param chattext
	 * @param mode
	 * @return
	 */
	public static MaplePacket multiChat(String name, String chattext, int mode) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.MULTICHAT.getValue());
		mplew.write(mode);
		mplew.writeMapleAsciiString(name);
		mplew.writeMapleAsciiString(chattext);
		return mplew.getPacket();
	}
	
	public static MaplePacket applyMonsterStatus(int oid, Map<MonsterStatus, Integer> stats, int skill, boolean monsterSkill, int delay) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		// 9B 00 67 40 6F 00 80 00 00 00 01 00 FD FE 30 00 08 00 64 00 01

		mplew.writeShort(SendPacketOpcode.APPLY_MONSTER_STATUS.getValue());
		mplew.writeInt(oid);

		int mask = 0;
		for (MonsterStatus stat : stats.keySet()) {
			mask |= stat.getValue();
		}

		mplew.writeInt(mask);

		for (Integer val : stats.values()) {
			mplew.writeShort(val);
			if (monsterSkill) {
				mplew.writeShort(skill);
				mplew.writeShort(1);
			} else {
				mplew.writeInt(skill);
			}
			mplew.writeShort(0); // as this looks similar to giveBuff this might actually be the buffTime but it's not displayed anywhere
		}

		mplew.writeShort(delay); // delay in ms
		mplew.write(1); // ?

		return mplew.getPacket();
	}
	
	public static MaplePacket cancelMonsterStatus(int oid, Map<MonsterStatus, Integer> stats) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CANCEL_MONSTER_STATUS.getValue());
		
		mplew.writeInt(oid);
		int mask = 0;
		for (MonsterStatus stat : stats.keySet()) {
			mask |= stat.getValue();
		}

		mplew.writeInt(mask);
		mplew.write(1);
		
		return mplew.getPacket();
	}
	
	public static MaplePacket getClock(int time) { // time in seconds
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CLOCK.getValue());
		mplew.write(2); // clock type. if you send 3 here you have to send another byte (which does not matter at all) before the timestamp
		mplew.writeInt(time);
		return mplew.getPacket();
	}
	
	public static MaplePacket spawnMist(int oid, int ownerCid, int skillId, Rectangle mistPosition) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.SPAWN_MIST.getValue());
		mplew.writeInt(oid); //maybe this should actually be the "mistid" - seems to always be 1 with only one mist in the map...
		mplew.write(0);
		mplew.writeInt(ownerCid); // probably only intresting for smokescreen
		mplew.writeInt(skillId);
		mplew.write(1); // who knows
		mplew.writeShort(7); // ???
		
		mplew.writeInt(mistPosition.x); // left position
		mplew.writeInt(mistPosition.y); // bottom position
		mplew.writeInt(mistPosition.x + mistPosition.width); // left position
		mplew.writeInt(mistPosition.y + mistPosition.height); // upper position
		mplew.write(0);

		return mplew.getPacket();
	}
	
	public static MaplePacket removeMist(int oid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.REMOVE_MIST.getValue());
		mplew.writeInt(oid);

		return mplew.getPacket();
	}
	
	public static MaplePacket damageSummon(int cid, int summonSkillId, int damage, int unkByte, int monsterIdFrom) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.DAMAGE_SUMMON.getValue());
		//77 00 29 1D 02 00 FA FE 30 00 00 10 00 00 00 BF 70 8F 00 00
		mplew.writeInt(cid);
		mplew.writeInt(summonSkillId);
		mplew.write(unkByte);
		mplew.writeInt(damage);
		mplew.writeInt(monsterIdFrom);
		mplew.write(0);

		return mplew.getPacket();
	}
	
	public static MaplePacket damageMonster(int oid, int damage) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.DAMAGE_MONSTER.getValue());
		mplew.writeInt(oid);
		mplew.write(0);
		mplew.writeInt(damage);

		return mplew.getPacket();
	}
	
	public static MaplePacket updateBuddylist(Collection<BuddylistEntry> buddylist) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
		mplew.write(7);
		mplew.write(buddylist.size());
		for (BuddylistEntry buddy : buddylist) {
			if (buddy.isVisible()) {
				mplew.writeInt(buddy.getCharacterId()); // cid
				mplew.writeAsciiString(StringUtil.getRightPaddedStr(buddy.getName(), '\0', 13));
				mplew.write(0);
				mplew.writeInt(buddy.getChannel() - 1);
			}
		}
		for (int x = 0; x < buddylist.size(); x++) {
			mplew.writeInt(0);
		}
		return mplew.getPacket();
	}
	
	public static MaplePacket requestBuddylistAdd(int cidFrom, String nameFrom) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
		mplew.write(9);
		mplew.writeInt(cidFrom);
		mplew.writeMapleAsciiString(nameFrom);
		mplew.writeInt(cidFrom);
		mplew.writeAsciiString(StringUtil.getRightPaddedStr(nameFrom, '\0', 13));
		mplew.write(1);
		mplew.write(31);
		mplew.writeInt(0);

		return mplew.getPacket();
	}
	
	public static MaplePacket updateBuddyChannel(int characterid, int channel) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
		//2B 00 14 30 C0 23 00 00 11 00 00 00
		mplew.write(0x14);
		mplew.writeInt(characterid);
		mplew.write(0);
		mplew.writeInt(channel);
		
		// 2B 00 14 30 C0 23 00 00 0D 00 00 00
		// 2B 00 14 30 75 00 00 00 11 00 00 00
		return mplew.getPacket();
	}
}
