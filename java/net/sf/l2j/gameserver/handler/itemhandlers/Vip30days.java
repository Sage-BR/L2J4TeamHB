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

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.logging.Logger;

import net.sf.l2j.gameserver.handler.IItemHandler;
import net.sf.l2j.gameserver.instancemanager.VipManager;
import net.sf.l2j.gameserver.model.L2ItemInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PlayableInstance;
import net.sf.l2j.gameserver.serverpackets.ExShowScreenMessage;

/**
 * @author MeGaPacK
 */
public class Vip30days implements IItemHandler
{
	protected static final Logger LOGGER = Logger.getLogger(Vip30days.class.getName());

	private static final int[] ITEM_IDS = { 10282 };

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

		int days = 30;

		if (VipManager.getInstance().hasVipPrivileges(activeChar.getObjectId()))
		{
			long _daysleft;
			final long now = Calendar.getInstance().getTimeInMillis();
			long duration = VipManager.getInstance().getVipDuration(activeChar.getObjectId());
			final long endDay = duration;

			_daysleft = ((endDay - now) / 86400000) + days + 1;

			long end_day;
			final Calendar calendar = Calendar.getInstance();
			if (_daysleft >= 30)
			{
				while (_daysleft >= 30)
				{
					if (calendar.get(Calendar.MONTH) == 11)
						calendar.roll(Calendar.YEAR, true);
					calendar.roll(Calendar.MONTH, true);
					_daysleft -= 30;
				}
			}

			if (_daysleft < 30 && _daysleft > 0)
			{
				while (_daysleft > 0)
				{
					if (calendar.get(Calendar.DATE) == 28 && calendar.get(Calendar.MONTH) == 1)
						calendar.roll(Calendar.MONTH, true);
					if (calendar.get(Calendar.DATE) == 30)
					{
						if (calendar.get(Calendar.MONTH) == 11)
							calendar.roll(Calendar.YEAR, true);
						calendar.roll(Calendar.MONTH, true);

					}
					calendar.roll(Calendar.DATE, true);
					_daysleft--;
				}
			}

			end_day = calendar.getTimeInMillis();
			VipManager.getInstance().updateVip(activeChar.getObjectId(), end_day);
		}
		else
		{
			long end_day;
			final Calendar calendar = Calendar.getInstance();
			if (days >= 31)
			{
				while (days >= 31)
				{
					if (calendar.get(Calendar.MONTH) == 11)
						calendar.roll(Calendar.YEAR, true);
					calendar.roll(Calendar.MONTH, true);
					days -= 31;
				}
			}

			if (days < 31 && days > 0)
			{
				while (days > 0)
				{
					if (calendar.get(Calendar.DATE) == 28 && calendar.get(Calendar.MONTH) == 1)
						calendar.roll(Calendar.MONTH, true);
					if (calendar.get(Calendar.DATE) == 31)
					{
						if (calendar.get(Calendar.MONTH) == 11)
							calendar.roll(Calendar.YEAR, true);
						calendar.roll(Calendar.MONTH, true);

					}
					calendar.roll(Calendar.DATE, true);
					days--;
				}
			}

			end_day = calendar.getTimeInMillis();
			VipManager.getInstance().addVip(activeChar.getObjectId(), end_day);
		}

		long _daysleft;
		final long now = Calendar.getInstance().getTimeInMillis();
		long duration = VipManager.getInstance().getVipDuration(activeChar.getObjectId());
		final long endDay = duration;
		_daysleft = ((endDay - now) / 86400000);
		if (_daysleft < 270)
		{
			activeChar.sendPacket(new ExShowScreenMessage("Your Vip privileges ends at " + new SimpleDateFormat("dd MMM, HH:mm").format(new Date(duration)) + ".", 10000));
			activeChar.sendMessage("Your vip privileges ends at " + new SimpleDateFormat("dd MMM, HH:mm").format(new Date(duration)) + ".");
		}
	}

	@Override
	public int[] getItemIds()
	{
		return ITEM_IDS;
	}
}
