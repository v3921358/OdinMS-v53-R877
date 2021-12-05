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

package net.sf.odinms.server.life;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.sf.odinms.provider.MapleData;
import net.sf.odinms.provider.MapleDataProvider;
import net.sf.odinms.provider.MapleDataProviderFactory;
import net.sf.odinms.provider.MapleDataTool;
import net.sf.odinms.server.maps.MapleMapFactory;
import net.sf.odinms.tools.StringUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapleLifeFactory {
	private static Logger log = LoggerFactory.getLogger(MapleMapFactory.class);
	private static MapleDataProvider data = MapleDataProviderFactory.getDataProvider(new File(System.getProperty("net.sf.odinms.wzpath") + "/Mob.wz"));
	private static MapleDataProvider stringDataWZ = MapleDataProviderFactory.getDataProvider(new File(System.getProperty("net.sf.odinms.wzpath") + "/String.wz"));
	private static MapleData mobStringData = stringDataWZ.getData("Mob.img");
	private static MapleData npcStringData = stringDataWZ.getData("Npc.img");
	private static Map<Integer, MapleMonsterStats> monsterStats = new HashMap<Integer, MapleMonsterStats>();

	public static AbstractLoadedMapleLife getLife(int id, String type) {
		if (type.equalsIgnoreCase("n")) {
			return getNPC(id);
		} else if (type.equalsIgnoreCase("m")) {
			return getMonster(id);
		} else {
			log.warn("Unknown Life type: {}", type);
			return null;
		}
	}
	
	public static MapleMonster getMonster (int mid) {
		MapleMonsterStats stats = monsterStats.get(Integer.valueOf(mid));
		if (stats == null) {
			MapleData monsterData = data.getData(StringUtil.getLeftPaddedStr(Integer.toString(mid) + ".img", '0', 11));
			MapleData monsterInfoData = monsterData.getChildByPath("info");
			stats = new MapleMonsterStats();
			stats.setHp(MapleDataTool.getIntConvert("maxHP", monsterInfoData));
			stats.setMp(MapleDataTool.getIntConvert("maxMP", monsterInfoData));
			stats.setExp(MapleDataTool.getIntConvert("exp", monsterInfoData));
			stats.setLevel(MapleDataTool.getIntConvert("level", monsterInfoData));
			stats.setBoss (MapleDataTool.getIntConvert("boss", monsterInfoData, 0) > 0);
			stats.setUndead (MapleDataTool.getIntConvert("undead", monsterInfoData, 0) > 0);
			stats.setName(MapleDataTool.getString(mid + "/name", mobStringData, "MISSINGNO"));
			
			for (MapleData idata : monsterData) {
				if (!idata.getName().equals("info")) {
					int delay = 0;
					for (MapleData pic : idata.getChildren()) {
						delay += MapleDataTool.getIntConvert("delay", pic, 0);
					}
					stats.setAnimationTime(idata.getName(), delay);
				}
			}
			
			MapleData reviveInfo = monsterInfoData.getChildByPath("revive");
			if (reviveInfo != null) {
				List<Integer> revives = new LinkedList<Integer>();
				for (MapleData data : reviveInfo) {			
					revives.add(MapleDataTool.getInt(data));		
				}
				stats.setRevives(revives);
			}
			
			decodeElementalString(stats, MapleDataTool.getString("elemAttr", monsterInfoData, ""));
			
			monsterStats.put(Integer.valueOf(mid), stats);
		}
		MapleMonster ret = new MapleMonster(mid, stats);
		return ret;
	}
	
	public static void decodeElementalString (MapleMonsterStats stats, String elemAttr) {
		for (int i = 0; i < elemAttr.length(); i += 2) {
			Element e = Element.getFromChar(elemAttr.charAt(i));
			ElementalEffectiveness ee = ElementalEffectiveness.getByNumber(Integer.valueOf(String.valueOf(elemAttr.charAt(i+1))));
			stats.setEffectiveness(e, ee);
		}
	}
	
	public static MapleNPC getNPC(int nid) {
		return new MapleNPC(nid, new MapleNPCStats(MapleDataTool.getString(nid + "/name", npcStringData, "MISSINGNO")));
	}
}
