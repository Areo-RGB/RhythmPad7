# StepMania grading (roadmap #7 and #5)

> Verbatim excerpts from the StepMania 5 source that RhythmPad7 ports (`stepmania-pcapp/`, not in git).
> StepMania is (c) Chris Danford, the StepMania development team, et al.; sm-ssc additions (c) the spinal shark collective.
> Both are under an MIT-style license reproduced in full in `LICENSE-StepMania.txt` in this folder.
> Excerpts are unmodified; only the surrounding notes are RhythmPad7's.


## Grade weights and tiers

Grades use their own weights (not dance points). The default theme uses 17 tiers; `GradeTier02IsAllW2s=true` means Tier01 (AAAA) = all W1 and Tier02 (AAA) = all W2-or-better.

`Themes/_fallback/metrics.ini` (lines 1361, 1362, 1363, 1364, 1365, 1366, 1367, 1368, 1369, 1370, 1371, 1372)

```ini
GradeWeightCheckpointHit=2
GradeWeightCheckpointMiss=-8
GradeWeightHeld=IsGame("pump") and 0 or 6
GradeWeightHitMine=-8
GradeWeightMissedHold=0
GradeWeightLetGo=0
GradeWeightMiss=-8
GradeWeightW1=2
GradeWeightW2=2
GradeWeightW3=1
GradeWeightW4=0
GradeWeightW5=-4
```

`Themes/default/metrics.ini` (lines 1123, 1124, 1125, 1127, 1129, 1131, 1133, 1135, 1137, 1139, 1141, 1143, 1145, 1147, 1149, 1151, 1153, 1155, 1157, 1159)

```ini
NumGradeTiersUsed=17
GradeTier01IsAllW2s=false
GradeTier02IsAllW2s=true
GradePercentTier01=1.000000
GradePercentTier02=1.000000
GradePercentTier03=0.98
GradePercentTier04=0.96
GradePercentTier05=0.94
GradePercentTier06=0.92
GradePercentTier07=0.89
GradePercentTier08=0.86
GradePercentTier09=0.83
GradePercentTier10=0.80
GradePercentTier11=0.76
GradePercentTier12=0.72
GradePercentTier13=0.68
GradePercentTier14=0.64
GradePercentTier15=0.60
GradePercentTier16=0.55
GradePercentTier17=-999
```

`Themes/_fallback/metrics.ini` (lines 1123)

```ini
GradeTier02IsFullCombo=false
```

`src/ScoreKeeperNormal.cpp` lines 751–777

```cpp
int ScoreKeeperNormal::TapNoteScoreToGradePoints( TapNoteScore tns, bool bBeginner )
{
	if( !GAMESTATE->ShowW1() && tns == TNS_W1 )
		tns = TNS_W2;

	/* This is used for Oni percentage displays. Grading values are currently in
	 * StageStats::GetGrade. */
	int iWeight = 0;
	switch( tns )
	{
	DEFAULT_FAIL( tns );
	case TNS_None:		iWeight = 0;												break;
	case TNS_AvoidMine:	iWeight = 0;												break;
	case TNS_HitMine:	iWeight = g_iGradeWeight.GetValue(SE_HitMine);				break;
	case TNS_Miss:		iWeight = g_iGradeWeight.GetValue(SE_Miss);					break;
	case TNS_W5:		iWeight = g_iGradeWeight.GetValue(SE_W5);					break;
	case TNS_W4:		iWeight = g_iGradeWeight.GetValue(SE_W4);					break;
	case TNS_W3:		iWeight = g_iGradeWeight.GetValue(SE_W3);					break;
	case TNS_W2:		iWeight = g_iGradeWeight.GetValue(SE_W2);					break;
	case TNS_W1:		iWeight = g_iGradeWeight.GetValue(SE_W1);					break;
	case TNS_CheckpointHit:	iWeight = g_iGradeWeight.GetValue(SE_CheckpointHit);	break;
	case TNS_CheckpointMiss:iWeight = g_iGradeWeight.GetValue(SE_CheckpointMiss);	break;
	}
	if( bBeginner && PREFSMAN->m_bMercifulBeginner )
		iWeight = std::max( 0, iWeight );
	return iWeight;
}
```

`src/ScoreKeeperNormal.cpp` lines 779–793

```cpp
int ScoreKeeperNormal::HoldNoteScoreToGradePoints( HoldNoteScore hns, bool bBeginner )
{
	int iWeight = 0;
	switch( hns )
	{
	DEFAULT_FAIL( hns );
	case HNS_None:	iWeight = 0;									break;
	case HNS_LetGo:	iWeight = g_iGradeWeight.GetValue(SE_LetGo);	break;
	case HNS_Held:	iWeight = g_iGradeWeight.GetValue(SE_Held);		break;
	case HNS_Missed:	iWeight = g_iGradeWeight.GetValue(SE_Missed);		break;
	}
	if( bBeginner && PREFSMAN->m_bMercifulBeginner )
		iWeight = std::max( 0, iWeight );
	return iWeight;
}
```

`src/ScoreKeeperNormal.cpp` lines 661–677

```cpp
int ScoreKeeperNormal::GetPossibleGradePoints( NoteData* nd, const TimingData* td, float fSongSeconds )
{
	/* Note: if W1 timing is disabled or not active (not course mode),
	 * W2 will be used instead. */
	// XXX: That's not actually implemented!
	RadarValues radars;
	NoteDataUtil::CalculateRadarValues( *nd, fSongSeconds, radars );

	int ret = 0;

	ret += int(radars[RadarCategory_TapsAndHolds]) * TapNoteScoreToGradePoints(TNS_W1, false);
	if( GAMESTATE->GetCurrentGame()->m_bTickHolds ) ret += NoteDataUtil::GetTotalHoldTicks( nd, td ) * g_iGradeWeight.GetValue(SE_CheckpointHit);
	ret += int(radars[RadarCategory_Holds]) * HoldNoteScoreToGradePoints(HNS_Held, false);
	ret += int(radars[RadarCategory_Rolls]) * HoldNoteScoreToGradePoints(HNS_Held, false);

	return ret;
}
```

## Computing the grade

`Grade_Tier01` is the best grade; a *larger* enum value is a *worse* grade, so `std::max(grade, Grade_Tier03)` caps a non-full-combo result at Tier03.

`src/PlayerStageStats.cpp` (lines 18)

```cpp
#define GRADE_PERCENT_TIER(i)	THEME->GetMetricF("PlayerStageStats",ssprintf("GradePercent%s",GradeToString((Grade)i).c_str()))
```

`src/PlayerStageStats.cpp` lines 167–180

```cpp
Grade GetGradeFromPercent( float fPercent )
{
	Grade grade = Grade_Failed;

	FOREACH_ENUM( Grade,g)
	{
		if( fPercent >= GRADE_PERCENT_TIER(g) )
		{
			grade = g;
			break;
		}
	}
	return grade;
}
```

`src/PlayerStageStats.cpp` lines 182–244

```cpp
Grade PlayerStageStats::GetGrade() const
{
	if( m_bFailed )
		return Grade_Failed;

	/* XXX: This entire calculation should be in ScoreKeeper, but final evaluation
	 * is tricky since at that point the ScoreKeepers no longer exist. */
	float fActual = 0;

	bool bIsBeginner = false;
	if( m_iStepsPlayed > 0 && !GAMESTATE->IsCourseMode() )
		bIsBeginner = m_vpPossibleSteps[0]->GetDifficulty() == Difficulty_Beginner;

	FOREACH_ENUM( TapNoteScore, tns )
	{
		int iTapScoreValue = ScoreKeeperNormal::TapNoteScoreToGradePoints( tns, bIsBeginner );
		fActual += m_iTapNoteScores[tns] * iTapScoreValue;
		//LOG->Trace( "GetGrade actual: %i * %i", m_iTapNoteScores[tns], iTapScoreValue );
	}

	FOREACH_ENUM( HoldNoteScore, hns )
	{
		int iHoldScoreValue = ScoreKeeperNormal::HoldNoteScoreToGradePoints( hns, bIsBeginner );
		fActual += m_iHoldNoteScores[hns] * iHoldScoreValue;
		//LOG->Trace( "GetGrade actual: %i * %i", m_iHoldNoteScores[hns], iHoldScoreValue );
	}

	//LOG->Trace( "GetGrade: fActual: %f, fPossible: %d", fActual, m_iPossibleGradePoints );

	float fPercent = (m_iPossibleGradePoints == 0) ? 0 : fActual / m_iPossibleGradePoints;

	Grade grade = GetGradeFromPercent( fPercent );

	//LOG->Trace( "GetGrade: Grade: %s, %i", GradeToString(grade).c_str(), GRADE_TIER02_IS_ALL_W2S );

	// TODO: Change these conditions to use Lua instead. -aj
	if( GRADE_TIER02_IS_ALL_W2S )
	{
		if( FullComboOfScore(TNS_W1) )
			return Grade_Tier01;

		if( FullComboOfScore(TNS_W2) )
			return Grade_Tier02;

		grade = std::max( grade, Grade_Tier03 );
	}

	if( GRADE_TIER01_IS_ALL_W2S )
	{
		if( FullComboOfScore(TNS_W2) )
			return Grade_Tier01;
		grade = std::max( grade, Grade_Tier02 );
	}

	if( GRADE_TIER02_IS_FULL_COMBO )
	{
		if( FullComboOfScore(g_MinScoreToMaintainCombo) )
			return Grade_Tier02;
		grade = std::max( grade, Grade_Tier03 );
	}

	return grade;
}
```

`src/PlayerStageStats.cpp` lines 558–594

```cpp
bool PlayerStageStats::FullComboOfScore( TapNoteScore tnsAllGreaterOrEqual ) const
{
	ASSERT( tnsAllGreaterOrEqual >= TNS_W5 );
	ASSERT( tnsAllGreaterOrEqual <= TNS_W1 );

  //if we've set MissCombo to anything besides 0, it's not a full combo
  if( !m_bPlayerCanAchieveFullCombo )
    return false;

	// If missed any holds, then it's not a full combo
	if( m_iHoldNoteScores[HNS_LetGo] > 0 )
		return false;

	//if any checkpoints were missed, it's not a full combo	either
	if( m_iTapNoteScores[TNS_CheckpointMiss] > 0 )
		return false;

	// If has any of the judgments below, then not a full combo
	for( int i=TNS_Miss; i<tnsAllGreaterOrEqual; i++ )
	{
		if( m_iTapNoteScores[i] > 0 )
			return false;
	}

	// hit any mines when they increment the miss combo? It's not a full combo.
	if( g_MineHitIncrementsMissCombo && m_iTapNoteScores[TNS_HitMine] > 0 )
		return false;

	// If has at least one of the judgments equal to or above, then is a full combo.
	for( int i=tnsAllGreaterOrEqual; i<NUM_TapNoteScore; i++ )
	{
		if( m_iTapNoteScores[i] > 0 )
			return true;
	}

	return false;
}
```

## Grade images

Already bundled in the app as `res/drawable-nodpi/sm_grade_tier01.png` … `sm_grade_tier17.png` and `sm_grade_failed.png` (from `Themes/default/Graphics/GradeDisplay Grade Grade_TierNN.png`).
