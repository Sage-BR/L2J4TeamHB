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
package net.sf.l2j.gameserver.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;

import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.gameserver.ThreadPoolManager;
import net.sf.l2j.gameserver.model.L2World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.serverpackets.ExShowScreenMessage;

/**
 * @author rapfersan92
 */
public class VipManager
{
	private static final Logger _log = Logger.getLogger(VipManager.class.getName());

	private final Map<Integer, Long> _vips;

	protected final Map<Integer, Long> _vipsTask;

	private ScheduledFuture<?> _scheduler;

	public static VipManager getInstance()
	{
		return SingletonHolder._instance;
	}

	protected VipManager()
	{
		_vips = new ConcurrentHashMap<>();
		_vipsTask = new ConcurrentHashMap<>();
		_scheduler = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new VipTask(), 1000, 1000);
		load();
	}

	public void reload()
	{
		_vips.clear();
		_vipsTask.clear();
		if (_scheduler != null)
		{
			_scheduler.cancel(true);
		}
		_scheduler = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new VipTask(), 1000, 1000);
		load();
	}

	public void load()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT objectId, duration FROM character_vip ORDER BY objectId");
			ResultSet rs = statement.executeQuery();
			while (rs.next())
			{
				_vips.put(rs.getInt("objectId"), rs.getLong("duration"));
			}
			rs.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warning("Exception: VipManager load: " + e.getMessage());
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

		_log.info("VipManager: Loaded " + _vips.size() + " characters with vip privileges.");
	}

	public void addVip(int objectId, long duration)
	{
		_vips.put(objectId, duration);
		_vipsTask.put(objectId, duration);
		addVipPrivileges(objectId);

		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("INSERT INTO character_vip (objectId, duration) VALUES (?, ?)");
			statement.setInt(1, objectId);
			statement.setLong(2, duration);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warning("Exception: VipManager addVip: " + e.getMessage());
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

	public void updateVip(int objectId, long duration)
	{
		_vips.put(objectId, duration);
		_vipsTask.put(objectId, duration);

		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("UPDATE character_vip SET duration = ? WHERE objectId = ?");
			statement.setLong(1, duration);
			statement.setInt(2, objectId);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warning("Exception: VipManager updateVip: " + e.getMessage());
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

	public void removeVip(int objectId)
	{
		_vips.remove(objectId);
		_vipsTask.remove(objectId);
		removeVipPrivileges(objectId);

		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("DELETE FROM character_vip WHERE objectId = ?");
			statement.setInt(1, objectId);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warning("Exception: VipManager removeVip: " + e.getMessage());
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

	public boolean hasVipPrivileges(int objectId)
	{
		return _vips.containsKey(objectId);
	}

	public long getVipDuration(int objectId)
	{
		return _vips.get(objectId);
	}

	public void addVipTask(int objectId, long duration)
	{
		_vipsTask.put(objectId, duration);
	}

	public void removeVipTask(int objectId)
	{
		_vipsTask.remove(objectId);
	}

	public void addVipPrivileges(int objectId)
	{
		final L2PcInstance player = L2World.getInstance().getPlayer(objectId);
		if (player == null)
		{
			return;
		}
		player.setVip(true);
		player.broadcastUserInfo();
	}

	public void removeVipPrivileges(int objectId)
	{
		// If the player has eternal VIP, do not remove privileges or clear color.
		if (hasEternalVip(objectId))
		{
			return;
		}

		// Always clear color from DB immediately (online or offline)
		clearNameColorInDb(objectId);

		final L2PcInstance player = L2World.getInstance().getPlayer(objectId);
		if (player == null)
		{
			return;
		}
		player.setVip(false);
		player.broadcastUserInfo();
	}

	private void clearNameColorInDb(int objectId)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("UPDATE characters SET color_name = '' WHERE charId = ?");
			statement.setInt(1, objectId);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warning("Exception: VipManager clearNameColorInDb: " + e.getMessage());
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

	public boolean hasEternalVip(int objectId)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT vip FROM characters_vip_eterno WHERE obj_Id = ?");
			statement.setInt(1, objectId);
			ResultSet rs = statement.executeQuery();
			boolean result = false;
			if (rs.next())
			{
				result = rs.getInt("vip") > 0;
			}
			rs.close();
			statement.close();
			return result;
		}
		catch (Exception e)
		{
			_log.warning("Exception: VipManager hasEternalVip: " + e.getMessage());
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
		return false;
	}

	public void updateEternalVipToZero(int objectId)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("UPDATE characters_vip_eterno SET vip = 0 WHERE obj_Id = ?");
			statement.setInt(1, objectId);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warning("Exception: VipManager updateEternalVipToZero: " + e.getMessage());
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

	public class VipTask implements Runnable
	{
		@Override
		public final void run()
		{
			if (_vipsTask.isEmpty())
			{
				return;
			}

			for (Map.Entry<Integer, Long> entry : _vipsTask.entrySet())
			{
				final long duration = entry.getValue();
				if (System.currentTimeMillis() > duration)
				{
					final int objectId = entry.getKey();
					removeVip(objectId);

					final L2PcInstance player = L2World.getInstance().getPlayer(objectId);
					if (player != null)
					{
						player.sendPacket(new ExShowScreenMessage("Your Vip privileges were removed.", 10000));
					}
				}
			}
		}
	}

	private static class SingletonHolder
	{
		protected static final VipManager _instance = new VipManager();
	}

	public boolean hasEverReceivedVip(int objectId)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT 1 FROM character_vip_free WHERE objectId = ?");
			statement.setInt(1, objectId);
			ResultSet rs = statement.executeQuery();
			boolean result = rs.next();
			rs.close();
			statement.close();
			return result;
		}
		catch (Exception e)
		{
			_log.warning("Exception: VipManager hasEverReceivedVip: " + e.getMessage());
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
		return false;
	}

	public void markVipReceived(int objectId)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("INSERT INTO character_vip_free (objectId, receivedAt) VALUES (?, ?)");
			statement.setInt(1, objectId);
			statement.setLong(2, System.currentTimeMillis());
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warning("Exception: VipManager markVipReceived: " + e.getMessage());
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
}
