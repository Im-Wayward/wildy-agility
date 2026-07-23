package com.ddtracker;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
public class DdCall
{
	static class TrackedPlayer
	{
		final String displayName;
		boolean complied;

		TrackedPlayer(String displayName)
		{
			this.displayName = displayName;
		}
	}

	private final String caller;

	@Setter
	private int endTick;

	@Setter
	private boolean finished;

	@Setter
	private int clearTick;

	// key: normalized lowercase name
	private final Map<String, TrackedPlayer> tracked = new LinkedHashMap<>();

	DdCall(String caller, int endTick)
	{
		this.caller = caller;
		this.endTick = endTick;
	}

	int getCompliedCount()
	{
		int n = 0;
		for (TrackedPlayer tp : tracked.values())
		{
			if (tp.complied)
			{
				n++;
			}
		}
		return n;
	}
}
