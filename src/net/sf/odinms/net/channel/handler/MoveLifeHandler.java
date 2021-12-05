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

package net.sf.odinms.net.channel.handler;

import java.awt.Point;
import java.util.List;

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.server.maps.MapleMapObject;
import net.sf.odinms.server.movement.LifeMovementFragment;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoveLifeHandler extends AbstractMovementPacketHandler {
	private static Logger log = LoggerFactory.getLogger(MoveLifeHandler.class);

	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		int objectid = slea.readInt();
		short moveid = slea.readShort();
		// or is the moveid an int?

		// when someone trys to move an item/npc he gets thrown out with a class cast exception mwaha
		
		MapleMapObject mmo = c.getPlayer().getMap().getMapObject(objectid);
		if (!(mmo instanceof MapleMonster) || mmo == null) {
			/*if (mmo != null) {
				log.warn("[dc] Player {} is trying to move something which is not a monster. It is a {}.", new Object[] {
					c.getPlayer().getName(), c.getPlayer().getMap().getMapObject(objectid).getClass().getCanonicalName() });
			}*/
			return;
		}
		MapleMonster monster = (MapleMonster) mmo;

		List<LifeMovementFragment> res = null;
		int skillByte = slea.readByte();
		int skill = slea.readInt();
		slea.readShort();
		slea.readInt(); // whatever
		int start_x = slea.readShort(); // hmm.. startpos?
		int start_y = slea.readShort(); // hmm...
		Point startPos = new Point(start_x, start_y);

		res = parseMovement(slea);
		
		if (monster.getController() != c.getPlayer()) {
			if (monster.isAttackedBy(c.getPlayer())) { // aggro and controller change
				monster.switchController(c.getPlayer(), true);
			} else {
				// String sCon;
				// if (monster.getController() == null) {
				// sCon = "undefined";
				// } else {
				// sCon = monster.getController().getName();
				// }
				// log.warn("[dc] Player {} is trying to move a monster he does not control on map {}. The controller is
				// {}.", new Object[] { c.getPlayer().getName(), c.getPlayer().getMapId(), sCon});
				return;
			}
		} else {
			if (skill == 255 && monster.isControllerKnowsAboutAggro() && !monster.isMobile()) {
				monster.setControllerHasAggro(false);
				monster.setControllerKnowsAboutAggro(false);
			}
		}
		boolean aggro = monster.isControllerHasAggro();
		c.getSession().write(MaplePacketCreator.moveMonsterResponse(objectid, moveid, monster.getMp(), aggro));
		if (aggro) {
			monster.setControllerKnowsAboutAggro(true);
		}
		
		// if (!monster.isAlive())
		// return;

		if (res != null) {
			if (slea.available() != 9) {
				log.warn("slea.available != 9 (movement parsing error)");
				return;
			}
			MaplePacket packet = MaplePacketCreator.moveMonster(skillByte, skill, objectid, startPos, res);
			c.getPlayer().getMap().broadcastMessage(c.getPlayer(), packet, monster.getPosition());
			// MaplePacket packet = MaplePacketCreator.moveMonster(200, res);
			// c.getPlayer().getMap().broadcastMessage(null, packet);
			updatePosition (res, monster, -1);
			c.getPlayer().getMap().moveMonster(monster, monster.getPosition());
			c.getPlayer().getCheatTracker().checkMoveMonster(monster.getPosition());
		}
	}
}
