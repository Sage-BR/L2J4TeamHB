/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.l2j.gameserver.datatables;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import Dev.SpecialMods.XMLDocumentFactory;
import net.sf.l2j.Config;
import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.gameserver.model.L2DropCategory;
import net.sf.l2j.gameserver.model.L2DropData;
import net.sf.l2j.gameserver.model.L2MinionData;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.base.ClassId;
import net.sf.l2j.gameserver.skills.Stats;
import net.sf.l2j.gameserver.templates.L2NpcTemplate;
import net.sf.l2j.gameserver.templates.StatsSet;

public class NpcTable
{
	private static Logger _log = Logger.getLogger(NpcTable.class.getName());

	private static NpcTable _instance;

	private Map<Integer, L2NpcTemplate> _npcs;

	private boolean _initialized = false;

	public static NpcTable getInstance()
	{
		if (_instance == null)
		{
			_instance = new NpcTable();
		}

		return _instance;
	}

	private NpcTable()
	{
		_npcs = new ConcurrentHashMap<>();

		restoreNpcData();
	}

	private void restoreNpcData()
	{
		loadNpcsFromXml("./data/xml/npcs");

		loadNpcsFromXml("./data/xml/npcs/customs");

		_log.info("NPCTable: Total NPC templates loaded: " + _npcs.size());

		// 94 orphan npcskills records exist in SQL referencing non-existent NPC
		// IDs — intentionally skipped.
		// Skills (npcskills), trainer data (skill_learn), and minions are
		// loaded from XML.
		// Droplist remains in SQL.

		java.sql.Connection con = null;

		try
		{
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement statement2 = con.prepareStatement("SELECT "
				        + L2DatabaseFactory.getInstance().safetyString(new String[] {
				                "mobId", "itemId", "min", "max", "category",
				                "chance" })
				        + " FROM droplist ORDER BY mobId, chance DESC");
				ResultSet dropData = statement2.executeQuery();
				L2DropData dropDat = null;
				L2NpcTemplate npcDat = null;
				int missingCount = 0;
				int droppedRowCount = 0;
				Set<Integer> reportedMissing = new HashSet<>();

				while (dropData.next())
				{
					droppedRowCount++;
					int mobId = dropData.getInt("mobId");
					npcDat = _npcs.get(mobId);
					if (npcDat == null)
					{
						if (!reportedMissing.contains(mobId))
						{
							_log.severe("NPCTable: No npc correlating with drop id: "
							        + mobId);
							reportedMissing.add(mobId);
						}
						missingCount++;
						continue;
					}
					dropDat = new L2DropData();

					dropDat.setItemId(dropData.getInt("itemId"));
					dropDat.setMinDrop(dropData.getInt("min"));
					dropDat.setMaxDrop(dropData.getInt("max"));
					dropDat.setChance(dropData.getInt("chance"));

					int category = dropData.getInt("category");

					npcDat.addDropData(dropDat, category);
				}

				dropData.close();
				statement2.close();

				if (missingCount > 0)
				{
					_log.warning("NPCTable: " + missingCount
					        + " droplist rows skipped ("
					        + reportedMissing.size()
					        + " unique NPC IDs missing from templates)");
				}
				_log.info("NPCTable: Loaded " + droppedRowCount
				        + " droplist rows for " + _npcs.size()
				        + " NPC templates.");
			}
			catch (Exception e)
			{
				_log.severe("NPCTable: Error reading NPC drop data: " + e);
			}

			if (Config.CUSTOM_DROPLIST_TABLE)
			{
				try
				{
					PreparedStatement statement2 = con.prepareStatement("SELECT "
					        + L2DatabaseFactory.getInstance().safetyString(new String[] {
					                "mobId", "itemId", "min", "max", "category",
					                "chance" })
					        + " FROM custom_droplist ORDER BY mobId, chance DESC");
					ResultSet dropData = statement2.executeQuery();
					L2DropData dropDat = null;
					L2NpcTemplate npcDat = null;
					int cCount = 0;
					int cMissing = 0;
					Set<Integer> cReportedMissing = new HashSet<>();
					while (dropData.next())
					{
						int mobId = dropData.getInt("mobId");
						npcDat = _npcs.get(mobId);
						if (npcDat == null)
						{
							if (!cReportedMissing.contains(mobId))
							{
								_log.warning("NPCTable: CUSTOM DROPLIST No npc correlating with id: "
								        + mobId);
								cReportedMissing.add(mobId);
							}
							cMissing++;
							continue;
						}
						dropDat = new L2DropData();
						dropDat.setItemId(dropData.getInt("itemId"));
						dropDat.setMinDrop(dropData.getInt("min"));
						dropDat.setMaxDrop(dropData.getInt("max"));
						dropDat.setChance(dropData.getInt("chance"));
						int category = dropData.getInt("category");
						npcDat.addDropData(dropDat, category);
						cCount++;
					}
					dropData.close();
					statement2.close();
					if (cMissing > 0)
					{
						_log.warning("NPCTable: " + cMissing
						        + " custom droplist rows skipped ("
						        + cReportedMissing.size()
						        + " unique NPC IDs missing from templates)");
					}
					_log.info("NPCTable: Loaded " + cCount
					        + " custom droplist rows.");
				}
				catch (Exception e)
				{
					_log.severe("NPCTable: Error reading NPC CUSTOM drop data: "
					        + e);
				}
			}

		}
		finally
		{
			try
			{
				con.close();
			}
			catch (Exception e)
			{
			}
		}

		_initialized = true;
	}

	private void loadNpcsFromXml(String dirPath)
	{
		File dir = new File(Config.DATAPACK_ROOT, dirPath);
		if (!dir.exists() || !dir.isDirectory())
		{
			_log.config("NpcTable: Directory " + dirPath + " not found.");
			return;
		}

		File[] files = dir.listFiles();
		if (files == null)
		{
			return;
		}

		int count = 0;
		for (File f : files)
		{
			if (!f.getName().endsWith(".xml"))
			{
				continue;
			}

			try
			{
				Document doc = XMLDocumentFactory.getInstance().loadDocument(f);
				Node listNode = doc.getFirstChild();

				for (Node n = listNode.getFirstChild(); n != null; n = n.getNextSibling())
				{
					if (!n.getNodeName().equalsIgnoreCase("npc"))
					{
						continue;
					}

					NamedNodeMap npcAttrs = n.getAttributes();

					String npcIdStr = npcAttrs.getNamedItem("id").getNodeValue();
					int npcId = Integer.parseInt(npcIdStr);
					int idTemplate = Integer.parseInt(getAttr(npcAttrs, "idTemplate", npcIdStr));
					String npcName = npcAttrs.getNamedItem("name").getNodeValue();
					String npcTitle = getAttr(npcAttrs, "title", "");

					StatsSet npcDat = new StatsSet();
					npcDat.set("npcId", npcId);
					npcDat.set("idTemplate", idTemplate);
					npcDat.set("name", npcName);
					npcDat.set("title", npcTitle);
					npcDat.set("serverSideName", false);
					npcDat.set("serverSideTitle", false);
					npcDat.set("baseShldDef", 0);
					npcDat.set("baseShldRate", 0);
					npcDat.set("baseCritRate", 38);
					npcDat.set("baseCpMax", 0);
					npcDat.set("armor", 0);
					npcDat.set("jClass", "");
					npcDat.set("sex", "male");
					npcDat.set("absorb_level", 0);
					npcDat.set("absorb_type", "LAST_HIT");
					npcDat.set("drop_herbs", false);

					int level = 0;

					for (Node child = n.getFirstChild(); child != null; child = child.getNextSibling())
					{
						String nodeName = child.getNodeName();

						if (nodeName.equalsIgnoreCase("set"))
						{
							NamedNodeMap setAttrs = child.getAttributes();
							String setName = setAttrs.getNamedItem("name").getNodeValue();
							String setVal = setAttrs.getNamedItem("val").getNodeValue();

							applySet(npcDat, setName, setVal);
							if (setName.equalsIgnoreCase("level"))
							{
								level = Integer.parseInt(setVal);
							}
						}
						else if (nodeName.equalsIgnoreCase("ai"))
						{
							NamedNodeMap aiAttrs = child.getAttributes();
							applyAi(npcDat, aiAttrs);
						}
						else if (nodeName.equalsIgnoreCase("skills"))
						{
							for (Node skillNode = child.getFirstChild(); skillNode != null; skillNode = skillNode.getNextSibling())
							{
								if (skillNode.getNodeName().equalsIgnoreCase("skill"))
								{
									NamedNodeMap sAttrs = skillNode.getAttributes();
									int skillId = Integer.parseInt(sAttrs.getNamedItem("id").getNodeValue());
									int skillLevel = Integer.parseInt(sAttrs.getNamedItem("level").getNodeValue());

									if (skillId == 4416
									        && _npcs.get(npcId) != null)
									{
										_npcs.get(npcId).setRace(skillLevel);
									}
									else
									{
										L2Skill skill = SkillTable.getInstance().getInfo(skillId, skillLevel);
										if (skill != null)
										{
											if (_npcs.get(npcId) != null)
											{
												_npcs.get(npcId).addSkill(skill);
											}
										}
									}
								}
							}
						}
						else if (nodeName.equalsIgnoreCase("minions"))
						{
							for (Node minionNode = child.getFirstChild(); minionNode != null; minionNode = minionNode.getNextSibling())
							{
								if (minionNode.getNodeName().equalsIgnoreCase("minion"))
								{
									NamedNodeMap mAttrs = minionNode.getAttributes();
									int minionId = Integer.parseInt(mAttrs.getNamedItem("id").getNodeValue());
									int minAmount = Integer.parseInt(getAttr(mAttrs, "min", "1"));
									int maxAmount = Integer.parseInt(getAttr(mAttrs, "max", "1"));

									L2MinionData minionDat = new L2MinionData();
									minionDat.setMinionId(minionId);
									minionDat.setAmountMin(minAmount);
									minionDat.setAmountMax(maxAmount);
									if (_npcs.get(npcId) != null)
									{
										_npcs.get(npcId).addRaidData(minionDat);
									}
								}
							}
						}
						else if (nodeName.equalsIgnoreCase("teachTo"))
						{
							String classes = getAttr(child.getAttributes(), "classes", "");
							if (!classes.isEmpty())
							{
								String[] classIds = classes.split(";");
								for (String cid : classIds)
								{
									try
									{
										int classId = Integer.parseInt(cid.trim());
										if (_npcs.get(npcId) != null)
										{
											_npcs.get(npcId).addTeachInfo(ClassId.values()[classId]);
										}
									}
									catch (Exception e)
									{
									}
								}
							}
						}
					}

					if (level > 0)
					{
						float hpReg = npcDat.getFloat("baseHpReg", 0);
						if (hpReg <= 0)
						{
							npcDat.set("baseHpReg", (float) (1.5
							        + ((level - 1) / 10.0)));
						}

						float mpReg = npcDat.getFloat("baseMpReg", 0);
						if (mpReg <= 0)
						{
							npcDat.set("baseMpReg", (float) (0.9
							        + 0.3 * ((level - 1) / 10.0)));
						}
					}

					L2NpcTemplate template = new L2NpcTemplate(npcDat);
					template.addVulnerability(Stats.BOW_WPN_VULN, 1);
					template.addVulnerability(Stats.CROSSBOW_WPN_VULN, 1);
					template.addVulnerability(Stats.BLUNT_WPN_VULN, 1);
					template.addVulnerability(Stats.DAGGER_WPN_VULN, 1);

					_npcs.put(npcId, template);
					count++;
				}
			}
			catch (Exception e)
			{
				_log.log(Level.SEVERE, "NpcTable: Error loading NPC XML file: "
				        + f.getName(), e);
			}
		}

		if (dirPath.contains("customs"))
		{
			_log.config("NpcTable: Loaded " + count + " Npcs Customs.");
		}
		else
		{
			_log.config("NpcTable: Loaded " + count + " Npcs.");
		}
	}

	private void applySet(StatsSet npcDat, String name, String val)
	{
		if (name.equalsIgnoreCase("usingServerSideName"))
		{
			npcDat.set("serverSideName", Boolean.parseBoolean(val));
		}
		else if (name.equalsIgnoreCase("usingServerSideTitle"))
		{
			npcDat.set("serverSideTitle", Boolean.parseBoolean(val));
		}
		else if (name.equalsIgnoreCase("level"))
		{
			npcDat.set("level", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("radius"))
		{
			npcDat.set("collision_radius", Double.parseDouble(val));
		}
		else if (name.equalsIgnoreCase("height"))
		{
			npcDat.set("collision_height", Double.parseDouble(val));
		}
		else if (name.equalsIgnoreCase("rHand"))
		{
			npcDat.set("rhand", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("lHand"))
		{
			npcDat.set("lhand", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("type"))
		{
			npcDat.set("type", val);
		}
		else if (name.equalsIgnoreCase("exp"))
		{
			npcDat.set("rewardExp", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("sp"))
		{
			npcDat.set("rewardSp", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("hp"))
		{
			npcDat.set("baseHpMax", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("mp"))
		{
			npcDat.set("baseMpMax", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("hpRegen"))
		{
			npcDat.set("baseHpReg", Float.parseFloat(val));
		}
		else if (name.equalsIgnoreCase("mpRegen"))
		{
			npcDat.set("baseMpReg", Float.parseFloat(val));
		}
		else if (name.equalsIgnoreCase("pAtk"))
		{
			npcDat.set("basePAtk", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("pDef"))
		{
			npcDat.set("basePDef", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("mAtk"))
		{
			npcDat.set("baseMAtk", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("mDef"))
		{
			npcDat.set("baseMDef", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("crit"))
		{
			npcDat.set("baseCritRate", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("atkSpd"))
		{
			npcDat.set("basePAtkSpd", Integer.parseInt(val));
			npcDat.set("baseMAtkSpd", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("str"))
		{
			npcDat.set("baseSTR", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("int"))
		{
			npcDat.set("baseINT", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("dex"))
		{
			npcDat.set("baseDEX", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("wit"))
		{
			npcDat.set("baseWIT", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("con"))
		{
			npcDat.set("baseCON", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("men"))
		{
			npcDat.set("baseMEN", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("walkSpd"))
		{
			npcDat.set("baseWalkSpd", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("runSpd"))
		{
			npcDat.set("baseRunSpd", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("armor"))
		{
			npcDat.set("armor", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("attackRange"))
		{
			npcDat.set("baseAtkRange", Integer.parseInt(val));
		}
		else if (name.equalsIgnoreCase("dropHerbGroup"))
		{
			npcDat.set("drop_herbs", !val.equals("0"));
		}
	}

	private void applyAi(StatsSet npcDat, NamedNodeMap aiAttrs)
	{
		String aiType = getAttr(aiAttrs, "type", "DEFAULT");
		if (aiType.equalsIgnoreCase("ARCHER"))
		{
			npcDat.set("AI", "archer");
		}
		else if (aiType.equalsIgnoreCase("BALANCED"))
		{
			npcDat.set("AI", "balanced");
		}
		else if (aiType.equalsIgnoreCase("MAGE"))
		{
			npcDat.set("AI", "mage");
		}
		else
		{
			npcDat.set("AI", "fighter");
		}

		npcDat.set("ss", Integer.parseInt(getAttr(aiAttrs, "ssCount", "0")));
		npcDat.set("ssRate", Integer.parseInt(getAttr(aiAttrs, "ssRate", "0")));
		npcDat.set("bss", Integer.parseInt(getAttr(aiAttrs, "spsCount", "0")));
		npcDat.set("aggroRange", Integer.parseInt(getAttr(aiAttrs, "aggro", "0")));

		String clan = getAttr(aiAttrs, "clan", null);
		if (clan != null && !clan.equalsIgnoreCase("NULL"))
		{
			npcDat.set("factionId", clan);
		}

		npcDat.set("factionRange", Integer.parseInt(getAttr(aiAttrs, "clanRange", "0")));
	}

	private String getAttr(NamedNodeMap attrs, String name, String defaultValue)
	{
		Node node = attrs.getNamedItem(name);
		if (node == null)
		{
			return defaultValue;
		}

		String val = node.getNodeValue();
		if (val == null || val.equalsIgnoreCase("NULL"))
		{
			return defaultValue;
		}

		return val;
	}

	public void reloadNpc(int id)
	{
		try
		{
			L2NpcTemplate old = getTemplate(id);
			if (old == null)
			{
				return;
			}

			Map<Integer, L2Skill> skills = new ConcurrentHashMap<>();
			if (old.getSkills() != null)
			{
				skills.putAll(old.getSkills());
			}

			ArrayList<L2DropCategory> categories = new ArrayList<>();
			if (old.getDropData() != null)
			{
				categories.addAll(old.getDropData());
			}

			ClassId[] classIds = null;
			if (old.getTeachInfo() != null)
			{
				classIds = old.getTeachInfo().clone();
			}

			List<L2MinionData> minions = new ArrayList<>();
			if (old.getMinionData() != null)
			{
				minions.addAll(old.getMinionData());
			}

			L2NpcTemplate created = getTemplate(id);

			for (L2Skill skill : skills.values())
			{
				created.addSkill(skill);
			}

			if (classIds != null)
			{
				for (ClassId classId : classIds)
				{
					created.addTeachInfo(classId);
				}
			}

			for (L2MinionData minion : minions)
			{
				created.addRaidData(minion);
			}
		}
		catch (Exception e)
		{
			_log.warning("NPCTable: Could not reload data for NPC " + id + ": "
			        + e);
		}
	}

	public void reloadAllNpc()
	{
		restoreNpcData();
	}

	private static final Map<String, String> _statsetToXmlName = new HashMap<>();

	static
	{
		_statsetToXmlName.put("collision_radius", "radius");
		_statsetToXmlName.put("collision_height", "height");
		_statsetToXmlName.put("rhand", "rHand");
		_statsetToXmlName.put("lhand", "lHand");
		_statsetToXmlName.put("hpreg", "hpRegen");
		_statsetToXmlName.put("mpreg", "mpRegen");
		_statsetToXmlName.put("patk", "pAtk");
		_statsetToXmlName.put("pdef", "pDef");
		_statsetToXmlName.put("matk", "mAtk");
		_statsetToXmlName.put("mdef", "mDef");
		_statsetToXmlName.put("atkspd", "atkSpd");
		_statsetToXmlName.put("runspd", "runSpd");
		_statsetToXmlName.put("attackrange", "attackRange");
		_statsetToXmlName.put("baseHpMax", "hp");
		_statsetToXmlName.put("baseMpMax", "mp");
		_statsetToXmlName.put("baseHpReg", "hpRegen");
		_statsetToXmlName.put("baseMpReg", "mpRegen");
		_statsetToXmlName.put("basePAtk", "pAtk");
		_statsetToXmlName.put("basePDef", "pDef");
		_statsetToXmlName.put("baseMAtk", "mAtk");
		_statsetToXmlName.put("baseMDef", "mDef");
		_statsetToXmlName.put("basePAtkSpd", "atkSpd");
		_statsetToXmlName.put("baseMAtkSpd", "atkSpd");
		_statsetToXmlName.put("baseRunSpd", "runSpd");
		_statsetToXmlName.put("baseWalkSpd", "walkSpd");
		_statsetToXmlName.put("baseCritRate", "crit");
		_statsetToXmlName.put("baseSTR", "str");
		_statsetToXmlName.put("baseINT", "int");
		_statsetToXmlName.put("baseDEX", "dex");
		_statsetToXmlName.put("baseWIT", "wit");
		_statsetToXmlName.put("baseCON", "con");
		_statsetToXmlName.put("baseMEN", "men");
		_statsetToXmlName.put("serverSideName", "usingServerSideName");
		_statsetToXmlName.put("serverSideTitle", "usingServerSideTitle");
		_statsetToXmlName.put("rewardExp", "exp");
		_statsetToXmlName.put("rewardSp", "sp");
	}

	public void saveNpc(StatsSet npc)
	{
		int npcId = npc.getInteger("npcId");
		File xmlFile = getNpcXmlFile(npcId);
		if (xmlFile == null)
		{
			_log.warning("NPCTable: No XML file found for NPC " + npcId);
			return;
		}

		Map<String, Object> stats = npc.getSet();
		List<String> lines = new ArrayList<>();
		boolean inTarget = false;
		boolean found = false;

		try (BufferedReader reader = new BufferedReader(new FileReader(xmlFile)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (!inTarget
				        && line.matches(".*<npc\\s+.*id=\"" + npcId + "\".*"))
				{
					inTarget = true;
					found = true;

					line = updateNpcAttr(line, "name", stats);
					line = updateNpcAttr(line, "title", stats);
					line = updateNpcAttr(line, "idTemplate", stats);
				}
				else if (inTarget && line.contains("</npc>"))
				{
					inTarget = false;
				}
				else if (inTarget)
				{
					line = updateSetVal(line, stats);
				}

				lines.add(line);
			}
		}
		catch (IOException e)
		{
			_log.warning("NPCTable: Error reading XML for NPC " + npcId + ": "
			        + e);
			return;
		}

		if (!found)
		{
			_log.warning("NPCTable: NPC " + npcId + " not found in XML file "
			        + xmlFile.getName());
			return;
		}

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(xmlFile)))
		{
			for (String l : lines)
			{
				writer.write(l);
				writer.newLine();
			}
		}
		catch (IOException e)
		{
			_log.warning("NPCTable: Error writing XML for NPC " + npcId + ": "
			        + e);
		}
	}

	private String updateSetVal(String line, Map<String, Object> stats)
	{
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("<set\\s+name=\"([^\"]+)\"\\s+val=\"([^\"]*)\".*").matcher(line.trim());
		if (!m.matches())
		{
			return line;
		}

		String xmlName = m.group(1);

		String value = null;
		if (stats.containsKey(xmlName))
		{
			value = String.valueOf(stats.get(xmlName));
		}
		else
		{
			String mappedKey = null;
			for (Map.Entry<String, String> e : _statsetToXmlName.entrySet())
			{
				if (e.getValue().equalsIgnoreCase(xmlName))
				{
					mappedKey = e.getKey();
					break;
				}
			}
			if (mappedKey != null && stats.containsKey(mappedKey))
			{
				value = String.valueOf(stats.get(mappedKey));
			}
		}

		if (value == null)
		{
			return line;
		}

		return line.replaceFirst("val=\"[^\"]*\"", "val=\"" + value + "\"");
	}

	private String updateNpcAttr(String line, String attr,
	        Map<String, Object> stats)
	{
		if (!stats.containsKey(attr))
		{
			return line;
		}

		String value = String.valueOf(stats.get(attr));
		java.util.regex.Pattern p = java.util.regex.Pattern.compile(attr
		        + "=\"[^\"]*\"");
		if (p.matcher(line).find())
		{
			return line.replaceFirst(attr + "=\"[^\"]*\"", attr + "=\"" + value
			        + "\"");
		}
		else
		{
			return line;
		}
	}

	private File getNpcXmlFile(int npcId)
	{
		String[] dirs = { "./data/xml/npcs", "./data/xml/npcs/customs" };
		int range = (npcId / 1000) * 1000;
		String filename = range + "-" + (range + 999) + ".xml";

		for (String dir : dirs)
		{
			File f = new File(Config.DATAPACK_ROOT, dir + "/" + filename);
			if (f.exists())
			{
				return f;
			}
		}
		return null;
	}

	public boolean isInitialized()
	{
		return _initialized;
	}

	public void replaceTemplate(L2NpcTemplate npc)
	{
		_npcs.put(npc.npcId, npc);
	}

	public L2NpcTemplate getTemplate(int id)
	{
		return _npcs.get(id);
	}

	public L2NpcTemplate getTemplateByName(String name)
	{
		for (L2NpcTemplate npcTemplate : _npcs.values())
		{
			if (npcTemplate.name.equalsIgnoreCase(name))
			{
				return npcTemplate;
			}
		}

		return null;
	}

	public L2NpcTemplate[] getAllOfLevel(int lvl)
	{
		List<L2NpcTemplate> list = new ArrayList<>();

		for (L2NpcTemplate t : _npcs.values())
		{
			if (t.level == lvl)
			{
				list.add(t);
			}
		}

		return list.toArray(new L2NpcTemplate[list.size()]);
	}

	public L2NpcTemplate[] getAllMonstersOfLevel(int lvl)
	{
		List<L2NpcTemplate> list = new ArrayList<>();

		for (L2NpcTemplate t : _npcs.values())
		{
			if (t.level == lvl && "L2Monster".equals(t.type))
			{
				list.add(t);
			}
		}

		return list.toArray(new L2NpcTemplate[list.size()]);
	}

	public L2NpcTemplate[] getAllNpcStartingWith(String letter)
	{
		List<L2NpcTemplate> list = new ArrayList<>();

		for (L2NpcTemplate t : _npcs.values())
		{
			if (t.name.startsWith(letter) && "L2Npc".equals(t.type))
			{
				list.add(t);
			}
		}

		return list.toArray(new L2NpcTemplate[list.size()]);
	}

	public Set<Integer> getAllNpcOfClassType(String classType)
	{
		return null;
	}

	public Set<Integer> getAllNpcOfL2jClass(Class<?> clazz)
	{
		return null;
	}

	public Set<Integer> getAllNpcOfAiType(String aiType)
	{
		return null;
	}
}