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

package net.sf.odinms.client;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import javax.script.ScriptEngine;

import net.sf.odinms.client.messages.MessageCallback;
import net.sf.odinms.database.DatabaseConnection;
import net.sf.odinms.database.DatabaseException;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.net.world.MaplePartyCharacter;
import net.sf.odinms.net.world.PartyOperation;
import net.sf.odinms.net.world.remote.WorldChannelInterface;
import net.sf.odinms.scripting.NPCScriptManager;
import net.sf.odinms.server.MapleTrade;
import net.sf.odinms.server.TimerManager;
import net.sf.odinms.tools.MapleAESOFB;
import net.sf.odinms.tools.MaplePacketCreator;

import org.apache.mina.common.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapleClient {
	public static final int LOGIN_NOTLOGGEDIN = 0;
	public static final int LOGIN_SERVER_TRANSITION = 1;
	public static final int LOGIN_LOGGEDIN = 2;

	public static final String CLIENT_KEY = "CLIENT";
	private static final Logger log = LoggerFactory.getLogger(MapleClient.class);

	private MapleAESOFB send;
	private MapleAESOFB receive;
	private IoSession session;

	private MapleCharacter player;
	private int channel = 1;
	private int accId = 1;
	private boolean loggedIn = false;
	private boolean serverTransition = false;
	private Calendar birthday = null;
	private String accountName;
	private int world;
	private long lastPong;
	private boolean gm;
	
	private Set<String> macs = new HashSet<String>();
	private Map<String,ScriptEngine> engines = new HashMap<String,ScriptEngine>();
	private ScheduledFuture<?> idleTask = null;

	public MapleClient(MapleAESOFB send, MapleAESOFB receive, IoSession session) {
		this.send = send;
		this.receive = receive;
		this.session = session;
	}

	public MapleAESOFB getReceiveCrypto() {
		return receive;
	}

	public MapleAESOFB getSendCrypto() {
		return send;
	}

	public IoSession getSession() {
		return session;
	}

	public MapleCharacter getPlayer() {
		return player;
	}

	public void setPlayer(MapleCharacter player) {
		this.player = player;
	}

	public void sendCharList(int server) {
		this.session.write(MaplePacketCreator.getCharList(this, server));
	}

	public List<MapleCharacter> loadCharacters(int serverId) { // TODO make this less costly zZz
		List<MapleCharacter> chars = new LinkedList<MapleCharacter>();
		for (CharNameAndId cni : loadCharactersInternal(serverId)) {
			try {
				chars.add(MapleCharacter.loadCharFromDB(cni.id, this, false));
			} catch (SQLException e) {
				log.error("Loading characters failed", e);
			}
		}
		return chars;
	}
	
	public List<String> loadCharacterNames(int serverId) {
		List<String> chars = new LinkedList<String>();
		for (CharNameAndId cni : loadCharactersInternal(serverId)) {
			chars.add(cni.name);
		}
		return chars;
	}
	
	private List<CharNameAndId> loadCharactersInternal (int serverId) {
		Connection con = DatabaseConnection.getConnection();
		PreparedStatement ps;
		List<CharNameAndId> chars = new LinkedList<CharNameAndId>();
		try {
			ps = con.prepareStatement("SELECT id, name FROM characters WHERE accountid = ? AND world = ?");
			ps.setInt(1, this.accId);
			ps.setInt(2, serverId);

			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				chars.add(new CharNameAndId(rs.getString("name"), rs.getInt("id")));
			}
			rs.close();
			ps.close();
		} catch (SQLException e) {
			log.error("THROW", e);
		}
		return chars;
	}

	public boolean isLoggedIn() {
		return loggedIn;
	}

	public boolean hasBannedIP() {
		boolean ret = false;
		try {
			Connection con = DatabaseConnection.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM ipbans WHERE ? LIKE CONCAT(ip, '%')");
			ps.setString(1, session.getRemoteAddress().toString());
			ResultSet rs = ps.executeQuery();
			rs.next();
			if (rs.getInt(1) > 0) {
				ret = true;
			}
			rs.close();
			ps.close();
		} catch (SQLException ex) {
			log.error("Error checking ip bans", ex);
		}
		return ret;
	}
	
	public boolean hasBannedMac() {
		if (macs.isEmpty())
			return false;
		boolean ret = false;
		int i = 0;
		try {
			Connection con = DatabaseConnection.getConnection();
			StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM macbans WHERE mac IN (");
			for (i = 0; i < macs.size(); i++) {
				sql.append("?");
				if (i != macs.size() - 1)
					sql.append(", ");
			}
			sql.append(")");
			PreparedStatement ps = con.prepareStatement(sql.toString());
			i = 0;
			for (String mac : macs) {
				i++;
				ps.setString(i, mac);
			}
			ResultSet rs = ps.executeQuery();
			rs.next();
			if (rs.getInt(1) > 0) {
				ret = true;
			}
			rs.close();
			ps.close();
		} catch (SQLException ex) {
			log.error("Error checking mac bans", ex);
		}
		return ret;
	}
	
	private void loadMacsIfNescessary() throws SQLException {
		if (macs.isEmpty()) {
			Connection con = DatabaseConnection.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT macs FROM accounts WHERE id = ?");
			ps.setInt(1, accId);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				String[] macData = rs.getString("macs").split(", ");
				for (String mac : macData) {
					if (!mac.equals("")) {
						macs.add(mac);
					}
				}
			} else {
				throw new RuntimeException("No valid account associated with this client.");
			}
			rs.close();
			ps.close();
		}
	}
	
	public void banMacs() {
		Connection con = DatabaseConnection.getConnection();
		try {
			loadMacsIfNescessary();
			List<String> filtered = new LinkedList<String>();
			PreparedStatement ps = con.prepareStatement("SELECT filter FROM macfilters");
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				filtered.add(rs.getString("filter"));
			}
			rs.close();
			ps.close();
			ps = con.prepareStatement("INSERT INTO macbans (mac) VALUES (?)");
			for (String mac : macs) {
				boolean matched = false;
				for (String filter : filtered) {
					if (mac.matches(filter)) {
						matched = true;
						break;
					}
				}
				if (!matched) {
					ps.setString(1, mac);
					try {
						ps.executeUpdate();
					} catch (SQLException e) {
						// can fail because of UNIQUE key, we dont care
					}
				}
			}
			ps.close();
		} catch (SQLException e) {
			log.error("Error banning MACs", e);
		}
	}

	/**
	 * Returns 0 on success, a state to be used for {@link MaplePacketCreator#getLoginFailed(int)} otherwise.
	 * @param success
	 * @return
	 */
	public int finishLogin(boolean success) {
		if (success) {
			synchronized (MapleClient.class) {
				if (getLoginState() > MapleClient.LOGIN_NOTLOGGEDIN) { // already loggedin
					loggedIn = false;
					return 7;
				}
				updateLoginState(MapleClient.LOGIN_LOGGEDIN);
			}
			return 0;
		} else {
			return 10;
		}
	}

	public int login(String login, String pwd, boolean ipMacBanned) {
		int loginok = 4;
		Connection con = DatabaseConnection.getConnection();
		try {
			PreparedStatement ps = con
				.prepareStatement("SELECT id,banned,gm,macs FROM accounts WHERE name = ? AND password = SHA1(?)");
			ps.setString(1, login);
			ps.setString(2, pwd);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				int banned = rs.getInt("banned");
				accId = rs.getInt("id");
				int igm = rs.getInt("gm");
				gm = igm > 0;
				if ((banned == 0 && !ipMacBanned) || banned == -1) {
					PreparedStatement ips = con.prepareStatement("INSERT INTO iplog (accountid, ip) VALUES (?, ?)");
					ips.setInt(1, accId);
					ips.setString(2, session.getRemoteAddress().toString());
					ips.executeUpdate();
					ips.close();
				}

				// do NOT track ALL mac addresses ever used
				/*String[] macData = rs.getString("macs").split(", ");
				for (String mac : macData) {
					if (!mac.equals(""))
						macs.add(mac);
				}*/
				ps.close();
				// if (gm > 0) {
				// session.write(MaplePacketCreator.getAuthSuccessRequestPin(getAccountName()));
				// return finishLogin(true);
				// }
				if (banned == 1) {
					loginok = 3;
				} else {
					// this is to simplify unbanning
					// all known ip and mac bans associated with the current client
					// will be deleted
					if (banned == -1)
						unban();
					if (getLoginState() > MapleClient.LOGIN_NOTLOGGEDIN) { // already loggedin
						loggedIn = false;
						loginok = 7;
					} else {
						loginok = 0;
					}
				}
			}
			rs.close();
			ps.close();
		} catch (SQLException e) {
			log.error("ERROR", e);
		}
		return loginok;
	}
	
	private void unban() {
		int i;
		try {
			Connection con = DatabaseConnection.getConnection();
			loadMacsIfNescessary();
			StringBuilder sql = new StringBuilder("DELETE FROM macbans WHERE mac IN (");
			for (i = 0; i < macs.size(); i++) {
				sql.append("?");
				if (i != macs.size() - 1)
					sql.append(", ");
			}
			sql.append(")");
			PreparedStatement ps = con.prepareStatement(sql.toString());
			i = 0;
			for (String mac : macs) {
				i++;
				ps.setString(i, mac);
			}
			ps.executeUpdate();
			ps.close();
			ps = con.prepareStatement("DELETE FROM ipbans WHERE ip LIKE CONCAT(?, '%')");
			ps.setString(1, getSession().getRemoteAddress().toString().split(":")[0]);
			ps.executeUpdate();
			ps.close();
			ps = con.prepareStatement("UPDATE accounts SET banned = 0 WHERE id = ?");
			ps.setInt(1, accId);
			ps.executeUpdate();
			ps.close();
		} catch (SQLException e) {
			log.error("Error while unbanning", e);
		}
	}
	
	public void updateMacs(String macData) {
		for (String mac : macData.split(", ")) {
			macs.add(mac);
		}
		StringBuilder newMacData = new StringBuilder();
		Iterator<String> iter = macs.iterator();
		while (iter.hasNext()) {
			String cur = iter.next();
			newMacData.append(cur);
			if (iter.hasNext())
				newMacData.append(", ");
		}
		Connection con = DatabaseConnection.getConnection();
		try {
			PreparedStatement ps = con.prepareStatement("UPDATE accounts SET macs = ? WHERE id = ?");
			ps.setString(1, newMacData.toString());
			ps.setInt(2, accId);
			ps.executeUpdate();
			ps.close();
		} catch (SQLException e) {
			log.error("Error saving MACs", e);
		}
	}

	public void setAccID(int id) {
		this.accId = id;
	}

	public int getAccID() {
		return this.accId;
	}

	public void updateLoginState(int newstate) { // TODO hide?
		Connection con = DatabaseConnection.getConnection();
		try {
			PreparedStatement ps = con
				.prepareStatement("UPDATE accounts SET loggedin = ?, lastlogin = CURRENT_TIMESTAMP() WHERE id = ?");
			ps.setInt(1, newstate);
			ps.setInt(2, getAccID());
			ps.executeUpdate();
			ps.close();
		} catch (SQLException e) {
			log.error("ERROR", e);
		}
		if (newstate == MapleClient.LOGIN_NOTLOGGEDIN) {
			loggedIn = false;
			serverTransition = false;
		} else {
			serverTransition = (newstate == MapleClient.LOGIN_SERVER_TRANSITION);
			loggedIn = !serverTransition;
		}
	}

	public int getLoginState() { // TODO hide?
		Connection con = DatabaseConnection.getConnection();
		try {
			PreparedStatement ps;
			ps = con.prepareStatement("SELECT loggedin, lastlogin, birthday FROM accounts WHERE id = ?");
			ps.setInt(1, getAccID());
			ResultSet rs = ps.executeQuery();
			if (!rs.next()) {
				ps.close();
				throw new DatabaseException("Everything sucks");
			}
			birthday = Calendar.getInstance();
			long blubb = rs.getLong("birthday");
			if (blubb > 0) {
				birthday.setTime(rs.getDate("birthday"));
			}
			int state = rs.getInt("loggedin");
			if (state == MapleClient.LOGIN_SERVER_TRANSITION) {
				Timestamp ts = rs.getTimestamp("lastlogin");
				long t = ts.getTime();
				long now = System.currentTimeMillis();
				if (t + 30000 < now) { // connecting to chanserver timeout
					state = MapleClient.LOGIN_NOTLOGGEDIN;
					updateLoginState(MapleClient.LOGIN_NOTLOGGEDIN);
				}
			}
			rs.close();
			ps.close();
			if (state == MapleClient.LOGIN_LOGGEDIN) {
				loggedIn = true;
			} else {
				loggedIn = false;
			}
			return state;
		} catch (SQLException e) {
			loggedIn = false;
			log.error("ERROR", e);
			throw new DatabaseException("Everything sucks");
		}
	}

	public boolean checkBirthDate(Calendar date) {
		if (date.get(Calendar.YEAR) == birthday.get(Calendar.YEAR) &&
			date.get(Calendar.MONTH) == birthday.get(Calendar.MONTH) &&
			date.get(Calendar.DAY_OF_MONTH) == birthday.get(Calendar.DAY_OF_MONTH)) {
			return true;
		}
		return false;
	}

	public void disconnect() {
		//pingTask.cancel(true);
		MapleCharacter chr = this.getPlayer();
		if (chr != null && isLoggedIn()) {
			//log.warn("[dc] Player {} disconnected from map {}", new Object[] {chr.getName(), chr.getMapId()});
			if (chr.getTrade() != null) {
				MapleTrade.cancelTrade(chr);
			}
			chr.cancelAllBuffs();
			if (chr.getEventInstance() != null) {
				chr.getEventInstance().playerDisconnected(chr);
			}
			chr.cancelMagicDoor();
			chr.saveToDB(true);
			chr.getCheatTracker().dispose();
			chr.getMap().removePlayer(chr);
			try {
				WorldChannelInterface wci = getChannelServer().getWorldInterface();
				if (chr.getParty() != null) {
					MaplePartyCharacter chrp = new MaplePartyCharacter(chr);
					chrp.setOnline(false);
					wci.updateParty(chr.getParty().getId(), PartyOperation.LOG_ONOFF, chrp);
				}
				wci.loggedOff(chr.getName(), chr.getId(), channel, chr.getBuddylist().getBuddyIds());
			} catch (RemoteException e) {
				getChannelServer().reconnectWorld();
			} catch (Exception e) {
				log.error(getLogMessage(this, "ERROR"), e);
			} finally {
				if (getChannelServer() != null) {
					getChannelServer().removePlayer(chr);
				} else {
					log.error(getLogMessage(this, "No channelserver associated to char {}", chr.getName()));
				}
			}
		}
		if (!this.serverTransition && isLoggedIn()) {
			this.updateLoginState(MapleClient.LOGIN_NOTLOGGEDIN);
		}
		NPCScriptManager npcsm = NPCScriptManager.getInstance();
		if (npcsm != null) {
			npcsm.dispose(this);
		}
	}
	
	public void dropDebugMessage (MessageCallback mc) {
		StringBuilder builder = new StringBuilder();
		builder.append("Connected: ");
		builder.append(getSession().isConnected());
		builder.append(" Closing: ");
		builder.append(getSession().isClosing());
		builder.append(" ClientKeySet: ");
		builder.append(getSession().getAttribute(MapleClient.CLIENT_KEY) != null);
		builder.append(" loggedin: ");
		builder.append(isLoggedIn());
		builder.append(" has char: ");
		builder.append(getPlayer() != null);
		mc.dropMessage(builder.toString());
	}

	/**
	 * Undefined when not logged to a channel
	 * 
	 * @return the channel the client is connected to
	 */
	public int getChannel() {
		return channel;
	}
	
	/**
	 * Convinence method to get the ChannelServer object this client is logged on to.
	 * @return
	 */
	public ChannelServer getChannelServer() {
		return ChannelServer.getInstance(getChannel());
	}

	public boolean deleteCharacter(int cid) {
		Connection con = DatabaseConnection.getConnection();
		try {
			PreparedStatement ps = con.prepareStatement("SELECT id FROM characters WHERE id = ? AND accountid = ?");
			ps.setInt(1, cid);
			ps.setInt(2, accId);
			ResultSet rs = ps.executeQuery();
			if (!rs.next()) {
				rs.close();
				ps.close();
				return false;
			}
			rs.close();
			ps.close();
			// ok this is actually our character, delete it
			ps = con.prepareStatement("DELETE FROM characters WHERE id = ?");
			ps.setInt(1, cid);
			ps.executeUpdate();
			ps.close();
			return true;
		} catch (SQLException e) {
			log.error("ERROR", e);
		}
		return false;
	}

	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	public void setChannel(int channel) {
		this.channel = channel;
	}

	public int getWorld() {
		return world;
	}

	public void setWorld(int world) {
		this.world = world;
	}

	public void pongReceived() {
		lastPong = System.currentTimeMillis();
	}
	
	public void sendPing() {
		final long then = System.currentTimeMillis();
		getSession().write(MaplePacketCreator.getPing());
		TimerManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				try {
					if (lastPong - then < 0) {
						if (getSession().isConnected()) {
							log.info(getLogMessage(MapleClient.this, "Autodc"));
							getSession().close();
						}
					}
				} catch (NullPointerException e) {
					// client already gone
				}
			}
		}, 15000); // note: idletime gets added to this too
	}
	
	public static String getLogMessage (MapleClient cfor, String message) {
		return getLogMessage(cfor, message, new Object[0]);
	}
	
	public static String getLogMessage (MapleCharacter cfor, String message) {
		return getLogMessage(cfor == null ? null : cfor.getClient(), message);
	}

	public static String getLogMessage (MapleCharacter cfor, String message, Object... parms) {
		return getLogMessage(cfor == null ? null : cfor.getClient(), message, parms);
	}
	
	public static String getLogMessage (MapleClient cfor, String message, Object... parms) {
		StringBuilder builder = new StringBuilder();
		if (cfor != null) {
			if (cfor.getPlayer() != null) {
				builder.append("<");
				builder.append(MapleCharacterUtil.makeMapleReadable(cfor.getPlayer().getName()));
				builder.append(" (cid: ");
				builder.append(cfor.getPlayer().getId());
				builder.append(")> ");
			}
			if (cfor.getAccountName() != null) {
				builder.append("(Account: ");
				builder.append(MapleCharacterUtil.makeMapleReadable(cfor.getAccountName()));
				builder.append(") ");
			}
		}
		builder.append(message);
		for (Object parm : parms) {
			int start = builder.indexOf("{}");
			builder.replace(start, start + 2, parm.toString());
		}
		return builder.toString();
	}

	public Set<String> getMacs() {
		return Collections.unmodifiableSet(macs);
	}

	public boolean isGm() {
		return gm;
	}
	
	public void setScriptEngine(String name, ScriptEngine e) {
		engines.put(name, e);
	}
	
	public ScriptEngine getScriptEngine(String name) {
		return engines.get(name);
	}
	
	public void removeScriptEngine(String name) {
		engines.remove(name);
	}

	public ScheduledFuture<?> getIdleTask() {
		return idleTask;
	}

	public void setIdleTask(ScheduledFuture<?> idleTask) {
		this.idleTask = idleTask;
	}
	
	private static class CharNameAndId {
		public String name;
		public int id;
		
		public CharNameAndId(String name, int id) {
			super();
			this.name = name;
			this.id = id;
		}
	}
}
