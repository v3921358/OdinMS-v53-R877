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

package net.sf.odinms.provider.wz;

import java.awt.Point;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import net.sf.odinms.tools.data.input.GenericSeekableLittleEndianAccessor;
import net.sf.odinms.tools.data.input.RandomAccessByteStream;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Ported Code, see WZFile.java for more info
 */
public class WZIMGFile {
	private Logger log = LoggerFactory.getLogger(WZIMGFile.class);
	private WZFileEntry file;
	private WZIMGEntry root;
	private boolean provideImages;

	public WZIMGFile(File wzfile, WZFileEntry file, boolean provideImages) throws IOException {
		RandomAccessFile raf = new RandomAccessFile(wzfile, "r");
		SeekableLittleEndianAccessor slea = new GenericSeekableLittleEndianAccessor(new RandomAccessByteStream(raf));
		slea.seek(file.getOffset());
		this.file = file;
		this.provideImages = provideImages;
		root = new WZIMGEntry(file.getParent());
		root.setName(file.getName());
		root.setType(MapleDataType.EXTENDED);
		// dumpImg(new FileOutputStream(file.getName()), slea);
		parseExtended(root, slea);
		root.finish();
		raf.close();
	}

	protected void dumpImg(OutputStream out, SeekableLittleEndianAccessor slea) throws IOException {
		DataOutputStream os = new DataOutputStream(out);
		long oldPos = slea.getPosition();
		slea.seek(file.getOffset());
		for (int x = 0; x < file.getSize(); x++) {
			os.write(slea.readByte());
		}
		slea.seek(oldPos);
	}

	public WZIMGEntry getRoot() {
		return root;
	}

	private void parse(WZIMGEntry entry, SeekableLittleEndianAccessor slea) {
		byte marker = slea.readByte();
		switch (marker) {
			case 0:
				entry.setName(WZTool.readDecodedString(slea));
				break;
			case 1:
				entry.setName(WZTool.readDecodedStringAtOffsetAndReset(slea, file.getOffset() + slea.readInt()));
				break;
			default:
				log.error("Unknown Image identifier: {} at offset {}", marker, (slea.getPosition() - file.getOffset()));
		}

		marker = slea.readByte();

		switch (marker) {
			case 0:
				entry.setType(MapleDataType.IMG_0x00);
				break;
			case 2:
			case 11: //??? no idea, since 0.49
				entry.setType(MapleDataType.SHORT);
				entry.setData(Short.valueOf(slea.readShort()));
				break;
			case 3:
				entry.setType(MapleDataType.INT);
				entry.setData(Integer.valueOf(WZTool.readValue(slea)));
				break;
			case 4:
				entry.setType(MapleDataType.FLOAT);
				entry.setData(Float.valueOf(WZTool.readFloatValue(slea)));
				break;
			case 5:
				entry.setType(MapleDataType.DOUBLE);
				entry.setData(Double.valueOf(slea.readDouble()));
				break;
			case 8:
				entry.setType(MapleDataType.STRING);
				byte iMarker = slea.readByte();
				if (iMarker == 0) {
					entry.setData(WZTool.readDecodedString(slea));
				} else if (iMarker == 1) {
					entry.setData(WZTool.readDecodedStringAtOffsetAndReset(slea, slea.readInt() + file.getOffset()));
				} else {
					log.error("Unknown String type {}", iMarker);
				}
				break;
			case 9:
				entry.setType(MapleDataType.EXTENDED);
				slea.readInt();
				parseExtended(entry, slea);
				break;
			default:
				log.error("Unknown Image type {}", marker);
		}
	}

	private void parseExtended(WZIMGEntry entry, SeekableLittleEndianAccessor slea) {
		byte marker = slea.readByte();

		String type = "";
		switch (marker) {
			case 0x73:
				type = WZTool.readDecodedString(slea);
				break;
			case 0x1B:
				type = WZTool.readDecodedStringAtOffsetAndReset(slea, file.getOffset() + slea.readInt());
				break;
			default:
				log.error("Unknown extended image identifier: {} at offset {}", marker, (slea.getPosition() - file
					.getOffset()));
		}

		if (type.equals("Property")) {
			entry.setType(MapleDataType.PROPERTY);
			slea.readByte();
			slea.readByte();
			int children = WZTool.readValue(slea);
			for (int i = 0; i < children; i++) {
				WZIMGEntry cEntry = new WZIMGEntry(entry);
				parse(cEntry, slea);
				cEntry.finish();
				entry.addChild(cEntry);
			}
		} else if (type.equals("Shape2D#Vector2D")) {
			entry.setType(MapleDataType.VECTOR);
			int x = WZTool.readValue(slea);
			int y = WZTool.readValue(slea);
			entry.setData(new Point(x, y));
		} else if (type.equals("Canvas")) {
			entry.setType(MapleDataType.CANVAS);
			slea.readByte();
			marker = slea.readByte();
			if (marker == 0) {
				// do nothing
			} else if (marker == 1) {
				slea.readByte();
				slea.readByte();
				int children = WZTool.readValue(slea);
				for (int i = 0; i < children; i++) {
					WZIMGEntry child = new WZIMGEntry(entry);
					parse(child, slea);
					child.finish();
					entry.addChild(child);
				}
			} else {
				log.warn("Canvas marker != 1 ({})", marker);
			}
			int width = WZTool.readValue(slea);
			int height = WZTool.readValue(slea);
			int format = WZTool.readValue(slea);
			int format2 = slea.readByte();
			slea.readInt();
			int dataLength = slea.readInt() - 1;
			slea.readByte();
			long offset = 0;
			
			if (provideImages) {
				offset = slea.getPosition();
				byte[] pngdata = slea.read(dataLength);
				entry.setData(new PNGMapleCanvas(width, height, dataLength, (int) offset, format + format2, pngdata));
			} else {
				slea.skip(dataLength);
			}
		} else if (type.equals("Shape2D#Convex2D")) {
			entry.setType(MapleDataType.CONVEX);
			int children = WZTool.readValue(slea);
			for (int i = 0; i < children; i++) {
				WZIMGEntry cEntry = new WZIMGEntry(entry);
				parseExtended(cEntry, slea);
				cEntry.finish();
				entry.addChild(cEntry);
			}
		} else if (type.equals("Sound_DX8")) { // XXX untested
			entry.setType(MapleDataType.SOUND);
			slea.readByte();
			int dataLength = WZTool.readValue(slea);
			WZTool.readValue(slea); // no clue what this is

			// the file starts here, but we don't know the header size (to tack onto the data size.)
			int offset = (int) slea.getPosition();
			slea.seek(offset + 51);
			dataLength += WZTool.readValue(slea);
			dataLength += slea.getPosition() - offset;

			slea.seek(offset + dataLength);

			entry.setData(new ImgMapleSound(dataLength, offset - file.getOffset()));
		} else if (type.equals("UOL")) { // XXX untested
			entry.setType(MapleDataType.UOL);
			slea.readByte();
			byte uolmarker = slea.readByte();
			switch (uolmarker) {
				case 0:
					entry.setData(WZTool.readDecodedString(slea));
					break;
				case 1:
					entry.setData(WZTool.readDecodedStringAtOffsetAndReset(slea, file.getOffset() + slea.readInt()));
					break;
				default:
					log.error("Unknown UOL marker: {} {}", uolmarker, entry.getName());
			}
		}

		else {

			log.error("Unhandeled extended type: {}", type);
		}
	}
}
