# StepMania high scores (roadmap #5)

> Verbatim excerpts from the StepMania 5 source that RhythmPad7 ports (`stepmania-pcapp/`, not in git).
> StepMania is (c) Chris Danford, the StepMania development team, et al.; sm-ssc additions (c) the spinal shark collective.
> Both are under an MIT-style license reproduced in full in `LICENSE-StepMania.txt` in this folder.
> Excerpts are unmodified; only the surrounding notes are RhythmPad7's.


RhythmPad7 has one player and stores its own JSON, so only the *rules* matter here: what a high score records and how two scores compare.

`src/HighScore.h` lines 20–110

```cpp
struct HighScore
{
	HighScore();

	/**
	 * @brief Retrieve the name of the player that set the high score.
	 * @return the name of the player. */
	RString	GetName() const;
	/**
	 * @brief Retrieve the grade earned from this score.
	 * @return the grade.
	 */
	Grade GetGrade() const;
	/**
	 * @brief Retrieve the score earned.
	 * @return the score. */
	unsigned int GetScore() const;
	/**
	 * @brief Determine if any judgments were tallied during this run.
	 * @return true if no judgments were recorded, false otherwise. */
	bool IsEmpty() const;
	float GetPercentDP() const;
	/**
	 * @brief Determine how many seconds the player had left in Survival mode.
	 * @return the number of seconds left. */
	float GetSurviveSeconds() const;
	float GetSurvivalSeconds() const;
	unsigned int   GetMaxCombo() const;
	StageAward GetStageAward() const;
	PeakComboAward GetPeakComboAward() const;
	/**
	 * @brief Get the modifiers used for this run.
	 * @return the modifiers. */
	RString GetModifiers() const;
	DateTime GetDateTime() const;
	RString GetPlayerGuid() const;
	RString GetMachineGuid() const;
	int GetProductID() const;
	int GetTapNoteScore( TapNoteScore tns ) const;
	int GetHoldNoteScore( HoldNoteScore tns ) const;
	const RadarValues &GetRadarValues() const;
	float GetLifeRemainingSeconds() const;
	/**
	 * @brief Determine if this score was from a situation that would cause disqualification.
	 * @return true if the score would be disqualified, false otherwise. */
	bool GetDisqualified() const;

	/**
	 * @brief Set the name of the Player that earned the score.
	 * @param sName the name of the Player. */
	void SetName( const RString &sName );
	void SetGrade( Grade g );
	void SetScore( unsigned int iScore );
	void SetPercentDP( float f );
	void SetAliveSeconds( float f );
	void SetMaxCombo( unsigned int i );
	void SetStageAward( StageAward a );
	void SetPeakComboAward( PeakComboAward a );
	void SetModifiers( RString s );
	void SetDateTime( DateTime d );
	void SetPlayerGuid( RString s );
	void SetMachineGuid( RString s );
	void SetProductID( int i );
	void SetTapNoteScore( TapNoteScore tns, int i );
	void SetHoldNoteScore( HoldNoteScore tns, int i );
	void SetRadarValues( const RadarValues &rv );
	void SetLifeRemainingSeconds( float f );
	void SetDisqualified( bool b );

	RString *GetNameMutable();
	const RString *GetNameMutable() const { return const_cast<RString *> (const_cast<HighScore *>(this)->GetNameMutable()); }

	void Unset();

	bool operator<(HighScore const& other) const;
	bool operator>(HighScore const& other) const;
	bool operator<=(HighScore const& other) const;
	bool operator>=(HighScore const& other) const;
	bool operator==(HighScore const& other) const;
	bool operator!=(HighScore const& other) const;

	XNode* CreateNode() const;
	void LoadFromNode( const XNode* pNode );

	RString GetDisplayName() const;

	// Lua
	void PushSelf( lua_State *L );
private:
	HiddenPtr<HighScoreImpl> m_Impl;
};
```

`PercentageScoring` defaults to true, so scores compare by DP% first, then grade:

`src/PrefsManager.cpp` (lines 227)

```cpp
	m_bPercentageScoring		( "PercentageScoring",		true ),
```

`src/HighScore.cpp` lines 238–254

```cpp
bool HighScore::operator<(HighScore const& other) const
{
	/* Make sure we treat AAAA as higher than AAA, even though the score
 	 * is the same. */
	if( PREFSMAN->m_bPercentageScoring )
	{
		if( GetPercentDP() != other.GetPercentDP() )
			return GetPercentDP() < other.GetPercentDP();
	}
	else
	{
		if( GetScore() != other.GetScore() )
			return GetScore() < other.GetScore();
	}

	return GetGrade() < other.GetGrade();
}
```

`src/HighScore.cpp` lines 307–331

```cpp
void HighScoreList::AddHighScore( HighScore hs, int &iIndexOut, bool bIsMachine )
{
	int i;
	for( i=0; i<(int)vHighScores.size(); i++ )
	{
		if( hs >= vHighScores[i] )
			break;
	}
	const int iMaxScores = bIsMachine ?
		PREFSMAN->m_iMaxHighScoresPerListForMachine :
		PREFSMAN->m_iMaxHighScoresPerListForPlayer;
	if( i < iMaxScores )
	{
		vHighScores.insert( vHighScores.begin()+i, hs );
		iIndexOut = i;

		// Delete extra machine high scores in RemoveAllButOneOfEachNameAndClampSize
		// and not here so that we don't end up with less than iMaxScores after
		// removing HighScores with duplicate names.
		//
		if( !bIsMachine )
			ClampSize( bIsMachine );
	}
	HighGrade = std::min( hs.GetGrade(), HighGrade );
}
```
