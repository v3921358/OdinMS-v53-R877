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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.server.maps.AbstractMapleMapObject;
import net.sf.odinms.server.maps.MapleMapObjectType;
import net.sf.odinms.tools.MaplePacketCreator;

/**
 *
 * @author Matze
 */
public class MaplePlayerShop extends AbstractMapleMapObject {
	private MapleCharacter owner;
	private MapleCharacter[] visitors = new MapleCharacter[3];
	private List<MaplePlayerShopItem> items = new ArrayList<MaplePlayerShopItem>();
	private String description;
	
	public MaplePlayerShop(MapleCharacter owner, String description) {
		this.owner = owner;
		this.description = description;
	}
	
	public boolean hasFreeSlot() {
		return visitors[0] == null || visitors[1] == null || visitors[2] == null;
	}
	
	public boolean isOwner(MapleCharacter c) {
		return owner == c;
	}
	
	public void addVisitor(MapleCharacter visitor) {
		for (int i = 0; i < 3; i++) {
			if (visitors[i] == null) {
				visitors[i] = visitor;
				break;
			}
		}
		for (int i = 0; i < 3; i++) {
			if (visitors[i] != null && visitors[i] != visitor) {
				visitors[i].getClient().getSession().write(
					MaplePacketCreator.getPlayerShopNewVisitor(visitor));
			}
		}
	}
	
	public void removeVisitor(MapleCharacter visitor) {
		for (int i = 0; i < 3; i++) {
			if (visitors[i] == visitor) {
				visitors[i] = null;
				break;
			}
		}
	}
	
	public boolean isVisitor(MapleCharacter visitor) {
		return visitors[0] == visitor || visitors[1] == visitor || visitors[2] == visitor;
	}
	
	public void addItem(MaplePlayerShopItem item) {
		items.add(item);
	}
	
	public void removeItem(int item) {
		items.remove(item);
	}
	
	/**
	 * no warnings for now o.op
	 * @param c
	 * @param item
	 * @param quantity
	 */
	public void buy(MapleClient c, int item, short quantity) {
		/*if (isVisitor(c.getPlayer())) {
			MaplePlayerShopItem pItem = items.get(item);
			synchronized (c.getPlayer()) {
				if (quantity <= pItem.getBundles() && c.getPlayer().getMeso() >= pItem.getPrice()) {
					IItem newItem = pItem.getItem().copy();
					newItem.setQuantity((short) (newItem.getQuantity() * quantity));
					c.getPlayer().gainMeso(-pItem.getPrice(), true);
					MapleInventoryManipulator.addFromDrop(c, newItem);
					pItem.setBundles((short) (pItem.getBundles() - quantity));
				}
			}
		}*/
	}
	
	private void broadcastToVisitors(MaplePacket packet) {
		for (int i = 0; i < 3; i++) {
			if (visitors[i] != null)
				visitors[i].getClient().getSession().write(packet);
		}
	}
	
	private void broadcast(MaplePacket packet) {
		if (owner.getClient() != null && owner.getClient().getSession() != null)
			owner.getClient().getSession().write(packet);
		broadcastToVisitors(packet);
	}
	
	public void chat(MapleClient c, String chat) {
		broadcast(MaplePacketCreator.getPlayerShopChat(c.getPlayer(), chat, isOwner(c.getPlayer())));
	}
	
	public void sendShop(MapleClient c) {
		c.getSession().write(MaplePacketCreator.getPlayerShop(c, this, isOwner(c.getPlayer())));
	}

	public MapleCharacter getOwner() {
		return owner;
	}

	public MapleCharacter[] getVisitors() {
		return visitors;
	}

	public List<MaplePlayerShopItem> getItems() {
		return Collections.unmodifiableList(items);
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public void sendDestroyData(MapleClient client) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void sendSpawnData(MapleClient client) {
		throw new UnsupportedOperationException();
	}

	@Override
	public MapleMapObjectType getType() {
		return MapleMapObjectType.SHOP;
	}
}
