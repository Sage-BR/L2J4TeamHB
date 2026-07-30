/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package net.sf.l2j.gameserver.model.actor.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import Dev.SpecialMods.MathUtil;
import net.sf.l2j.Config;
import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.datatables.sql.BufferTable;
import net.sf.l2j.gameserver.model.L2Character;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.L2Summon;
import net.sf.l2j.gameserver.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.serverpackets.MyTargetSelected;
import net.sf.l2j.gameserver.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.serverpackets.SocialAction;
import net.sf.l2j.gameserver.serverpackets.ValidateLocation;
import net.sf.l2j.gameserver.templates.L2NpcTemplate;
import net.sf.l2j.util.Rnd;
import net.sf.l2j.util.StringUtil;

public class L2BufferInstance extends L2NpcInstance
{
	private static final int PAGE_LIMIT1 = 6;

	public L2BufferInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}

	@Override
	public void onAction(L2PcInstance player)
	{
		if (this != player.getTarget())
		{
			player.setTarget(this);
			player.sendPacket(new MyTargetSelected(getObjectId(), player.getLevel()
			        - getLevel()));
			player.sendPacket(new ValidateLocation(this));
		}
		else if (isInsideRadius(player, INTERACTION_DISTANCE, false, false))
		{

			SocialAction sa = new SocialAction(getObjectId(), Rnd.get(8));
			broadcastPacket(sa);
			showSubBufferWindow(player);
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
		else
		{
			player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
	}

	@Override
	public void onBypassFeedback(L2PcInstance player, String command)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		String currentCommand = st.nextToken();

		if (currentCommand.startsWith("menu"))
		{
			NpcHtmlMessage html = new NpcHtmlMessage(1);
			html.setFile(getHtmlPath(getNpcId(), 0));
			html.replace("%objectId%", String.valueOf(getObjectId()));
			player.sendPacket(html);
		}
		else if (currentCommand.equalsIgnoreCase("getbuff"))
		{
			int buffid = 0;
			int bufflevel = 1;
			String nextWindow = null;

			if (st.countTokens() == 3)
			{
				buffid = Integer.valueOf(st.nextToken());
				bufflevel = Integer.valueOf(st.nextToken());
				nextWindow = st.nextToken();
			}
			else if (st.countTokens() == 1)
			{
				buffid = Integer.valueOf(st.nextToken());
			}

			if (buffid != 0)
			{
				player.broadcastPacket(new MagicSkillUse(this, player, buffid, bufflevel, 5, 0));

				SkillTable.getInstance().getInfo(buffid, bufflevel).getEffects(this, player);
				showSubBufferWindow(player);
				showChatWindow(player, nextWindow);
			}
		}
		else if (currentCommand.equalsIgnoreCase("vipbuff"))
		{
			int buffid = 0;
			int bufflevel = 1;
			String nextWindow = null;

			if (st.countTokens() == 3)
			{
				buffid = Integer.valueOf(st.nextToken());
				bufflevel = Integer.valueOf(st.nextToken());
				nextWindow = st.nextToken();
			}
			else if (st.countTokens() == 1)
			{
				buffid = Integer.valueOf(st.nextToken());
			}

			if (!player.isGM())
			{
				player.sendMessage("You must be vip to get this buff.");
				showChatWindow(player, nextWindow);
				return;
			}

			if (buffid != 0)
			{
				player.broadcastPacket(new MagicSkillUse(this, player, buffid, bufflevel, 5, 0));
				SkillTable.getInstance().getInfo(buffid, bufflevel).getEffects(this, player);
				showSubBufferWindow(player);
				showChatWindow(player, nextWindow);
			}
		}
		else if (currentCommand.equalsIgnoreCase("paybuff"))
		{
			int buffid = 0;
			int bufflevel = 1;
			int buffPrice = 0;
			String nextWindow = null;

			if (st.countTokens() == 4)
			{
				buffid = Integer.valueOf(st.nextToken());
				bufflevel = Integer.valueOf(st.nextToken());
				buffPrice = Integer.valueOf(st.nextToken());
				nextWindow = st.nextToken();
			}
			else if (st.countTokens() == 3)
			{
				buffid = Integer.valueOf(st.nextToken());
				bufflevel = Integer.valueOf(st.nextToken());
				nextWindow = st.nextToken();
			}
			else if (st.countTokens() == 1)
			{
				buffid = Integer.valueOf(st.nextToken());
			}

			if (buffid != 0
			        && player.destroyItemByItemId("buff", Config.COIN_ITEMID, buffPrice, player.getLastFolkNPC(), true))
			{
				player.broadcastPacket(new MagicSkillUse(this, player, buffid, bufflevel, 5, 0));
				SkillTable.getInstance().getInfo(buffid, bufflevel).getEffects(this, player);
				showChatWindow(player, nextWindow);
			}
			else
			{
				showSubBufferWindow(player);
			}
		}
		else if (currentCommand.equalsIgnoreCase("fbuff"))
		{
			player.stopAllEffects();

			for (Integer skillid : Config.FIGHTER_BUFF_LIST)
			{
				L2Skill skill = SkillTable.getInstance().getInfo(skillid, SkillTable.getInstance().getMaxLevel(skillid, 0));
				if (skill != null)
				{
					skill.getEffects(player, player);
				}
			}

			player.setCurrentHpMp(player.getMaxHp(), player.getMaxMp());
			player.setCurrentCp(player.getMaxCp());
			player.sendMessage("You get a Fighter-buff complect.");

			showSubBufferWindow(player);
		}
		else if (currentCommand.equalsIgnoreCase("mbuff"))
		{
			player.stopAllEffects();

			for (Integer skillid : Config.MAGE_BUFF_LIST)
			{
				L2Skill skill = SkillTable.getInstance().getInfo(skillid, SkillTable.getInstance().getMaxLevel(skillid, 0));
				if (skill != null)
				{
					skill.getEffects(player, player);
				}
			}

			player.setCurrentHpMp(player.getMaxHp(), player.getMaxMp());
			player.setCurrentCp(player.getMaxCp());
			player.sendMessage("You get a Mage-buff complect.");

			showSubBufferWindow(player);
		}
		else if (currentCommand.startsWith("cleanup"))
		{
			player.broadcastPacket(new MagicSkillUse(this, player, 1056, 12, 100, 0));
			player.stopAllEffects();

			final L2Summon summon = player.getPet();
			if (summon != null)
			{
				summon.stopAllEffects();
			}
			NpcHtmlMessage html = new NpcHtmlMessage(1);
			html.setFile(getHtmlPath(getNpcId(), 0));
			html.replace("%objectId%", String.valueOf(getObjectId()));
			player.sendPacket(html);
		}
		else if (currentCommand.startsWith("heal"))
		{
			player.broadcastPacket(new MagicSkillUse(this, player, 1218, 33, 100, 0));

			player.setCurrentHpMp(player.getMaxHp(), player.getMaxMp());
			player.setCurrentCp(player.getMaxCp());

			final L2Summon summon = player.getPet();
			if (summon != null)
			{
				summon.setCurrentHpMp(summon.getMaxHp(), summon.getMaxMp());
			}

			NpcHtmlMessage html = new NpcHtmlMessage(1);
			html.setFile(getHtmlPath(getNpcId(), 0));
			html.replace("%objectId%", String.valueOf(getObjectId()));
			player.sendPacket(html);
		}
		else if (currentCommand.startsWith("support"))
		{
			try
			{
				showGiveBuffsWindow(player);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		else if (currentCommand.startsWith("givebuffs"))
		{
			try
			{
				final String schemeName = st.nextToken();
				// final int cost = Integer.parseInt(st.nextToken());

				L2Character target = null;
				if (st.hasMoreTokens())
				{
					final String targetType = st.nextToken();
					if (targetType != null
					        && targetType.equalsIgnoreCase("pet"))
					{
						target = player.getPet();
					}
				}
				else
				{
					target = player;
				}

				if (target == null)
				{
					player.sendMessage("You don't have a pet.");
				}
				else
				{ // else if (cost == 0 || player.reduceAdena("NPC Buffer", // cost, this, true)) // {
					// else if (cost == 0 || player.reduceAdena("NPC Buffer",
										// cost, this, true))
										// {
										for (int skillId : BufferTable.getInstance().getScheme(player.getObjectId(), schemeName))
										{
											SkillTable.getInstance().getInfo(skillId, SkillTable.getInstance().getMaxLevel(skillId, 0)).getEffects(this, target);
									// }
										}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		else if (currentCommand.startsWith("editschemes"))
		{
			showEditSchemeWindow(player, st.nextToken(), st.nextToken(), Integer.parseInt(st.nextToken()));
		}
		else if (currentCommand.startsWith("skill"))
		{
			final String groupType = st.nextToken();
			final String schemeName = st.nextToken();

			final int skillId = Integer.parseInt(st.nextToken());
			final int page = Integer.parseInt(st.nextToken());

			final List<Integer> skills = BufferTable.getInstance().getScheme(player.getObjectId(), schemeName);

			if (currentCommand.startsWith("skillselect")
			        && !schemeName.equalsIgnoreCase("none"))
			{
				if (skills.size() < player.getMaxBuffCount())
				{
					skills.add(skillId);
				}
				else
				{
					player.sendMessage("This scheme has reached the maximum amount of buffs.");
				}
			}
			else if (currentCommand.startsWith("skillunselect"))
			{
				skills.remove(Integer.valueOf(skillId));
			}

			showEditSchemeWindow(player, groupType, schemeName, page);
		}
		else if (currentCommand.startsWith("subbuffer"))
		{
			showSubBufferWindow(player);
		}
		else if (currentCommand.startsWith("createscheme"))
		{
			try
			{
				final String schemeName = st.nextToken();
				if (schemeName.length() > 14)
				{
					player.sendMessage("Scheme's name must contain up to 14 chars. Spaces are trimmed.");
					return;
				}

				final Map<String, ArrayList<Integer>> schemes = BufferTable.getInstance().getPlayerSchemes(player.getObjectId());
				if (schemes != null)
				{
					if (schemes.size() == Config.BUFFER_MAX_SCHEMES)
					{
						player.sendMessage("Maximum schemes amount is already reached.");
						return;
					}

					if (schemes.containsKey(schemeName))
					{
						player.sendMessage("The scheme name already exists.");
						return;
					}
				}

				BufferTable.getInstance().setScheme(player.getObjectId(), schemeName.trim(), new ArrayList<>());
				showGiveBuffsWindow(player);
			}
			catch (Exception e)
			{
				player.sendMessage("Scheme's name must contain up to 14 chars. Spaces are trimmed.");
			}
		}
		else if (currentCommand.startsWith("deletescheme"))
		{
			try
			{
				final String schemeName = st.nextToken();
				final Map<String, ArrayList<Integer>> schemes = BufferTable.getInstance().getPlayerSchemes(player.getObjectId());

				if (schemes != null && schemes.containsKey(schemeName))
				{
					schemes.remove(schemeName);
				}
			}
			catch (Exception e)
			{
				player.sendMessage("This scheme name is invalid.");
			}
			showGiveBuffsWindow(player);
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
	}

	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String filename = "";
		if (val == 0)
		{
			filename = "" + npcId;
		}
		else
		{
			filename = npcId + "-" + val;
		}

		return "data/html/mods/buffer/" + filename + ".htm";
	}

	private void showSubBufferWindow(L2PcInstance player)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(1);

		html.setFile(getHtmlPath(getNpcId(), 0));
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
	}

	/**
	 * Sends an html packet to player with Give Buffs menu info for player and
	 * pet, depending on targetType parameter {player, pet}
	 *
	 * @param player
	 *            : The player to make checks on.
	 */
	private void showGiveBuffsWindow(L2PcInstance player)
	{
		final StringBuilder sb = new StringBuilder(200);

		final Map<String, ArrayList<Integer>> schemes = BufferTable.getInstance().getPlayerSchemes(player.getObjectId());
		if (schemes == null || schemes.isEmpty())
		{
			sb.append("<font color=\"LEVEL\">You haven't defined any scheme.</font>");
		}
		else
		{
			for (Map.Entry<String, ArrayList<Integer>> scheme : schemes.entrySet())
			{
				StringUtil.append(sb, "<font color=\"LEVEL\">", scheme.getKey(), " [", scheme.getValue().size(), " / ", player.getMaxBuffCount(), "]</font><br1>");
				StringUtil.append(sb, "<a action=\"bypass -h npc_%objectId%_givebuffs ", scheme.getKey(), "\">Use on Me</a>&nbsp;|&nbsp;");
				StringUtil.append(sb, "<a action=\"bypass -h npc_%objectId%_givebuffs ", scheme.getKey(), " pet\">Use on Pet</a>&nbsp;|&nbsp;");
				StringUtil.append(sb, "<a action=\"bypass -h npc_%objectId%_editschemes Buffs ", scheme.getKey(), " 1\">Edit</a>&nbsp;|&nbsp;");
				StringUtil.append(sb, "<a action=\"bypass -h npc_%objectId%_deletescheme ", scheme.getKey(), "\">Delete</a><br>");
			}
		}

		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(getHtmlPath(getNpcId(), 1));
		html.replace("%schemes%", sb.toString());
		html.replace("%max_schemes%", Config.BUFFER_MAX_SCHEMES);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
	}

	/**
	 * This sends an html packet to player with Edit Scheme Menu info. This
	 * allows player to edit each created scheme (add/delete skills)
	 *
	 * @param player
	 *            : The player to make checks on.
	 * @param groupType
	 *            : The group of skills to select.
	 * @param schemeName
	 *            : The scheme to make check.
	 * @param page
	 *            The page.
	 */
	private void showEditSchemeWindow(L2PcInstance player, String groupType,
	        String schemeName, int page)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		final List<Integer> schemeSkills = BufferTable.getInstance().getScheme(player.getObjectId(), schemeName);

		html.setFile(getHtmlPath(getNpcId(), 2));
		html.replace("%schemename%", schemeName);
		html.replace("%count%", schemeSkills.size() + " / "
		        + player.getMaxBuffCount());
		html.replace("%typesframe%", getTypesFrame(groupType, schemeName));
		html.replace("%skilllistframe%", getGroupSkillList(player, groupType, schemeName, page));
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
	}

	/**
	 * @param player
	 *            : The player to make checks on.
	 * @param groupType
	 *            : The group of skills to select.
	 * @param schemeName
	 *            : The scheme to make check.
	 * @param page
	 *            The page.
	 * @return a String representing skills available to selection for a given
	 *         groupType.
	 */
	private String getGroupSkillList(L2PcInstance player, String groupType,
	        String schemeName, int page)
	{
		// Retrieve the entire skills list based on group type.
		List<Integer> skills = BufferTable.getInstance().getSkillsIdsByType(groupType);
		if (skills.isEmpty())
		{
			return "That group doesn't contain any skills.";
		}

		// Calculate page number.
		final int max = MathUtil.countPagesNumber(skills.size(), PAGE_LIMIT1);
		if (page > max)
		{
			page = max;
		}

		// Cut skills list up to page number.
		skills = skills.subList((page - 1)
		        * PAGE_LIMIT1, Math.min(page * PAGE_LIMIT1, skills.size()));

		final List<Integer> schemeSkills = BufferTable.getInstance().getScheme(player.getObjectId(), schemeName);
		final StringBuilder sb = new StringBuilder(skills.size() * 150);

		int row = 0;
		for (int skillId : skills)
		{
			sb.append(((row
			        % 2) == 0 ? "<table width=\"280\" bgcolor=\"000000\"><tr>" : "<table width=\"280\"><tr>"));

			if (skillId < 100)
			{
				if (schemeSkills.contains(skillId))
				{
					StringUtil.append(sb, "<td height=40 width=40><img src=\"icon.skill00", skillId, "\" width=32 height=32></td><td width=190>", SkillTable.getInstance().getInfo(skillId, 1).getName(), "<br1><font color=\"B09878\">", BufferTable.getInstance().getAvailableBuff(skillId).getDescription(), "</font></td><td><button action=\"bypass -h npc_%objectId%_skillunselect ", groupType, " ", schemeName, " ", skillId, " ", page, "\" width=32 height=32 back=\"L2UI_CH3.mapbutton_zoomout2\" fore=\"L2UI_CH3.mapbutton_zoomout1\"></td>");
				}
				else
				{
					StringUtil.append(sb, "<td height=40 width=40><img src=\"icon.skill00", skillId, "\" width=32 height=32></td><td width=190>", SkillTable.getInstance().getInfo(skillId, 1).getName(), "<br1><font color=\"B09878\">", BufferTable.getInstance().getAvailableBuff(skillId).getDescription(), "</font></td><td><button action=\"bypass -h npc_%objectId%_skillselect ", groupType, " ", schemeName, " ", skillId, " ", page, "\" width=32 height=32 back=\"L2UI_CH3.mapbutton_zoomin2\" fore=\"L2UI_CH3.mapbutton_zoomin1\"></td>");
				}
			}
			else if (skillId < 1000)
			{
				if (schemeSkills.contains(skillId))
				{
					StringUtil.append(sb, "<td height=40 width=40><img src=\"icon.skill0", skillId, "\" width=32 height=32></td><td width=190>", SkillTable.getInstance().getInfo(skillId, 1).getName(), "<br1><font color=\"B09878\">", BufferTable.getInstance().getAvailableBuff(skillId).getDescription(), "</font></td><td><button action=\"bypass -h npc_%objectId%_skillunselect ", groupType, " ", schemeName, " ", skillId, " ", page, "\" width=32 height=32 back=\"L2UI_CH3.mapbutton_zoomout2\" fore=\"L2UI_CH3.mapbutton_zoomout1\"></td>");
				}
				else
				{
					StringUtil.append(sb, "<td height=40 width=40><img src=\"icon.skill0", skillId, "\" width=32 height=32></td><td width=190>", SkillTable.getInstance().getInfo(skillId, 1).getName(), "<br1><font color=\"B09878\">", BufferTable.getInstance().getAvailableBuff(skillId).getDescription(), "</font></td><td><button action=\"bypass -h npc_%objectId%_skillselect ", groupType, " ", schemeName, " ", skillId, " ", page, "\" width=32 height=32 back=\"L2UI_CH3.mapbutton_zoomin2\" fore=\"L2UI_CH3.mapbutton_zoomin1\"></td>");
				}
			}
			else
			{
				if (schemeSkills.contains(skillId))
				{
					StringUtil.append(sb, "<td height=40 width=40><img src=\"icon.skill", skillId, "\" width=32 height=32></td><td width=190>", SkillTable.getInstance().getInfo(skillId, 1).getName(), "<br1><font color=\"B09878\">", BufferTable.getInstance().getAvailableBuff(skillId).getDescription(), "</font></td><td><button action=\"bypass -h npc_%objectId%_skillunselect ", groupType, " ", schemeName, " ", skillId, " ", page, "\" width=32 height=32 back=\"L2UI_CH3.mapbutton_zoomout2\" fore=\"L2UI_CH3.mapbutton_zoomout1\"></td>");
				}
				else
				{
					StringUtil.append(sb, "<td height=40 width=40><img src=\"icon.skill", skillId, "\" width=32 height=32></td><td width=190>", SkillTable.getInstance().getInfo(skillId, 1).getName(), "<br1><font color=\"B09878\">", BufferTable.getInstance().getAvailableBuff(skillId).getDescription(), "</font></td><td><button action=\"bypass -h npc_%objectId%_skillselect ", groupType, " ", schemeName, " ", skillId, " ", page, "\" width=32 height=32 back=\"L2UI_CH3.mapbutton_zoomin2\" fore=\"L2UI_CH3.mapbutton_zoomin1\"></td>");
				}
			}

			sb.append("</tr></table><img src=\"L2UI.SquareGray\" width=277 height=1>");
			row++;
		}

		// Build page footer.
		sb.append("<br><img src=\"L2UI.SquareGray\" width=277 height=1><table width=\"100%\" bgcolor=000000><tr>");

		if (page > 1)
		{
			StringUtil.append(sb, "<td align=left width=70><a action=\"bypass -h npc_"
			        + getObjectId()
			        + "_editschemes ", groupType, " ", schemeName, " ", page
			                - 1, "\">Previous</a></td>");
		}
		else
		{
			StringUtil.append(sb, "<td align=left width=70>Previous</td>");
		}

		StringUtil.append(sb, "<td align=center width=100>Page ", page, "</td>");

		if (page < max)
		{
			StringUtil.append(sb, "<td align=right width=70><a action=\"bypass -h npc_"
			        + getObjectId()
			        + "_editschemes ", groupType, " ", schemeName, " ", page
			                + 1, "\">Next</a></td>");
		}
		else
		{
			StringUtil.append(sb, "<td align=right width=70>Next</td>");
		}

		sb.append("</tr></table><img src=\"L2UI.SquareGray\" width=277 height=1>");

		return sb.toString();
	}

	/**
	 * @param groupType
	 *            : The group of skills to select.
	 * @param schemeName
	 *            : The scheme to make check.
	 * @return a string representing all groupTypes available. The group
	 *         currently on selection isn't linkable.
	 */
	private static String getTypesFrame(String groupType, String schemeName)
	{
		final StringBuilder sb = new StringBuilder(500);
		sb.append("<table>");

		int count = 0;
		for (String type : BufferTable.getInstance().getSkillTypes())
		{
			if (count == 0)
			{
				sb.append("<tr>");
			}

			if (groupType.equalsIgnoreCase(type))
			{
				StringUtil.append(sb, "<td width=65>", type, "</td>");
			}
			else
			{
				StringUtil.append(sb, "<td width=65><a action=\"bypass -h npc_%objectId%_editschemes ", type, " ", schemeName, " 1\">", type, "</a></td>");
			}

			count++;
			if (count == 4)
			{
				sb.append("</tr>");
				count = 0;
			}
		}

		if (!sb.toString().endsWith("</tr>"))
		{
			sb.append("</tr>");
		}

		sb.append("</table>");

		return sb.toString();
	}
}