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
 * MapleShopFactory.java
 *
 * Created on 28. November 2007, 18:23
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.odinms.server;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author Matze
 */
public class MapleShopFactory {
	
	private Map<Integer,MapleShop> shops;
	
	private static MapleShopFactory instance = new MapleShopFactory();
	
	/** Creates a new instance of MapleShopFactory */
	private MapleShopFactory() {
		shops = new LinkedHashMap<Integer,MapleShop>();
	}
	
	public static MapleShopFactory getInstance() {
		return instance;
	}
	
	public void clear() {
		shops.clear();
	}
	
	public MapleShop getShop(int shopId) {
		MapleShop ret = shops.get(shopId);
		if (ret == null) {
			ret = MapleShop.createFromDB(shopId);
			shops.put(shopId, ret);
		}
		return ret;
	}
	
	public MapleShop getShopForNPC(int npcId) {
		MapleShop ret = null;
		for (MapleShop shop : shops.values()) {
			if (shop.getNpcId() == npcId) {
				ret = shop;
				break;
			}
		}
		if (ret == null) {
			ret = MapleShop.createFromDB(npcId);
			if (ret != null)
				shops.put(ret.getId(), ret);
		}
		return ret;
	}
	
}
