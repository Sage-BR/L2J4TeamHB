/*
 * L2jFrozen Project - www.l2jfrozen.com 
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package net.sf.l2j;

import java.util.StringTokenizer;

import java.util.concurrent.ConcurrentHashMap;


public class ClassMasterSettings
{
	private final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Integer>> _claimItems;
	private final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Integer>> _rewardItems;
	private final ConcurrentHashMap<Integer, Boolean> _allowedClassChange;
	
	public ClassMasterSettings(final String _configLine)
	{
		_claimItems = new ConcurrentHashMap<>();
		_rewardItems = new ConcurrentHashMap<>();
		_allowedClassChange = new ConcurrentHashMap<>();
		if (_configLine != null)
		{
			parseConfigLine(_configLine.trim());
		}
	}
	
	private void parseConfigLine(final String _configLine)
	{
		final StringTokenizer st = new StringTokenizer(_configLine, ";");
		
		while (st.hasMoreTokens())
		{
			final int job = Integer.parseInt(st.nextToken());
			
			_allowedClassChange.put(job, true);
			
			ConcurrentHashMap<Integer, Integer> _items = new ConcurrentHashMap<>();
			
			if (st.hasMoreTokens())
			{
				final StringTokenizer st2 = new StringTokenizer(st.nextToken(), "[],");
				
				while (st2.hasMoreTokens())
				{
					final StringTokenizer st3 = new StringTokenizer(st2.nextToken(), "()");
					final int _itemId = Integer.parseInt(st3.nextToken());
					final int _quantity = Integer.parseInt(st3.nextToken());
					_items.put(_itemId, _quantity);
				}
			}
			
			_claimItems.put(job, _items);
			_items = new ConcurrentHashMap<>();
			
			if (st.hasMoreTokens())
			{
				final StringTokenizer st2 = new StringTokenizer(st.nextToken(), "[],");
				
				while (st2.hasMoreTokens())
				{
					final StringTokenizer st3 = new StringTokenizer(st2.nextToken(), "()");
					final int _itemId = Integer.parseInt(st3.nextToken());
					final int _quantity = Integer.parseInt(st3.nextToken());
					_items.put(_itemId, _quantity);
				}
			}
			_rewardItems.put(job, _items);
		}
	}
	
	public boolean isAllowed(final int job)
	{
		if (_allowedClassChange == null)
			return false;
		if (_allowedClassChange.containsKey(job))
			return _allowedClassChange.get(job);
		return false;
	}
	
	public ConcurrentHashMap<Integer, Integer> getRewardItems(final int job)
	{
		if (_rewardItems.containsKey(job))
			return _rewardItems.get(job);
		return null;
	}
	
	public ConcurrentHashMap<Integer, Integer> getRequireItems(final int job)
	{
		if (_claimItems.containsKey(job))
			return _claimItems.get(job);
		return null;
	}
}