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

import java.util.List;

import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.anticheat.CheatingOffense;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.server.movement.AbsoluteLifeMovement;
import net.sf.odinms.server.movement.LifeMovementFragment;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MovePlayerHandler extends AbstractMovementPacketHandler {
	private static Logger log = LoggerFactory.getLogger(MovePlayerHandler.class);

	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		slea.readByte();
		slea.readInt();
		// log.trace("Movement command received: unk1 {} unk2 {}", new Object[] { unk1, unk2 });
		List<LifeMovementFragment> res = parseMovement(slea);
		// TODO more validation of input data
		if (res != null) {
			if (slea.available() != 18) {
				log.warn("slea.available != 18 (movement parsing error)");
				return;
			}
			MapleCharacter player = c.getPlayer();
			if (!player.isHidden()) {
				MaplePacket packet = MaplePacketCreator.movePlayer(player.getId(), res);
				c.getPlayer().getMap().broadcastMessage(player, packet, false);
			}
			// c.getSession().write(MaplePacketCreator.movePlayer(30000, res));
			if (CheatingOffense.FAST_MOVE.isEnabled() || CheatingOffense.HIGH_JUMP.isEnabled()) {
				checkMovementSpeed (c.getPlayer(), res);
			}
			updatePosition (res, c.getPlayer(), 0);
			c.getPlayer().getMap().movePlayer(c.getPlayer(), c.getPlayer().getPosition());
		}
	}
	
	private static void checkMovementSpeed(MapleCharacter chr, List<LifeMovementFragment> moves) {
		// boolean wasALM = true;
		// Point oldPosition = new Point (c.getPlayer().getPosition());
		double playerSpeedMod = chr.getSpeedMod() + 0.005;
		// double playerJumpMod = c.getPlayer().getJumpMod() + 0.005;
		boolean encounteredUnk0 = false;
		for (LifeMovementFragment lmf : moves) {
			if (lmf.getClass() == AbsoluteLifeMovement.class) {
				final AbsoluteLifeMovement alm = (AbsoluteLifeMovement) lmf;
				double speedMod = Math.abs(alm.getPixelsPerSecond().x) / 125.0;
				// int distancePerSec = Math.abs(alm.getPixelsPerSecond().x);
				// double jumpMod = Math.abs(alm.getPixelsPerSecond().y) / 525.0;
				// double normalSpeed = distancePerSec / playerSpeedMod;
				// System.out.println(speedMod + "(" + playerSpeedMod + ") " + alm.getUnk());
				if (speedMod > playerSpeedMod) {
					if (alm.getUnk() == 0) { // to prevent FJ fucking us
						encounteredUnk0 = true;
					}
					if (!encounteredUnk0) {
						if (speedMod > playerSpeedMod) {
							chr.getCheatTracker().registerOffense(CheatingOffense.FAST_MOVE);
						}
					}
				}
				// if (wasALM && (oldPosition.y == newPosition.y)) {
				// int distance = Math.abs(oldPosition.x - newPosition.x);
				// if (alm.getDuration() > 60) { // short durations are strange and show too fast movement
				// double distancePerSec = (distance / (double) ((LifeMovement) move).getDuration()) * 1000.0;
				// double speedMod = distancePerSec / 125.0;
				// double normalSpeed = distancePerSec / playerSpeedMod;
				// System.out.println(speedMod + " " + normalSpeed + " " + distancePerSec + " " + distance + " "
				// + alm.getWobble());
				// }
				// }
				// oldPosition = newPosition;
				// wasALM = true;
				// } else {
				// wasALM = false;
			}
		}
	}
}
