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

package net.sf.odinms.server.maps;

import java.awt.Point;

import net.sf.odinms.server.MaplePortal;

public class MapleGenericPortal implements MaplePortal {
	private String name;
	private String target;
	private Point position;
	private int targetmap;
	private int type;
	private int id;

	public MapleGenericPortal(int type) {
		this.type = type;
	}
	@Override
	public int getId() {
		return id;
	}

	@Override
	public void setId(int id) {
		this.id  = id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Point getPosition() {
		return position;
	}

	@Override
	public String getTarget() {
		return target;
	}

	@Override
	public int getTargetMapId() {
		return targetmap;
	}

	@Override
	public int getType() {
		return type;
	}

	@Override
	public void setName(String name) {
		this.name =name;
	}

	@Override
	public void setPosition(Point position) {
		this.position = position;
	}

	@Override
	public void setTarget(String target) {
		this.target = target;
	}

	@Override
	public void setTargetMapId(int targetmapid) {
		this.targetmap = targetmapid;
	}

}
