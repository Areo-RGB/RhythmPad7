# StepMania judgment windows, combo and dance points (roadmap #7)

> Verbatim excerpts from the StepMania 5 source that RhythmPad7 ports (`stepmania-pcapp/`, not in git).
> StepMania is (c) Chris Danford, the StepMania development team, et al.; sm-ssc additions (c) the spinal shark collective.
> Both are under an MIT-style license reproduced in full in `LICENSE-StepMania.txt` in this folder.
> Excerpts are unmodified; only the surrounding notes are RhythmPad7's.


## Timing windows

Default window sizes in seconds (each is ± around the exact note time), then scaled/offset by two preferences (both 1.0 / 0 by default).

`src/Player.cpp` lines 50–98

```cpp
void TimingWindowSecondsInit( std::size_t /*TimingWindow*/ i, RString &sNameOut, float &defaultValueOut );

/**
 * @brief Helper class to ensure that each row is only judged once without taking too much memory.
 */
class JudgedRows
{
	std::vector<bool> m_vRows;
	int m_iStart;
	int m_iOffset;

	void Resize( std::size_t iMin )
	{
		std::size_t iNewSize = std::max( 2*m_vRows.size(), iMin );
		std::vector<bool> vNewRows( m_vRows.begin() + m_iOffset, m_vRows.end() );
		vNewRows.reserve( iNewSize );
		vNewRows.insert( vNewRows.end(), m_vRows.begin(), m_vRows.begin() + m_iOffset );
		vNewRows.resize( iNewSize, false );
		m_vRows.swap( vNewRows );
		m_iOffset = 0;
	}
public:
	JudgedRows() : m_iStart(0), m_iOffset(0) { Resize( 32 ); }
	// Returns true if the row has already been judged.
	bool JudgeRow( int iRow )
	{
		if( iRow < m_iStart )
			return true;
		if( iRow >= m_iStart+int(m_vRows.size()) )
			Resize( iRow+1-m_iStart );
		const int iIndex = (iRow - m_iStart + m_iOffset) % m_vRows.size();
		const bool ret = m_vRows[iIndex];
		m_vRows[iIndex] = true;
		while( m_vRows[m_iOffset] )
		{
			m_vRows[m_iOffset] = false;
			++m_iStart;
			if( ++m_iOffset >= int(m_vRows.size()) )
				m_iOffset -= m_vRows.size();
		}
		return ret;
	}
	void Reset( int iStart )
	{
		m_iStart = iStart;
		m_iOffset = 0;
		m_vRows.assign( m_vRows.size(), false );
	}
};
```

`src/Player.cpp` lines 163–165

```cpp
static Preference<float> m_fTimingWindowScale	( "TimingWindowScale",		1.0f );
static Preference<float> m_fTimingWindowAdd	( "TimingWindowAdd",		0 );
static Preference1D<float> m_fTimingWindowSeconds( TimingWindowSecondsInit, NUM_TimingWindow );
```

`src/Player.cpp` lines 245–251

```cpp
float Player::GetWindowSeconds( TimingWindow tw )
{
	float fSecs = m_fTimingWindowSeconds[tw];
	fSecs *= m_fTimingWindowScale;
	fSecs += m_fTimingWindowAdd;
	return fSecs;
}
```

## Turning a step's timing error into a judgment

`fSecondsFromExact` is the absolute error. The first window that contains it wins; anything beyond W5 is not judged by the step (the note later becomes a Miss when it scrolls past W5).

`src/Player.cpp` lines 2312–2322

```cpp
				if( (pTN->type == TapNoteType_Lift) == bRelease )
				{
					const auto &disabledWindows = m_pPlayerState->m_PlayerOptions.GetCurrent().m_twDisabledWindows;
					if(	fSecondsFromExact <= GetWindowSeconds(TW_W1) && !disabledWindows[TW_W1] )	score = TNS_W1;
					else if( fSecondsFromExact <= GetWindowSeconds(TW_W2) && !disabledWindows[TW_W2] )	score = TNS_W2;
					else if( fSecondsFromExact <= GetWindowSeconds(TW_W3) && !disabledWindows[TW_W3] )	score = TNS_W3;
					else if( fSecondsFromExact <= GetWindowSeconds(TW_W4) && !disabledWindows[TW_W4] )	score = TNS_W4;
					else if( fSecondsFromExact <= GetWindowSeconds(TW_W5) && !disabledWindows[TW_W5] )	score = TNS_W5;
				}
				break;
			}
```

## Combo rules

Theme functions that pick which judgments continue/maintain the combo for the `dance` game (W3 = Great or better keeps the combo; W4/W5/Miss break it).

`Themes/_fallback/Scripts/03 Gameplay.lua` lines 191–198

```lua
function ComboContinue()
	local Continue = {
		dance = GAMESTATE:GetPlayMode() == "PlayMode_Oni" and "TapNoteScore_W2" or "TapNoteScore_W3",
		pump = "TapNoteScore_W3",
		beat = "TapNoteScore_W3",
		kb7 = "TapNoteScore_W3",
		para = "TapNoteScore_W4"
	}
```

`Themes/_fallback/Scripts/03 Gameplay.lua` lines 202–209

```lua
function ComboMaintain()
	local Maintain = {
		dance = "TapNoteScore_W3",
		pump = "TapNoteScore_W4",
		beat = "TapNoteScore_W3",
		kb7 = "TapNoteScore_W3",
		para = "TapNoteScore_W4"
	}
```

`Themes/_fallback/metrics.ini` (lines 574, 575)

```ini
MinScoreToContinueCombo=ComboContinue()
MinScoreToMaintainCombo=ComboMaintain()
```

`src/ScoreKeeperNormal.cpp` lines 454–480

```cpp
void ScoreKeeperNormal::HandleComboInternal( int iNumHitContinueCombo, int iNumHitMaintainCombo, int iNumBreakCombo, int iRow )
{
	// Regular combo
	if( m_ComboIsPerRow )
	{
		iNumHitContinueCombo = std::min( iNumHitContinueCombo, 1 );
		iNumHitMaintainCombo = std::min( iNumHitMaintainCombo, 1 );
		iNumBreakCombo = std::min( iNumBreakCombo, 1 );
	}

	if( iNumHitContinueCombo > 0 || iNumHitMaintainCombo > 0 )
	{
		m_pPlayerStageStats->m_iCurMissCombo = 0;
	}
	TimingData &td = *GAMESTATE->m_pCurSteps[m_pPlayerState->m_PlayerNumber]->GetTimingData();
	if( iNumBreakCombo == 0 )
	{
		int multiplier = ( iRow == -1 ? 1 : td.GetComboSegmentAtRow( iRow )->GetCombo() );
		m_pPlayerStageStats->m_iCurCombo += iNumHitContinueCombo * multiplier;
	}
	else
	{
		m_pPlayerStageStats->m_iCurCombo = 0;
		int multiplier = ( iRow == -1 ? 1 : td.GetComboSegmentAtRow(iRow)->GetMissCombo());
		m_pPlayerStageStats->m_iCurMissCombo += ( m_MissComboIsPerRow ? 1 : iNumBreakCombo ) * multiplier;
	}
}
```

`src/ScoreKeeperNormal.cpp` lines 528–608

```cpp
void ScoreKeeperNormal::HandleTapRowScore( const NoteData &nd, int iRow )
{
	int iNumHitContinueCombo, iNumHitMaintainCombo, iNumBreakCombo;
	GetRowCounts( nd, iRow, iNumHitContinueCombo, iNumHitMaintainCombo, iNumBreakCombo );

	int iNumTapsInRow = iNumHitContinueCombo + iNumHitMaintainCombo + iNumBreakCombo;
	if( iNumTapsInRow <= 0 )
		return;

	m_iNumNotesHitThisRow = iNumTapsInRow;

	TapNoteScore scoreOfLastTap = NoteDataWithScoring::LastTapNoteWithResult( nd, iRow ).result.tns;
	HandleTapNoteScoreInternal( scoreOfLastTap, TNS_W1, iRow );

	if ( GAMESTATE->GetCurrentGame()->m_bCountNotesSeparately )
	{
		HandleComboInternal( iNumHitContinueCombo, iNumHitMaintainCombo, iNumBreakCombo, iRow );
	}
	else
	{
		HandleRowComboInternal( scoreOfLastTap, iNumTapsInRow, iRow ); //This should work?
	}

	if( m_pPlayerState->m_PlayerNumber != PLAYER_INVALID )
		MESSAGEMAN->Broadcast( enum_add2(Message_CurrentComboChangedP1,m_pPlayerState->m_PlayerNumber) );

	AddTapRowScore( scoreOfLastTap, nd, iRow );		// only score once per row

	// handle combo logic
#ifndef DEBUG
	if( (GamePreferences::m_AutoPlay != PC_HUMAN || m_pPlayerState->m_PlayerOptions.GetCurrent().m_fPlayerAutoPlay != 0)
		&& !GAMESTATE->m_bDemonstrationOrJukebox )	// cheaters always prosper >:D -aj comment edit
	{
		m_cur_toasty_combo = 0;
		return;
	}
#endif //DEBUG

	// Toasty combo
	if(scoreOfLastTap >= m_toasty_min_tns)
	{
		m_cur_toasty_combo += iNumTapsInRow;
		if(m_cur_toasty_combo > m_next_toasty_at &&
			!GAMESTATE->m_bDemonstrationOrJukebox)
		{
			++m_cur_toasty_level;
			// Broadcast the message before posting the screen message so that the
			// transition layer can catch the message to know the level and respond
			// accordingly. -Kyz
			Message msg("ToastyAchieved");
			msg.SetParam("PlayerNumber", m_pPlayerState->m_PlayerNumber);
			msg.SetParam("ToastyCombo", m_cur_toasty_combo);
			msg.SetParam("Level", m_cur_toasty_level);
			MESSAGEMAN->Broadcast(msg);
			SCREENMAN->PostMessageToTopScreen(SM_PlayToasty, 0);
			// TODO: keep a pointer to the Profile.  Don't index with m_PlayerNumber
			// TODO: Make the profile count the level and combo of the toasty. -Kyz
			PROFILEMAN->IncrementToastiesCount(m_pPlayerState->m_PlayerNumber);
			m_next_toasty_at= CalcNextToastyAt(m_cur_toasty_level);
		}
	}
	else
	{
		m_cur_toasty_combo = 0;
		m_cur_toasty_level= 0;
		m_next_toasty_at= 0;
		m_next_toasty_at= CalcNextToastyAt(m_cur_toasty_level);
		Message msg("ToastyDropped");
		msg.SetParam( "PlayerNumber", m_pPlayerState->m_PlayerNumber );
		MESSAGEMAN->Broadcast(msg);
	}

	// TODO: Remove indexing with PlayerNumber
	PlayerNumber pn = m_pPlayerState->m_PlayerNumber;
	float offset = NoteDataWithScoring::LastTapNoteWithResult( nd, iRow ).result.fTapNoteOffset;
	Message msg( "ScoreChanged" );
	msg.SetParam( "PlayerNumber", m_pPlayerState->m_PlayerNumber );
	msg.SetParam( "MultiPlayer", m_pPlayerState->m_mp );
	msg.SetParam( "ToastyCombo", m_cur_toasty_combo );
	MESSAGEMAN->Broadcast( msg );
}
```

## Jumps: one judgment per row

A row (all arrows at the same beat) is scored once, after every arrow in it is judged. If any arrow in the row is a Miss the row is a Miss; otherwise the row takes the result of the arrow with the largest timing offset. Dance points and judgment counts are added once per row; the `dance` combo adds the number of arrows in the row (`ComboIsPerRow` is false for dance).

`src/NoteDataWithScoring.cpp` lines 25–54

```cpp
int LastTapNoteScoreTrack( const NoteData &in, unsigned iRow, PlayerNumber pn )
{
	float scoretime = -9999;
	int best_track = -1;
	for( int t=0; t<in.GetNumTracks(); t++ )
	{
		/* Skip empty tracks and mines */
		const TapNote &tn = in.GetTapNote( t, iRow );
		if (tn.type == TapNoteType_Empty ||
			tn.type == TapNoteType_Mine ||
			tn.type == TapNoteType_Fake ||
			tn.type == TapNoteType_AutoKeysound)
			continue;
		if( tn.pn != PLAYER_INVALID && tn.pn != pn && pn != PLAYER_INVALID )
			continue;

		TapNoteScore tns = tn.result.tns;

		if( tns == TNS_Miss || tns == TNS_None )
			return t;

		float tm = tn.result.fTapNoteOffset;
		if(tm < scoretime) continue;

		scoretime = tm;
		best_track = t;
	}

	return best_track;
}
```

`src/ScoreKeeperNormal.cpp` lines 428–452

```cpp
void ScoreKeeperNormal::HandleTapNoteScoreInternal( TapNoteScore tns, TapNoteScore maximum, int row )
{
	// Update dance points.
	if( !m_pPlayerStageStats->m_bFailed )
		m_pPlayerStageStats->m_iActualDancePoints += TapNoteScoreToDancePoints( tns );

	// update judged row totals. Respect Combo segments here.
	TimingData &td = *GAMESTATE->m_pCurSteps[m_pPlayerState->m_PlayerNumber]->GetTimingData();
	ComboSegment *cs = td.GetComboSegmentAtRow(row);
	if (tns == TNS_CheckpointHit || tns >= m_MinScoreToContinueCombo)
	{
		m_pPlayerStageStats->m_iTapNoteScores[tns] += cs->GetCombo();
	}
	else if (tns == TNS_CheckpointMiss || tns < m_MinScoreToMaintainCombo)
	{
		m_pPlayerStageStats->m_iTapNoteScores[tns] += cs->GetMissCombo();
	}
	else
	{
		m_pPlayerStageStats->m_iTapNoteScores[tns] += 1;
	}

	// increment the current total possible dance score
	m_pPlayerStageStats->m_iCurPossibleDancePoints += TapNoteScoreToDancePoints( maximum );
}
```

`src/ScoreKeeperNormal.cpp` lines 482–505

```cpp
void ScoreKeeperNormal::HandleRowComboInternal( TapNoteScore tns, int iNumTapsInRow, int iRow )
{
	if( m_ComboIsPerRow )
	{
		iNumTapsInRow = std::min( iNumTapsInRow, 1);
	}
	TimingData &td = *GAMESTATE->m_pCurSteps[m_pPlayerState->m_PlayerNumber]->GetTimingData();
	if ( tns >= m_MinScoreToContinueCombo )
	{
		m_pPlayerStageStats->m_iCurMissCombo = 0;
		int multiplier = ( iRow == -1 ? 1 : td.GetComboSegmentAtRow( iRow )->GetCombo() );
		m_pPlayerStageStats->m_iCurCombo += iNumTapsInRow * multiplier;
	}
	else if ( tns < m_MinScoreToMaintainCombo )
	{
		m_pPlayerStageStats->m_iCurCombo = 0;

		if( tns <= m_MaxScoreToIncrementMissCombo )
		{
			int multiplier = ( iRow == -1 ? 1 : td.GetComboSegmentAtRow(iRow)->GetMissCombo());
			m_pPlayerStageStats->m_iCurMissCombo += ( m_MissComboIsPerRow ? 1 : iNumTapsInRow ) * multiplier;
		}
	}
}
```

`Themes/_fallback/Scripts/03 Gameplay.lua` lines 213–237

```lua
function ComboPerRow()
	sGame = CurGameName()
	if sGame == "pump" then
		return true
	elseif GAMESTATE:GetPlayMode() == "PlayMode_Oni" then
		return true
	else
		return false
	end
end

function EvalUsesCheckpointsWithJudgments()
	return (CurGameName() == "pump") and true or false
end

local ComboThresholds = {
	dance	= { Hit = 2, Miss = 2, Fail = -1 },
	pump	= { Hit = 4, Miss = 4, Fail = 51 },
	beat	= { Hit = 1, Miss = 0, Fail = -1 },
	kb7		= { Hit = 1, Miss = 0, Fail = -1 },
	para	= { Hit = 2, Miss = 0, Fail = -1 },
	maniax	= { Hit = 5, Miss = 0, Fail = -1 },
	-------------------------------------------
	default	= { Hit = 2, Miss = 2, Fail = -1 }
}
```

`Themes/_fallback/metrics.ini` (lines 572)

```ini
ComboIsPerRow=ComboPerRow()
```

Possible points count *rows*: the radar value `TapsAndHolds` is incremented once per row (only for the first arrow on it).

`src/NoteDataUtil.cpp` lines 1095–1125

```cpp
			{
				case TapNoteType_Tap:
				case TapNoteType_HoldHead:
					// Lifts have to be counted with taps for them to be added to max dp
					// correctly. -Kyz
				case TapNoteType_Lift:
					// HoldTails and Attacks are counted by IsTap.  But it doesn't
					// make sense to count HoldTails as hittable notes. -Kyz
				case TapNoteType_Attack:
					++out[RadarCategory_Notes];
					++state.num_notes_on_curr_row;
					++total_taps;
					recent_notes.push_back(
						recent_note(curr_row, curr_note.Track()));
					max_notes_in_voltage_window= std::max(recent_notes.size(),
						max_notes_in_voltage_window);
					// If there is one hold active, and one tap on this row, it does
					// not count as a jump.  Hands do need to count the number of
					// holds active though. -Kyz
					switch(state.num_notes_on_curr_row)
					{
						case 1:
							++out[RadarCategory_TapsAndHolds];
							break;
						case 2:
							++out[RadarCategory_Jumps];
							break;
						default:
							break;
					}
					if(curr_note->type == TapNoteType_HoldHead)
```

## Dance points (the percentage shown as "DP%")

Weights per judgment come from metrics; percent = actual ÷ possible dance points, where every tap and hold is worth a W1 / Held when possible.

`Themes/_fallback/metrics.ini` (lines 1349, 1350, 1351, 1352, 1353, 1354, 1355, 1356, 1357, 1358, 1359, 1360)

```ini
PercentScoreWeightCheckpointHit=3
PercentScoreWeightCheckpointMiss=0
PercentScoreWeightHeld=IsGame("pump") and 0 or 3
PercentScoreWeightHitMine=-2
PercentScoreWeightMissedHold=0
PercentScoreWeightLetGo=0
PercentScoreWeightMiss=0
PercentScoreWeightW1=3
PercentScoreWeightW2=2
PercentScoreWeightW3=1
PercentScoreWeightW4=0
PercentScoreWeightW5=0
```

`src/ScoreKeeperNormal.cpp` lines 708–733

```cpp
int ScoreKeeperNormal::TapNoteScoreToDancePoints( TapNoteScore tns, bool bBeginner )
{
	if( !GAMESTATE->ShowW1() && tns == TNS_W1 )
		tns = TNS_W2;

	/* This is used for Oni percentage displays. Grading values are currently in
	 * StageStats::GetGrade. */
	int iWeight = 0;
	switch( tns )
	{
	DEFAULT_FAIL( tns );
	case TNS_None:		iWeight = 0;														break;
	case TNS_HitMine:	iWeight = g_iPercentScoreWeight.GetValue(SE_HitMine);				break;
	case TNS_Miss:		iWeight = g_iPercentScoreWeight.GetValue(SE_Miss);					break;
	case TNS_W5:		iWeight = g_iPercentScoreWeight.GetValue(SE_W5);					break;
	case TNS_W4:		iWeight = g_iPercentScoreWeight.GetValue(SE_W4);					break;
	case TNS_W3:		iWeight = g_iPercentScoreWeight.GetValue(SE_W3);					break;
	case TNS_W2:		iWeight = g_iPercentScoreWeight.GetValue(SE_W2);					break;
	case TNS_W1:		iWeight = g_iPercentScoreWeight.GetValue(SE_W1);					break;
	case TNS_CheckpointHit:	iWeight = g_iPercentScoreWeight.GetValue(SE_CheckpointHit);		break;
	case TNS_CheckpointMiss:iWeight = g_iPercentScoreWeight.GetValue(SE_CheckpointMiss);	break;
	}
	if( bBeginner && PREFSMAN->m_bMercifulBeginner )
		iWeight = std::max( 0, iWeight );
	return iWeight;
}
```

`src/ScoreKeeperNormal.cpp` lines 735–749

```cpp
int ScoreKeeperNormal::HoldNoteScoreToDancePoints( HoldNoteScore hns, bool bBeginner )
{
	int iWeight = 0;
	switch( hns )
	{
	DEFAULT_FAIL( hns );
	case HNS_None:	iWeight = 0;										break;
	case HNS_LetGo:	iWeight = g_iPercentScoreWeight.GetValue(SE_LetGo);	break;
	case HNS_Held:	iWeight = g_iPercentScoreWeight.GetValue(SE_Held);	break;
	case HNS_Missed:	iWeight = g_iPercentScoreWeight.GetValue(SE_Missed);	break;
	}
	if( bBeginner && PREFSMAN->m_bMercifulBeginner )
		iWeight = std::max( 0, iWeight );
	return iWeight;
}
```

`src/ScoreKeeperNormal.cpp` lines 633–649

```cpp
int ScoreKeeperNormal::GetPossibleDancePoints( NoteData* nd, const TimingData* td, float fSongSeconds )
{
	/* Note: If W1 timing is disabled or not active (not course mode),
	 * W2 will be used instead. */
	// XXX: That's not actually implemented!
	RadarValues radars;
	NoteDataUtil::CalculateRadarValues( *nd, fSongSeconds, radars );

	int ret = 0;

	ret += int(radars[RadarCategory_TapsAndHolds]) * TapNoteScoreToDancePoints(TNS_W1, false);
	if( GAMESTATE->GetCurrentGame()->m_bTickHolds ) ret += NoteDataUtil::GetTotalHoldTicks( nd, td ) * g_iPercentScoreWeight.GetValue(SE_CheckpointHit);
	ret += int(radars[RadarCategory_Holds]) * HoldNoteScoreToDancePoints(HNS_Held, false);
	ret += int(radars[RadarCategory_Rolls]) * HoldNoteScoreToDancePoints(HNS_Held, false);

	return ret;
}
```

`src/PlayerStageStats.cpp` lines 246–274

```cpp
float PlayerStageStats::MakePercentScore( int iActual, int iPossible )
{
	if( iPossible == 0 )
		return 0; // div/0

	if( iActual == iPossible )
		return 1;	// correct for rounding error

	// This can happen in battle, with transform attacks.
	//ASSERT_M( iActual <= iPossible, ssprintf("%i/%i", iActual, iPossible) );

	float fPercent =  iActual / (float)iPossible;

	// don't allow negative
	fPercent = std::max(0.0f, fPercent);

	int iPercentTotalDigits = 3 + CommonMetrics::PERCENT_SCORE_DECIMAL_PLACES;	// "100" + "." + "00"

	// TRICKY: printf will round, but we want to truncate. Otherwise, we may display
	// a percent score that's too high and doesn't match up with the calculated grade.
	float fTruncInterval = std::pow( 0.1f, (float)iPercentTotalDigits-1 );

	// TRICKY: ftruncf is rounding 1.0000000 to 0.99990004. Give a little boost
	// to fPercentDancePoints to correct for this.
	fPercent += 0.000001f;

	fPercent = ftruncf( fPercent, fTruncInterval );
	return fPercent;
}
```
