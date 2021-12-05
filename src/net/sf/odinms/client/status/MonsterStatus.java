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

package net.sf.odinms.client.status;

import net.sf.odinms.net.IntValueHolder;

public enum MonsterStatus implements IntValueHolder {
	WATK(0x1),
	WDEF(0x2),
	SPEED(0x40),
	STUN(0x80), //this is possibly only the bowman stun
	FREEZE(0x100),
	POISON(0x200),
	SEAL(0x400),
	DOOM(0x10000),
	SHADOW_WEB(0x20000),
	MAGIC_DEFENSE_UP(0x8000),
	;
	
	private final int i;

	private MonsterStatus(int i) {
		this.i = i;
	}

	@Override
	public int getValue() {
		return i;
	}
}
