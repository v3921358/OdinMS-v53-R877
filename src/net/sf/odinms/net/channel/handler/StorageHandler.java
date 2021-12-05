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

package net.sf.odinms.net.channel.handler;

import net.sf.odinms.client.IItem;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.server.AutobanManager;
import net.sf.odinms.server.MapleInventoryManipulator;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

/**
 *
 * @author Matze
 */
public class StorageHandler extends AbstractMaplePacketHandler {

	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
		byte mode = slea.readByte();
		if (mode == 4) { // take out
			byte type = slea.readByte();
			byte slot = slea.readByte();
			slot = c.getPlayer().getStorage().getSlot(MapleInventoryType.getByType(type), slot);
			IItem item = c.getPlayer().getStorage().takeOut(slot);
			if (item != null) {
				StringBuilder logInfo = new StringBuilder("Taken out from storage by ");
				logInfo.append(c.getPlayer().getName());
				MapleInventoryManipulator.addFromDrop(c, item, logInfo.toString());
				c.getPlayer().getStorage().sendTakenOut(c, ii.getInventoryType(item.getItemId()));
			} else {
				AutobanManager.getInstance().addPoints(c, 1000, 0, "Trying to take out item from storage which does not exist.");
			}
		} else if (mode == 5) { // store
			byte slot = (byte) slea.readShort();
			int itemId = slea.readInt();
			short quantity = slea.readShort();
			if (quantity < 1) {
				AutobanManager.getInstance().addPoints(c, 1000, 0, "Trying to store " + quantity + " of " + itemId);
				return;
			}
			if (c.getPlayer().getStorage().isFull()) {
				c.getSession().write(MaplePacketCreator.getStorageFull());
				return;
			}
			MapleInventoryType type = ii.getInventoryType(itemId);
			IItem item = c.getPlayer().getInventory(type).getItem(slot).copy();
			if (item.getItemId() == itemId && (item.getQuantity() >= quantity || ii.isThrowingStar(itemId))) {
				if (ii.isThrowingStar(itemId))
					quantity = item.getQuantity();
				StringBuilder logMsg = new StringBuilder("Stored by ");
				logMsg.append(c.getPlayer().getName());
				item.log(logMsg.toString(),false);
				c.getPlayer().gainMeso(-100, true, true, true);
				MapleInventoryManipulator.removeFromSlot(c, type, slot, quantity, false);
				item.setQuantity(quantity);
				c.getPlayer().getStorage().store(item);
				c.getPlayer().getStorage().sendStored(c, ii.getInventoryType(itemId));
			} else {
				AutobanManager.getInstance().addPoints(c, 1000, 0, "Trying to store non-matching itemid (" + itemId + "/" + item.getItemId() + ") or quantity not in posession (" + quantity + "/" + item.getQuantity() + ")");
			}
		} else if (mode == 6) { // meso
			int meso = slea.readInt();
			if (meso > 0 && c.getPlayer().getStorage().getMeso() >= meso 
				|| c.getPlayer().getMeso() >= Math.abs(meso)) {
				c.getPlayer().gainMeso(meso, true, true, true);
				c.getPlayer().getStorage().setMeso(c.getPlayer().getStorage().getMeso() - meso);
			} else {
				AutobanManager.getInstance().addPoints(c, 1000, 0, "Trying to store or take out unavailable amount of mesos (" + meso + "/" + c.getPlayer().getStorage().getMeso() + "/" + c.getPlayer().getMeso() + ")");
			}
			c.getPlayer().getStorage().sendMeso(c);
		} else if (mode == 7) { // close
			c.getPlayer().getStorage().close();
		}
	}

}
