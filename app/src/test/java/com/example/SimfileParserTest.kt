package com.example

import com.example.core.chart.Difficulty
import com.example.core.chart.MsdFile
import com.example.core.chart.SimfileLoader
import com.example.core.chart.SongLibrary
import com.example.core.chart.TimingData
import com.example.core.gameplay.GameplayRoundState
import com.example.core.model.GameLane
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimfileParserTest {

  // ---------------------------------------------------------------- MsdFile

  @Test
  fun msdParsesTagsParamsAndComments() {
    val values = MsdFile.parse(
      """
      #TITLE:My Song; // trailing comment
      #NOTES:dance-single:desc:Hard:9:radar:
      0000
      ;
      """.trimIndent()
    )
    assertEquals(listOf("TITLE", "My Song"), values[0])
    assertEquals("NOTES", values[1][0])
    assertEquals("dance-single", values[1][1])
    assertEquals("9", values[1][4])
    assertEquals("0000", values[1][6].trim())
  }

  @Test
  fun msdRecoversFromMissingSemicolonAtLineStartHash() {
    val values = MsdFile.parse("#BPMS:0=181.685\n#STOPS:;\n#TITLE:a#b;")
    assertEquals(listOf("BPMS", "0=181.685"), values[0])
    assertEquals(listOf("STOPS", ""), values[1])
    // '#' that is not first on its line stays part of the value
    assertEquals(listOf("TITLE", "a#b"), values[2])
  }

  @Test
  fun msdUnescapesBackslash() {
    val values = MsdFile.parse("#TITLE:A\\:B\\;C;")
    assertEquals("A:B;C", values[0][1])
  }

  // ---------------------------------------------------------------- TimingData

  @Test
  fun constantBpmWithOffset() {
    val t = TimingData(offsetSeconds = -0.1, bpms = listOf(0.0 to 120.0))
    assertEquals(0.1, t.timeAtBeat(0.0), 1e-9)
    assertEquals(0.6, t.timeAtBeat(1.0), 1e-9)
    assertEquals(4.0, t.beatAtTime(t.timeAtBeat(4.0)), 1e-6)
  }

  @Test
  fun bpmChangeStopsAndDelaysMatchStepManiaOrdering() {
    val t = TimingData(
      offsetSeconds = 0.0,
      bpms = listOf(0.0 to 60.0, 4.0 to 120.0),
      stops = listOf(2.0 to 1.0),
      delays = listOf(6.0 to 0.5)
    )
    // A note on the stop's beat is hit before the stop
    assertEquals(2.0, t.timeAtBeat(2.0), 1e-9)
    // After the stop: 4 beats at 60 BPM + 1 s stop
    assertEquals(5.0, t.timeAtBeat(4.0), 1e-9)
    // A note on a delay's beat is hit after the delay: 5 + 2 * 0.5 + 0.5
    assertEquals(6.5, t.timeAtBeat(6.0), 1e-9)
    // During the stop the displayed beat is frozen at the stop beat
    assertEquals(2.0, t.beatAtTime(2.5), 1e-9)
    assertEquals(3.0, t.beatAtTime(4.0), 1e-9)
  }

  @Test
  fun warpsSkipTimeAndMarkWarpedBeats() {
    val t = TimingData(
      offsetSeconds = 0.0,
      bpms = listOf(0.0 to 60.0),
      warps = listOf(2.0 to 2.0)
    )
    assertEquals(2.0, t.timeAtBeat(2.0), 1e-9)
    assertEquals(2.0, t.timeAtBeat(4.0), 1e-9)
    assertEquals(3.0, t.timeAtBeat(5.0), 1e-9)
    assertTrue(t.isWarpedBeat(3.0))
    assertFalse(t.isWarpedBeat(4.0))
  }

  // ---------------------------------------------------------------- Loader

  private val sampleSsc = """
    #VERSION:0.83;
    #TITLE:Test Song;
    #ARTIST:Tester;
    #MUSIC:song.ogg;
    #BANNER:bn.png;
    #OFFSET:0.000;
    #BPMS:0=120;
    #SAMPLESTART:10;
    #SAMPLELENGTH:5;
    #NOTEDATA:;
    #STEPSTYPE:dance-single;
    #DIFFICULTY:Easy;
    #METER:3;
    #NOTES:
    1000
    0100
    0010
    0001
    ,
    2000
    0000
    3M00
    0000
    0000
    0000
    0000
    0F01
    ;
    #NOTEDATA:;
    #STEPSTYPE:dance-double;
    #DIFFICULTY:Hard;
    #METER:9;
    #NOTES:
    10000000
    ;
    #NOTEDATA:;
    #STEPSTYPE:dance-single;
    #DIFFICULTY:Challenge;
    #METER:11;
    #OFFSET:-0.5;
    #BPMS:0=60;
    #NOTES:
    1001
    ;
  """.trimIndent()

  @Test
  fun loadsDanceSingleChartsWithQuantizedBeats() {
    val song = SimfileLoader.load(sampleSsc, isSsc = true, folder = "songs/test") { name ->
      SongLibrary.resolveFile(listOf("song.ogg", "bn.jpg"), name)
    }
    assertEquals("Test Song", song.title)
    assertEquals("songs/test/song.ogg", song.musicPath)
    assertEquals("songs/test/bn.jpg", song.bannerPath)
    assertEquals(2, song.charts.size) // dance-double skipped

    val easy = song.chart(Difficulty.EASY)!!
    // 4 quarter taps, hold head at beat 4 (tail/mine/fake ignored), tap at beat 7.5 (8th)
    assertEquals(6, easy.noteCount)
    assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0, 7.5), easy.steps.map { it.beat })
    assertEquals(GameLane.RIGHT, easy.steps.last().lane)
    assertEquals(3.75, easy.steps.last().timeSeconds, 1e-9)

    // Split timing: Challenge uses its own OFFSET/BPMS
    val challenge = song.chart(Difficulty.CHALLENGE)!!
    assertEquals(2, challenge.noteCount)
    assertEquals(0.5, challenge.steps.first().timeSeconds, 1e-9)
  }

  @Test
  fun songRoundPlacesNotesAfterCountdown() {
    val song = SimfileLoader.load(sampleSsc, isSsc = true, folder = "songs/test")
    val round = GameplayRoundState.createSongRound(song, song.chart(Difficulty.EASY)!!)
    assertTrue(round.isSongRound)
    assertTrue(round.notes.first().hitTimeMs >= GameplayRoundState.SONG_FIRST_STEP_MIN_MS)
    val musicStart = round.musicStartMs!!
    assertEquals(musicStart + 3750L, round.notes.last().hitTimeMs)
    assertEquals(7.5, round.beatAt(round.notes.last().hitTimeMs), 1e-6)
    assertEquals(round.notes.last().hitTimeMs, round.gameTimeAtBeat(7.5))
  }

  @Test
  fun bundledSongsAllLoad() {
    val root = listOf(File("src/main/assets/songs"), File("app/src/main/assets/songs")).first { it.isDirectory }
    val folders = root.listFiles()!!.filter { it.isDirectory }
    assertEquals(3, folders.size)
    for (folder in folders) {
      val files = folder.list()!!.toList()
      val ssc = folder.listFiles()!!.first { it.name.endsWith(".ssc") }
      val song = SimfileLoader.load(ssc.readText(), isSsc = true, folder = "songs/${folder.name}") {
        SongLibrary.resolveFile(files, it)
      }
      assertNotNull("music for ${folder.name}", song.musicFile)
      assertNotNull("banner for ${folder.name}", song.bannerFile)
      assertEquals(
        "${folder.name} difficulties",
        listOf(Difficulty.BEGINNER, Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD, Difficulty.CHALLENGE),
        song.charts.map { it.difficulty }
      )
      for (chart in song.charts) {
        assertTrue("${song.title} ${chart.difficulty} has notes", chart.noteCount > 20)
        val times = chart.steps.map { it.timeSeconds }
        assertEquals("${song.title} ${chart.difficulty} sorted", times.sorted(), times)
      }
    }
  }
}
