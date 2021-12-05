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

package net.sf.odinms.net.login;

import java.rmi.RemoteException;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.server.TimerManager;
import net.sf.odinms.tools.MaplePacketCreator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Matze
 */
public class LoginWorker implements Runnable {

	private static LoginWorker instance = new LoginWorker();
	private Deque<MapleClient> waiting;
	private Set<String> waitingNames;
	private List<Integer> possibleLoginHistory = new LinkedList<Integer>();

	public static Logger log = LoggerFactory.getLogger(LoginWorker.class);

	private LoginWorker() {
		waiting = new LinkedList<MapleClient>();
		waitingNames = new HashSet<String>();
	}

	public static LoginWorker getInstance() {
		return instance;
	}

	public void registerClient(MapleClient c) {
		synchronized (waiting) {
			if (!waiting.contains(c) && !waitingNames.contains(c.getAccountName().toLowerCase())) {
				waiting.add(c);
				waitingNames.add(c.getAccountName().toLowerCase());
			}
		}
	}
	
	public void registerGMClient(MapleClient c) {
		synchronized (waiting) {
			if (!waiting.contains(c) && !waitingNames.contains(c.getAccountName().toLowerCase())) {
				waiting.addFirst(c);
				waitingNames.add(c.getAccountName().toLowerCase());
			}
		}
	}


	public void deregisterClient(MapleClient c) {
		synchronized (waiting) {
			waiting.remove(c);
			if (c.getAccountName() != null) {
				waitingNames.remove(c.getAccountName().toLowerCase());
			}
		}
	}

	public void run() {
		int possibleLogins = LoginServer.getInstance().getPossibleLogins();

		try {
			LoginServer.getInstance().getWorldInterface().isAvailable();
		} catch (RemoteException e) {
			LoginServer.getInstance().reconnectWorld();
		}
		
		if (possibleLoginHistory.size() >= (5 * 60 * 1000) / LoginServer.getInstance().getLoginInterval()) {
			possibleLoginHistory.remove(0);
		}
		possibleLoginHistory.add(possibleLogins);
		
		log.info("Possible logins: " + possibleLogins + " (Waiting: " + waiting.size() + ")");
		for (int i = 0; i < possibleLogins; i++) {
			final MapleClient client;
			synchronized (waiting) {
				if (waiting.isEmpty())
					return;
				client = waiting.removeFirst();
			}
			waitingNames.remove(client.getAccountName().toLowerCase());
			if (client.finishLogin(true) == 0) {
				client.getSession().write(MaplePacketCreator.getAuthSuccessRequestPin(client.getAccountName()));
				client.setIdleTask(TimerManager.getInstance().schedule(new Runnable() {

					public void run() {
						client.getSession().close();
					}
					
				}, 10 * 60 * 10000));
			} else {
				client.getSession().write(MaplePacketCreator.getLoginFailed(7));
			}
		}
	}

	public double getPossibleLoginAverage() {
		int sum = 0;
		for (Integer i : possibleLoginHistory) {
			sum += i;
		}
		return (double) sum / (double) possibleLoginHistory.size();
	}
	
	public int getWaitingUsers() {
		return waiting.size();
	}
}
