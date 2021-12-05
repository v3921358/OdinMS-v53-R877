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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.odinms.server.quest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Properties;

import net.sf.odinms.client.MapleQuestStatus;
import net.sf.odinms.database.DatabaseConnection;

/**
 *
 * @author Matze
 */
public class MapleCustomQuest extends MapleQuest {
	
	public MapleCustomQuest(int id) {
		try {
			this.id = id;
			startActs = new LinkedList<MapleQuestAction>();
			completeActs = new LinkedList<MapleQuestAction>();
			startReqs = new LinkedList<MapleQuestRequirement>();
			completeReqs = new LinkedList<MapleQuestRequirement>();
			Connection con = DatabaseConnection.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT * FROM questrequirements WHERE " +
				"questid = ?");
			ps.setInt(1, id);
			ResultSet rs = ps.executeQuery();
			MapleQuestRequirement req;
			MapleCustomQuestData data;
			while (rs.next()) {
				Blob blob = rs.getBlob("data");
				ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(
					blob.getBytes(1, (int) blob.length())));
				data = (MapleCustomQuestData) ois.readObject();
				req = new MapleQuestRequirement(this, 
					MapleQuestRequirementType.getByWZName(data.getName()), data);
				MapleQuestStatus.Status status = MapleQuestStatus.Status.getById(
					rs.getInt("status"));
				if (status.equals(MapleQuestStatus.Status.NOT_STARTED)) {
					startReqs.add(req);
				} else if (status.equals(MapleQuestStatus.Status.STARTED)) {
					completeReqs.add(req);
				}
			}
			rs.close();
			ps.close();
			ps = con.prepareStatement("SELECT * FROM questactions WHERE questid = ?");
			ps.setInt(1, id);
			rs = ps.executeQuery();
			MapleQuestAction act;
			while (rs.next()) {
				Blob blob = rs.getBlob("data");
				ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(
					blob.getBytes(1, (int) blob.length())));
				data = (MapleCustomQuestData) ois.readObject();
				act = new MapleQuestAction(MapleQuestActionType.getByWZName(data.getName()), data);
				MapleQuestStatus.Status status = MapleQuestStatus.Status.getById(
					rs.getInt("status"));
				if (status.equals(MapleQuestStatus.Status.NOT_STARTED)) {
					startActs.add(act);
				} else if (status.equals(MapleQuestStatus.Status.STARTED)) {
					completeActs.add(act);
				}
			}
			rs.close();
			ps.close();			
		} catch (SQLException ex) {
			log.error("Error loading custom quest.", ex);
		} catch (IOException e) {
			log.error("Error loading custom quest.", e);
		} catch (ClassNotFoundException e) {
			log.error("Error loading custom quest.", e);
		}
	}
	
	public static void main(String[] args) throws Exception {
		/*int questid = 100012;

		Connection con = DatabaseConnection.getConnection();
		PreparedStatement psr = con.prepareStatement("INSERT INTO questrequirements VALUES (DEFAULT, ?, ?, ?)");
		PreparedStatement psa = con.prepareStatement("INSERT INTO questactions VALUES (DEFAULT, ?, ?, ?)");
		psr.setInt(1, questid);
		psa.setInt(1, questid);
		MapleCustomQuestData data;
		MapleCustomQuestData dataEntry;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 1102065, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("prop", 1, dataEntry));
		dataEntry = new MapleCustomQuestData("1", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 1332032, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("prop", 1, dataEntry));
		dataEntry = new MapleCustomQuestData("2", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 2022120, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 50, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("prop", 1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 1002419, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();*/
		
		
		
/*			 THIEF
 int questid = 100011;

			Properties dbProps = new Properties();
			dbProps.load(new FileReader("db.properties"));
			DatabaseConnection.setProps(dbProps);
			Connection con = DatabaseConnection.getConnection();
			PreparedStatement psr = con.prepareStatement("INSERT INTO questrequirements VALUES (DEFAULT, ?, ?, ?)");
			PreparedStatement psa = con.prepareStatement("INSERT INTO questactions VALUES (DEFAULT, ?, ?, ?)");
			psr.setInt(1, questid);
			psa.setInt(1, questid);
			MapleCustomQuestData data;
			MapleCustomQuestData dataEntry;
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(bos);

			data = new MapleCustomQuestData("quest", null, null);
			dataEntry = new MapleCustomQuestData("0", null, data);
			data.addChild(dataEntry);
			dataEntry.addChild(new MapleCustomQuestData("id", 100010, dataEntry));
			dataEntry.addChild(new MapleCustomQuestData("state", MapleQuestStatus.Status.COMPLETED.getId(), dataEntry));
			psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			oos.writeObject(data);
			oos.flush();
			psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
			psr.executeUpdate();
			
			data = new MapleCustomQuestData("item", null, null);
			dataEntry = new MapleCustomQuestData("0", null, data);
			data.addChild(dataEntry);
			dataEntry.addChild(new MapleCustomQuestData("id", 4031012, dataEntry));
			dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
			psr.setInt(2, MapleQuestStatus.Status.STARTED.getId());
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			oos.writeObject(data);
			oos.flush();
			psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
			psr.executeUpdate();

			data = new MapleCustomQuestData("item", null, null);
			dataEntry = new MapleCustomQuestData("1", null, data);
			data.addChild(dataEntry);
			dataEntry.addChild(new MapleCustomQuestData("id", 4031012, dataEntry));
			dataEntry.addChild(new MapleCustomQuestData("count", -1, dataEntry));
			psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			oos.writeObject(data);
			oos.flush();
			psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
			psa.executeUpdate();
			
			questid = 100010;

			psr.setInt(1, questid);
			psa.setInt(1, questid);

			data = new MapleCustomQuestData("quest", null, null);
			dataEntry = new MapleCustomQuestData("0", null, data);
			data.addChild(dataEntry);
			dataEntry.addChild(new MapleCustomQuestData("id", 100009, dataEntry));
			dataEntry.addChild(new MapleCustomQuestData("state", MapleQuestStatus.Status.COMPLETED.getId(), dataEntry));
			psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			oos.writeObject(data);
			oos.flush();
			psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
			psr.executeUpdate();
			
			data = new MapleCustomQuestData("item", null, null);
			dataEntry = new MapleCustomQuestData("0", null, data);
			data.addChild(dataEntry);
			dataEntry.addChild(new MapleCustomQuestData("id", 4031013, dataEntry));
			dataEntry.addChild(new MapleCustomQuestData("count", 30, dataEntry));
			psr.setInt(2, MapleQuestStatus.Status.STARTED.getId());
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			oos.writeObject(data);
			oos.flush();
			psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
			psr.executeUpdate();

			data = new MapleCustomQuestData("item", null, null);
			dataEntry = new MapleCustomQuestData("0", null, null);
			data.addChild(dataEntry);
			dataEntry.addChild(new MapleCustomQuestData("id", 4031013, dataEntry));
			dataEntry.addChild(new MapleCustomQuestData("count", -30, dataEntry));
			dataEntry = new MapleCustomQuestData("1", null, data);
			data.addChild(dataEntry);
			dataEntry.addChild(new MapleCustomQuestData("id", 4031012, dataEntry));
			dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
			psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			oos.writeObject(data);
			oos.flush();
			psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
			psa.executeUpdate();

			questid = 100009;

			psr.setInt(1, questid);
			psa.setInt(1, questid);

			data = new MapleCustomQuestData("job", null, null);
			data.addChild(new MapleCustomQuestData("0", MapleJob.THIEF.getId(), data));
			psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			oos.writeObject(data);
			oos.flush();
			psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
			psr.executeUpdate();

			data = new MapleCustomQuestData("lvmin", 30, null);
			psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			oos.writeObject(data);
			oos.flush();
			psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
			psr.executeUpdate();

			data = new MapleCustomQuestData("item", null, null);
			dataEntry = new MapleCustomQuestData("0", null, data);
			data.addChild(dataEntry);
			dataEntry.addChild(new MapleCustomQuestData("id", 4031011, dataEntry));
			dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
			psa.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			oos.writeObject(data);
			oos.flush();
			psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
			psa.executeUpdate();

			data = new MapleCustomQuestData("item", null, null);
			dataEntry = new MapleCustomQuestData("0", null, data);
			data.addChild(dataEntry);
			dataEntry.addChild(new MapleCustomQuestData("id", 4031011, dataEntry));
			dataEntry.addChild(new MapleCustomQuestData("count", -1, dataEntry));
			psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			oos.writeObject(data);
			oos.flush();
			psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
			psa.executeUpdate();

			psr.close();
			psa.close();
			con.close();*/
		
		/* BOWMAN
		 int questid = 100002;

		Properties dbProps = new Properties();
		dbProps.load(new FileReader("db.properties"));
		DatabaseConnection.setProps(dbProps);
		Connection con = DatabaseConnection.getConnection();
		PreparedStatement psr = con.prepareStatement("INSERT INTO questrequirements VALUES (DEFAULT, ?, ?, ?)");
		PreparedStatement psa = con.prepareStatement("INSERT INTO questactions VALUES (DEFAULT, ?, ?, ?)");
		psr.setInt(1, questid);
		psa.setInt(1, questid);
		MapleCustomQuestData data;
		MapleCustomQuestData dataEntry;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);

		data = new MapleCustomQuestData("quest", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 100001, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("state", MapleQuestStatus.Status.COMPLETED.getId(), dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();
		
		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031012, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("1", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031012, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", -1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();
		
		questid = 100001;

		psr.setInt(1, questid);
		psa.setInt(1, questid);

		data = new MapleCustomQuestData("quest", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 100000, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("state", MapleQuestStatus.Status.COMPLETED.getId(), dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();
		
		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031013, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 30, dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, null);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031013, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", -30, dataEntry));
		dataEntry = new MapleCustomQuestData("1", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031012, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();

		questid = 100000;

		psr.setInt(1, questid);
		psa.setInt(1, questid);

		data = new MapleCustomQuestData("job", null, null);
		data.addChild(new MapleCustomQuestData("0", MapleJob.BOWMAN.getId(), data));
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("lvmin", 30, null);
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031010, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031010, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", -1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();

		psr.close();
		psa.close();
		con.close();*/
		
		// WARRIOR
		/*int questid = 100005;

		Properties dbProps = new Properties();
		dbProps.load(new FileReader("db.properties"));
		DatabaseConnection.setProps(dbProps);
		Connection con = DatabaseConnection.getConnection();
		PreparedStatement psr = con.prepareStatement("INSERT INTO questrequirements VALUES (DEFAULT, ?, ?, ?)");
		PreparedStatement psa = con.prepareStatement("INSERT INTO questactions VALUES (DEFAULT, ?, ?, ?)");
		psr.setInt(1, questid);
		psa.setInt(1, questid);
		MapleCustomQuestData data;
		MapleCustomQuestData dataEntry;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);

		data = new MapleCustomQuestData("quest", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 100004, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("state", MapleQuestStatus.Status.COMPLETED.getId(), dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();
		
		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031012, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("1", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031012, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", -1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();
		
		questid = 100004;

		psr.setInt(1, questid);
		psa.setInt(1, questid);

		data = new MapleCustomQuestData("quest", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 100003, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("state", MapleQuestStatus.Status.COMPLETED.getId(), dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();
		
		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031013, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 30, dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, null);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031013, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", -30, dataEntry));
		dataEntry = new MapleCustomQuestData("1", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031012, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();

		questid = 100003;

		psr.setInt(1, questid);
		psa.setInt(1, questid);

		data = new MapleCustomQuestData("job", null, null);
		data.addChild(new MapleCustomQuestData("0", MapleJob.WARRIOR.getId(), data));
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("lvmin", 30, null);
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031008, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031008, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", -1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();

		psr.close();
		psa.close();
		con.close();

		// MAGICIAN
		int questid = 100008;

		Properties dbProps = new Properties();
		dbProps.load(new FileReader("db.properties"));
		DatabaseConnection.setProps(dbProps);
		Connection con = DatabaseConnection.getConnection();
		PreparedStatement psr = con.prepareStatement("INSERT INTO questrequirements VALUES (DEFAULT, ?, ?, ?)");
		PreparedStatement psa = con.prepareStatement("INSERT INTO questactions VALUES (DEFAULT, ?, ?, ?)");
		psr.setInt(1, questid);
		psa.setInt(1, questid);
		MapleCustomQuestData data;
		MapleCustomQuestData dataEntry;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);

		data = new MapleCustomQuestData("quest", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 100007, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("state", MapleQuestStatus.Status.COMPLETED.getId(), dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();
		
		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031012, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("1", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031012, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", -1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();
		
		questid = 100007;

		psr.setInt(1, questid);
		psa.setInt(1, questid);

		data = new MapleCustomQuestData("quest", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 100006, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("state", MapleQuestStatus.Status.COMPLETED.getId(), dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();
		
		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031013, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 30, dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, null);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031013, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", -30, dataEntry));
		dataEntry = new MapleCustomQuestData("1", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031012, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();

		questid = 100006;

		psr.setInt(1, questid);
		psa.setInt(1, questid);

		data = new MapleCustomQuestData("job", null, null);
		data.addChild(new MapleCustomQuestData("0", MapleJob.MAGICIAN.getId(), data));
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("lvmin", 30, null);
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031009, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031009, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", -1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();

		psr.close();
		psa.close();
		con.close();*/
		
		// 3rd job
		int questid = 100100;

		Properties dbProps = new Properties();
		dbProps.load(new FileReader("db.properties"));
		DatabaseConnection.setProps(dbProps);
		Connection con = DatabaseConnection.getConnection();
		PreparedStatement psr = con.prepareStatement("INSERT INTO questrequirements VALUES (DEFAULT, ?, ?, ?)");
		PreparedStatement psa = con.prepareStatement("INSERT INTO questactions VALUES (DEFAULT, ?, ?, ?)");
		psr.setInt(1, questid);
		psa.setInt(1, questid);
		MapleCustomQuestData data;
		MapleCustomQuestData dataEntry;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);

		data = new MapleCustomQuestData("lvmin", 70, null);
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());		
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();
		
		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031057, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("1", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031057, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", -1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();
		
		// clone quest (still 3rd job)
		questid = 100101;

		psr.setInt(1, questid);
		psa.setInt(1, questid);

		data = new MapleCustomQuestData("quest", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 100100, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("state", MapleQuestStatus.Status.STARTED.getId(), dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();
		
		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031059, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, null);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031059, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", -1, dataEntry));
		dataEntry = new MapleCustomQuestData("1", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031057, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();

		// quiz quest (still 3rd job)
		questid = 100102;

		psr.setInt(1, questid);
		psa.setInt(1, questid);

		data = new MapleCustomQuestData("lvmin", 70, null);
		psr.setInt(2, MapleQuestStatus.Status.NOT_STARTED.getId());		
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();
		
		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("0", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031058, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", 1, dataEntry));
		psr.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psr.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psr.executeUpdate();

		data = new MapleCustomQuestData("item", null, null);
		dataEntry = new MapleCustomQuestData("1", null, data);
		data.addChild(dataEntry);
		dataEntry.addChild(new MapleCustomQuestData("id", 4031058, dataEntry));
		dataEntry.addChild(new MapleCustomQuestData("count", -1, dataEntry));
		psa.setInt(2, MapleQuestStatus.Status.STARTED.getId());
		bos = new ByteArrayOutputStream();
		oos = new ObjectOutputStream(bos);
		oos.writeObject(data);
		oos.flush();
		psa.setBlob(3, new ByteArrayInputStream(bos.toByteArray()));
		psa.executeUpdate();

		psr.close();
		psa.close();
		con.close();

	}

}
