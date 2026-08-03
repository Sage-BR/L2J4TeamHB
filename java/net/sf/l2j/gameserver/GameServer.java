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

package net.sf.l2j.gameserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.mmocore.network.SelectorConfig;
import org.mmocore.network.SelectorThread;

import Dev.SpecialMods.XMLDocumentFactory;
import Guard.Protection;
import net.sf.l2j.Config;
import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.Server;
import net.sf.l2j.gameserver.ai.special.manager.AILoader;
import net.sf.l2j.gameserver.cache.CrestCache;
import net.sf.l2j.gameserver.cache.HtmCache;
import net.sf.l2j.gameserver.communitybbs.Manager.ForumsBBSManager;
import net.sf.l2j.gameserver.datatables.AccessLevels;
import net.sf.l2j.gameserver.datatables.AdminCommandAccessRights;
import net.sf.l2j.gameserver.datatables.ArmorSetsTable;
import net.sf.l2j.gameserver.datatables.AugmentationData;
import net.sf.l2j.gameserver.datatables.CharNameTable;
import net.sf.l2j.gameserver.datatables.CharTemplateTable;
import net.sf.l2j.gameserver.datatables.ClanTable;
import net.sf.l2j.gameserver.datatables.DoorTable;
import net.sf.l2j.gameserver.datatables.EventDroplist;
import net.sf.l2j.gameserver.datatables.ExtractableItemsData;
import net.sf.l2j.gameserver.datatables.FishTable;
import net.sf.l2j.gameserver.datatables.HelperBuffTable;
import net.sf.l2j.gameserver.datatables.HennaTable;
import net.sf.l2j.gameserver.datatables.HennaTreeTable;
import net.sf.l2j.gameserver.datatables.HeroSkillTable;
import net.sf.l2j.gameserver.datatables.ItemTable;
import net.sf.l2j.gameserver.datatables.LevelUpData;
import net.sf.l2j.gameserver.datatables.MapRegionTable;
import net.sf.l2j.gameserver.datatables.MerchantPriceConfigTable;
import net.sf.l2j.gameserver.datatables.NobleSkillTable;
import net.sf.l2j.gameserver.datatables.NpcBufferTable;
import net.sf.l2j.gameserver.datatables.NpcTable;
import net.sf.l2j.gameserver.datatables.NpcWalkerRoutesTable;
import net.sf.l2j.gameserver.datatables.SkillSpellbookTable;
import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.datatables.SkillTreeTable;
import net.sf.l2j.gameserver.datatables.SpawnTable;
import net.sf.l2j.gameserver.datatables.StaticObjects;
import net.sf.l2j.gameserver.datatables.SummonItemsData;
import net.sf.l2j.gameserver.datatables.TeleportLocationTable;
import net.sf.l2j.gameserver.datatables.ZoneData;
import net.sf.l2j.gameserver.datatables.sql.BufferTable;
import net.sf.l2j.gameserver.datatables.xml.IconTable;
import net.sf.l2j.gameserver.geoeditorcon.GeoEditorListener;
import net.sf.l2j.gameserver.handler.AdminCommandHandler;
import net.sf.l2j.gameserver.handler.ChatHandler;
import net.sf.l2j.gameserver.handler.ItemHandler;
import net.sf.l2j.gameserver.handler.SkillHandler;
import net.sf.l2j.gameserver.handler.UserCommandHandler;
import net.sf.l2j.gameserver.handler.VoicedCommandHandler;
import net.sf.l2j.gameserver.idfactory.IdFactory;
import net.sf.l2j.gameserver.instancemanager.AuctionManager;
import net.sf.l2j.gameserver.instancemanager.BoatManager;
import net.sf.l2j.gameserver.instancemanager.CastleManager;
import net.sf.l2j.gameserver.instancemanager.CastleManorManager;
import net.sf.l2j.gameserver.instancemanager.ClanHallManager;
import net.sf.l2j.gameserver.instancemanager.CoupleManager;
import net.sf.l2j.gameserver.instancemanager.CursedWeaponsManager;
import net.sf.l2j.gameserver.instancemanager.DayNightSpawnManager;
import net.sf.l2j.gameserver.instancemanager.DimensionalRiftManager;
import net.sf.l2j.gameserver.instancemanager.FortManager;
import net.sf.l2j.gameserver.instancemanager.FortSiegeManager;
import net.sf.l2j.gameserver.instancemanager.GrandBossManager;
import net.sf.l2j.gameserver.instancemanager.ItemsOnGroundManager;
import net.sf.l2j.gameserver.instancemanager.MercTicketManager;
import net.sf.l2j.gameserver.instancemanager.PetitionManager;
import net.sf.l2j.gameserver.instancemanager.QuestManager;
import net.sf.l2j.gameserver.instancemanager.RaidBossPointsManager;
import net.sf.l2j.gameserver.instancemanager.RaidBossSpawnManager;
import net.sf.l2j.gameserver.instancemanager.SiegeManager;
import net.sf.l2j.gameserver.instancemanager.SiegeRewardManager;
import net.sf.l2j.gameserver.instancemanager.TransformationManager;
import net.sf.l2j.gameserver.instancemanager.VipManager;
import net.sf.l2j.gameserver.model.AutoChatHandler;
import net.sf.l2j.gameserver.model.AutoSpawnHandler;
import net.sf.l2j.gameserver.model.L2Manor;
import net.sf.l2j.gameserver.model.L2PetDataTable;
import net.sf.l2j.gameserver.model.L2World;
import net.sf.l2j.gameserver.model.PartyMatchWaitingList;
import net.sf.l2j.gameserver.model.entity.Hero;
import net.sf.l2j.gameserver.model.entity.TvTManager;
import net.sf.l2j.gameserver.network.L2GameClient;
import net.sf.l2j.gameserver.network.L2GamePacketHandler;
import net.sf.l2j.gameserver.script.faenor.FaenorScriptEngine;
import net.sf.l2j.gameserver.scripting.CompiledScriptCache;
import net.sf.l2j.gameserver.scripting.L2ScriptEngineManager;
import net.sf.l2j.gameserver.taskmanager.KnownListUpdateTaskManager;
import net.sf.l2j.gameserver.taskmanager.TaskManager;
import net.sf.l2j.gameserver.util.DynamicExtension;
import net.sf.l2j.gameserver.util.FloodProtector;
import net.sf.l2j.server.gameserver.model.PartyMatchRoomList;
import net.sf.l2j.status.Status;
import net.sf.l2j.util.DeadLockDetector;

/**
 * This class ...
 *
 * @version $Revision: 1.29.2.15.2.19 $ $Date: 2005/04/05 19:41:23 $
 */
public class GameServer
{
	private static final Logger _log = Logger.getLogger(GameServer.class.getName());

	private final SelectorThread<L2GameClient> _selectorThread;

	private final DeadLockDetector _deadDetectThread;

	private SkillTable _skillTable;

	private ItemTable _itemTable;

	private NpcTable _npcTable;

	private HennaTable _hennaTable;

	private final IdFactory _idFactory;

	public static GameServer gameServer;

	private static ClanHallManager _cHManager;

	private final Shutdown _shutdownHandler;

	private DoorTable _doorTable;

	private SevenSigns _sevenSignsEngine;

	private AutoChatHandler _autoChatHandler;

	private AutoSpawnHandler _autoSpawnHandler;

	private LoginServerThread _loginThread;

	private HelperBuffTable _helperBuffTable;

	private static Status _statusServer;

	@SuppressWarnings("unused")
	private final ThreadPoolManager _threadpools;

	public static final Calendar dateTimeServerStarted = Calendar.getInstance();

	public long getUsedMemoryMB()
	{
		return (Runtime.getRuntime().totalMemory()
		        - Runtime.getRuntime().freeMemory()) / 1048576; // 1024 * 1024 =
		                                                        // 1048576;
	}

	public SelectorThread<L2GameClient> getSelectorThread()
	{
		return _selectorThread;
	}

	public DeadLockDetector getDeadLockDetectorThread()
	{
		return _deadDetectThread;
	}

	public ClanHallManager getCHManager()
	{
		return _cHManager;
	}

	public GameServer() throws Exception
	{
		final long startTime = System.currentTimeMillis();
		gameServer = this;
		_log.finest("used mem:" + getUsedMemoryMB() + "MB");

		_idFactory = IdFactory.getInstance();
		if (!_idFactory.isInitialized())
		{
			_log.severe("Could not read object IDs from DB. Please Check Your Data.");
			throw new Exception("Could not initialize the ID factory");
		}

		_threadpools = ThreadPoolManager.getInstance();

		new File(Config.DATAPACK_ROOT, "data/clans").mkdirs();
		new File(Config.DATAPACK_ROOT, "data/crests").mkdirs();

		// load script engines
		L2ScriptEngineManager.getInstance();

		// start game time control early
		GameTimeController.getInstance();

		// Parallel data loading via Virtual Threads
		loadParallel(
		        // Group A: Items and characters
		        () -> {
			        CharNameTable.getInstance();
			        _itemTable = ItemTable.getInstance();
			        ExtractableItemsData.getInstance();
			        SummonItemsData.getInstance();
			        MerchantPriceConfigTable.getInstance();
			        TradeController.getInstance();
			        CharTemplateTable.getInstance();
			        AccessLevels.getInstance();
			        AdminCommandAccessRights.getInstance();
		        },
		        // Group B: Skills and NPCs (NpcTable depends on SkillTable)
		        () -> {
			        _skillTable = SkillTable.getInstance();
			        SkillTreeTable.getInstance();
			        SkillSpellbookTable.getInstance();
			        NobleSkillTable.getInstance();
			        HeroSkillTable.getInstance();
			        NpcBufferTable.getInstance();
			        _npcTable = NpcTable.getInstance();
		        },
		        // Group C: Character data tables
		        () -> {
			        _hennaTable = HennaTable.getInstance();
			        HennaTreeTable.getInstance();
			        _helperBuffTable = HelperBuffTable.getInstance();
			        LevelUpData.getInstance();
			        TeleportLocationTable.getInstance();
			        FishTable.getInstance();
			        ArmorSetsTable.getInstance();
			        IconTable.getInstance();
		        },
		        // Group D: World, caches, and utilities
		        () -> {
			        RecipeController.getInstance();
			        PartyMatchWaitingList.getInstance();
			        PartyMatchRoomList.getInstance();
			        if (Config.ALLOW_NPC_WALKERS)
					{
						NpcWalkerRoutesTable.getInstance().load();
					}
			        HtmCache.getInstance();
			        CrestCache.getInstance();
			        L2World.getInstance();
			        MapRegionTable.getInstance();
			        ZoneData.getInstance();
		        },
		        // Group E: Entities and managers
		        () -> {
			        ClanTable.getInstance();
			        AuctionManager.getInstance();
			        CastleManager.getInstance();
			        SiegeManager.getInstance();
			        FortManager.getInstance();
			        FortSiegeManager.getInstance();
			        _cHManager = ClanHallManager.getInstance();
			        Announcements.getInstance();
			        EventDroplist.getInstance();
			        BufferTable.getInstance();
			        XMLDocumentFactory.getInstance();
			        L2Manor.getInstance();
			        BoatManager.getInstance();
			        CastleManorManager.getInstance();
			        MercTicketManager.getInstance();
			        PetitionManager.getInstance();
			        QuestManager.getInstance();
			        TransformationManager.getInstance();
			        SiegeRewardManager.getInstance();
			        VipManager.getInstance();
		        });

		// GeoData (pathfinding now uses geo-grid directly, no pathnode files needed)
		GeoData.getInstance();

		// Spawn loading (heavy DB I/O, runs sequentially to avoid connection
	// contention)
		SpawnTable.getInstance();
		RaidBossSpawnManager.getInstance();
		DayNightSpawnManager.getInstance().notifyChangeMode();
		GrandBossManager.getInstance();
		RaidBossPointsManager.init();
		DimensionalRiftManager.getInstance();

		// Post-parallel validation
		if (!_itemTable.isInitialized())
		{
			_log.severe("Could not find the extraced files. Please Check Your Data.");
			throw new Exception("Could not initialize the item table");
		}
		if (!_skillTable.isInitialized())
		{
			_log.severe("Could not find the extraced files. Please Check Your Data.");
			throw new Exception("Could not initialize the skill table");
		}
		if (!_npcTable.isInitialized())
		{
			_log.severe("Could not find the extraced files. Please Check Your Data.");
			throw new Exception("Could not initialize the npc table");
		}
		if (!_hennaTable.isInitialized())
		{
			throw new Exception("Could not initialize the Henna Table");
		}
		if (!_helperBuffTable.isInitialized())
		{
			throw new Exception("Could not initialize the Helper Buff Table");
		}

		_log.info("AI");
		if (!Config.ALT_DEV_NO_AI)
		{
			AILoader.init();
		}
		else
		{
			_log.info("AI: disable load.");
		}

		_log.info("Loading Server Scripts");
		if (!Config.ALT_DEV_NO_SCRIPT)
		{
			final File scripts = new File(Config.DATAPACK_ROOT, "data/scripts.cfg");
			L2ScriptEngineManager.getInstance().executeScriptsList(scripts);

			final CompiledScriptCache compiledScriptCache = L2ScriptEngineManager.getInstance().getCompiledScriptCache();
			if (compiledScriptCache == null)
			{
				_log.info("Compiled Scripts Cache is disabled.");
			}
			else
			{
				compiledScriptCache.purge();
				if (compiledScriptCache.isModified())
				{
					compiledScriptCache.save();
					_log.info("Compiled Scripts Cache was saved.");
				}
				else
				{
					_log.info("Compiled Scripts Cache is up-to-date.");
				}
			}
			FaenorScriptEngine.getInstance();
		}
		else
		{
			_log.info("Script: disable load.");
		}

		QuestManager.getInstance().report();
		TransformationManager.getInstance().report();

		AugmentationData.getInstance();
		if (Config.SAVE_DROPPED_ITEM)
		{
			ItemsOnGroundManager.getInstance();
		}

		if (Config.AUTODESTROY_ITEM_AFTER > 0
		        || Config.HERB_AUTO_DESTROY_TIME > 0)
		{
			ItemsAutoDestroy.getInstance();
		}

		MonsterRace.getInstance();

		_doorTable = DoorTable.getInstance();
		_doorTable.parseData();
		StaticObjects.getInstance();

		_sevenSignsEngine = SevenSigns.getInstance();
		SevenSignsFestival.getInstance();
		_autoSpawnHandler = AutoSpawnHandler.getInstance();
		_autoChatHandler = AutoChatHandler.getInstance();

		// Spawn the Orators/Preachers if in the Seal Validation period.
		_sevenSignsEngine.spawnSevenSignsNPC();

		Olympiad.getInstance();
		Hero.getInstance();
		FaenorScriptEngine.getInstance();
		// Init of a cursed weapon manager
		CursedWeaponsManager.getInstance();

		_log.config("AutoChatHandler: Loaded " + _autoChatHandler.size()
		        + " handlers in total.");
		_log.config("AutoSpawnHandler: Loaded " + _autoSpawnHandler.size()
		        + " handlers in total.");

		AdminCommandHandler.getInstance();
		ChatHandler.getInstance();
		ItemHandler.getInstance();
		SkillHandler.getInstance();
		UserCommandHandler.getInstance();
		VoicedCommandHandler.getInstance();

		AccessLevels.getInstance();
		AdminCommandAccessRights.getInstance();

		if (Config.L2JMOD_ALLOW_WEDDING)
		{
			CoupleManager.getInstance();
		}

		TaskManager.getInstance();

		GmListTable.getInstance();

		// read pet stats from db
		L2PetDataTable.getInstance().loadPetsData();

		Universe.getInstance();

		if (Config.ACCEPT_GEOEDITOR_CONN)
		{
			GeoEditorListener.getInstance();
		}

		_shutdownHandler = Shutdown.getInstance();
		Runtime.getRuntime().addShutdownHook(_shutdownHandler);

		try
		{
			_doorTable.getDoor(24190001).openMe();
			_doorTable.getDoor(24190002).openMe();
			_doorTable.getDoor(24190003).openMe();
			_doorTable.getDoor(24190004).openMe();
			_doorTable.getDoor(23180001).openMe();
			_doorTable.getDoor(23180002).openMe();
			_doorTable.getDoor(23180003).openMe();
			_doorTable.getDoor(23180004).openMe();
			_doorTable.getDoor(23180005).openMe();
			_doorTable.getDoor(23180006).openMe();

			_doorTable.checkAutoOpen();
		}
		catch (NullPointerException e)
		{
			_log.warning("There is errors in your Door.csv file. Update door.csv");
			if (Config.DEBUG)
			{
				e.printStackTrace();
			}
		}

		ForumsBBSManager.getInstance();

		_log.config("IdFactory: Free ObjectID's remaining: "
		        + IdFactory.getInstance().size());

		// initialize the dynamic extension loader
		try
		{
			DynamicExtension.getInstance();
		}
		catch (Exception ex)
		{
			_log.log(Level.WARNING, "DynamicExtension could not be loaded and initialized", ex);
		}

		FloodProtector.getInstance();
		TvTManager.getInstance();
		KnownListUpdateTaskManager.getInstance();
		if (Config.DEADLOCK_DETECTOR)
		{
			_deadDetectThread = new DeadLockDetector(20, DeadLockDetector.RESTART);
		}
		else
		{
			_deadDetectThread = null;
		}
		System.gc();
		long usedHeap = (Runtime.getRuntime().totalMemory()
		        - Runtime.getRuntime().freeMemory()) / 1048576;
		long totalMem = Runtime.getRuntime().maxMemory() / 1048576;
		long geodataMb = 0;
		if (Config.GEODATA > 0)
		{
			File geoDir = new File("./data/geodata/");
			File[] files = geoDir.listFiles((_, name) -> name.endsWith(".l2j"));
			if (files != null)
			{
				for (File f : files)
				{
					geodataMb += f.length();
				}
				geodataMb /= 1048576;
			}
		}
		_log.info("GameServer Started in "
		        + (System.currentTimeMillis() - startTime) / 1000
		        + "s, used memory " + (usedHeap + geodataMb) + " Mb of "
		        + totalMem + " Mb");

		_loginThread = LoginServerThread.getInstance();
		_loginThread.start();

		L2GamePacketHandler gph = new L2GamePacketHandler();
		SelectorConfig sc = new SelectorConfig();
		sc.MAX_SEND_PER_PASS = 12;
		sc.SLEEP_TIME = 20;
		sc.TCP_NODELAY = Config.TCP_NODELAY;

		_selectorThread = new SelectorThread<>(sc, gph, gph, gph, null);

		InetAddress bindAddress = null;
		if (!Config.GAMESERVER_HOSTNAME.equals("*"))
		{
			try
			{
				bindAddress = InetAddress.getByName(Config.GAMESERVER_HOSTNAME);
			}
			catch (UnknownHostException e1)
			{
				_log.severe("WARNING: The GameServer bind address is invalid, using all avaliable IPs. Reason: "
				        + e1.getMessage());
				if (Config.DEVELOPER)
				{
					e1.printStackTrace();
				}
			}
		}

		try
		{
			_selectorThread.openServerSocket(bindAddress, Config.PORT_GAME);
		}
		catch (IOException e)
		{
			_log.severe("FATAL: Failed to open server socket. Reason: "
			        + e.getMessage());
			if (Config.DEVELOPER)
			{
				e.printStackTrace();
			}
			System.exit(1);
		}
		_selectorThread.start();
		_log.config("Maximum Numbers of Connected Players: "
		        + Config.MAXIMUM_ONLINE_USERS);
	}

	private static void loadParallel(Runnable... groups) throws Exception
	{
		if (groups.length == 0)
		{
			return;
		}
		List<Thread> threads = new ArrayList<>(groups.length);
		Throwable[] err = { null };
		for (Runnable group : groups)
		{
			Thread t = Thread.ofVirtual().start(() -> {
				try
				{
					group.run();
				}
				catch (Throwable e)
				{
					synchronized (err)
					{
						if (err[0] == null)
						{
							err[0] = e;
						}
					}
				}
			});
			threads.add(t);
		}
		for (Thread t : threads)
		{
			t.join();
		}
		if (err[0] != null)
		{
			if (err[0] instanceof Exception)
			{
				throw (Exception) err[0];
			}
			throw new RuntimeException(err[0]);
		}
	}

	public static void main(String[] args) throws Exception
	{
		Server.serverMode = Server.MODE_GAMESERVER;
//      Local Constants
		final String LOG_FOLDER = "log"; // Name of folder for log file
		final String LOG_NAME = "./log.cfg"; // Name of log file

		/*** Main ***/
		// Create log folder
		File logFolder = new File(Config.DATAPACK_ROOT, LOG_FOLDER);
		logFolder.mkdir();

		// Create input stream for log file -- or store file data into memory
		InputStream is = new FileInputStream(new File(LOG_NAME));
		LogManager.getLogManager().readConfiguration(is);
		is.close();

		// Server logo (same as LoginServer)
		_log.info("                                                                      ");
		_log.info("                      ###  #####  #####    ##    ##   ##              ");
		_log.info("                     ####  #####  ##      ####   ### ###              ");
		_log.info("                    ## ##   ###   #####  ##  ##  #######              ");
		_log.info("                   ######   ###   ##    ######## ## # ##              ");
		_log.info("                      ###   ###   ##### ###  ### ##   ##              ");
		_log.info("                       www.4teambr.com      bY SageBR!                ");

		// Initialize config
		Config.load();
		L2DatabaseFactory.getInstance();
		Protection.Init();
		gameServer = new GameServer();

		if (Config.IS_TELNET_ENABLED)
		{
			_statusServer = new Status(Server.serverMode);
			_statusServer.start();
		}
		else
		{
			_log.info("Telnet server is currently disabled.");
		}
	}
}
