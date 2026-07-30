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
package net.sf.l2j;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * L2DatabaseFactory — Otimizado para Virtual Threads (JDK 21+).
 *
 * <p>
 * Com Virtual Threads, threads bloqueadas em I/O (JDBC) são desmontadas do
 * carrier thread, permitindo muito mais conexões concorrentes sem consumir
 * threads do SO. O pool de conexão foi ajustado para suportar essa concorrência
 * maior.
 * </p>
 *
 * <p>
 * Melhorias:
 * <ul>
 * <li><b>MaximumPoolSize</b> aumentado — virtual threads permitem mais
 * operações DB simultâneas sem overhead de plataforma.</li>
 * <li><b>keepaliveTime</b> adicionado para detectar conexões mortas.</li>
 * <li><b>leakDetectionThreshold</b> para debugging de conexões não
 * fechadas.</li>
 * <li><b>cachePrepStmts + useServerPrepStmts</b> mantidos (já otimizados).</li>
 * </ul>
 * </p>
 */
public class L2DatabaseFactory
{
	static Logger _log = Logger.getLogger(L2DatabaseFactory.class.getName());

	public static enum ProviderType
	{
		MySql,
		MsSql
	}

	private static L2DatabaseFactory _instance;

	private ProviderType _providerType;

	private HikariDataSource _source;

	public L2DatabaseFactory() throws SQLException
	{
		try
		{
			if (Config.DATABASE_MAX_CONNECTIONS < 2)
			{
				Config.DATABASE_MAX_CONNECTIONS = 2;
				_log.warning("A minimum of " + Config.DATABASE_MAX_CONNECTIONS
				        + " db connections are required.");
			}

			HikariConfig config = new HikariConfig();
			config.setDriverClassName(Config.DATABASE_DRIVER);
			config.setJdbcUrl(Config.DATABASE_URL);
			config.setUsername(Config.DATABASE_LOGIN);
			config.setPassword(Config.DATABASE_PASSWORD);
			config.setMaximumPoolSize(Config.DATABASE_MAX_CONNECTIONS);
			config.setMinimumIdle(Math.min(10, Config.DATABASE_MAX_CONNECTIONS));
			config.setConnectionTimeout(5000);
			config.setIdleTimeout(600000);
			config.setMaxLifetime(1800000);
			config.setKeepaliveTime(300000);
			config.setConnectionTestQuery("SELECT 1");
			config.setValidationTimeout(3000);
			config.setLeakDetectionThreshold(60000);
			config.addDataSourceProperty("cachePrepStmts", "true");
			config.addDataSourceProperty("prepStmtCacheSize", "250");
			config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
			config.addDataSourceProperty("useServerPrepStmts", "true");

			_source = new HikariDataSource(config);

			/* Test the connection */
			_source.getConnection().close();

			if (Config.DEBUG)
			{
				_log.fine("Database Connection Working");
			}

			if (Config.DATABASE_DRIVER.toLowerCase().contains("microsoft"))
			{
				_providerType = ProviderType.MsSql;
			}
			else
			{
				_providerType = ProviderType.MySql;
			}
		}
		catch (SQLException x)
		{
			if (Config.DEBUG)
			{
				_log.fine("Database Connection FAILED");
			}
			throw x;
		}
		catch (Exception e)
		{
			if (Config.DEBUG)
			{
				_log.fine("Database Connection FAILED");
			}
			throw new SQLException("could not init DB connection:" + e);
		}
	}

	public final String prepQuerySelect(String[] fields, String tableName,
	        String whereClause, boolean returnOnlyTopRecord)
	{
		String msSqlTop1 = "";
		String mySqlTop1 = "";
		if (returnOnlyTopRecord)
		{
			if (getProviderType() == ProviderType.MsSql)
			{
				msSqlTop1 = " Top 1 ";
			}
			if (getProviderType() == ProviderType.MySql)
			{
				mySqlTop1 = " Limit 1 ";
			}
		}
		String query = "SELECT " + msSqlTop1 + safetyString(fields) + " FROM "
		        + tableName + " WHERE " + whereClause + mySqlTop1;
		return query;
	}

	public void shutdown()
	{
		try
		{
			_source.close();
		}
		catch (Exception e)
		{
			_log.log(Level.INFO, "", e);
		}
		try
		{
			_source = null;
		}
		catch (Exception e)
		{
			_log.log(Level.INFO, "", e);
		}
	}

	public final String safetyString(String... whatToCheck)
	{
		String braceLeft = "`";
		String braceRight = "`";
		if (getProviderType() == ProviderType.MsSql)
		{
			braceLeft = "[";
			braceRight = "]";
		}

		String result = "";
		for (String word : whatToCheck)
		{
			if (result != "")
			{
				result += ", ";
			}
			result += braceLeft + word + braceRight;
		}
		return result;
	}

	public static L2DatabaseFactory getInstance() throws SQLException
	{
		if (_instance == null)
		{
			_instance = new L2DatabaseFactory();
		}
		return _instance;
	}

	public Connection getConnection()
	{
		Connection con = null;
		while (con == null)
		{
			try
			{
				con = _source.getConnection();
			}
			catch (SQLException e)
			{
				_log.warning("L2DatabaseFactory: getConnection() failed, trying again "
				        + e);
			}
		}
		return con;
	}

	public int getBusyConnectionCount()
	{
		return _source.getHikariPoolMXBean().getActiveConnections();
	}

	public int getIdleConnectionCount()
	{
		return _source.getHikariPoolMXBean().getIdleConnections();
	}

	public final ProviderType getProviderType()
	{
		return _providerType;
	}
}
