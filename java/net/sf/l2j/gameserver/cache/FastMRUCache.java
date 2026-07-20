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
package net.sf.l2j.gameserver.cache;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class FastMRUCache<K,V>
{
	private static final long serialVersionUID = 1L;
	private static final int DEFAULT_CAPACITY = 50;
	private static final int DEFAULT_FORGET_TIME = 300000;

	private ConcurrentHashMap<K,CacheNode> _cache = new ConcurrentHashMap<K,CacheNode>();
	private ConcurrentHashMap<K,V> _map;
	private ArrayList<K> _mruList = new ArrayList<K>();
	private int _cacheSize;
	private int _forgetTime;

	class CacheNode
	{
		long _lastModified;
		V _node;

		public CacheNode(V object)
		{
			_lastModified = System.currentTimeMillis();
			_node = object;
		}

		@Override
		public boolean equals(Object object)
		{
			return _node == object;
		}
	}

	public static <K,V> FastMRUCache<K,V> newInstance()
	{
		return new FastMRUCache<K,V>();
	}

	public FastMRUCache()
	{
		this(new ConcurrentHashMap<K,V>(), DEFAULT_CAPACITY, DEFAULT_FORGET_TIME);
	}

	public FastMRUCache(ConcurrentHashMap<K,V> map)
	{
		this(map, DEFAULT_CAPACITY, DEFAULT_FORGET_TIME);
	}

	public FastMRUCache(ConcurrentHashMap<K,V> map, int max)
	{
		this(map, max, DEFAULT_FORGET_TIME);
	}

	public FastMRUCache(ConcurrentHashMap<K,V> map, int max, int forgetTime)
	{
		_map = map;
		_cacheSize = max;
		_forgetTime = forgetTime;
	}

	public synchronized V get(K key)
	{
		V result;

		if (!_cache.containsKey(key))
		{
			if (_mruList.size() >= _cacheSize)
			{
				_cache.remove(_mruList.get(_mruList.size() - 1));
				_mruList.remove(_mruList.size() - 1);
			}

			result = _map.get(key);

			_cache.put(key, new CacheNode(result));
			_mruList.add(0, key);
		}
		else
		{
			CacheNode current = _cache.get(key);

			if ((current._lastModified + _forgetTime) <= System.currentTimeMillis())
			{
				current._lastModified = System.currentTimeMillis();
				current._node = _map.get(key);
				_cache.put(key, current);
			}

			_mruList.remove(key);
			_mruList.add(0, key);

			result = current._node;
		}

		return result;
	}

	public synchronized boolean remove(Object key)
	{
		_cache.remove(key);
		_mruList.remove(key);
		return _map.remove(key) == key;
	}

	public ConcurrentHashMap<K,V> getContentMap()
	{
		return _map;
	}

	public int size()
	{
		return _mruList.size();
	}

	public int capacity()
	{
		return _cacheSize;
	}

	public int getForgetTime()
	{
		return _forgetTime;
	}

	public synchronized void clear()
	{
		_cache.clear();
		_mruList.clear();
		_map.clear();
	}
}
