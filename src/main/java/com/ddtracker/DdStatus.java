package com.ddtracker;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Snapshot of the running DD call, rendered live in the sidebar panel. */
@Getter
@RequiredArgsConstructor
public class DdStatus
{
	private final String caller;
	private final int elapsedSeconds;

	/** Names on the DD tile right now. */
	private final List<String> onTile;

	/** Tracked names not on the tile right now, whether or not they made it earlier. */
	private final List<String> offTile;

	/** How many tracked players have reached the tile at some point during this call. */
	private final int complied;

	public int getTracked()
	{
		return onTile.size() + offTile.size();
	}
}
