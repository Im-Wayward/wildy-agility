package com.ddtracker;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * One DD event. A call has no fixed length: it runs until a rank calls it off, until it is
 * cleared by hand, or until {@link #deadlineTick} trips as a backstop for a clear that
 * never came.
 */
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

	/** Tick the call went out in chat, used for the elapsed-time readout. */
	private final int startTick;

	/** Backstop only - a call normally ends because someone cleared it. */
	@Setter
	private int deadlineTick;

	@Setter
	private boolean finished;

	/** Tick at which the finished call stops being displayed. */
	@Setter
	private int clearTick;

	/** How it ended, for the chat summary: "cleared by Torza", "timed out". */
	@Setter
	private String endReason;

	// key: normalized lowercase name
	private final Map<String, TrackedPlayer> tracked = new LinkedHashMap<>();

	DdCall(String caller, int startTick, int deadlineTick)
	{
		this.caller = caller;
		this.startTick = startTick;
		this.deadlineTick = deadlineTick;
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
