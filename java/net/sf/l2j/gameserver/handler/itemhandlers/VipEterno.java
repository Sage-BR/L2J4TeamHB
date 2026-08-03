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
package net.sf.l2j.gameserver.handler.itemhandlers;

import java.util.logging.Logger;

import net.sf.l2j.gameserver.handler.IItemHandler;
import net.sf.l2j.gameserver.handler.admincommandhandlers.AdminVip;
import net.sf.l2j.gameserver.model.L2ItemInstance;
import net.sf.l2j.gameserver.model.L2World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PlayableInstance;
import net.sf.l2j.gameserver.clientpackets.Say2;
import net.sf.l2j.gameserver.serverpackets.CreatureSay;

/**
 * @author MeGaPacK
 */
public class VipEterno implements IItemHandler
{
	protected static final Logger LOGGER = Logger.getLogger(VipEterno.class.getName());

	private static final int[] ITEM_IDS = { 10283 };

	@Override
	public void useItem(L2PlayableInstance playable, L2ItemInstance item)
	{
		if (!(playable instanceof L2PcInstance))
		{
			return;
		}

		L2PcInstance activeChar = (L2PcInstance) playable;

		if (activeChar.isInOlympiadMode())
		{
			activeChar.sendMessage("SYS: Voce nao pode fazer isso.");
			return;
		}

		if (activeChar.isVip())
		{
			activeChar.sendMessage("SYS: Voce ja esta com status Vip.");
			return;
		}

		activeChar.destroyItem("Consume", item.getObjectId(), 1, null, false);
		activeChar.setVip(true);
		AdminVip.updateDatabase(activeChar, true);

		activeChar.broadcastUserInfo();

		for (L2PcInstance allgms : L2World.getInstance().getAllGMs())
		{
			allgms.sendPacket(new CreatureSay(0, Say2.SHOUT, "(Vip Manager)", activeChar.getName() + " ativou Vip Eterno."));
		}
	}

	@Override
	public int[] getItemIds()
	{
		return ITEM_IDS;
	}
}
