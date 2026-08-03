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
package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.sql.Connection;
import java.sql.PreparedStatement;

import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.instancemanager.VipManager;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.serverpackets.ExShowScreenMessage;

/**
 * @author MeGaPacK
 */
public class AdminVip implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_setvip",
		"admin_remove_vip",
		"admin_remove_eternal_vip"
	};

	@Override
	public boolean useAdminCommand(String command, L2PcInstance activeChar)
	{
		if (activeChar.getAccessLevel().getLevel() < 7)
		{
			return false;
		}

		final L2Object target = activeChar.getTarget();
		if (target == null || !(target instanceof L2PcInstance))
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			return false;
		}

		if (command.startsWith("admin_setvip"))
		{
			L2PcInstance targetPlayer = (L2PcInstance) target;
			boolean newVip = !targetPlayer.isVip();

			if (newVip)
			{
				if (VipManager.getInstance().hasVipPrivileges(targetPlayer.getObjectId()))
				{
					removeVip(activeChar, targetPlayer);
				}

				targetPlayer.setVip(true);
				targetPlayer.sendMessage("[Vip System]: Voce se tornou um Vip ETERNO.");
				updateDatabase(targetPlayer, true);
				targetPlayer.broadcastUserInfo();
			}
			else
			{
				targetPlayer.setVip(false);
				targetPlayer.sendMessage("[Vip System]: Seu Vip ETERNO foi removido.");
				updateDatabase(targetPlayer, false);
				targetPlayer.broadcastUserInfo();
			}
		}
		else if (command.equalsIgnoreCase("admin_remove_vip"))
		{
			removeVip(activeChar, (L2PcInstance) target);
		}
		else if (command.equalsIgnoreCase("admin_remove_eternal_vip"))
		{
			removeEternalVip(activeChar, (L2PcInstance) target);
		}

		return true;
	}

	public static void removeVip(L2PcInstance activeChar, L2PcInstance targetChar)
	{
		if (!VipManager.getInstance().hasVipPrivileges(targetChar.getObjectId()))
		{
			activeChar.sendMessage("Your target does not have Vip privileges.");
			return;
		}

		VipManager.getInstance().removeVip(targetChar.getObjectId());
		activeChar.sendMessage("You have removed Vip privileges from " + targetChar.getName() + ".");
		targetChar.sendPacket(new ExShowScreenMessage("Your Vip privileges were removed by the admin.", 10000));
		targetChar.setVip(false);
		targetChar.broadcastUserInfo();
	}

	public static void removeEternalVip(L2PcInstance activeChar, L2PcInstance targetChar)
	{
		if (!VipManager.getInstance().hasEternalVip(targetChar.getObjectId()))
		{
			activeChar.sendMessage(targetChar.getName() + " does not have Eternal Vip.");
			return;
		}

		VipManager.getInstance().updateEternalVipToZero(targetChar.getObjectId());
		VipManager.getInstance().removeVip(targetChar.getObjectId());
		targetChar.setVip(false);
		targetChar.broadcastUserInfo();
		activeChar.sendMessage("You have removed Eternal Vip from " + targetChar.getName() + ".");
		targetChar.sendPacket(new ExShowScreenMessage("Your Eternal Vip was revoked by the admin.", 10000));
	}

	public static void updateDatabase(L2PcInstance player, boolean newVip)
	{
		// prevents any NPE.
		if (player == null)
		{
			return;
		}

		Connection con = null;
		try
		{
			// Database Connection
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement stmt = con.prepareStatement(INSERT_DATA);

			// if it is a new donator insert proper data
			if (newVip)
			{
				stmt.setInt(1, player.getObjectId());
				stmt.setString(2, player.getName());
				stmt.setInt(3, 1);
				stmt.execute();
				stmt.close();
				stmt = null;
			}
			else
			{
				// delegate to VipManager to avoid SQL duplication
				stmt.close();
				stmt = null;
				VipManager.getInstance().updateEternalVipToZero(player.getObjectId());
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
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
	}

	// Updates That Will be Executed by MySQL
	static String INSERT_DATA = "REPLACE INTO characters_vip_eterno (obj_Id, char_name, vip) VALUES (?,?,?)";

	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
