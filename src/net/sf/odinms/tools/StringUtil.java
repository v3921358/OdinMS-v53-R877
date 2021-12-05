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

package net.sf.odinms.tools;

public class StringUtil {
	public static String getLeftPaddedStr(String in, char padchar, int length) {
		StringBuilder builder = new StringBuilder(length);
		for (int x = in.length(); x < length; x++) {
			builder.append(padchar);
		}
		builder.append(in);
		return builder.toString();
	}

	public static String getRightPaddedStr(String in, char padchar, int length) {
		StringBuilder builder = new StringBuilder(in);
		for (int x = in.length(); x < length; x++) {
			builder.append(padchar);
		}
		return builder.toString();
	}

	public static String joinStringFrom(String arr[], int start) {
		return joinStringFrom(arr, start, " ");
	}

	public static String joinStringFrom(String arr[], int start, String sep) {
		StringBuilder builder = new StringBuilder();
		for (int i = start; i < arr.length; i++) {
			builder.append(arr[i]);
			if (i != arr.length - 1) {
				builder.append(sep);
			}
		}
		return builder.toString();
	}

	public static String makeEnumHumanReadable(String enumName) {
		StringBuilder builder = new StringBuilder(enumName.length() + 1);
		String[] words = enumName.split("_");
		for (String word: words) {
			if (word.length() <= 2) {
				builder.append(word); //assume that it's an abbrevation 
			} else {
				builder.append(word.charAt(0));
				builder.append(word.substring(1).toLowerCase());
			}
			builder.append(' ');
		}
		return builder.substring(0, enumName.length());
	}
	
	public static int countCharacters (String str, char chr) {
		int ret = 0;
		for (int i = 0; i < str.length(); i++) {
			if (str.charAt(i) == chr) {
				ret++;
			}
		}
		return ret;
	}
}
