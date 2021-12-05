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

package net.sf.odinms.scripting;

import java.util.HashMap;
import java.util.Map;
import javax.script.Invocable;
import net.sf.odinms.client.MapleClient;

/**
 *
 * @author Matze
 */
public class NPCScriptManager extends AbstractScriptManager {

	private Map<MapleClient,NPCConversationManager> cms = new HashMap<MapleClient,NPCConversationManager>();
	private Map<MapleClient,NPCScript> scripts = new HashMap<MapleClient,NPCScript>();
	private static NPCScriptManager instance = new NPCScriptManager();
	
	public synchronized static NPCScriptManager getInstance() {
		return instance;
	}
	
	public void start(MapleClient c, int npc) {
		try {
			NPCConversationManager cm = new NPCConversationManager(c, npc);
			cms.put(c, cm);
			Invocable iv = getInvocable("npc/" + npc + ".js", c);
			if (iv == null) {
				cm.dispose();
				return;
			}
			engine.put("cm", cm);
			NPCScript ns = iv.getInterface(NPCScript.class);
			scripts.put(c, ns);
			ns.start();
		} catch (Exception e) {
			log.error("Error executing NPC script.", e);
		}
	}
	
	public void action(MapleClient c, byte mode, byte type, byte selection) {
		NPCScript ns = scripts.get(c);
		if (ns != null) {
			try {
				ns.action(mode, type, selection);
			} catch (Exception e) {
				log.error("Error executing NPC script.", e);
			}
		}
	}
	
	public void dispose(NPCConversationManager cm) {
		cms.remove(cm.getC());
		scripts.remove(cm.getC());
		resetContext("npc/" + cm.getNpc() + ".js", cm.getC());
	}
	
	public void dispose(MapleClient c) {
		NPCConversationManager npccm = cms.get(c);
		if (npccm != null) {
			dispose (npccm);
		}
	}
}
