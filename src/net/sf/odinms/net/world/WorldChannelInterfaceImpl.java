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

package net.sf.odinms.net.world;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import net.sf.odinms.database.DatabaseConnection;
import net.sf.odinms.net.channel.remote.ChannelWorldInterface;
import net.sf.odinms.net.login.remote.LoginWorldInterface;
import net.sf.odinms.net.world.remote.CheaterData;
import net.sf.odinms.net.world.remote.WorldChannelInterface;
import net.sf.odinms.net.world.remote.WorldLocation;
import net.sf.odinms.tools.CollectionUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Matze
 */
public class WorldChannelInterfaceImpl extends UnicastRemoteObject implements WorldChannelInterface {
	private static final long serialVersionUID = -5568606556235590482L;
	private static Logger log = LoggerFactory.getLogger(WorldChannelInterfaceImpl.class);
	private ChannelWorldInterface cb;
	private int dbId;
	private boolean ready = false;
	
	public WorldChannelInterfaceImpl() throws RemoteException {
		super(0, new SslRMIClientSocketFactory(), new SslRMIServerSocketFactory());
	}
	
	public WorldChannelInterfaceImpl(ChannelWorldInterface cb, int dbId) throws RemoteException {
		super(0, new SslRMIClientSocketFactory(), new SslRMIServerSocketFactory());
		this.cb = cb;
		this.dbId = dbId;
	}

	public Properties getDatabaseProperties() throws RemoteException {
		return WorldServer.getInstance().getDbProp();
	}

	public Properties getGameProperties() throws RemoteException {
		Properties ret = new Properties(WorldServer.getInstance().getWorldProp());
		try {
			Connection con = DatabaseConnection.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT * FROM channelconfig WHERE channelid = ?");
			ps.setInt(1, dbId);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				ret.setProperty(rs.getString("name"), rs.getString("value"));
			}
			rs.close();
			ps.close();
		} catch (SQLException ex) {
			log.error("Could not retrieve channel configuration", ex);
		}
		return ret;
	}

	public void serverReady() throws RemoteException {
		ready = true;
		for (LoginWorldInterface wli : WorldRegistryImpl.getInstance().getLoginServer()) {
			try {
				wli.channelOnline(cb.getChannelId(), cb.getIP());
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterLoginServer(wli);
			}
		}
		log.info("Channel {} is online.", cb.getChannelId());
	}
	
	public boolean isReady() {
		return ready;
	}

	public String getIP(int channel) throws RemoteException {
		ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(channel);
		if (cwi == null) {
			return "0.0.0.0:0";
		} else {
			try {
				return cwi.getIP();
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterChannelServer(channel);
				return "0.0.0.0:0";
			}
		}
	}

	public void whisper(String sender, String target, int channel, String message) throws RemoteException {
		for (int i : WorldRegistryImpl.getInstance().getChannelServer()) {
			ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(i);
			try {
				cwi.whisper(sender, target, channel, message);
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterChannelServer(i);
			}
		}
	}

	public boolean isConnected(String charName) throws RemoteException {
		for (int i : WorldRegistryImpl.getInstance().getChannelServer()) {
			ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(i);
			try {
				if (cwi.isConnected(charName)) {
					return true;
				}
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterChannelServer(i);
			}
		}
		return false;
	}

	public void broadcastMessage(String sender, byte[] message) throws RemoteException {
		for (int i : WorldRegistryImpl.getInstance().getChannelServer()) {
			ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(i);
			try {
				cwi.broadcastMessage(sender, message);
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterChannelServer(i);
			}
		}
	}

	public int find(String charName) throws RemoteException {
		for (int i : WorldRegistryImpl.getInstance().getChannelServer()) {
			ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(i);
			try {
				if (cwi.isConnected(charName)) {
					return cwi.getChannelId();
				}
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterChannelServer(i);
			}
		}
		return -1;
	}

	// can we generify this
	@Override
	public int find(int characterId) throws RemoteException {
		for (int i : WorldRegistryImpl.getInstance().getChannelServer()) {
			ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(i);
			try {
				if (cwi.isConnected(characterId)) {
					return cwi.getChannelId();
				}
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterChannelServer(i);
			}
		}
		return -1;
	}

	public void shutdown(int time) throws RemoteException {
		for (LoginWorldInterface lwi : WorldRegistryImpl.getInstance().getLoginServer()) {
			try {
				lwi.shutdown();
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterLoginServer(lwi);
			}
		}
		for (int i : WorldRegistryImpl.getInstance().getChannelServer()) {
			ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(i);
			try {
				cwi.shutdown(time);
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterChannelServer(i);
			}
		}
	}

	public Map<Integer, Integer> getConnected() throws RemoteException {
		Map<Integer, Integer> ret = new HashMap<Integer, Integer>();
		int total = 0;
		for (int i : WorldRegistryImpl.getInstance().getChannelServer()) {
			ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(i);
			try {
				int curConnected = cwi.getConnected();
				ret.put(i, curConnected);
				total += curConnected;
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterChannelServer(i);
			}
		}
		ret.put(0, total);
		return ret;
	}
	
	public void loggedOn(String name, int characterId, int channel, int[] buddies) throws RemoteException {
		for (int i : WorldRegistryImpl.getInstance().getChannelServer()) {
			ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(i);
			try {
				cwi.loggedOn(name, characterId, channel, buddies);
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterChannelServer(i);
			}
		}
	}
	
	@Override
	public void loggedOff(String name, int characterId, int channel, int[] buddies) throws RemoteException {
		for (int i : WorldRegistryImpl.getInstance().getChannelServer()) {
			ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(i);
			try {
				cwi.loggedOff(name, characterId, channel, buddies);
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterChannelServer(i);
			}
		}
	}
	
	//TODO only notify channels where partymembers are?
	public void updateParty (int partyid, PartyOperation operation, MaplePartyCharacter target) throws RemoteException {
		MapleParty party = WorldRegistryImpl.getInstance().getParty(partyid);
		if (party == null) {
			throw new IllegalArgumentException("no party with the specified partyid exists");
		}
		switch (operation) {
			case JOIN:
				party.addMember(target);
				break;
			case EXPEL:
			case LEAVE:
				party.removeMember(target);
				break;
			case DISBAND:
				WorldRegistryImpl.getInstance().disbandParty(partyid);
				break;
			case SILENT_UPDATE:
			case LOG_ONOFF:
				party.updateMember (target);
				break;
			default:
				throw new RuntimeException("Unhandeled updateParty operation " + operation.name());
		}
		for (int i : WorldRegistryImpl.getInstance().getChannelServer()) {
			ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(i);
			try {
				cwi.updateParty(party, operation, target);
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterChannelServer(i);
			}
		}
	}
	
	public MapleParty createParty (MaplePartyCharacter chrfor) throws RemoteException {
		return WorldRegistryImpl.getInstance().createParty(chrfor);
	}
	
	public MapleParty getParty(int partyid) throws RemoteException {
		return WorldRegistryImpl.getInstance().getParty(partyid);
	}

	@Override
	public void partyChat(int partyid, String chattext, String namefrom) throws RemoteException {
		MapleParty party = WorldRegistryImpl.getInstance().getParty(partyid);
		if (party == null) {
			throw new IllegalArgumentException("no party with the specified partyid exists");
		}
		for (int i : WorldRegistryImpl.getInstance().getChannelServer()) {
			ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(i);
			try {
				cwi.partyChat(party, chattext, namefrom);
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterChannelServer(i);
			}
		}
	}

	public boolean isAvailable() throws RemoteException {
		return true;
	}

	public WorldLocation getLocation(String charName) throws RemoteException {
		for (int i : WorldRegistryImpl.getInstance().getChannelServer()) {
			ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(i);
			try {
				if (cwi.isConnected(charName)) {
					return new WorldLocation(cwi.getLocation(charName), cwi.getChannelId());
				}
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterChannelServer(i);
			}
		}
		return null;
	}

	public List<CheaterData> getCheaters() throws RemoteException {
		List<CheaterData> allCheaters = new ArrayList<CheaterData>();
		for (int i : WorldRegistryImpl.getInstance().getChannelServer()) {
			ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(i);
			try {
				allCheaters.addAll(cwi.getCheaters());
			} catch (RemoteException e) {
				WorldRegistryImpl.getInstance().deregisterChannelServer(i);
			}
		}
		Collections.sort(allCheaters);
		return CollectionUtil.copyFirst(allCheaters, 10);
	}

	@Override
	public ChannelWorldInterface getChannelInterface(int channel) {
		ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(channel);
		return cwi;
	}

	@Override
	public void buddyChat(int[] recipientCharacterIds, int cidFrom, String nameFrom, String chattext) throws RemoteException {
		for (ChannelWorldInterface cwi : WorldRegistryImpl.getInstance().getAllChannelServers()) {
			cwi.buddyChat(recipientCharacterIds, cidFrom, nameFrom, chattext);
		}
	}

	@Override
	public CharacterIdChannelPair[] multiBuddyFind(int charIdFrom, int[] characterIds) throws RemoteException {
		List<CharacterIdChannelPair> foundsChars = new ArrayList<CharacterIdChannelPair>(characterIds.length);
		for (int i : WorldRegistryImpl.getInstance().getChannelServer()) {
			ChannelWorldInterface cwi = WorldRegistryImpl.getInstance().getChannel(i);
			for (int charid : cwi.multiBuddyFind(charIdFrom, characterIds)) {
				foundsChars.add(new CharacterIdChannelPair(charid, i));
			}
		}
		return foundsChars.toArray(new CharacterIdChannelPair[foundsChars.size()]);
	}
}
