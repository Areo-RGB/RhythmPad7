package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.GameLane
import com.example.ui.canvas.NoteSkinDrawing
import com.example.ui.canvas.rememberNoteSkin
import com.example.ui.theme.OpenSans
import com.example.ui.theme.SmCharcoal
import com.example.ui.theme.SmCharcoalEdge
import com.example.ui.theme.SmCharcoalLight
import com.example.ui.theme.SmGold
import com.example.ui.theme.SmGoldLight
import com.example.ui.theme.SmPlum
import com.example.ui.theme.SmPlumDark
import com.example.ui.theme.SmPlumLight
import com.example.ui.theme.SmStageOrange
import com.example.ui.theme.SmTextMuted
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Shapes
// ---------------------------------------------------------------------------

/**
 * Hexagonal "frame" shape used throughout the StepMania 5 default theme
 * (ScoreDisplayNormal frame, MusicWheelItem, LifeMeter frame): pointed left/right ends.
 */
class ChamferShape(
  private val cut: Dp,
  private val pointLeft: Boolean = true,
  private val pointRight: Boolean = true
) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val c = with(density) { cut.toPx() }.coerceAtMost(size.width / 2f)
    val midY = size.height / 2f
    val path = Path().apply {
      moveTo(if (pointLeft) c else 0f, 0f)
      lineTo(if (pointRight) size.width - c else size.width, 0f)
      if (pointRight) lineTo(size.width, midY)
      lineTo(if (pointRight) size.width - c else size.width, size.height)
      lineTo(if (pointLeft) c else 0f, size.height)
      if (pointLeft) lineTo(0f, midY)
      close()
    }
    return Outline.Generic(path)
  }
}

val SmPanelShape = CutCornerShape(10.dp)

/** White text with the theme's 1 px drop shadow (`shadowlength,1`). */
fun TextStyle.smShadow(length: Float = 2f, color: Color = Color(0xCC000000)): TextStyle =
  copy(shadow = Shadow(color = color, offset = Offset(length, length), blurRadius = 0f))

// ---------------------------------------------------------------------------
// Background (ScreenWithMenuElements background / ScreenTitleMenu background)
// ---------------------------------------------------------------------------

/**
 * Warm orange StepMania background: `common bg base` + slowly spinning maze overlay +
 * scrolling barcode strips on both edges. [showTunnels] adds the title-screen tunnel rings.
 * [dim] darkens the whole thing (used behind gameplay).
 */
@Composable
fun SmBackground(
  modifier: Modifier = Modifier,
  showTunnels: Boolean = false,
  animated: Boolean = true,
  dim: Float = 0f
) {
  val transition = rememberInfiniteTransition(label = "sm_bg")
  val spin by if (animated) {
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(tween(160_000, easing = LinearEasing)),
      label = "maze_spin"
    )
  } else remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
  val scroll by if (animated) {
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(10_000, easing = LinearEasing)),
      label = "barcode_scroll"
    )
  } else remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

  val maze = ImageBitmap.imageResource(R.drawable.sm_bg_maze)
  val barcode = ImageBitmap.imageResource(R.drawable.sm_bg_barcode)
  val tunnel = ImageBitmap.imageResource(R.drawable.sm_bg_tunnel)

  Box(modifier = modifier.fillMaxSize()) {
    Image(
      painter = painterResource(R.drawable.sm_bg_base),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize()
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
      val mazeSize = maxOf(size.width, size.height) * 1.1f
      val center = Offset(size.width / 2f, size.height / 2f)
      rotate(spin, pivot = center) {
        drawImage(
          image = maze,
          srcOffset = IntOffset.Zero,
          srcSize = IntSize(maze.width, maze.height),
          dstOffset = IntOffset((center.x - mazeSize / 2f).roundToInt(), (center.y - mazeSize / 2f).roundToInt()),
          dstSize = IntSize(mazeSize.roundToInt(), mazeSize.roundToInt()),
          alpha = 0.16f,
          colorFilter = ColorFilter.tint(Color(0xFFF67849), BlendMode.Modulate)
        )
      }

      if (showTunnels) {
        val tunnelCenter = Offset(size.width * 0.16f, size.height / 2f)
        listOf(
          Triple(1.75f, 0.14f, 16.5f),
          Triple(1.0f, 0.12f, -11f),
          Triple(0.5f, 0.10f, 5.5f),
          Triple(0.2f, 0.08f, -2.2f)
        ).forEach { (zoom, alpha, speed) ->
          val tSize = size.height * zoom
          rotate(spin * speed / 2.2f, pivot = tunnelCenter) {
            drawImage(
              image = tunnel,
              srcOffset = IntOffset.Zero,
              srcSize = IntSize(tunnel.width, tunnel.height),
              dstOffset = IntOffset(
                (tunnelCenter.x - tSize / 2f).roundToInt(),
                (tunnelCenter.y - tSize / 2f).roundToInt()
              ),
              dstSize = IntSize(tSize.roundToInt(), tSize.roundToInt()),
              alpha = alpha * 1.6f,
              blendMode = BlendMode.Plus
            )
          }
        }
      }

      // Barcode strips (left scrolls up, right scrolls down)
      val stripW = 40.dp.toPx()
      val tileH = size.height
      listOf(0f to -1f, size.width - stripW to 1f).forEach { (x, dir) ->
        val shift = (scroll * dir * tileH) % tileH
        for (i in -1..1) {
          drawImage(
            image = barcode,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(barcode.width, barcode.height),
            dstOffset = IntOffset(x.roundToInt(), (shift + i * tileH).roundToInt()),
            dstSize = IntSize(stripW.roundToInt(), tileH.roundToInt()),
            alpha = 0.10f,
            blendMode = BlendMode.Plus
          )
        }
      }

      // Vignette for legibility
      drawRect(
        brush = Brush.radialGradient(
          colors = listOf(Color.Transparent, Color(0x55000000)),
          center = center,
          radius = maxOf(size.width, size.height) * 0.75f
        )
      )
      if (dim > 0f) drawRect(color = Color.Black.copy(alpha = dim))
    }
  }
}

// ---------------------------------------------------------------------------
// Header / Footer (ScreenWithMenuElements header & footer)
// ---------------------------------------------------------------------------

@Composable
fun SmHeader(
  title: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  color: Color = SmPlum,
  onIconClick: (() -> Unit)? = null,
  iconTestTag: String? = null,
  actions: @Composable RowScope.() -> Unit = {}
) {
  val slide = remember { Animatable(-1f) }
  val diamondSlide = remember { Animatable(-1f) }
  LaunchedEffect(Unit) {
    slide.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
  }
  LaunchedEffect(Unit) {
    kotlinx.coroutines.delay(200)
    diamondSlide.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
  }

  val darkTone = Color(
    red = color.red * 0.55f,
    green = color.green * 0.55f,
    blue = color.blue * 0.55f
  )
  val lightTone = Color(
    red = (color.red + (1f - color.red) * 0.35f),
    green = (color.green + (1f - color.green) * 0.35f),
    blue = (color.blue + (1f - color.blue) * 0.35f)
  )

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(84.dp)
      .graphicsLayer { translationY = slide.value * size.height * 1.15f }
  ) {
    // Base bar + shade
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(78.dp)
        .background(Brush.verticalGradient(listOf(color, darkTone)))
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.horizontalGradient(listOf(Color(0x30FFFFFF), Color(0x08FFFFFF), Color.Transparent))
          )
      )
      // Thin highlight line along the bottom edge
      Box(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .fillMaxWidth()
          .height(2.dp)
          .background(Brush.horizontalGradient(listOf(lightTone, lightTone.copy(alpha = 0f))))
      )
    }
    // Drop shadow
    Box(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .fillMaxWidth()
        .height(6.dp)
        .background(Brush.verticalGradient(listOf(Color(0x55000000), Color.Transparent)))
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(78.dp)
        .padding(start = 26.dp, end = 20.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Diamond icon
      Box(
        modifier = Modifier
          .size(56.dp)
          .graphicsLayer { translationX = diamondSlide.value * 140.dp.toPx() }
          .let { m ->
            if (onIconClick != null) {
              m.clickable(onClick = onIconClick)
            } else m
          }
          .let { m -> if (iconTestTag != null) m.testTag(iconTestTag) else m },
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .rotate(45f)
            .background(Brush.linearGradient(listOf(lightTone, color)))
            .border(1.5.dp, Color(0x99FFFFFF))
        )
        Icon(
          imageVector = icon,
          contentDescription = if (onIconClick != null) "Back" else null,
          tint = Color.White.copy(alpha = 0.92f),
          modifier = Modifier.size(24.dp)
        )
      }

      Spacer(modifier = Modifier.width(18.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = TextStyle(
            fontFamily = OpenSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            color = Color.White,
            letterSpacing = 0.sp
          ).smShadow(),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
          Text(
            text = subtitle,
            style = TextStyle(
              fontFamily = OpenSans,
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.sp,
              color = Color.White.copy(alpha = 0.72f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = actions
      )
    }
  }
}

@Composable
fun SmFooter(
  text: String,
  modifier: Modifier = Modifier,
  trailing: String? = null
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(34.dp)
      .background(Brush.verticalGradient(listOf(SmCharcoalLight, SmCharcoal)))
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(Color(0x22FFFFFF))
    )
    Text(
      text = text,
      style = TextStyle(
        fontFamily = OpenSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = Color.White.copy(alpha = 0.8f)
      ),
      modifier = Modifier.align(Alignment.Center)
    )
    if (trailing != null) {
      Text(
        text = trailing,
        style = TextStyle(
          fontFamily = OpenSans,
          fontWeight = FontWeight.SemiBold,
          fontSize = 12.sp,
          color = SmTextMuted
        ),
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(end = 20.dp)
      )
    }
  }
}

// ---------------------------------------------------------------------------
// Panels
// ---------------------------------------------------------------------------

/** Charcoal frame panel with cut corners (ScreenSelectMusic info pane / options page). */
@Composable
fun SmPanel(
  modifier: Modifier = Modifier,
  title: String? = null,
  titleColor: Color = SmPlum,
  titleTrailing: @Composable RowScope.() -> Unit = {},
  contentPadding: PaddingValues = PaddingValues(16.dp),
  backgroundAlpha: Float = 0.9f,
  content: @Composable ColumnScope.() -> Unit
) {
  Column(
    modifier = modifier
      .clip(SmPanelShape)
      .background(
        Brush.verticalGradient(
          listOf(
            SmCharcoalLight.copy(alpha = backgroundAlpha),
            SmCharcoal.copy(alpha = backgroundAlpha)
          )
        )
      )
      .border(1.dp, Color(0x40FFFFFF), SmPanelShape)
  ) {
    if (title != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Brush.horizontalGradient(listOf(titleColor, titleColor.copy(alpha = 0.35f))))
          .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = title,
          style = TextStyle(
            fontFamily = OpenSans,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 1.2.sp,
            color = Color.White
          ).smShadow(1.5f),
          modifier = Modifier.weight(1f)
        )
        titleTrailing()
      }
    }
    Column(modifier = Modifier.padding(contentPadding), content = content)
  }
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

enum class SmButtonStyle { Gold, Plum, Dark }

@Composable
fun SmButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  style: SmButtonStyle = SmButtonStyle.Plum,
  icon: ImageVector? = null,
  height: Dp = 54.dp,
  fontSize: Int = 17,
  /** Pad cursor is on this button (StepMania menu highlight). */
  focused: Boolean = false
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val shape = ChamferShape(height * 0.32f)

  val (fill, border, textColor) = when (style) {
    SmButtonStyle.Gold -> Triple(
      Brush.verticalGradient(listOf(SmGoldLight, SmGold, Color(0xFFD9822B))),
      Color(0xFFFFF1C4),
      Color(0xFF2A1A08)
    )
    SmButtonStyle.Plum -> Triple(
      Brush.verticalGradient(listOf(SmPlumLight, SmPlum, SmPlumDark)),
      Color(0x88FFFFFF),
      Color.White
    )
    SmButtonStyle.Dark -> Triple(
      Brush.verticalGradient(listOf(Color(0xFF45433F), SmCharcoalLight, SmCharcoal)),
      Color(0x55FFFFFF),
      Color.White
    )
  }

  Box(
    modifier = modifier
      .height(height)
      .graphicsLayer {
        val s = when {
          pressed -> 0.96f
          focused -> 1.04f
          else -> 1f
        }
        scaleX = s
        scaleY = s
      }
      .clip(shape)
      .background(fill)
      .border(if (focused) 3.dp else 1.5.dp, if (focused) Color.White else border, shape)
      .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    // Top gloss (matchParentSize so overlays never drive the button's own size)
    Box(
      modifier = Modifier
        .matchParentSize()
        .background(
          Brush.verticalGradient(
            0f to Color(0x40FFFFFF),
            0.5f to Color(0x08FFFFFF),
            0.5f to Color.Transparent
          )
        )
    )
    if (pressed) {
      Box(modifier = Modifier.matchParentSize().background(Color(0x33FFFFFF)))
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier.padding(horizontal = height * 0.4f)
    ) {
      if (icon != null) {
        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size((fontSize + 7).dp))
        Spacer(modifier = Modifier.width(10.dp))
      }
      Text(
        text = text,
        style = TextStyle(
          fontFamily = OpenSans,
          fontWeight = FontWeight.ExtraBold,
          fontSize = fontSize.sp,
          letterSpacing = 1.2.sp,
          color = textColor
        ).let { if (style == SmButtonStyle.Gold) it else it.smShadow(1.5f) },
        maxLines = 1
      )
    }
  }
}

/** Compact hexagonal icon button for in-game controls. */
@Composable
fun SmIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  active: Boolean = false,
  size: Dp = 52.dp
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val shape = ChamferShape(size * 0.24f)
  Box(
    modifier = modifier
      .width(size * 1.25f)
      .height(size)
      .clip(shape)
      .background(
        if (active) {
          Brush.verticalGradient(listOf(SmPlumLight, SmPlum, SmPlumDark))
        } else {
          Brush.verticalGradient(listOf(Color(0xFF45433F), SmCharcoalLight, SmCharcoal))
        }
      )
      .border(1.5.dp, if (pressed) Color.White else Color(0x66FFFFFF), shape)
      .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    if (pressed) Box(Modifier.fillMaxSize().background(Color(0x33FFFFFF)))
    Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(size * 0.48f))
  }
}

/**
 * Title-menu style choice row: a plum MusicWheelItem bar with a pointed left edge.
 * [highlighted] brightens it (the cursor position).
 */
@Composable
fun SmMenuItem(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  highlighted: Boolean = false,
  caption: String? = null
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val lit = highlighted || pressed
  val shape = ChamferShape(22.dp, pointLeft = true, pointRight = false)
  val transition = rememberInfiniteTransition(label = "menu_glow")
  val glow by transition.animateFloat(
    initialValue = 0.55f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "menu_glow_alpha"
  )

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(if (caption != null) 70.dp else 60.dp)
      .graphicsLayer {
        translationX = if (lit) -10.dp.toPx() else 0f
      }
      .clip(shape)
      .background(
        Brush.horizontalGradient(
          if (lit) {
            listOf(SmPlumLight, SmPlum, SmPlum.copy(alpha = 0.55f))
          } else {
            listOf(SmPlum, SmPlum.copy(alpha = 0.85f), SmPlumDark.copy(alpha = 0.35f))
          }
        )
      )
      .border(1.dp, Color(0x55FFFFFF), shape)
      .clickable(interactionSource = interaction, indication = null, onClick = onClick)
  ) {
    // Gloss + cursor stripe (the MusicWheelItem highlight line)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.5f)
        .background(Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color.Transparent)))
    )
    Box(
      modifier = Modifier
        .padding(start = 96.dp)
        .width(3.dp)
        .fillMaxHeight()
        .alpha(if (lit) glow else 0.35f)
        .background(Color.White)
    )
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(start = 34.dp, end = 20.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(modifier = Modifier.width(50.dp), contentAlignment = Alignment.CenterStart) {
        if (icon != null) {
          Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
      }
      Spacer(modifier = Modifier.width(26.dp))
      Column {
        Text(
          text = text,
          style = TextStyle(
            fontFamily = OpenSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            letterSpacing = 1.sp,
            color = Color.White
          ).smShadow(),
          maxLines = 1
        )
        if (caption != null) {
          Text(
            text = caption,
            style = TextStyle(
              fontFamily = OpenSans,
              fontWeight = FontWeight.SemiBold,
              fontSize = 12.sp,
              color = Color.White.copy(alpha = 0.75f)
            ),
            maxLines = 1
          )
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Meters & badges
// ---------------------------------------------------------------------------

/** Gold LifeMeter-frame progress bar with a plum→orange stream fill. */
@Composable
fun SmMeterBar(
  progress: Float,
  modifier: Modifier = Modifier,
  height: Dp = 22.dp
) {
  val shape = ChamferShape(height * 0.45f)
  Box(
    modifier = modifier
      .height(height)
      .clip(shape)
      .background(Brush.verticalGradient(listOf(SmGoldLight, SmGold, Color(0xFFC77A22))))
      .padding(3.dp)
  ) {
    val inner = ChamferShape(height * 0.38f)
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(inner)
        .background(Color(0xFF151413))
    ) {
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .fillMaxWidth(progress.coerceIn(0f, 1f))
          .background(Brush.horizontalGradient(listOf(SmPlum, SmStageOrange, SmGoldLight)))
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.45f)
            .background(Color(0x33FFFFFF))
        )
      }
    }
  }
}

/** Small angled label chip (StageDisplay / difficulty plate). */
@Composable
fun SmChip(
  text: String,
  color: Color,
  modifier: Modifier = Modifier,
  textColor: Color = Color.White
) {
  val shape = ChamferShape(8.dp)
  Box(
    modifier = modifier
      .clip(shape)
      .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.75f))))
      .border(1.dp, Color(0x66FFFFFF), shape)
      .padding(horizontal = 16.dp, vertical = 4.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      style = TextStyle(
        fontFamily = OpenSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        color = textColor
      ),
      maxLines = 1
    )
  }
}

/** Noteskin arrow for a lane: grey receptor when idle, colored tap note + flash when pressed. */
@Composable
fun LaneArrowIcon(
  lane: GameLane,
  pressed: Boolean,
  modifier: Modifier = Modifier,
  size: Dp = 56.dp
) {
  val skin = rememberNoteSkin()
  Canvas(modifier = modifier.size(size)) {
    val center = Offset(this.size.width / 2f, this.size.height / 2f)
    val px = this.size.minDimension
    if (pressed) {
      NoteSkinDrawing.drawNote(this, skin, lane, center, px, lane.quantFrame, isMissed = false)
      NoteSkinDrawing.drawExplosion(
        this, skin, lane, center, px, com.example.core.model.Judgment.W1, elapsedMs = 380L
      )
    } else {
      NoteSkinDrawing.drawReceptor(this, skin, lane, center, px, beatPhase = 0.6f, pressAmount = 0f, flashAlpha = 0f)
    }
  }
}

/** Fixed noteskin color per lane for UI icons (red, blue, green, yellow quantization frames). */
val GameLane.quantFrame: Int
  get() = when (this) {
    GameLane.LEFT -> 4
    GameLane.DOWN -> 1
    GameLane.UP -> 2
    GameLane.RIGHT -> 0
  }

/** Draws a thin decorative stroke frame; used for highlighted rows. */
fun Modifier.smGlowBorder(color: Color, shape: Shape = SmPanelShape): Modifier =
  this.border(2.dp, color, shape)
