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

package net.sf.odinms.net;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public enum RecvPacketOpcode implements WritableIntValueHolder {
	// GENERIC
	PONG,
	// LOGIN
	AFTER_LOGIN,
	SERVERLIST_REQUEST,
	SERVERLIST_REREQUEST,
	CHARLIST_REQUEST,
	CHAR_SELECT,
	CHECK_CHAR_NAME,
	CREATE_CHAR,
	DELETE_CHAR,
	LOGIN_PASSWORD,
	RELOG,
	SERVERSTATUS_REQUEST,
	// CHANNEL
	CHANGE_CHANNEL,
	CHAR_INFO_REQUEST,
	CLOSE_RANGE_ATTACK,
	RANGED_ATTACK,
	MAGIC_ATTACK,
	FACE_EXPRESSION,
	HEAL_OVER_TIME,
	ITEM_MOVE,
	ITEM_PICKUP,
	CHANGE_MAP,
	MESO_DROP,
	MOVE_LIFE,
	MOVE_PLAYER,
	NPC_SHOP,
	NPC_TALK,
	NPC_TALK_MORE,
	PLAYER_LOGGEDIN,
	QUEST_ACTION,
	TAKE_DAMAGE,
	USE_CASH_ITEM,
	USE_ITEM,
	USE_RETURN_SCROLL,
	USE_UPGRADE_SCROLL,
	GENERAL_CHAT,
	WHISPER,
	SPECIAL_MOVE,
	CANCEL_BUFF,
	PLAYER_INTERACTION,
	CANCEL_ITEM_EFFECT,
	DISTRIBUTE_AP,
	DISTRIBUTE_SP,
	CHANGE_KEYMAP,
	CHANGE_MAP_SPECIAL,
	STORAGE,
	STRANGE_DATA,
	GIVE_FAME,
	PARTY_OPERATION,
	DENY_PARTY_REQUEST, //probably something else too..
	PARTYCHAT,
	USE_DOOR,
	ENTER_MTS,
	ENTER_CASH_SHOP,
	DAMAGE_SUMMON,
	MOVE_SUMMON,
	SUMMON_ATTACK,
	BUDDYLIST_MODIFY,
	;

	private int code = -2;

	public void setValue(int code) {
		this.code = code;
	}

	@Override
	public int getValue() {
		return code;
	}
	
	public static Properties getDefaultProperties() throws FileNotFoundException, IOException {
		Properties props = new Properties();
		FileInputStream fis = new FileInputStream(System.getProperty("net.sf.odinms.recvops"));
		props.load(fis);
		fis.close();
		return props;
	}

	static {
		try {
			ExternalCodeTableGetter.populateValues(getDefaultProperties(), values());
		} catch (IOException e) {
			throw new RuntimeException("Failed to load recvops", e);
		}
	}
}
