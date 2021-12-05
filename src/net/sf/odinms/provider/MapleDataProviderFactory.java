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

package net.sf.odinms.provider;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.odinms.provider.wz.WZFile;

public class MapleDataProviderFactory {
	private static Logger log = LoggerFactory.getLogger(MapleDataProviderFactory.class);

	private static MapleDataProvider getWZ(Object in, boolean provideImages) {
		if (in instanceof File) {
			File file = (File) in;

			if (file.getName().endsWith("wz")) {
				try {
					return new WZFile((File) in, provideImages);
				} catch (IOException e) {
					log.error("Loading WZ File failed", e);
				}
			}
		}
		throw new IllegalArgumentException("Can't create data provider for input " + in);
	}
	
	public static MapleDataProvider getDataProvider(Object in) {
		return getWZ(in, false);
	}
	
	public static MapleDataProvider getImageProvidingDataProvider(Object in) {
		return getWZ(in, true);
	}
	
	
}
