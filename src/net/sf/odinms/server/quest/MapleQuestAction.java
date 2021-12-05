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
 * MapleQuestAction.java
 *
 * Created on 11. Dezember 2007, 23:04
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.odinms.server.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.sf.odinms.client.InventoryException;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.client.MapleJob;
import net.sf.odinms.client.MapleQuestStatus;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.provider.MapleData;
import net.sf.odinms.provider.MapleDataTool;
import net.sf.odinms.server.MapleInventoryManipulator;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.tools.MaplePacketCreator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Matze
 */
public class MapleQuestAction {
	private static Logger log = LoggerFactory.getLogger(MapleQuestAction.class);
	
	private MapleQuestActionType type;
	private MapleData data;
	
	/** Creates a new instance of MapleQuestAction */
	public MapleQuestAction(MapleQuestActionType type, MapleData data) {
		this.type = type;
		this.data = data;
	}
	
	public boolean check (MapleCharacter c) {
		switch (type) {
			case MESO:
				int mesars = MapleDataTool.getInt(data) * ChannelServer.getInstance(c.getClient().getChannel()).getExpRate();
				if (c.getMeso() + mesars < 0) {
					return false;
				}
				break;
		}
		return true;
	}
	
	private boolean canGetItem(MapleData item, MapleCharacter c) {
		if (item.getChildByPath("gender") != null) {
			int gender = MapleDataTool.getInt(item.getChildByPath("gender"));
			if (gender != 2 && gender != c.getGender())
				return false;
		}
		if (item.getChildByPath("job") != null) {
			int job = MapleDataTool.getInt(item.getChildByPath("job"));
			if (job < 100) {
				// koreans suck.
				if (MapleJob.getBy5ByteEncoding(job).getId() / 100 != c.getJob().getId() / 100)
					return false;
			} else {
				if (job != c.getJob().getId())
					return false;
			}
		}
		return true;
	}
	
	public void run(MapleCharacter c, Integer extSelection) {
		switch (type) {
			case EXP:
				c.gainExp(MapleDataTool.getInt(data) * ChannelServer.getInstance(c.getClient().getChannel()).getExpRate(), true, true);
				break;
			case ITEM:
				MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
				// first check for randomness in item selection
				Map<Integer,Integer> props = new HashMap<Integer,Integer>();
				for (MapleData iEntry : data.getChildren()) {
					if (iEntry.getChildByPath("prop") != null && MapleDataTool.getInt(iEntry.getChildByPath("prop")) != -1 && canGetItem(iEntry, c)) {
						for (int i = 0; i < MapleDataTool.getInt(iEntry.getChildByPath("prop")); i++) {
							props.put(props.size(),
								MapleDataTool.getInt(iEntry.getChildByPath("id")));
						}
					}
				}
				int selection = 0;
				int extNum = 0;
				if (props.size() > 0) {
					Random r = new Random();
					selection = props.get(r.nextInt(props.size()));
				}
				for (MapleData iEntry : data.getChildren()) {
					if (!canGetItem(iEntry, c))
						continue;
					if (iEntry.getChildByPath("prop") != null) {
						if (MapleDataTool.getInt(iEntry.getChildByPath("prop")) == -1) {
							if (extSelection != extNum++)
								continue;
						}
						else if (MapleDataTool.getInt(iEntry.getChildByPath("id")) != selection)
							continue;
					}
					if (MapleDataTool.getInt(iEntry.getChildByPath("count")) < 0) { // remove items
						int itemId = MapleDataTool.getInt(iEntry.getChildByPath("id"));
						MapleInventoryType iType = ii.getInventoryType(itemId);
						short quantity = (short)
						(MapleDataTool.getInt(iEntry.getChildByPath("count")) * -1);
						try {
							MapleInventoryManipulator.removeById(c.getClient(), iType, itemId, quantity, true, false);
						} catch (InventoryException ie) {
							// it's better to catch this here so we'll atleast try to remove the other items
							log.warn("[h4x] Completing a quest without meeting the requirements", ie);
						}
						c.getClient().getSession().write(
							MaplePacketCreator.getShowItemGain(itemId, (short) MapleDataTool.getInt(iEntry.getChildByPath("count")), true));
					} else { // add items
						int itemId = MapleDataTool.getInt(iEntry.getChildByPath("id"));
						@SuppressWarnings("unused")
						MapleInventoryType iType = ii.getInventoryType(itemId);
						short quantity = (short) MapleDataTool.getInt(iEntry.getChildByPath("count"));
						StringBuilder logInfo = new StringBuilder(c.getName());
						logInfo.append(" received ");
						logInfo.append(quantity);
						logInfo.append(" as reward from a quest");
						MapleInventoryManipulator.addById(c.getClient(), itemId, quantity, logInfo.toString());
						c.getClient().getSession().write(MaplePacketCreator.getShowItemGain(itemId,
							quantity, true));
					}
				}
				break;
			case MESO:
				c.gainMeso(MapleDataTool.getInt(data) * ChannelServer.getInstance(c.getClient().getChannel()).getExpRate(), true, false, true);
				break;
			case QUEST:
				for (MapleData qEntry : data) {
					int quest = MapleDataTool.getInt(qEntry.getChildByPath("id"));
					int stat = MapleDataTool.getInt(qEntry.getChildByPath("state"));
					c.updateQuest(new MapleQuestStatus(MapleQuest.getInstance(quest),
						MapleQuestStatus.Status.getById(stat)));
				}
				break;
			case SKILL:
				//TODO needs gain/lost message?
				for (MapleData sEntry : data) {
					int skillid = MapleDataTool.getInt(sEntry.getChildByPath("id"));
					int skillLevel = MapleDataTool.getInt(sEntry.getChildByPath("skillLevel"));
					int masterLevel = MapleDataTool.getInt(sEntry.getChildByPath("masterLevel"));
					
					boolean shouldLearn = false;
					MapleData applicableJobs = sEntry.getChildByPath("job");
					for (MapleData applicableJob : applicableJobs) {
						MapleJob job = MapleJob.getById(MapleDataTool.getInt(applicableJob));
						if (c.getJob() == job) {
							shouldLearn = true;
							break;
						}
					}
					if (shouldLearn) {
						c.changeSkillLevel(SkillFactory.getSkill(skillid), skillLevel, masterLevel);
					}
				}
				break;
			default:
		}
	}
	
	public MapleQuestActionType getType() {
		return type;
	}
	
	@Override
	public String toString() {
		return type + ": " + data;
	}
}
