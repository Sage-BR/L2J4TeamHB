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
package net.sf.l2j.gameserver.skills;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import net.sf.l2j.gameserver.Item;
import net.sf.l2j.gameserver.templates.L2Armor;
import net.sf.l2j.gameserver.templates.L2ArmorType;
import net.sf.l2j.gameserver.templates.L2EtcItem;
import net.sf.l2j.gameserver.templates.L2EtcItemType;
import net.sf.l2j.gameserver.templates.L2Item;
import net.sf.l2j.gameserver.templates.L2Weapon;
import net.sf.l2j.gameserver.templates.L2WeaponType;
import net.sf.l2j.gameserver.templates.StatsSet;

/**
 * @author mkizub
 *
 *         TODO To change the template for this generated type comment go to
 *         Window - Preferences - Java - Code Style - Code Templates
 */
final class DocumentItem extends DocumentBase
{
	private Item _currentItem = null;

	private List<L2Item> _itemsInFile = new ArrayList<>();

	private Map<Integer, Item> _itemData = new ConcurrentHashMap<>();

	/**
	 * @param armorData
	 * @param f
	 */
	public DocumentItem(Map<Integer, Item> pItemData, File file)
	{
		super(file);
		_itemData = pItemData;
	}

	/**
	 * @param item
	 */
	private void setCurrentItem(Item item)
	{
		_currentItem = item;
	}

	@Override
	protected StatsSet getStatsSet()
	{
		return _currentItem.set;
	}

	@Override
	protected String getTableValue(String name)
	{
		return _tables.get(name)[_currentItem.currentLevel];
	}

	@Override
	protected String getTableValue(String name, int idx)
	{
		return _tables.get(name)[idx - 1];
	}

	@Override
	protected void parseDocument(Document doc)
	{
		for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if ("list".equalsIgnoreCase(n.getNodeName()))
			{

				for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
				{
					if ("item".equalsIgnoreCase(d.getNodeName()))
					{
						setCurrentItem(new Item());
						parseItem(d);
						_itemsInFile.add(_currentItem.item);
						resetTable();
					}
				}
			}
			else if ("item".equalsIgnoreCase(n.getNodeName()))
			{
				setCurrentItem(new Item());
				parseItem(n);
				_itemsInFile.add(_currentItem.item);
			}
		}
	}

	protected void parseItem(Node n)
	{
		int itemId = Integer.parseInt(n.getAttributes().getNamedItem("id").getNodeValue());
		String itemName = n.getAttributes().getNamedItem("name").getNodeValue();

		_currentItem.id = itemId;
		_currentItem.name = itemName;

		Item item;
		StatsSet baseSet;
		if ((item = _itemData.get(_currentItem.id)) == null)
		{
			baseSet = new StatsSet();
			baseSet.set("item_id", itemId);
			baseSet.set("name", itemName);
		}
		else
		{
			baseSet = item.set;
			_currentItem.type = item.type;
		}
		_currentItem.set = baseSet;

		Node first = n.getFirstChild();
		for (n = first; n != null; n = n.getNextSibling())
		{
			if ("table".equalsIgnoreCase(n.getNodeName()))
			{
				parseTable(n);
			}
		}
		for (n = first; n != null; n = n.getNextSibling())
		{
			if ("set".equalsIgnoreCase(n.getNodeName()))
			{
				parseBeanSet(n, baseSet, 1);
			}
		}
		// Set defaults for L2Item fields missing in partially-migrated items
		if (baseSet.getString("type1", null) == null)
		{
			baseSet.set("type1", 0);
		}
		if (baseSet.getString("type2", null) == null)
		{
			baseSet.set("type2", 0);
		}
		if (baseSet.getString("weight", null) == null)
		{
			baseSet.set("weight", 0);
		}
		if (baseSet.getString("material", null) == null)
		{
			baseSet.set("material", 0);
		}
		if (baseSet.getString("duration", null) == null)
		{
			baseSet.set("duration", -1);
		}
		if (baseSet.getString("bodypart", null) == null)
		{
			baseSet.set("bodypart", 0);
		}
		if (baseSet.getString("price", null) == null)
		{
			baseSet.set("price", 0);
		}
		if (baseSet.getString("crystallizable", null) == null)
		{
			baseSet.set("crystallizable", false);
		}
		// Set defaults for L2Armor fields missing in partially-migrated items
		if (baseSet.getString("avoid_modify", null) == null)
		{
			baseSet.set("avoid_modify", 0);
		}
		if (baseSet.getString("p_def", null) == null)
		{
			baseSet.set("p_def", 0);
		}
		if (baseSet.getString("m_def", null) == null)
		{
			baseSet.set("m_def", 0);
		}
		if (baseSet.getString("skill", null) == null)
		{
			baseSet.set("skill", "0-0;");
		}
		// Set defaults for L2Weapon fields missing in partially-migrated items
		if (baseSet.getString("soulshots", null) == null)
		{
			baseSet.set("soulshots", 0);
		}
		if (baseSet.getString("spiritshots", null) == null)
		{
			baseSet.set("spiritshots", 0);
		}
		if (baseSet.getString("p_dam", null) == null)
		{
			baseSet.set("p_dam", 0);
		}
		if (baseSet.getString("rnd_dam", null) == null)
		{
			baseSet.set("rnd_dam", 0);
		}
		if (baseSet.getString("critical", null) == null)
		{
			baseSet.set("critical", 0);
		}
		if (baseSet.getString("hit_modify", null) == null)
		{
			baseSet.set("hit_modify", 0.0);
		}
		if (baseSet.getString("shield_def", null) == null)
		{
			baseSet.set("shield_def", 0);
		}
		if (baseSet.getString("shield_def_rate", null) == null)
		{
			baseSet.set("shield_def_rate", 0.0);
		}
		if (baseSet.getString("atk_speed", null) == null)
		{
			baseSet.set("atk_speed", 0);
		}
		if (baseSet.getString("mp_consume", null) == null)
		{
			baseSet.set("mp_consume", 0);
		}
		if (baseSet.getString("m_dam", null) == null)
		{
			baseSet.set("m_dam", 0);
		}
		if (baseSet.getString("enchant4_skill_id", null) == null)
		{
			baseSet.set("enchant4_skill_id", 0);
		}
		if (baseSet.getString("enchant4_skill_lvl", null) == null)
		{
			baseSet.set("enchant4_skill_lvl", 0);
		}
		if (baseSet.getString("onCast_skill_id", null) == null)
		{
			baseSet.set("onCast_skill_id", 0);
		}
		if (baseSet.getString("onCast_skill_lvl", null) == null)
		{
			baseSet.set("onCast_skill_lvl", 0);
		}
		if (baseSet.getString("onCast_skill_chance", null) == null)
		{
			baseSet.set("onCast_skill_chance", 0);
		}
		if (baseSet.getString("onCrit_skill_id", null) == null)
		{
			baseSet.set("onCrit_skill_id", 0);
		}
		if (baseSet.getString("onCrit_skill_lvl", null) == null)
		{
			baseSet.set("onCrit_skill_lvl", 0);
		}
		if (baseSet.getString("onCrit_skill_chance", null) == null)
		{
			baseSet.set("onCrit_skill_chance", 0);
		}
		if (baseSet.getString("change_weaponId", null) == null)
		{
			baseSet.set("change_weaponId", 0);
		}
		if (_currentItem.type == null)
		{
			// Try weapon type first
			String weaponType = baseSet.getString("weapon_type", null);
			if (weaponType != null)
			{
				if (weaponType.equalsIgnoreCase("sword"))
				{
					_currentItem.type = L2WeaponType.SWORD;
				}
				else if (weaponType.equalsIgnoreCase("blunt"))
				{
					_currentItem.type = L2WeaponType.BLUNT;
				}
				else if (weaponType.equalsIgnoreCase("bigsword"))
				{
					_currentItem.type = L2WeaponType.BIGSWORD;
				}
				else if (weaponType.equalsIgnoreCase("bigblunt"))
				{
					_currentItem.type = L2WeaponType.BIGBLUNT;
				}
				else if (weaponType.equalsIgnoreCase("dagger"))
				{
					_currentItem.type = L2WeaponType.DAGGER;
				}
				else if (weaponType.equalsIgnoreCase("bow"))
				{
					_currentItem.type = L2WeaponType.BOW;
				}
				else if (weaponType.equalsIgnoreCase("pole"))
				{
					_currentItem.type = L2WeaponType.POLE;
				}
				else if (weaponType.equalsIgnoreCase("dual"))
				{
					_currentItem.type = L2WeaponType.DUAL;
				}
				else if (weaponType.equalsIgnoreCase("dualfist"))
				{
					_currentItem.type = L2WeaponType.DUALFIST;
				}
				else if (weaponType.equalsIgnoreCase("fist"))
				{
					_currentItem.type = L2WeaponType.FIST;
				}
				else if (weaponType.equalsIgnoreCase("etc"))
				{
					_currentItem.type = L2WeaponType.ETC;
				}
				else if (weaponType.equalsIgnoreCase("pet"))
				{
					_currentItem.type = L2WeaponType.PET;
				}
				else if (weaponType.equalsIgnoreCase("rod"))
				{
					_currentItem.type = L2WeaponType.ROD;
				}
				else if (weaponType.equalsIgnoreCase("crossbow"))
				{
					_currentItem.type = L2WeaponType.CROSSBOW;
				}
				else if (weaponType.equalsIgnoreCase("rapier"))
				{
					_currentItem.type = L2WeaponType.RAPIER;
				}
				else if (weaponType.equalsIgnoreCase("ancient"))
				{
					_currentItem.type = L2WeaponType.ANCIENT_SWORD;
				}
				else
				{
					_currentItem.type = L2WeaponType.NONE;
				}
			}
			else
			{
				String armorType = baseSet.getString("armor_type", null);
				if (armorType != null)
				{
					if (armorType.equalsIgnoreCase("light"))
					{
						_currentItem.type = L2ArmorType.LIGHT;
					}
					else if (armorType.equalsIgnoreCase("heavy"))
					{
						_currentItem.type = L2ArmorType.HEAVY;
					}
					else if (armorType.equalsIgnoreCase("magic"))
					{
						_currentItem.type = L2ArmorType.MAGIC;
					}
					else if (armorType.equalsIgnoreCase("pet"))
					{
						_currentItem.type = L2ArmorType.PET;
					}
					else
					{
						_currentItem.type = L2ArmorType.NONE;
					}
				}
				else
				{
					String itemType = baseSet.getString("item_type", null);
					if (itemType != null)
					{
						if (itemType.equalsIgnoreCase("castle_guard"))
						{
							_currentItem.type = L2EtcItemType.SCROLL;
						}
						else if (itemType.equalsIgnoreCase("material"))
						{
							_currentItem.type = L2EtcItemType.MATERIAL;
						}
						else if (itemType.equalsIgnoreCase("pet_collar"))
						{
							_currentItem.type = L2EtcItemType.PET_COLLAR;
						}
						else if (itemType.equalsIgnoreCase("potion"))
						{
							_currentItem.type = L2EtcItemType.POTION;
						}
						else if (itemType.equalsIgnoreCase("recipe"))
						{
							_currentItem.type = L2EtcItemType.RECEIPE;
						}
						else if (itemType.equalsIgnoreCase("scroll"))
						{
							_currentItem.type = L2EtcItemType.SCROLL;
						}
						else if (itemType.equalsIgnoreCase("seed"))
						{
							_currentItem.type = L2EtcItemType.SEED;
						}
						else if (itemType.equalsIgnoreCase("shot"))
						{
							_currentItem.type = L2EtcItemType.SHOT;
						}
						else if (itemType.equalsIgnoreCase("spellbook"))
						{
							_currentItem.type = L2EtcItemType.SPELLBOOK;
						}
						else if (itemType.equalsIgnoreCase("herb"))
						{
							_currentItem.type = L2EtcItemType.HERB;
						}
						else if (itemType.equalsIgnoreCase("arrow"))
						{
							_currentItem.type = L2EtcItemType.ARROW;
						}
						else if (itemType.equalsIgnoreCase("bolt"))
						{
							_currentItem.type = L2EtcItemType.BOLT;
						}
						else if (itemType.equalsIgnoreCase("quest"))
						{
							_currentItem.type = L2EtcItemType.QUEST;
						}
						else if (itemType.equalsIgnoreCase("lure"))
						{
							_currentItem.type = L2EtcItemType.OTHER;
						}
						else
						{
							_currentItem.type = L2EtcItemType.OTHER;
						}
					}
					else
					{
						_currentItem.type = L2EtcItemType.OTHER;
					}
				}
			}
		}
		for (n = first; n != null; n = n.getNextSibling())
		{
			if ("for".equalsIgnoreCase(n.getNodeName()))
			{
				makeItem();
				parseTemplate(n, _currentItem.item);
			}
		}
		if (_currentItem.item == null)
		{
			makeItem();
		}
	}

	private void makeItem()
	{
		if (_currentItem.item != null)
		{
			return;
		}
		if (_currentItem.type instanceof L2ArmorType)
		{
			_currentItem.item = new L2Armor((L2ArmorType) _currentItem.type, _currentItem.set);
		}
		else if (_currentItem.type instanceof L2WeaponType)
		{
			_currentItem.item = new L2Weapon((L2WeaponType) _currentItem.type, _currentItem.set);
		}
		else if (_currentItem.type instanceof L2EtcItemType)
		{
			_currentItem.item = new L2EtcItem((L2EtcItemType) _currentItem.type, _currentItem.set);
		}
		else
		{
			throw new Error("Unknown item type " + _currentItem.type);
		}
	}

	/**
	 * @return
	 */
	public List<L2Item> getItemList()
	{
		return _itemsInFile;
	}
}
