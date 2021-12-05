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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.odinms.net.channel;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import net.sf.odinms.client.BuddyList;
import net.sf.odinms.client.BuddylistEntry;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleCharacterUtil;
import net.sf.odinms.client.BuddyList.BuddyAddResult;
import net.sf.odinms.client.BuddyList.BuddyOperation;
import net.sf.odinms.net.ByteArrayMaplePacket;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.net.channel.remote.ChannelWorldInterface;
import net.sf.odinms.net.world.MapleParty;
import net.sf.odinms.net.world.MaplePartyCharacter;
import net.sf.odinms.net.world.PartyOperation;
import net.sf.odinms.net.world.remote.CheaterData;
import net.sf.odinms.server.ShutdownServer;
import net.sf.odinms.server.TimerManager;
import net.sf.odinms.tools.CollectionUtil;
import net.sf.odinms.tools.MaplePacketCreator;

/**
 *
 * @author Matze
 */
public class ChannelWorldInterfaceImpl extends UnicastRemoteObject implements ChannelWorldInterface {
	private static final long serialVersionUID = 7815256899088644192L;

	private ChannelServer server;
	
	public ChannelWorldInterfaceImpl() throws RemoteException {
		super(0, new SslRMIClientSocketFactory(), new SslRMIServerSocketFactory());
	}
	
	public ChannelWorldInterfaceImpl(ChannelServer server) throws RemoteException {
		super(0, new SslRMIClientSocketFactory(), new SslRMIServerSocketFactory());
		this.server = server;
	}
	
	public void setChannelId(int id) throws RemoteException {
		server.setChannel(id);
	}

	public int getChannelId() throws RemoteException {
		return server.getChannel();
	}

	public String getIP() throws RemoteException {
		return server.getIP();
	}

	public void broadcastMessage(String sender, byte[] message) throws RemoteException {
		MaplePacket packet = new ByteArrayMaplePacket(message);
		server.broadcastPacket(packet);
	}

	public void whisper(String sender, String target, int channel, String message) throws RemoteException {
		if (isConnected(target)) {
			server.getPlayerStorage().getCharacterByName(target).getClient().getSession().write(
				MaplePacketCreator.getWhisper(sender, channel, message));
		}
	}

	public boolean isConnected(String charName) throws RemoteException {
		return server.getPlayerStorage().getCharacterByName(charName) != null;
	}

	public void shutdown(int time) throws RemoteException {
		server.broadcastPacket(
			MaplePacketCreator.serverNotice(0, "The world will be shut down in " + (time / 60000) +
			" minutes, please log off safely"));
		TimerManager.getInstance().schedule(new ShutdownServer(server.getChannel()), time);
	}

	public int getConnected() throws RemoteException {
		return server.getConnectedClients();
	}
	
	@Override
	public void loggedOff(String name, int characterId, int channel, int[] buddies) throws RemoteException {
		updateBuddies(characterId, channel, buddies, true);
	}

	@Override
	public void loggedOn(String name, int characterId, int channel, int buddies[]) throws RemoteException {
		updateBuddies(characterId, channel, buddies, false);
	}
	
	private void updateBuddies(int characterId, int channel, int[] buddies, boolean offline) {
		IPlayerStorage playerStorage = server.getPlayerStorage();
		for (int buddy : buddies) {
			MapleCharacter chr = playerStorage.getCharacterById(buddy);
			if (chr != null) {
				BuddylistEntry ble = chr.getBuddylist().get(characterId);
				if (ble != null && ble.isVisible()) {
					int mcChannel;
					if (offline) {
						ble.setChannel(-1);
						mcChannel = -1;
					} else {
						ble.setChannel(channel);
						mcChannel = channel - 1;
					}
					chr.getBuddylist().put(ble);
					chr.getClient().getSession().write(MaplePacketCreator.updateBuddyChannel(ble.getCharacterId(), mcChannel));
				}
			}
		}
	}


	@Override
	public void updateParty(MapleParty party, PartyOperation operation, MaplePartyCharacter target) throws RemoteException {
		for (MaplePartyCharacter partychar : party.getMembers()) {
			if (partychar.getChannel() == server.getChannel()) {
				MapleCharacter chr = server.getPlayerStorage().getCharacterByName(partychar.getName());
				if (chr != null) {
					if (operation == PartyOperation.DISBAND) {
						chr.setParty(null);
					} else {
						chr.setParty(party);
					}
					chr.getClient().getSession().write(MaplePacketCreator.updateParty(chr.getClient().getChannel(), party, operation, target));
				}
			}
		}
		switch (operation) {
			case LEAVE:
			case EXPEL:
				if (target.getChannel() == server.getChannel()) {
					MapleCharacter chr = server.getPlayerStorage().getCharacterByName(target.getName());
					if (chr != null) {
						chr.getClient().getSession().write(MaplePacketCreator.updateParty(chr.getClient().getChannel(), party, operation, target));
						chr.setParty(null);
					}
				}
		}
	}

	@Override
	public void partyChat(MapleParty party, String chattext, String namefrom) throws RemoteException {
		for (MaplePartyCharacter partychar : party.getMembers()) {
			if (partychar.getChannel() == server.getChannel() && !(partychar.getName().equals(namefrom))) {
				MapleCharacter chr = server.getPlayerStorage().getCharacterByName(partychar.getName());
				if (chr != null) {
					chr.getClient().getSession().write(MaplePacketCreator.multiChat(namefrom, chattext, 1));
				}
			}
		}
	}

	public boolean isAvailable() throws RemoteException {
		return true;
	}

	public int getLocation(String name) throws RemoteException {
		MapleCharacter chr = server.getPlayerStorage().getCharacterByName(name);
		if (chr != null)
			return server.getPlayerStorage().getCharacterByName(name).getMapId();
		return -1;
	}

	public List<CheaterData> getCheaters() throws RemoteException {
		List<CheaterData> cheaters = new ArrayList<CheaterData>();
		List<MapleCharacter> allplayers = new ArrayList<MapleCharacter>(server.getPlayerStorage().getAllCharacters());
		/*Collections.sort(allplayers, new Comparator<MapleCharacter>() {
			@Override
			public int compare(MapleCharacter o1, MapleCharacter o2) {
				int thisVal = o1.getCheatTracker().getPoints();
				int anotherVal = o2.getCheatTracker().getPoints();
				return (thisVal<anotherVal ? 1 : (thisVal==anotherVal ? 0 : -1));
			}
		});*/
		for (int x = allplayers.size() - 1; x >= 0; x--) {
			MapleCharacter cheater = allplayers.get(x);
			if (cheater.getCheatTracker().getPoints() > 0) {
				cheaters.add(new CheaterData(cheater.getCheatTracker().getPoints(), MapleCharacterUtil.makeMapleReadable(cheater.getName()) + " (" + cheater.getCheatTracker().getPoints() + ") " + cheater.getCheatTracker().getSummary()));
			}
		}
		Collections.sort(cheaters);
		return CollectionUtil.copyFirst(cheaters, 10);
	}

	@Override
	public BuddyAddResult requestBuddyAdd(String addName, int channelFrom, int cidFrom, String nameFrom) {
		MapleCharacter addChar = server.getPlayerStorage().getCharacterByName(addName);
		if (addChar != null) {
			BuddyList buddylist = addChar.getBuddylist();
			if (buddylist.isFull()) {
				return BuddyAddResult.BUDDYLIST_FULL;
			}
			if (!buddylist.contains(cidFrom)) {
				buddylist.addBuddyRequest(addChar.getClient(), cidFrom, nameFrom, channelFrom);
			} else {
				if (buddylist.containsVisible(cidFrom)) {
					return BuddyAddResult.ALREADY_ON_LIST;
				}
			}
		}
		return BuddyAddResult.OK;
	}

	@Override
	public boolean isConnected(int characterId) throws RemoteException {
		return server.getPlayerStorage().getCharacterById(characterId) != null;
	}

	@Override
	public void buddyChanged(int cid, int cidFrom, String name, int channel, BuddyOperation operation) {
		MapleCharacter addChar = server.getPlayerStorage().getCharacterById(cid);
		if (addChar != null) {
			BuddyList buddylist = addChar.getBuddylist();
			switch (operation) {
				case ADDED:
					if (buddylist.contains(cidFrom)) {
						buddylist.put(new BuddylistEntry(name, cidFrom, channel, true));
						addChar.getClient().getSession().write(MaplePacketCreator.updateBuddyChannel(cidFrom, channel - 1));
					}
					break;
				case DELETED:
					if (buddylist.contains(cidFrom)) {
						buddylist.put(new BuddylistEntry(name, cidFrom, -1, buddylist.get(cidFrom).isVisible()));
						addChar.getClient().getSession().write(MaplePacketCreator.updateBuddyChannel(cidFrom, -1));
					}
					break;
			}
		}
	}

	@Override
	public void buddyChat(int[] recipientCharacterIds, int cidFrom, String nameFrom, String chattext) throws RemoteException {
		IPlayerStorage playerStorage = server.getPlayerStorage();
		for (int characterId : recipientCharacterIds) {
			MapleCharacter chr = playerStorage.getCharacterById(characterId);
			if (chr != null) {
				if (chr.getBuddylist().containsVisible(cidFrom)) {
					chr.getClient().getSession().write(MaplePacketCreator.multiChat(nameFrom, chattext, 0));
				}
			}
		}
	}

	@Override
	public int[] multiBuddyFind(int charIdFrom, int[] characterIds) throws RemoteException {
		List<Integer> ret = new ArrayList<Integer>(characterIds.length);
		IPlayerStorage playerStorage = server.getPlayerStorage();
		for (int characterId : characterIds) {
			MapleCharacter chr = playerStorage.getCharacterById(characterId);
			if (chr != null) {
				if (chr.getBuddylist().containsVisible(charIdFrom)) {
					ret.add(characterId);
				}
			}
		}
		int [] retArr = new int[ret.size()];
		int pos = 0;
		for (Integer i : ret) {
			retArr[pos++] = i.intValue();
		}
		return retArr;
	}
}
