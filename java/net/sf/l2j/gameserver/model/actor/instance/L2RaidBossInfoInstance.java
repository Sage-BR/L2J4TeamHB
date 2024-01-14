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
package net.sf.l2j.gameserver.model.actor.instance;
 
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;


import Dev.SpecialMods.RaidBossInfoManager;
import net.sf.l2j.Config;
import net.sf.l2j.gameserver.datatables.ItemTable;
import net.sf.l2j.gameserver.datatables.NpcTable;
import net.sf.l2j.gameserver.datatables.xml.IconTable;
import net.sf.l2j.gameserver.model.L2DropCategory;
import net.sf.l2j.gameserver.model.L2DropData;
import net.sf.l2j.gameserver.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.templates.L2Item;
import net.sf.l2j.gameserver.templates.L2NpcTemplate;
import net.sf.l2j.util.Rnd;
import net.sf.l2j.util.StringUtil;

public class L2RaidBossInfoInstance extends L2NpcInstance
{
private final Map<Integer, Integer> _lastPage = new ConcurrentHashMap<>();
 
private final String[][] _messages =
{
{
"<font color=\"LEVEL\">%player%</font>, are you not afraid?",
"Be careful <font color=\"LEVEL\">%player%</font>!"
},
{
"Here is the drop list of <font color=\"LEVEL\">%boss%</font>!",
"Seems that <font color=\"LEVEL\">%boss%</font> has good drops."
},
};
 
public L2RaidBossInfoInstance(int objectId, L2NpcTemplate template)
{
super(objectId, template);
}
 
@Override
public void showChatWindow(L2PcInstance player, int val)
{
String name = "data/html/mods/raidbossinfo/" + getNpcId() + ".htm";
if (val != 0)
name = "data/html/mods/raidbossinfo/" + getNpcId() + "-" + val + ".htm";
 
NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
html.setFile(name);
html.replace("%objectId%", getObjectId());
player.sendPacket(html);
player.sendPacket(ActionFailed.STATIC_PACKET);
}
 
@Override
public void onBypassFeedback(L2PcInstance player, String command)
{
StringTokenizer st = new StringTokenizer(command, " ");
String currentCommand = st.nextToken();
 
if (currentCommand.startsWith("RaidBossInfo"))
{
int pageId = Integer.parseInt(st.nextToken());
_lastPage.put(player.getObjectId(), pageId);
showRaidBossInfo(player, pageId);
}
else if (currentCommand.startsWith("RaidBossDrop"))
{
int bossId = Integer.parseInt(st.nextToken());
int pageId = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 1;
showRaidBossDrop(player, bossId, pageId);
}
 
super.onBypassFeedback(player, command);
}
 
private void showRaidBossInfo(L2PcInstance player, int pageId)
{
List<Integer> infos = new ArrayList<>();
infos.addAll(Config.LIST_RAID_BOSS_IDS);
 
final int limit = Config.RAID_BOSS_INFO_PAGE_LIMIT;
final int max = infos.size() / limit + (infos.size() % limit == 0 ? 0 : 1);
infos = infos.subList((pageId - 1) * limit, Math.min(pageId * limit, infos.size()));
 
final StringBuilder sb = new StringBuilder();
sb.append("<html>");
sb.append("<center>");
sb.append("<body>");
sb.append("<table><tr>");
sb.append("<td width=32><img src=Icon.etc_alphabet_b_i00 height=32 width=32></td>");
sb.append("<td width=32><img src=Icon.etc_alphabet_i_i00 height=32 width=32></td>");
sb.append("<td width=32><img src=Icon.etc_alphabet_g_i00 height=32 width=32></td>");
sb.append("<td width=32><img src=Icon.etc_alphabet_b_i00 height=32 width=32></td>");
sb.append("<td width=32><img src=Icon.etc_alphabet_o_i00 height=32 width=32></td>");
sb.append("<td width=32><img src=Icon.etc_alphabet_s_i00 height=32 width=32></td>");
sb.append("<td width=32><img src=Icon.etc_alphabet_s_i00 height=32 width=32></td>");
sb.append("</tr></table><br>");

sb.append("<img src=\"L2UI.SquareGray\" width=300 height=1>");
sb.append("<table bgcolor=\"000000\" width=\"318\">");
sb.append("<tr><td><center>" + _messages[0][Rnd.get(_messages.length)].replace("%player%", player.getName()) + "</center></td></tr>");
sb.append("<tr><td><center><font color=\"FF8C00\">Raid Boss Infos</font></center></td></tr>");
sb.append("</table>");
sb.append("<img src=\"L2UI.SquareGray\" width=300 height=1>");

sb.append("<table bgcolor=\"000000\" width=\"318\">");
 
for (int bossId : infos)
{
final L2NpcTemplate template = NpcTable.getInstance().getTemplate(bossId);
if (template == null)
continue;
 
String bossName = template.getName();
if (bossName.length() > 23)
bossName = bossName.substring(0, 23) + "...";
 
final long respawnTime = RaidBossInfoManager.getInstance().getRaidBossRespawnTime(bossId);
if (respawnTime <= System.currentTimeMillis())
{
sb.append("<tr>");
sb.append("<td><a action=\"bypass -h npc_%objectId%_RaidBossDrop " + bossId + "\">" + bossName + "</a></td>");
sb.append("<td><font color=\"9CC300\">Alive</font></td>");
sb.append("</tr>");
}
else
{
sb.append("<tr>");
sb.append("<td width=\"159\" align=\"left\"><a action=\"bypass -h npc_%objectId%_RaidBossDrop " + bossId + "\">" + bossName + "</a></td>");
sb.append("<td width=\"159\" align=\"left\"><font color=\"FB5858\">Dead</font> " + new SimpleDateFormat(Config.RAID_BOSS_DATE_FORMAT).format(new Date(respawnTime)) + "</td>");
sb.append("</tr>");
}
} 
sb.append("</table>");

sb.append("<img src=\"L2UI.SquareGray\" width=300 height=1>");

sb.append("<table width=\"300\" cellspacing=\"2\">");
sb.append("<tr>");
for (int x = 0; x < max; x++)
{
	final int pageNr = x + 1;
	if (pageId == pageNr)
		sb.append("<td align=\"center\">" + pageNr + "</td>");
	else
		sb.append("<td align=\"center\"><a action=\"bypass -h npc_%objectId%_RaidBossInfo " + pageNr + "\">" + pageNr + "</a></td>");
}
sb.append("</tr>");
sb.append("</table>");

sb.append("<img src=\"L2UI.SquareGray\" width=300 height=1>");

sb.append("<table bgcolor=\"000000\" width=\"350\">");
sb.append("<tr><td><center><a action=\"bypass -h npc_%objectId%_Chat 0\">Return</a></center></td></tr>");
sb.append("</table>");
sb.append("<img src=\"L2UI.SquareGray\" width=300 height=1>");


sb.append("</center>");
sb.append("</body>");
sb.append("</html>");
 
final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
html.setHtml(sb.toString());
html.replace("%name%", getName());
html.replace("%objectId%", getObjectId());
player.sendPacket(html);
}
 
private void showRaidBossDrop(L2PcInstance player, int npcId, int page)
{
	final L2NpcTemplate template = NpcTable.getInstance().getTemplate(npcId);
	if (template == null)
		return; 
	
	if (template.getDropData().isEmpty()) 
	{
		player.sendMessage("This target have not drop info.");
		return;
	} 
	
	final List<L2DropCategory> list = new ArrayList<>();
	template.getDropData().forEach(c -> list.add(c));
	Collections.reverse(list);
	
	int myPage = 1;
	int i = 0;
	int shown = 0;
	boolean hasMore = false;
	
	final StringBuilder sb = new StringBuilder();
	sb.append("<html>");
	sb.append("<center>");
	sb.append("<body>");
	sb.append("<table width=\"256\">");
	sb.append("<tr><td width=\"256\" align=\"center\">%name%</td></tr>");
	sb.append("</table>");
	sb.append("<br>");
	sb.append("<table width=\"256\">");
	sb.append("<tr><td width=\"256\" align=\"left\">" + MESSAGE[1][Rnd.get(MESSAGE.length)].replace("%boss%", template.getName()) + "</td></tr>");
	sb.append("</table>");
	sb.append("<br>");
	sb.append("<table width=\"224\" bgcolor=\"000000\">");
	sb.append("<tr><td width=\"224\" align=\"center\">Raid Boss Drops</td></tr>");
	sb.append("</table>");
	sb.append("<br>");
	
	for (L2DropCategory cat : list) 
	{
		if (shown == PAGE_LIMIT)
		{
			hasMore = true;
			break;
		} 
		
		for (L2DropData drop : cat.getAllDrops())
		{
			final double chance = Math.min(100, (((drop.getItemId() == 57) ? drop.getChance() * Config.RATE_DROP_ADENA : drop.getChance() * Config.RATE_DROP_ITEMS) / 10000));
			final L2Item item = ItemTable.getInstance().getTemplate(drop.getItemId());

			String name = item.getName();
			if (name.startsWith("Recipe: "))
				name = "R: " + name.substring(8);
			
			if (name.length() >= 45)
				name = name.substring(0, 42) + "...";
			
			String percent = null;
			if (chance <= 0.001)
			{
				DecimalFormat df = new DecimalFormat("#.####");
				percent = df.format(chance);
			}
			else if (chance <= 0.01)
			{
				DecimalFormat df = new DecimalFormat("#.###");
				percent = df.format(chance);
			}
			else
			{
				DecimalFormat df = new DecimalFormat("##.##");
				percent = df.format(chance);
			}
			
			if (myPage != page)
			{
				i++;
				if (i == PAGE_LIMIT)
				{
					myPage++;
					i = 0;
				}
				continue;
			}
			
			if (shown == PAGE_LIMIT)
			{
				hasMore = true;
				break;
			}
			
			sb.append(((shown % 2) == 0 ? "<table width=\"280\" bgcolor=\"000000\"><tr>" : "<table width=\"280\"><tr>"));
			sb.append("<td width=44 height=41 align=center><table bgcolor=" + (cat.isSweep() ? "FF00FF" : "FFFFFF") + " cellpadding=6 cellspacing=\"-5\"><tr><td><button width=32 height=32 back=" + IconTable.getIcon(item.getItemId()) + " fore=" + IconTable.getIcon(item.getItemId()) + "></td></tr></table></td>");
			sb.append("<td width=240>" + (cat.isSweep() ? ("<font color=ff00ff>" + name + "</font>") : name) + "<br1><font color=B09878>" + (cat.isSweep() ? "Spoil" : "Drop") + " Chance : " + percent + "%</font></td>");
			sb.append("</tr></table><img src=L2UI.SquareGray width=280 height=1>");
			shown++;
		} 
	} 

	// Build page footer.
	sb.append("<br><img src=\"L2UI.SquareGray\" width=277 height=1><table width=\"100%\" bgcolor=000000><tr>");
	
	if (page > 1)
		StringUtil.append(sb, "<td align=left width=70><a action=\"bypass -h npc_%objectId%_RaidBossDrop "+ npcId + " ", (page - 1), "\">Previous</a></td>");
	else
		StringUtil.append(sb, "<td align=left width=70>Previous</td>");
	
	StringUtil.append(sb, "<td align=center width=100>Page ", page, "</td>");
	
	if (page < shown)
		StringUtil.append(sb, "<td align=right width=70>" + (hasMore ? "<a action=\"bypass -h npc_%objectId%_RaidBossDrop " + npcId + " " + (page + 1) + "\">Next</a>" : "") + "</td>");
	else
		StringUtil.append(sb, "<td align=right width=70>Next</td>");
	
	sb.append("</tr></table><img src=\"L2UI.SquareGray\" width=277 height=1>");
	sb.append("<br>");
	sb.append("<center>");
	sb.append("<table width=\"160\" cellspacing=\"2\">");
	sb.append("<tr>");											
	sb.append("<td width=\"160\" align=\"center\"><a action=\"bypass -h npc_%objectId%_RaidBossInfo " + _lastPage.get(player.getObjectId()) + "\">Return</a></td>");
	sb.append("</tr>");
	sb.append("</table>");
	sb.append("</center>");
	sb.append("</body>");
	sb.append("</html>");

	final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
	html.setHtml(sb.toString());
	html.replace("%name%", getName());
	html.replace("%objectId%", getObjectId());
	player.sendPacket(html);
}

}