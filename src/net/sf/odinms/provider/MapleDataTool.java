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

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import net.sf.odinms.provider.wz.MapleDataType;
import net.sf.odinms.provider.wz.PNGMapleCanvas;

public class MapleDataTool {

	private MapleDataTool() {

	}

	public static String getString(MapleData data) {
		return ((String) data.getData());
	}

	public static String getString(MapleData data, String def) {
		if (data == null || data.getData() == null) {
			return def;
		} else {
			return ((String) data.getData());
		}
	}

	public static String getString(String path, MapleData data) {
		return getString(data.getChildByPath(path));
	}

	public static String getString(String path, MapleData data, String def) {
		return getString(data.getChildByPath(path), def);
	}

	public static double getDouble(MapleData data) {
		return ((Double) data.getData()).doubleValue();
	}

	public static int getInt(MapleData data) {
		return ((Integer) data.getData()).intValue();
	}

	public static int getInt(MapleData data, int def) {
		if (data == null || data.getData() == null) {
			return def;
		} else {
			return ((Integer) data.getData()).intValue();
		}
	}

	public static int getInt(String path, MapleData data) {
		return getInt(data.getChildByPath(path));
	}

	public static int getIntConvert(MapleData data) {
		if (data.getType() == MapleDataType.STRING) {
			return Integer.parseInt(getString(data));
		} else {
			return getInt(data);
		}
	}

	public static int getIntConvert(String path, MapleData data) {
		MapleData d = data.getChildByPath(path);
		if (d.getType() == MapleDataType.STRING) {
			return Integer.parseInt(getString(d));
		} else {
			return getInt(d);
		}
	}

	public static int getInt(String path, MapleData data, int def) {
		return getInt(data.getChildByPath(path), def);
	}

	public static int getIntConvert(String path, MapleData data, int def) {
		MapleData d = data.getChildByPath(path);
		if (d == null) {
			return def;
		}
		if (d.getType() == MapleDataType.STRING) {
			try {
				return Integer.parseInt(getString(d));
			} catch (NumberFormatException nfe) {
				return def;
			}
		} else {
			return getInt(d, def);
		}
	}

	public static BufferedImage getImage(MapleData data) {
		return getImage(data, new int[]{2, 1, 0, 3});
	}

	public static BufferedImage getImage(MapleData data, int[] zahlen) {
		PNGMapleCanvas c = (PNGMapleCanvas) data.getData();

		int sizeUncompressed = 0;
		int size8888 = 0;
		//int maxOutBuf = 1;
		int maxWriteBuf = 2;
		int maxHeight = 3;

		byte[] writeBuf = new byte[maxWriteBuf];
		@SuppressWarnings(value = "unused")
		byte[] rowPointers = new byte[maxHeight];

		switch (c.getFormat()) {
			case 1:
			case 513:
				sizeUncompressed = c.getHeight() * c.getWidth() * 4;
				break;
			case 2:
				sizeUncompressed = c.getHeight() * c.getWidth() * 8;
				break;
			case 517:
				sizeUncompressed = c.getHeight() * c.getWidth() / 128;
				break;
		}

		size8888 = c.getHeight() * c.getWidth() * 8;

		if (size8888 > maxWriteBuf) {
			maxWriteBuf = size8888;
			writeBuf = new byte[maxWriteBuf];
		}

		if (c.getHeight() > maxHeight) {
			maxHeight = c.getHeight();
			rowPointers = new byte[maxHeight];
		}

		Inflater dec = new Inflater();
		dec.setInput(c.getData(), 0, c.getDataLength());

		int declen = 0;
		byte[] uc = new byte[sizeUncompressed];

		try {
			declen = dec.inflate(uc);
		} catch ( DataFormatException ex) {
			throw new RuntimeException("zlib fucks", ex);
		}

		dec.end();
		// fuck the format
		if (c.getFormat() == 1) {
			for ( int i = 0; i < sizeUncompressed; i++) {
				byte low = (byte) (uc[i] & 0x0F);
				byte high = (byte) (uc[i] & 0xF0);

				writeBuf[(i << 1)] = (byte) (((low << 4) | low) & 0xFF);
				writeBuf[(i << 1) + 1] = (byte) (high | ((high >>> 4) & 0xF));
			}
		} else if (c.getFormat() == 2) {
			writeBuf = uc;
		} else if (c.getFormat() == 513) {
			for ( int i = 0; i < declen; i += 2) {
				byte bBits = (byte) ((uc[i] & 0x1F) << 3);
				byte gBits = (byte) (((uc[i + 1] & 0x07) << 5) | ((uc[i] & 0xE0) >> 3));
				byte rBits = (byte) (uc[i + 1] & 0xF8);

				writeBuf[(i << 1)] = (byte) (bBits | (bBits >> 5));
				writeBuf[(i << 1) + 1] = (byte) (gBits | (gBits >> 6));
				writeBuf[(i << 1) + 2] = (byte) (rBits | (rBits >> 5));
				writeBuf[(i << 1) + 3] = (byte) 0xFF;
			}
		} else if (c.getFormat() == 517) {
			byte b = 0x00;
			int pixelIndex = 0;

			for ( int i = 0; i < declen; i++) {
				for ( int j = 0; j < 8; j++) {
					b = (byte) (((uc[i] & (0x01 << (7 - j))) >> (7 - j)) * 255);
					for ( int k = 0; k < 16; k++) {
						pixelIndex = (i << 19) + (j << 6) + k * 2;
						writeBuf[pixelIndex] = b;
						writeBuf[pixelIndex + 1] = b;
						writeBuf[pixelIndex + 2] = b;
						writeBuf[pixelIndex + 3] = (byte) 0xFF;
					}
				}
			}
		}

		DataBufferByte imgData = new DataBufferByte(writeBuf, sizeUncompressed);

		//SampleModel sm = new PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, c.getWidth(), c.getHeight(), 4, c.getWidth() * 4, new int[] {2, 1, 0, 3});
		SampleModel sm = new PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, c.getWidth(), c.getHeight(), 4, c.getWidth() * 4, zahlen);
		WritableRaster imgRaster = Raster.createWritableRaster(sm, imgData, new Point(0, 0));

		BufferedImage aa = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
		aa.setData(imgRaster);

		return aa;
	}
}
