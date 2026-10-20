# StepMania code RhythmPad7 already ports (charts, timing, scrolling, holds)

> Verbatim excerpts from the StepMania 5 source that RhythmPad7 ports (`stepmania-pcapp/`, not in git).
> StepMania is (c) Chris Danford, the StepMania development team, et al.; sm-ssc additions (c) the spinal shark collective.
> Both are under an MIT-style license reproduced in full in `LICENSE-StepMania.txt` in this folder.
> Excerpts are unmodified; only the surrounding notes are RhythmPad7's.


Kotlin ports live in `core/chart/MsdFile.kt`, `core/chart/TimingData.kt`, `core/gameplay/GameplayRoundState.kt` (`scrollUnits`, hold life) and `core/gameplay/ScrollMod.kt`. Keep them behaviour-identical to these originals.

## .sm / .ssc tokenizer → `MsdFile.kt`

`src/MsdFile.cpp` lines 32–159

```cpp
void MsdFile::ReadBuf( const char *buf, int len, bool bUnescape )
{
	values.reserve( 64 );

	bool ReadingValue=false;
	int i = 0;
	char *cProcessed = new char[len];
	int iProcessedLen = -1;
	while( i < len )
	{
		if( i+1 < len && buf[i] == '/' && buf[i+1] == '/' )
		{
			/* Skip a comment entirely; don't copy the comment to the value/parameter */
			do
			{
				i++;
			} while( i < len && buf[i] != '\n' );

			continue;
		}

		if( ReadingValue && buf[i] == '#' )
		{
			/* Unfortunately, many of these files are missing ;'s.
			 * If we get a # when we thought we were inside a value, assume we
			 * missed the ;.  Back up and end the value. */
			// Make sure this # is the first non-whitespace character on the line.
			bool FirstChar = true;
			int j = iProcessedLen;
			while( j > 0 && cProcessed[j - 1] != '\r' && cProcessed[j - 1] != '\n' )
			{
				if( cProcessed[j - 1] == ' ' || cProcessed[j - 1] == '\t' )
				{
					--j;
					continue;
				}

				FirstChar = false;
				break;
			}

			if( !FirstChar )
			{
				/* We're not the first char on a line.  Treat it as if it were a normal character. */
				cProcessed[iProcessedLen++] = buf[i++];
				continue;
			}

			/* Skip newlines and whitespace before adding the value. */
			iProcessedLen = j;
			while( iProcessedLen > 0 &&
			       ( cProcessed[iProcessedLen - 1] == '\r' || cProcessed[iProcessedLen - 1] == '\n' ||
			         cProcessed[iProcessedLen - 1] == ' ' || cProcessed[iProcessedLen - 1] == '\t' ) )
				--iProcessedLen;

			AddParam( cProcessed, iProcessedLen );
			iProcessedLen = 0;
			ReadingValue=false;
		}

		/* # starts a new value. */
		if( !ReadingValue && buf[i] == '#' )
		{
			AddValue();
			ReadingValue=true;
		}

		if( !ReadingValue )
		{
			if( bUnescape && buf[i] == '\\' )
				i += 2;
			else
				++i;
			continue; /* nothing else is meaningful outside of a value */
		}

		/* : and ; end the current param, if any. */
		if( iProcessedLen != -1 && (buf[i] == ':' || buf[i] == ';') )
			AddParam( cProcessed, iProcessedLen );

		/* # and : begin new params. */
		if( buf[i] == '#' || buf[i] == ':' )
		{
			++i;
			iProcessedLen = 0;
			continue;
		}

		/* ; ends the current value. */
		if( buf[i] == ';' )
		{
			ReadingValue=false;
			++i;
			continue;
		}

		/* We've gone through all the control characters.  All that is left is either an escaped character, 
		 * ie \#, \\, \:, etc., or a regular character. */
		if(buf[i] == '\\' && i < len)
		{
			// If we're escaping the next character, skip the '\\'
			if(bUnescape)
			{
				++i;
			}
			// Otherwise, add the '\\' to cProcessed here, so that
			// whatever character is coming next stays escaped in
			// the resulting value/parameter string 
			// (and most importantly, it doesn't get parsed as a control character)
			// on the next iteration
			else 
			{
				cProcessed[iProcessedLen++] = buf[i++];	
			}
		}
		
		if( i < len )
		{
			cProcessed[iProcessedLen++] = buf[i++];
		}
	}

	/* Add any unterminated value at the very end. */
	if( ReadingValue )
		AddParam( cProcessed, iProcessedLen );

	delete [] cProcessed;
}
```

## Beat ↔ time → `TimingData.kt`

`src/TimingData.cpp` lines 760–1012

```cpp
}

enum
{
	FOUND_WARP,
	FOUND_WARP_DESTINATION,
	FOUND_BPM_CHANGE,
	FOUND_STOP,
	FOUND_DELAY,
	FOUND_STOP_DELAY, // we have these two on the same row.
	FOUND_MARKER,
	NOT_FOUND
};

void FindEvent(int& event_row, int& event_type,
	TimingData::GetBeatStarts& start, float beat, bool find_marker,
	const std::vector<TimingSegment*>& bpms, const std::vector<TimingSegment*>& warps,
	const std::vector<TimingSegment*>& stops, const std::vector<TimingSegment*>& delays)
{
	if(start.is_warping && BeatToNoteRow(start.warp_destination) < event_row)
	{
		event_row= BeatToNoteRow(start.warp_destination);
		event_type= FOUND_WARP_DESTINATION;
	}
	if(start.bpm < bpms.size() && bpms[start.bpm]->GetRow() < event_row)
	{
		event_row= bpms[start.bpm]->GetRow();
		event_type= FOUND_BPM_CHANGE;
	}
	if(start.delay < delays.size() && delays[start.delay]->GetRow() < event_row)
	{
		event_row= delays[start.delay]->GetRow();
		event_type= FOUND_DELAY;
	}
	if(find_marker && BeatToNoteRow(beat) < event_row)
	{
		event_row= BeatToNoteRow(beat);
		event_type= FOUND_MARKER;
	}
	if(start.stop < stops.size() && stops[start.stop]->GetRow() < event_row)
	{
		int tmp_row= event_row;
		event_row= stops[start.stop]->GetRow();
		event_type= (tmp_row == event_row) ? FOUND_STOP_DELAY : FOUND_STOP;
	}
	if(start.warp < warps.size() && warps[start.warp]->GetRow() < event_row)
	{
		event_row= warps[start.warp]->GetRow();
		event_type= FOUND_WARP;
	}
}

void TimingData::GetBeatInternal(GetBeatStarts& start, GetBeatArgs& args,
	unsigned int max_segment) const
{
	const std::vector<TimingSegment*>& bpms= m_avpTimingSegments[SEGMENT_BPM];
	const std::vector<TimingSegment*>& warps= m_avpTimingSegments[SEGMENT_WARP];
	const std::vector<TimingSegment*>& stops= m_avpTimingSegments[SEGMENT_STOP];
	const std::vector<TimingSegment*>& delays= m_avpTimingSegments[SEGMENT_DELAY];
	unsigned int curr_segment= start.bpm+start.warp+start.stop+start.delay;

	float bps= GetBPMAtRow(start.last_row) / 60.0f;
	while(curr_segment < max_segment)
	{
		int event_row= INT_MAX;
		int event_type= NOT_FOUND;
		FindEvent(event_row, event_type, start, 0, false, bpms, warps, stops, delays);
		if(event_type == NOT_FOUND)
		{
			break;
		}
		float time_to_next_event = start.is_warping ? 0 : NoteRowToBeat(event_row - start.last_row) / bps;
		float next_event_time = start.last_time + time_to_next_event;
		if(args.elapsed_time < next_event_time)
		{
			break;
		}
		start.last_time= next_event_time;
		switch(event_type)
		{
			case FOUND_WARP_DESTINATION:
				start.is_warping= false;
				break;
			case FOUND_BPM_CHANGE:
				bps= ToBPM(bpms[start.bpm])->GetBPS();
				++start.bpm;
				++curr_segment;
				break;
			case FOUND_DELAY:
			case FOUND_STOP_DELAY:
				{
					const DelaySegment* ss= ToDelay(delays[start.delay]);
					time_to_next_event= ss->GetPause();
					next_event_time= start.last_time + time_to_next_event;
					if(args.elapsed_time < next_event_time)
					{
						args.freeze_out= false;
						args.delay_out= true;
						args.beat= ss->GetBeat();
						args.bps_out= bps;
						return;
					}
					start.last_time= next_event_time;
					++start.delay;
					++curr_segment;
					if(event_type == FOUND_DELAY)
					{
						break;
					}
					[[fallthrough]];
				}
			case FOUND_STOP:
				{
					const StopSegment* ss= ToStop(stops[start.stop]);
					time_to_next_event= ss->GetPause();
					next_event_time= start.last_time + time_to_next_event;
					if(args.elapsed_time < next_event_time)
					{
						args.freeze_out= true;
						args.delay_out= false;
						args.beat= ss->GetBeat();
						args.bps_out= bps;
						return;
					}
					start.last_time= next_event_time;
					++start.stop;
					++curr_segment;
					break;
				}
			case FOUND_WARP:
				{
					start.is_warping= true;
					const WarpSegment* ws= ToWarp(warps[start.warp]);
					float warp_sum= ws->GetLength() + ws->GetBeat();
					if(warp_sum > start.warp_destination)
					{
						start.warp_destination= warp_sum;
					}
					args.warp_begin_out= event_row;
					args.warp_dest_out= start.warp_destination;
					++start.warp;
					++curr_segment;
					break;
				}
		}
		start.last_row= event_row;
	}
	if(args.elapsed_time == FLT_MAX)
	{
		args.elapsed_time= start.last_time;
	}
	args.beat= NoteRowToBeat(start.last_row) +
		(args.elapsed_time - start.last_time) * bps;
	args.bps_out= bps;
}

void TimingData::GetBeatAndBPSFromElapsedTimeNoOffset(GetBeatArgs& args) const
{
	GetBeatStarts start;
	start.last_time= -m_fBeat0OffsetInSeconds;
	beat_start_lookup_t::const_iterator looked_up_start=
		FindEntryInLookup(m_beat_start_lookup, args.elapsed_time);
	if(looked_up_start != m_beat_start_lookup.end())
	{
		start= looked_up_start->second;
	}
	GetBeatInternal(start, args, INT_MAX);
}

float TimingData::GetElapsedTimeInternal(GetBeatStarts& start, float beat,
	unsigned int max_segment) const
{
	const std::vector<TimingSegment*>& bpms= m_avpTimingSegments[SEGMENT_BPM];
	const std::vector<TimingSegment*>& warps= m_avpTimingSegments[SEGMENT_WARP];
	const std::vector<TimingSegment*>& stops= m_avpTimingSegments[SEGMENT_STOP];
	const std::vector<TimingSegment*>& delays= m_avpTimingSegments[SEGMENT_DELAY];
	unsigned int curr_segment= start.bpm+start.warp+start.stop+start.delay;

	float bps= GetBPMAtRow(start.last_row) / 60.0f;
	bool find_marker= beat < FLT_MAX;

	while(curr_segment < max_segment)
	{
		int event_row= INT_MAX;
		int event_type= NOT_FOUND;
		FindEvent(event_row, event_type, start, beat, find_marker, bpms, warps, stops, delays);
		float time_to_next_event= start.is_warping ? 0 : NoteRowToBeat(event_row - start.last_row) / bps;
		float next_event_time= start.last_time + time_to_next_event;
		start.last_time= next_event_time;
		switch(event_type)
		{
			case FOUND_WARP_DESTINATION:
				start.is_warping= false;
				break;
			case FOUND_BPM_CHANGE:
				bps= ToBPM(bpms[start.bpm])->GetBPS();
				++start.bpm;
				++curr_segment;
				break;
			case FOUND_STOP:
			case FOUND_STOP_DELAY:
				time_to_next_event= ToStop(stops[start.stop])->GetPause();
				next_event_time= start.last_time + time_to_next_event;
				start.last_time= next_event_time;
				++start.stop;
				++curr_segment;
				break;
			case FOUND_DELAY:
				time_to_next_event= ToDelay(delays[start.delay])->GetPause();
				next_event_time= start.last_time + time_to_next_event;
				start.last_time= next_event_time;
				++start.delay;
				++curr_segment;
				break;
			case FOUND_MARKER:
				return start.last_time;
			case FOUND_WARP:
				{
					start.is_warping= true;
					WarpSegment* ws= ToWarp(warps[start.warp]);
					float warp_sum= ws->GetLength() + ws->GetBeat();
					if(warp_sum > start.warp_destination)
					{
						start.warp_destination= warp_sum;
					}
					++start.warp;
					++curr_segment;
					break;
				}
		}
		start.last_row= event_row;
	}
	return start.last_time;
}

float TimingData::GetElapsedTimeFromBeat( float fBeat ) const
{
	return TimingData::GetElapsedTimeFromBeatNoOffset( fBeat )
		- GAMESTATE->m_SongOptions.GetCurrent().m_fMusicRate * PREFSMAN->m_fGlobalOffsetSeconds;
}

float TimingData::GetElapsedTimeFromBeatNoOffset( float fBeat ) const
{
	GetBeatStarts start;
	start.last_time= -m_fBeat0OffsetInSeconds;
	beat_start_lookup_t::const_iterator looked_up_start=
		FindEntryInLookup(m_time_start_lookup, fBeat);
	if(looked_up_start != m_time_start_lookup.end())
	{
		start= looked_up_start->second;
	}
	GetElapsedTimeInternal(start, fBeat, INT_MAX);
	return start.last_time;
```

## SCROLLS / SPEEDS → `TimingData.displayedBeat` / `displayedSpeedPercent`

`src/TimingData.cpp` lines 1015–1028

```cpp
float TimingData::GetDisplayedBeat( float fBeat ) const
{
	float fOutBeat = 0;
	unsigned i;
	const std::vector<TimingSegment *> &scrolls = m_avpTimingSegments[SEGMENT_SCROLL];
	for( i=0; i<scrolls.size()-1; i++ )
	{
		if( scrolls[i+1]->GetBeat() > fBeat )
			break;
		fOutBeat += (scrolls[i+1]->GetBeat() - scrolls[i]->GetBeat()) * ToScroll(scrolls[i])->GetRatio();
	}
	fOutBeat += (fBeat - scrolls[i]->GetBeat()) * ToScroll(scrolls[i])->GetRatio();
	return fOutBeat;
}
```

`src/TimingData.cpp` lines 1133–1186

```cpp
float TimingData::GetDisplayedSpeedPercent( float fSongBeat, float fMusicSeconds ) const
{
	const std::vector<TimingSegment *> &speeds = GetTimingSegments(SEGMENT_SPEED);
	if( speeds.size() == 0 )
	{
#ifdef DEBUG
		LOG->Trace("No speed segments found: using default value.");
#endif
		return 1.0f;
	}

	const int index = GetSegmentIndexAtBeat( SEGMENT_SPEED, fSongBeat );

	const SpeedSegment *seg = ToSpeed(speeds[index]);
	float fStartBeat = seg->GetBeat();
	float fStartTime = GetElapsedTimeFromBeat( fStartBeat ) - GetDelayAtBeat( fStartBeat );
	float fEndTime;
	float fCurTime = fMusicSeconds;

	if( seg->GetUnit() == SpeedSegment::UNIT_SECONDS )
	{
		fEndTime = fStartTime + seg->GetDelay();
	}
	else
	{
		fEndTime = GetElapsedTimeFromBeat( fStartBeat + seg->GetDelay() )
			- GetDelayAtBeat( fStartBeat + seg->GetDelay() );
	}

	SpeedSegment *first = ToSpeed(speeds[0]);

	if( ( index == 0 && first->GetDelay() > 0.0 ) && fCurTime < fStartTime )
	{
		return 1.0f;
	}
	else if( fEndTime >= fCurTime && ( index > 0 || first->GetDelay() > 0.0 ) )
	{
		const float fPriorSpeed = (index == 0) ? 1 :
			ToSpeed(speeds[index-1])->GetRatio();

		float fTimeUsed = fCurTime - fStartTime;
		float fDuration = fEndTime - fStartTime;
		float fRatioUsed = fDuration == 0.0 ? 1 : fTimeUsed / fDuration;

		float fDistance = fPriorSpeed - seg->GetRatio();
		float fRatioNeed = fRatioUsed * -fDistance;
		return (fPriorSpeed + fRatioNeed);
	}
	else
	{
		return seg->GetRatio();
	}

}
```

## Arrow position → `GameplayRoundState.scrollUnits`

Only the part up to the accel/effect mods is ported (RhythmPad7 has no Boost/Brake/Wave mods).

`src/ArrowEffects.cpp` lines 464–516

```cpp
float ArrowEffects::GetYOffset( const PlayerState* pPlayerState, int iCol, float fNoteBeat, float &fPeakYOffsetOut, bool &bIsPastPeakOut, bool bAbsolute )
{
	// Default values that are returned if boomerang is off.
	fPeakYOffsetOut = FLT_MAX;
	bIsPastPeakOut = true;

	float fYOffset = 0;
	const SongPosition &position = pPlayerState->GetDisplayedPosition();

	float fSongBeat = position.m_fSongBeatVisible;

	Steps *pCurSteps = GAMESTATE->m_pCurSteps[pPlayerState->m_PlayerNumber];

	/* Usually, fTimeSpacing is 0 or 1, in which case we use entirely beat spacing or
	 * entirely time spacing (respectively). Occasionally, we tween between them. */
	if( curr_options->m_fTimeSpacing != 1.0f )
	{
		if( GAMESTATE->m_bInStepEditor ) {
			// Use constant spacing in step editor
			fYOffset = fNoteBeat - fSongBeat;
		} else {
			fYOffset = GetDisplayedBeat(pPlayerState, fNoteBeat) - GetDisplayedBeat(pPlayerState, fSongBeat);
			fYOffset *= pCurSteps->GetTimingData()->GetDisplayedSpeedPercent(
								     position.m_fSongBeatVisible,
								     position.m_fMusicSecondsVisible );
		}
		fYOffset *= 1 - curr_options->m_fTimeSpacing;
	}

	if( curr_options->m_fTimeSpacing != 0.0f )
	{
		float fSongSeconds = pPlayerState->m_Position.m_fMusicSecondsVisible;
		float fNoteSeconds = pCurSteps->GetTimingData()->GetElapsedTimeFromBeat(fNoteBeat);
		float fSecondsUntilStep = fNoteSeconds - fSongSeconds;
		float fBPM = curr_options->m_fScrollBPM;
		float fBPS = fBPM/60.f / GAMESTATE->m_SongOptions.GetCurrent().m_fMusicRate;
		float fYOffsetTimeSpacing = fSecondsUntilStep * fBPS;
		fYOffset += fYOffsetTimeSpacing * curr_options->m_fTimeSpacing;
	}

	// TODO: If we allow noteskins to have metricable row spacing
	// (per issue 24), edit this to reflect that. -aj
	fYOffset *= ARROW_SPACING;

	// Factor in scroll speed
	float fScrollSpeed = curr_options->m_fScrollSpeed;
	if(curr_options->m_fMaxScrollBPM != 0)
	{
		fScrollSpeed= curr_options->m_fMaxScrollBPM /
			(pPlayerState->m_fReadBPM * GAMESTATE->m_SongOptions.GetCurrent().m_fMusicRate);
	}

	// don't mess with the arrows after they've crossed 0
```

## Hold and roll life → `GameplayRoundState.advanceTime`

`src/Player.cpp` lines 1355–1555

```cpp
	}

	if( bInitiatedNote && fLife != 0 && bHeadJudged )
	{
		//LOG->Trace("[Player::UpdateHoldNotes] initiated note, fLife != 0");
		/* This hold note is not judged and we stepped on its head.
		 * Update iLastHeldRow. Do this even if we're a little beyond the end
		 * of the hold note, to make sure iLastHeldRow is clamped to iEndRow
		 * if the hold note is held all the way. */
		for (TrackRowTapNote const &trtn : vTN)
		{
			TapNote &tn = *trtn.pTN;
			int iEndRow = iStartRow + tn.iDuration;

			//LOG->Trace(ssprintf("trying for min between iSongRow (%i) and iEndRow (%i) (duration %i)",iSongRow,iEndRow,tn.iDuration));
			tn.HoldResult.iLastHeldRow = std::min( iSongRow, iEndRow );
		}
	}

	// If the song beat is in the range of this hold:
	if( iStartRow <= iSongRow  &&  iStartRow <= iMaxEndRow && bHeadJudged )
	{
		switch( subType )
		{
		case TapNoteSubType_Hold:
			for (TrackRowTapNote const &trtn : vTN)
			{
				TapNote &tn = *trtn.pTN;

				// set hold flag so NoteField can do intelligent drawing
				tn.HoldResult.bHeld = bIsHoldingButton && bInitiatedNote;
				tn.HoldResult.bActive = bInitiatedNote;
			}

			if( bInitiatedNote && bIsHoldingButton )
			{
				//LOG->Trace("bInitiatedNote && bIsHoldingButton; Increasing hold life to MAX_HOLD_LIFE");
				// Increase life
				fLife = MAX_HOLD_LIFE; // was 1 -aj
			}
			else
			{
				/*
				LOG->Trace("Checklist:");
				if(bInitiatedNote)
					LOG->Trace("[X] Initiated Note");
				else
					LOG->Trace("[ ] Initiated Note");

				if(bIsHoldingButton)
					LOG->Trace("[X] Holding Button");
				else
					LOG->Trace("[ ] Holding Button");
				*/

				TimingWindow window = m_bTickHolds ? TW_Checkpoint : TW_Hold;
				//LOG->Trace("fLife before minus: %f",fLife);
				fLife -= fDeltaTime / GetWindowSeconds(window);
				//LOG->Trace("fLife before clamp: %f",fLife);
				fLife = std::max(0.0f, fLife);
				//LOG->Trace("fLife after: %f",fLife);
			}
			break;
		case TapNoteSubType_Roll:
			for (TrackRowTapNote const &trtn : vTN)
			{
				TapNote &tn = *trtn.pTN;
				tn.HoldResult.bHeld = true;
				tn.HoldResult.bActive = bInitiatedNote;
			}

			// give positive life in Step(), not here.

			// Decrease life
			fLife -= fDeltaTime/GetWindowSeconds(TW_Roll);
			fLife = std::max( fLife, 0.0f );	// clamp
			break;
		/*
		case TapNoteSubType_Mine:
			break;
		*/
		default:
			FAIL_M(ssprintf("Invalid tap note subtype: %i", subType));
		}
	}

	// TODO: Cap the active time passed to the score keeper to the actual start time and end time of the hold.
	if( vTN[0].pTN->HoldResult.bActive )
	{
		float fSecondsActiveSinceLastUpdate = fDeltaTime * GAMESTATE->m_SongOptions.GetCurrent().m_fMusicRate;
		if( m_pPrimaryScoreKeeper )
			m_pPrimaryScoreKeeper->HandleHoldActiveSeconds( fSecondsActiveSinceLastUpdate );
		if( m_pSecondaryScoreKeeper )
			m_pSecondaryScoreKeeper->HandleHoldActiveSeconds( fSecondsActiveSinceLastUpdate );
	}

	// check for LetGo. If the head was missed completely, don't count an LetGo.
	/* Why? If you never step on the head, then it will be left as HNS_None,
	 * which doesn't seem correct. */
	if( IMMEDIATE_HOLD_LET_GO )
	{
		if( bInitiatedNote && fLife == 0 && bHeadJudged )	// the player has not pressed the button for a long time!
		{
			//LOG->Trace("LetGo from life == 0 (did initiate hold)");
			hns = HNS_LetGo;
		}
	}

	// score hold notes that have passed
	if( iSongRow >= iMaxEndRow && bHeadJudged )
	{
		bool bLetGoOfHoldNote = false;

		/* Score rolls that end with fLife == 0 as LetGo, even if
		 * m_bTickHolds is on. Rolls don't have iCheckpointsMissed set, so,
		 * unless we check Life == 0, rolls would always be scored as Held. */
		bool bAllowHoldCheckpoints;
		switch( subType )
		{
		DEFAULT_FAIL( subType );
		case TapNoteSubType_Hold:
			bAllowHoldCheckpoints = true;
			break;
		case TapNoteSubType_Roll:
			bAllowHoldCheckpoints = false;
			break;
		/*
		case TapNoteSubType_Mine:
			bAllowHoldCheckpoints = true;
			break;
		*/
		}

		if( m_bTickHolds  &&  bAllowHoldCheckpoints )
		{
			//LOG->Trace("(hold checkpoints are allowed and enabled.)");
			int iCheckpointsHit = 0;
			int iCheckpointsMissed = 0;
			for (TrackRowTapNote const &v : vTN)
			{
				iCheckpointsHit += v.pTN->HoldResult.iCheckpointsHit;
				iCheckpointsMissed += v.pTN->HoldResult.iCheckpointsMissed;
			}
			bLetGoOfHoldNote = iCheckpointsMissed > 0 || iCheckpointsHit == 0;

			// TRICKY: If the hold is so short that it has no checkpoints,
			// then mark it as Held if the head was stepped on.
			if( iCheckpointsHit == 0  &&  iCheckpointsMissed == 0 )
				bLetGoOfHoldNote = !bSteppedOnHead;

			/*
			if(bLetGoOfHoldNote)
				LOG->Trace("let go of hold note, life is 0");
			else
				LOG->Trace("did not let go of hold note :D");
			*/
		}
		else
		{
			//LOG->Trace("(hold checkpoints disabled.)");
			bLetGoOfHoldNote = fLife == 0;
			/*
			if(bLetGoOfHoldNote)
				LOG->Trace("let go of hold note, life is 0");
			else
				LOG->Trace("did not let go of hold note :D");
			*/
		}

		if( bInitiatedNote )
		{
			if(!bLetGoOfHoldNote)
			{
				//LOG->Trace("initiated note and didn't let go");
				fLife = 1; // xxx: should be MAX_HOLD_LIFE instead? -aj
				hns = HNS_Held;
				bool bBright = m_pPlayerStageStats && m_pPlayerStageStats->m_iCurCombo>(unsigned int)BRIGHT_GHOST_COMBO_THRESHOLD;
				if( m_pNoteField )
				{
					for (TrackRowTapNote const &trtn : vTN)
					{
						int iTrack = trtn.iTrack;
						m_pNoteField->DidHoldNote( iTrack, HNS_Held, bBright );	// bright ghost flash
					}
				}
			}

			else
			{
				//LOG->Trace("initiated note and let go :(");
			}
		}
		else if( SCORE_MISSED_HOLDS_AND_ROLLS )
		{
			hns = HNS_LetGo;
		}
		else
		{
			hns = HNS_Missed;
		}
	}
```
