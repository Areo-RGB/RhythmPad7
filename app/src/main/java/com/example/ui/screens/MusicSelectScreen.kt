package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.chart.Chart
import com.example.core.chart.Difficulty
import com.example.core.chart.Song
import com.example.core.gameplay.ScrollMod
import com.example.core.model.GameLane
import com.example.core.state.GameInputState
import com.example.ui.components.ChamferShape
import com.example.ui.components.SmBackground
import com.example.ui.components.SmButton
import com.example.ui.components.SmButtonStyle
import com.example.ui.components.SmFooter
import com.example.ui.components.SmHeader
import com.example.ui.components.SmPanel
import com.example.ui.components.rememberAssetBitmap
import com.example.ui.components.smShadow
import com.example.ui.theme.OpenSans
import com.example.ui.theme.SmCharcoal
import com.example.ui.theme.SmP1Blue
import com.example.ui.theme.SmPlum
import com.example.ui.theme.SmPlumDark
import com.example.ui.theme.SmPlumLight
import com.example.ui.theme.SmTextMuted
import com.example.ui.theme.SmTextSecondary
import kotlinx.coroutines.delay
import java.util.Locale

/** StepMania GameColor.Difficulty palette (Themes/default/Scripts/02 Colors.lua). */
fun Difficulty.color(): Color = when (this) {
  Difficulty.BEGINNER -> Color(0xFF1AE0E4)
  Difficulty.EASY -> Color(0xFF3ACF2A)
  Difficulty.MEDIUM -> Color(0xFFCFC42A)
  Difficulty.HARD -> Color(0xFFCF502A)
  Difficulty.CHALLENGE -> Color(0xFF981F41)
  Difficulty.EDIT -> Color(0xFFCCCCCC)
}

/** Wheel cursor + preferred difficulty, hoisted so returning from a song keeps the selection. */
class MusicSelectState {
  var selectedIndex by mutableIntStateOf(0)
  var preferredDifficulty by mutableIntStateOf(Difficulty.BEGINNER.ordinal)
}

/** One music wheel row: a real song, or the built-in practice pattern (song == null). */
private data class WheelEntry(val song: Song?)

/**
 * ScreenSelectMusic-style song picker: music wheel on the right, banner + song info + difficulty
 * list on the left. Dance mat: ↑/↓ song, ←/→ difficulty, ←+→ jump starts.
 */
@Composable
fun MusicSelectScreen(
  songs: List<Song>,
  isLoading: Boolean,
  inputState: GameInputState,
  onStartSong: (Song, Chart) -> Unit,
  onStartPractice: () -> Unit,
  onPreview: (Song?) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  state: MusicSelectState = remember { MusicSelectState() },
  scrollMod: ScrollMod = ScrollMod.DEFAULT,
  onScrollModChange: (ScrollMod) -> Unit = {}
) {
  BackHandler(onBack = onNavigateBack)

  val entries = remember(songs) { songs.map { WheelEntry(it) } + WheelEntry(null) }
  var selectedIndex by state::selectedIndex
  var preferredDifficulty by state::preferredDifficulty

  val selected = entries.getOrNull(selectedIndex.coerceIn(0, entries.lastIndex))
  val charts = selected?.song?.charts.orEmpty()
  // Like StepMania, keep the chosen difficulty across songs, falling back to the closest one.
  val selectedChart = charts.minByOrNull { kotlin.math.abs(it.difficulty.ordinal - preferredDifficulty) }

  val startSelected = rememberUpdatedState {
    val song = selected?.song
    if (song == null) {
      onStartPractice()
    } else if (selectedChart != null) {
      onStartSong(song, selectedChart)
    }
  }

  fun moveSong(delta: Int) {
    if (entries.isEmpty()) return
    selectedIndex = (selectedIndex + delta + entries.size) % entries.size
  }

  fun moveDifficulty(delta: Int) {
    if (charts.isEmpty() || selectedChart == null) return
    val idx = charts.indexOf(selectedChart)
    val next = (idx + delta).coerceIn(0, charts.lastIndex)
    preferredDifficulty = charts[next].difficulty.ordinal
  }

  // Music sample preview
  LaunchedEffect(selected?.song) {
    delay(250)
    onPreview(selected?.song)
  }
  DisposableEffect(Unit) { onDispose { onPreview(null) } }

  // Dance-mat navigation (press counters increase once per fresh step)
  val latestInput by rememberUpdatedState(inputState)
  val lastCounts = remember { IntArray(4) { -1 } }
  LaunchedEffect(inputState.pressCounts) {
    val counts = GameLane.entries.map { inputState.getPressCount(it) }
    if (lastCounts[0] < 0) {
      counts.forEachIndexed { i, c -> lastCounts[i] = c }
      return@LaunchedEffect
    }
    val stepped = GameLane.entries.filter { counts[it.ordinal] > lastCounts[it.ordinal] }
    counts.forEachIndexed { i, c -> lastCounts[i] = c }
    if (stepped.isEmpty()) return@LaunchedEffect
    // Short grace period so a LEFT+RIGHT jump is not read as two single steps
    delay(70)
    val now = latestInput
    if (now.isLanePressed(GameLane.LEFT) && now.isLanePressed(GameLane.RIGHT)) {
      startSelected.value()
      return@LaunchedEffect
    }
    when (stepped.last()) {
      GameLane.UP -> moveSong(-1)
      GameLane.DOWN -> moveSong(1)
      GameLane.LEFT -> moveDifficulty(-1)
      GameLane.RIGHT -> moveDifficulty(1)
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    SmBackground()

    Column(modifier = Modifier.fillMaxSize()) {
      SmHeader(
        title = "Select Music",
        subtitle = if (isLoading) "Loading songs…" else "${songs.size} songs • dance-single",
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        onIconClick = onNavigateBack,
        iconTestTag = "music_button_back"
      )

      Row(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(start = 48.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
      ) {
        // ---------------- Left: banner, info, difficulty list ----------------
        Column(
          modifier = Modifier
            .width(470.dp)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          SongBanner(song = selected?.song)
          SongInfoPane(song = selected?.song, chart = selectedChart)
          SpeedModPicker(selected = scrollMod, onSelect = onScrollModChange)
          DifficultyList(
            charts = charts,
            selected = selectedChart,
            onSelect = { preferredDifficulty = it.difficulty.ordinal },
            modifier = Modifier.weight(1f)
          )
          SmButton(
            text = if (selected?.song == null) "START PRACTICE" else "START",
            icon = Icons.Default.PlayArrow,
            style = SmButtonStyle.Gold,
            onClick = { startSelected.value() },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("music_button_start")
          )
        }

        // ---------------- Right: music wheel ----------------
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          if (isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
          } else {
            MusicWheel(
              entries = entries,
              selectedIndex = selectedIndex,
              onSelect = { index ->
                if (index == selectedIndex) startSelected.value() else selectedIndex = index
              }
            )
          }
        }
      }

      SmFooter(
        text = "Pad: ↑ ↓ choose song  •  ← → difficulty  •  jump ← + → to start  •  tap a song twice to play",
        trailing = "RhythmPad7 v0.3.0"
      )
    }
  }
}

@Composable
private fun SongBanner(song: Song?) {
  val banner by rememberAssetBitmap(song?.bannerPath)
  val shape = ChamferShape(14.dp, pointLeft = false)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(418f / 164f)
      .clip(shape)
      .background(SmCharcoal)
      .border(2.dp, Color(0x99FFFFFF), shape),
    contentAlignment = Alignment.Center
  ) {
    val image = banner
    if (image != null) {
      Image(
        bitmap = image,
        contentDescription = song?.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Brush.linearGradient(listOf(SmPlumLight, SmPlum, SmPlumDark)))
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
          painter = painterResource(R.drawable.sm_logo_arrow),
          contentDescription = null,
          modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
          text = song?.title ?: "Practice Pattern",
          style = TextStyle(
            fontFamily = OpenSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            color = Color.White
          ).smShadow(3f)
        )
      }
    }
  }
}

@Composable
private fun SongInfoPane(song: Song?, chart: Chart?) {
  SmPanel(
    modifier = Modifier.fillMaxWidth(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = song?.title ?: "StepMania Pad Foundation #1",
          style = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.White),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = song?.artist ?: "RhythmPad7 Practice",
          style = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SmTextMuted),
          maxLines = 1
        )
      }
      InfoStat(label = "BPM", value = song?.displayBpm ?: "120")
      Spacer(modifier = Modifier.width(18.dp))
      val lengthSec = chart?.steps?.maxOfOrNull { it.timeSeconds }?.toInt() ?: 34
      InfoStat(label = "LENGTH", value = String.format(Locale.US, "%d:%02d", lengthSec / 60, lengthSec % 60))
      Spacer(modifier = Modifier.width(18.dp))
      InfoStat(label = "STEPS", value = "${chart?.noteCount ?: 77}")
    }
  }
}

/** ScreenPlayerOptions "Speed" row: tap a chip (M = constant read speed, x = multiplier, C = constant). */
@Composable
private fun SpeedModPicker(selected: ScrollMod, onSelect: (ScrollMod) -> Unit) {
  SmPanel(
    modifier = Modifier.fillMaxWidth(),
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = "SPEED",
        style = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, color = SmTextMuted),
        modifier = Modifier.width(52.dp)
      )
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(ScrollMod.PRESETS) { mod ->
          val isSelected = mod == selected
          val shape = ChamferShape(7.dp)
          Box(
            modifier = Modifier
              .height(30.dp)
              .clip(shape)
              .background(if (isSelected) SmP1Blue else Color(0x26FFFFFF))
              .border(1.dp, if (isSelected) Color.White else Color(0x33FFFFFF), shape)
              .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(mod) }
              .padding(horizontal = 12.dp)
              .testTag("speed_mod_${mod.serialize()}"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = mod.label,
              style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InfoStat(label: String, value: String) {
  Column(horizontalAlignment = Alignment.End) {
    Text(
      text = label,
      style = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, color = SmTextMuted)
    )
    Text(
      text = value,
      style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, fontFeatureSettings = "tnum")
    )
  }
}

/** StepsDisplayList: one row per difficulty with the P1 cursor on the chosen chart. */
@Composable
private fun DifficultyList(
  charts: List<Chart>,
  selected: Chart?,
  onSelect: (Chart) -> Unit,
  modifier: Modifier = Modifier
) {
  SmPanel(
    modifier = modifier.fillMaxWidth(),
    title = "DIFFICULTY",
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
  ) {
    if (charts.isEmpty()) {
      DifficultyRow(
        name = "Beginner",
        meter = 1,
        steps = 77,
        color = Difficulty.BEGINNER.color(),
        isSelected = true,
        onClick = {}
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        charts.forEach { chart ->
          DifficultyRow(
            name = chart.difficulty.displayName,
            meter = chart.meter,
            steps = chart.noteCount,
            color = chart.difficulty.color(),
            isSelected = chart == selected,
            onClick = { onSelect(chart) }
          )
        }
      }
    }
  }
}

@Composable
private fun DifficultyRow(
  name: String,
  meter: Int,
  steps: Int,
  color: Color,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val shape = ChamferShape(10.dp, pointLeft = false)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(31.dp)
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // P1 cursor
    Box(modifier = Modifier.width(22.dp), contentAlignment = Alignment.Center) {
      if (isSelected) {
        Box(
          modifier = Modifier
            .size(14.dp)
            .clip(ChamferShape(7.dp, pointLeft = false))
            .background(SmP1Blue)
        )
      }
    }
    Row(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .clip(shape)
        .background(
          if (isSelected) {
            Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.55f)))
          } else {
            Brush.horizontalGradient(listOf(color.copy(alpha = 0.35f), Color(0x22FFFFFF)))
          }
        )
        .border(if (isSelected) 1.5.dp else 0.dp, if (isSelected) Color.White else Color.Transparent, shape)
        .padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = name.uppercase(Locale.US),
        style = TextStyle(
          fontFamily = OpenSans,
          fontWeight = FontWeight.ExtraBold,
          fontSize = 14.sp,
          letterSpacing = 1.sp,
          color = if (isSelected) SmCharcoal else Color.White
        ),
        modifier = Modifier.weight(1f)
      )
      Text(
        text = "$steps steps",
        style = TextStyle(fontSize = 12.sp, color = if (isSelected) SmCharcoal.copy(alpha = 0.75f) else SmTextSecondary)
      )
      Spacer(modifier = Modifier.width(14.dp))
      Text(
        text = "$meter",
        style = TextStyle(
          fontWeight = FontWeight.Black,
          fontSize = 20.sp,
          color = if (isSelected) SmCharcoal else lerp(color, Color.White, 0.35f),
          fontFeatureSettings = "tnum"
        )
      )
    }
  }
}

@Composable
private fun MusicWheel(
  entries: List<WheelEntry>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit
) {
  val listState = rememberLazyListState()
  LaunchedEffect(selectedIndex) {
    listState.animateScrollToItem((selectedIndex - 2).coerceAtLeast(0))
  }
  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(vertical = 12.dp)
  ) {
    itemsIndexed(entries) { index, entry ->
      WheelItem(
        entry = entry,
        isSelected = index == selectedIndex,
        onClick = { onSelect(index) },
        modifier = Modifier.testTag("music_wheel_item_$index")
      )
    }
  }
}

@Composable
private fun WheelItem(
  entry: WheelEntry,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val shift by animateFloatAsState(
    targetValue = if (isSelected) 1f else 0f,
    animationSpec = tween(180, easing = FastOutSlowInEasing),
    label = "wheel_shift"
  )
  val shape = ChamferShape(20.dp, pointLeft = true, pointRight = false)
  val song = entry.song
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(70.dp)
      .graphicsLayer { translationX = (1f - shift) * 36.dp.toPx() }
      .clip(shape)
      .background(
        Brush.horizontalGradient(
          if (isSelected) {
            listOf(SmPlumLight, SmPlum, SmPlum.copy(alpha = 0.6f))
          } else if (song == null) {
            listOf(Color(0xFF6B4A2A), Color(0xCC4A3420), Color(0x664A3420))
          } else {
            listOf(SmPlum.copy(alpha = 0.9f), SmPlumDark.copy(alpha = 0.8f), SmPlumDark.copy(alpha = 0.35f))
          }
        )
      )
      .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color.White else Color(0x55FFFFFF), shape)
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.5f)
        .background(Brush.verticalGradient(listOf(Color(0x26FFFFFF), Color.Transparent)))
    )
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(start = 32.dp, end = 60.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (song == null) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = song?.title ?: "Practice Pattern",
          style = TextStyle(
            fontFamily = OpenSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 21.sp,
            color = Color.White
          ).smShadow(2f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = song?.artist ?: "Built-in 4-panel warm-up • 120 BPM",
          style = TextStyle(
            fontFamily = OpenSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.75f)
          ),
          maxLines = 1
        )
      }
      // Difficulty pips like the wheel's grade/difficulty strip
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (song?.charts?.map { it.difficulty } ?: listOf(Difficulty.BEGINNER)).forEach { d ->
          Box(
            modifier = Modifier
              .size(width = 14.dp, height = 8.dp)
              .clip(ChamferShape(4.dp))
              .background(d.color())
          )
        }
      }
      if (song != null) {
        Spacer(modifier = Modifier.width(14.dp))
        Text(
          text = song.displayBpm,
          style = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.85f)
          )
        )
      }
    }
  }
}
