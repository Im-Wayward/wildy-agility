package com.ddtracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PlayerStats
{
	private final String displayName;
	private int callsSeen;
	private int ddCount;
	private boolean lastComplied;

	void record(boolean complied)
	{
		callsSeen++;
		lastComplied = complied;
		if (complied)
		{
			ddCount++;
		}
	}

	public int getMissed()
	{
		return callsSeen - ddCount;
	}

	public int getCompliancePct()
	{
		return callsSeen == 0 ? 100 : Math.round(100f * ddCount / callsSeen);
	}
}
