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

package net.sf.odinms.server;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.tools.MaplePacketCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Matze
 */
public class AutobanManager implements Runnable {

	private static class ExpirationEntry implements Comparable<ExpirationEntry> {
		public long time;
		public int acc;
		public int points;
		
		public ExpirationEntry(long time, int acc, int points) {
			this.time = time;
			this.acc = acc;
			this.points = points;
		}

		public int compareTo(AutobanManager.ExpirationEntry o) {
			return (int) (time - o.time);
		}
	}
	
	private Map<Integer,Integer> points = new HashMap<Integer,Integer>();
	private Map<Integer,List<String>> reasons = new HashMap<Integer,List<String>>();
	private Set<ExpirationEntry> expirations = new TreeSet<ExpirationEntry>();
	private static final int AUTOBAN_POINTS = 1000;
	private static AutobanManager instance = null;
	private static Logger log = LoggerFactory.getLogger(AutobanManager.class);
	
	public static AutobanManager getInstance() {
		if (instance == null) instance = new AutobanManager();
		return instance;
	}
	
	public void autoban (MapleClient c, String reason) {
		if (c.getPlayer().isGM()) {
			log.warn("[h4x] Trying to a/b gm - something fishy going on: {}", reason);
		}
		addPoints(c, AUTOBAN_POINTS, 0, reason);
	}
	

	public synchronized void addPoints(MapleClient c, int points, long expiration, String reason) {
		// if (c.getPlayer().isGM()) return;
		int acc = c.getPlayer().getAccountID();
		List<String> reasonList;
		if (this.points.containsKey(acc)) {
			if (this.points.get(acc) >= AUTOBAN_POINTS) return;
			this.points.put(acc, this.points.get(acc) + points);
			reasonList = this.reasons.get(acc);
			reasonList.add(reason);
		} else {
			this.points.put(acc, points);
			reasonList = new LinkedList<String>();
			reasonList.add(reason);
			this.reasons.put(acc, reasonList);
		}
		if (this.points.get(acc) >= AUTOBAN_POINTS) {
			String name = c.getPlayer().getName();
			StringBuilder banReason = new StringBuilder("Autoban for char ");
			banReason.append(name);
			banReason.append(" (IP ");
			banReason.append(c.getSession().getRemoteAddress().toString());
			banReason.append("): ");
			for (String s : reasons.get(acc)) {
				banReason.append(s);
				banReason.append(", ");
			}
			
			if (c.getPlayer().isGM()) {
				log.warn("[h4x] Trying to a/b gm - something fishy going on: {}", banReason.toString());
			} else {
				c.getPlayer().ban(banReason.toString());
				try {
					c.getChannelServer().getWorldInterface().broadcastMessage(null, 
						MaplePacketCreator.serverNotice(0, "[Autoban] " + name + " banned by the system (Last reason: " + reason + ")").getBytes());
				} catch (RemoteException e) {
					c.getChannelServer().reconnectWorld();
				}
				log.warn("[h4x] Autobanned player {} (accountid {})", name, acc);
			}
			return;
		}
		if (expiration > 0) {
			expirations.add(new ExpirationEntry(System.currentTimeMillis() + expiration, acc, points));
		}
	}
	
	public void run() {
		long now = System.currentTimeMillis();
		for (ExpirationEntry e : expirations) {
			if (e.time <= now)
				this.points.put(e.acc, this.points.get(e.acc) - e.points);
			else
				return;
		}
	}
	
	
}
