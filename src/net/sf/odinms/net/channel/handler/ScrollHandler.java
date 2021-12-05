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

package net.sf.odinms.net.channel.handler;

import net.sf.odinms.client.IEquip;
import net.sf.odinms.client.IItem;
import net.sf.odinms.client.InventoryException;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventory;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.client.IEquip.ScrollResult;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Matze
 * @author Frz
 */
public class ScrollHandler extends AbstractMaplePacketHandler {
	private static Logger log = LoggerFactory.getLogger(ScrollHandler.class);

	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		// TODO: Check if scroll target is valid
		slea.readInt(); // whatever...
		byte slot = (byte) slea.readShort();
		byte dst = (byte) slea.readShort();
		MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
		IEquip toScroll;
		if (dst < 0) {
			toScroll = (IEquip) c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem(dst);
		} else {
			// legendary spirit
			toScroll = (IEquip) c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem(dst);
		}
		byte oldLevel = toScroll.getLevel();
		if (((IEquip) toScroll).getUpgradeSlots() < 1) {
			c.getSession().write(MaplePacketCreator.getInventoryFull());
			return;
		}
		MapleInventory useInventory = c.getPlayer().getInventory(MapleInventoryType.USE);
		IItem scroll = useInventory.getItem(slot);
		if (!ii.canScroll(scroll.getItemId(), toScroll.getItemId())) {
			log.info("[h4x] Player {} is trying to scroll {} with {} which should not work", new Object[] {
				c.getPlayer().getName(), toScroll.getItemId(), scroll.getItemId() });
			return;
		}
		if (scroll.getQuantity() <= 0) {
			throw new InventoryException("<= 0 quantity when scrolling");
		}
		IEquip scrolled = (IEquip) ii.scrollEquipWithId(toScroll, scroll.getItemId());
		ScrollResult scrollSuccess = IEquip.ScrollResult.FAIL; // fail
		if (scrolled == null) {
			scrollSuccess = IEquip.ScrollResult.CURSE;
		} else if (scrolled.getLevel() > oldLevel) {
			scrollSuccess = IEquip.ScrollResult.SUCCESS;
		}
		useInventory.removeItem(scroll.getPosition(), (short) 1, false);
		if (scrollSuccess == IEquip.ScrollResult.CURSE) { // TODO: make it work without client crash
			c.getSession().write(MaplePacketCreator.scrolledItem(scroll, toScroll, true));
			c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).removeItem(dst);
		} else {
			c.getSession().write(MaplePacketCreator.scrolledItem(scroll, scrolled, false));
		}
		c.getPlayer().getMap().broadcastMessage(
			MaplePacketCreator.getScrollEffect(c.getPlayer().getId(), scrollSuccess));

		// equipped item was scrolled and changed
		if (dst < 0 && (scrollSuccess == IEquip.ScrollResult.SUCCESS || scrollSuccess == IEquip.ScrollResult.CURSE)) {
			c.getPlayer().equipChanged();
		}
	}
}
