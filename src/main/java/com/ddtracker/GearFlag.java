package com.ddtracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** A channel member currently showing a gear problem, as listed in the sidebar panel. */
@Getter
@RequiredArgsConstructor
public class GearFlag
{
	private final String displayName;

	/** "NAKED", "Unarmed" or "No bow/staff" - the same label drawn over their head. */
	private final String warning;

	boolean isNaked()
	{
		return "NAKED".equals(warning);
	}
}
