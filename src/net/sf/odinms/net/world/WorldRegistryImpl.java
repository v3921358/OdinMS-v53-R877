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
 * To change this template, choose Tools | Templates and open the template in the editor.
 */

package net.sf.odinms.net.world;

import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import net.sf.odinms.database.DatabaseConnection;
import net.sf.odinms.net.channel.remote.ChannelWorldInterface;
import net.sf.odinms.net.login.remote.LoginWorldInterface;
import net.sf.odinms.net.world.remote.WorldChannelInterface;
import net.sf.odinms.net.world.remote.WorldLoginInterface;
import net.sf.odinms.net.world.remote.WorldRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Matze
 */
public class WorldRegistryImpl extends UnicastRemoteObject implements WorldRegistry {
	private static final long serialVersionUID = -5170574938159280746L;

	private static WorldRegistryImpl instance = null;
	private static Logger log = LoggerFactory.getLogger(WorldRegistryImpl.class);
	private Map<Integer, ChannelWorldInterface> channelServer = new LinkedHashMap<Integer, ChannelWorldInterface>();
	private List<LoginWorldInterface> loginServer = new LinkedList<LoginWorldInterface>();

	private Map<Integer, MapleParty> parties = new HashMap<Integer, MapleParty>();
	private AtomicInteger runningPartyId = new AtomicInteger();

	private WorldRegistryImpl() throws RemoteException {
		super(0, new SslRMIClientSocketFactory(), new SslRMIServerSocketFactory());
		DatabaseConnection.setProps(WorldServer.getInstance().getDbProp());
		
		Connection con = DatabaseConnection.getConnection();
		PreparedStatement ps;
		try {
			ps = con.prepareStatement("SELECT MAX(party)+1 FROM characters");
			ResultSet rs = ps.executeQuery();
			rs.next();
			runningPartyId.set(rs.getInt(1));
			rs.close();
			ps.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	public static WorldRegistryImpl getInstance() {
		if (instance == null) {
			try {
				instance = new WorldRegistryImpl();
			} catch (RemoteException e) {
				// can't do much anyway we are fucked ^^
				throw new RuntimeException(e);
			}
		}
		return instance;
	}

	private int getFreeChannelId() {
		for (int i = 0; i < 30; i++) {
			if (!channelServer.containsKey(i))
				return i;
		}
		return -1;
	}

	public WorldChannelInterface registerChannelServer(String authKey, ChannelWorldInterface cb) throws RemoteException {
		try {
			Connection con = DatabaseConnection.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT * FROM channels WHERE `key` = SHA1(?) AND world = ?");
			ps.setString(1, authKey);
			ps.setInt(2, WorldServer.getInstance().getWorldId());
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				int channelId = rs.getInt("number");
				if (channelId < 1) {
					channelId = getFreeChannelId();
					if (channelId == -1) {
						throw new RuntimeException("Maximum channels reached");
					}
				} else {
					if (channelServer.containsKey(channelId)) {
						ChannelWorldInterface oldch = channelServer.get(channelId);
						try {
							oldch.shutdown(0);
						} catch (ConnectException ce) {
							// silently ignore as we assume that the server is offline
						}
						// int switchChannel = getFreeChannelId();
						// if (switchChannel == -1) {
						// throw new RuntimeException("Maximum channels reached");
						// }
						// ChannelWorldInterface switchIf = channelServer.get(channelId);
						// deregisterChannelServer(switchChannel);
						// channelServer.put(switchChannel, switchIf);
						// switchIf.setChannelId(switchChannel);
						// for (LoginWorldInterface wli : loginServer) {
						// wli.channelOnline(switchChannel, switchIf.getIP());
						// }
					}
				}
				channelServer.put(channelId, cb);
				cb.setChannelId(channelId);
				WorldChannelInterface ret = new WorldChannelInterfaceImpl(cb, rs.getInt("channelid"));
				rs.close();
				ps.close();
				return ret;
			}
			rs.close();
			ps.close();
		} catch (SQLException ex) {
			log.error("Encountered database error while authenticating channelserver", ex);
		}
		throw new RuntimeException("Couldn't find a channel with the given key (" + authKey + ")");
	}

	public void deregisterChannelServer(int channel) throws RemoteException {
		channelServer.remove(channel);
		for (LoginWorldInterface wli : loginServer) {
			wli.channelOffline(channel);
		}
		log.info("Channel {} is offline.", channel);
	}

	public WorldLoginInterface registerLoginServer(String authKey, LoginWorldInterface cb) throws RemoteException {
		WorldLoginInterface ret = null;
		try {
			Connection con = DatabaseConnection.getConnection();
			PreparedStatement ps = con
				.prepareStatement("SELECT * FROM loginserver WHERE `key` = SHA1(?) AND world = ?");
			ps.setString(1, authKey);
			ps.setInt(2, WorldServer.getInstance().getWorldId());
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				loginServer.add(cb);
				for (ChannelWorldInterface cwi : channelServer.values()) {
					cb.channelOnline(cwi.getChannelId(), authKey);
				}
			}
			rs.close();
			ps.close();
			ret = new WorldLoginInterfaceImpl();
		} catch (Exception e) {
			log.error("Encountered database error while authenticating loginserver", e);
		}
		return ret;
	}

	public void deregisterLoginServer(LoginWorldInterface cb) throws RemoteException {
		loginServer.remove(cb);
	}

	public List<LoginWorldInterface> getLoginServer() {
		return new LinkedList<LoginWorldInterface>(loginServer);
	}

	public ChannelWorldInterface getChannel(int channel) {
		return channelServer.get(channel);
	}

	public Set<Integer> getChannelServer() {
		return new HashSet<Integer>(channelServer.keySet());
	}
	
	public Collection<ChannelWorldInterface> getAllChannelServers() {
		return channelServer.values();
	}

	public MapleParty createParty(MaplePartyCharacter chrfor) {
		int partyid = runningPartyId.getAndIncrement();
		MapleParty party = new MapleParty(partyid, chrfor);
		parties.put(party.getId(), party);
		return party;
	}

	public MapleParty getParty(int partyid) {
		return parties.get(partyid);
	}
	
	public MapleParty disbandParty(int partyid) {
		return parties.remove(partyid);
	}

	public String getStatus() throws RemoteException {
		StringBuilder ret = new StringBuilder();
		List<Entry<Integer,ChannelWorldInterface>> channelServers = new ArrayList<Entry<Integer,ChannelWorldInterface>>(channelServer.entrySet());
		Collections.sort(channelServers, new Comparator<Entry<Integer,ChannelWorldInterface>>() {
			@Override
			public int compare(Entry<Integer, ChannelWorldInterface> o1, Entry<Integer, ChannelWorldInterface> o2) {
				return o1.getKey().compareTo(o2.getKey());
			}
		});
		int totalUsers = 0;
		for (Entry<Integer,ChannelWorldInterface> cs : channelServers) {
			ret.append("Channel ");
			ret.append(cs.getKey());
			try {
				cs.getValue().isAvailable();
				ret.append(": online, ");
				int channelUsers = cs.getValue().getConnected();
				totalUsers += channelUsers;
				ret.append(channelUsers);
				ret.append(" users\n");
			} catch (RemoteException e) {
				ret.append(": offline\n");
			}
		}
		ret.append("Total users online: ");
		ret.append(totalUsers);
		ret.append("\n");
		Properties props = new Properties(WorldServer.getInstance().getWorldProp());
		int loginInterval = Integer.parseInt(props.getProperty("net.sf.odinms.login.interval"));
		for (LoginWorldInterface lwi : loginServer) {
			ret.append("Login: ");
			try {
				lwi.isAvailable();
				ret.append("online\n");
				ret.append("Users waiting in login queue: ");
				ret.append(lwi.getWaitingUsers());
				ret.append(" users\n");
				int loginMinutes = (int) Math.ceil((double) loginInterval * ((double) lwi.getWaitingUsers() / lwi.getPossibleLoginAverage())) / 60000;
				ret.append("Current average login waiting time: ");
				ret.append(loginMinutes);
				ret.append(" minutes\n");
			} catch (RemoteException e) {
				ret.append("offline\n");
			}
		}
		return ret.toString();
	}
}
