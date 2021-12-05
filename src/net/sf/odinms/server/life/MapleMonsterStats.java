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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bean ^__^ that holds monster stats - setters shouldn't be called after loading is complete.
 * 
 * @author Frz
 */
public class MapleMonsterStats {
	private int exp;
	private int hp, mp;
	private int level;
	private boolean boss;
	private boolean undead;
	private String name;
	private Map<String, Integer> animationTimes = new HashMap<String, Integer>();
	private Map<Element, ElementalEffectiveness> resistance = new HashMap<Element, ElementalEffectiveness>();
	private List<Integer> revives = Collections.emptyList();

	public int getExp() {
		return exp;
	}

	public void setExp(int exp) {
		this.exp = exp;
	}

	public int getHp() {
		return hp;
	}

	public void setHp(int hp) {
		this.hp = hp;
	}

	public int getMp() {
		return mp;
	}

	public void setMp(int mp) {
		this.mp = mp;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public void setBoss(boolean boss) {
		this.boss = boss;
	}

	public boolean isBoss() {
		return boss;
	}

	public void setAnimationTime(String name, int delay) {
		animationTimes.put(name, delay);
	}

	public int getAnimationTime(String name) {
		Integer ret = animationTimes.get(name);
		if (ret == null) {
			return 500;
		}
		return ret.intValue();
	}
	
	public boolean isMobile() {
		return animationTimes.containsKey("move") || animationTimes.containsKey("fly");
	}

	public List<Integer> getRevives() {
		return revives;
	}

	public void setRevives(List<Integer> revives) {
		this.revives = revives;
	}

	public void setUndead(boolean undead) {
		this.undead = undead;
	}

	public boolean getUndead() {
		return undead;
	}
	
	public void setEffectiveness (Element e, ElementalEffectiveness ee) {
		resistance.put(e, ee);
	}
	
	public ElementalEffectiveness getEffectiveness (Element e) {
		ElementalEffectiveness elementalEffectiveness = resistance.get(e);
		if (elementalEffectiveness == null) {
			return ElementalEffectiveness.NORMAL;
		} else {
			return elementalEffectiveness;
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}