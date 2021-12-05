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
 * MapleQuestRequirementType.java
 *
 * Created on 10. Dezember 2007, 23:15
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.odinms.server.quest;

/**
 *
 * @author Matze
 */
public enum MapleQuestRequirementType {
	UNDEFINED(-1),
	JOB(0),
	ITEM(1),
	QUEST(2),
	MIN_LEVEL(3),
	MAX_LEVEL(4),
	END_DATE(5),
	MOB(6),
	NPC(7),
	FIELD_ENTER(8),
	;

	public MapleQuestRequirementType getITEM() {
		return ITEM;
	}
	
	final byte type;
	
	private MapleQuestRequirementType(int type) {
		this.type = (byte)type;
	}

	public byte getType() {
		return type;
	}
	
	public static MapleQuestRequirementType getByType(byte type) {
		for (MapleQuestRequirementType l : MapleQuestRequirementType.values()) {
			if (l.getType() == type) {
				return l;
			}
		}
		return null;
	}
	
	public static MapleQuestRequirementType getByWZName(String name) {
		if (name.equals("job")) return JOB;
		else if (name.equals("quest")) return QUEST;
		else if (name.equals("item")) return ITEM;
		else if (name.equals("lvmin")) return MIN_LEVEL;
		else if (name.equals("lvmax")) return MAX_LEVEL;
		else if (name.equals("end")) return END_DATE;
		else if (name.equals("mob")) return MOB;
		else if (name.equals("npc")) return NPC;
		else if (name.equals("fieldEnter")) return FIELD_ENTER;
		else return UNDEFINED;
	}
	
}
