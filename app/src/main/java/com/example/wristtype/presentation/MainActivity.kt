package com.example.wristtype.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.example.wristtype.presentation.theme.WristTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.TimeText
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalConfiguration


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WristTheme {
                WristFlickApp()
            }
        }
    }
}

@Composable
fun WristFlickApp() {
    val context = LocalContext.current

    // Classifier must exist BEFORE effects that reference it
    val classifier = remember { FlickClassifier(context) }

    // Text / decoding state
    var typed by remember { mutableStateOf("") }
    var codeBuffer by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf(listOf<String>()) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    // Hover (pointer) state
    var hoverAngle by remember { mutableStateOf(0f) }    // radians, 0 = North
    var hiIndex by remember { mutableIntStateOf(0) }     // 0..3

    // Focus to receive rotary events
    val focusRequester = remember { FocusRequester() }

    // Has the user calibrated yet?
    var calibrated by remember { mutableStateOf(false) }

    fun commitCurrentWord() {
        if (codeBuffer.isEmpty() && candidates.isEmpty()) return
        // PREDICTED word = candidate if present, else simple expand
        val commit = candidates.getOrNull(selectedIndex) ?: ArcDecoder.expand(codeBuffer)
        typed += (if (typed.isEmpty()) "" else " ") + commit
        codeBuffer = ""
        candidates = emptyList()
        selectedIndex = 0
    }

    // Wire hover + start sensor stream + handle select/delete
    DisposableEffect(Unit) {
        classifier.onHoverAngle = { ang ->
            hoverAngle = ang
            hiIndex = ArcDecoder.arcIndexForAngle(ang)  // 0..3
        }
        classifier.start { dir ->
            when (dir) {
                Direction.SHAKE -> {
                    // delete last word
                    val parts = typed.trimEnd().split(" ")
                    typed = parts.dropLast(1).joinToString(" ")
                    codeBuffer = ""
                    candidates = emptyList()
                    selectedIndex = 0
                }
                Direction.CENTER -> {
                    // select current direction (add one “key”)
                    val token = ArcDecoder.tokenForArcIndex(hiIndex)
                    codeBuffer += token
                    candidates = ArcDecoder.candidatesFor(codeBuffer)
                    selectedIndex = 0
                }
            }
        }
        onDispose { classifier.stop(); classifier.onHoverAngle = null }
    }

    Scaffold(timeText = { TimeText() }) {
        // 1) Safe-area padding (system/time text)
        val safe = WindowInsets.safeDrawing.asPaddingValues()

        // 2) Extra radial padding for round screens (GW4 Classic is round)
        val isRound = LocalConfiguration.current.isScreenRound
        val radial = if (isRound) {
            PaddingValues(horizontal = 18.dp, vertical = 12.dp)
        } else {
            PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        }

        if (!calibrated) {
            // -------- FIRST SCREEN: CALIBRATION --------
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background)
                    .padding(safe)
                    .padding(radial)
                    .clickable {
                        classifier.calibrateNorth()
                        calibrated = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "WristFlick setup",
                        style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Hold your wrist in a comfy pose,\nthen tap to calibrate.",
                        style = MaterialTheme.typography.caption2
                    )
                }
            }
        } else {
            // -------- MAIN WRISTFLICK UI --------

            // 1) "Actually typed" = deterministic expansion of the code (letters like GAGGM)
            val rawTyped = if (codeBuffer.isNotEmpty()) ArcDecoder.expand(codeBuffer) else ""

            // 2) Predicted word = top candidate if any, else fall back to rawTyped
            val predicted = when {
                candidates.isNotEmpty() -> {
                    val idx = selectedIndex.coerceIn(0, candidates.lastIndex)
                    candidates[idx]
                }
                codeBuffer.isNotEmpty() -> rawTyped
                else -> ""
            }

            // 3) Center preview in the format:
            //
            //    hello
            //    [GAGGM]
            //
            val centerPreview =
                if (rawTyped.isNotEmpty() || predicted.isNotEmpty()) {
                    val top = if (predicted.isNotEmpty()) predicted else rawTyped
                    val bottom = if (rawTyped.isNotEmpty()) "[${rawTyped}]" else ""
                    if (bottom.isNotEmpty()) "$top\n$bottom" else top
                } else {
                    ""
                }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background)
                    .padding(safe)
                    .padding(radial)
                    .onRotaryScrollEvent { event ->
                        if (candidates.isNotEmpty()) {
                            val delta = if (event.verticalScrollPixels > 0) 1 else -1
                            selectedIndex = (selectedIndex + delta).mod(candidates.size)
                            true
                        } else false
                    }
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (!it.isFocused) focusRequester.requestFocus() }
                    .clickable { commitCurrentWord() }   // tap anywhere to commit current word
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Already committed text
                    Text(
                        text = typed,
                        style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.SemiBold)
                    )

                    // Ring keyboard + center preview of predicted + code
                    ArcKeyboard(
                        modifier = Modifier.size(232.dp),
                        hoverAngleRad = hoverAngle,
                        highlightedIndex = hiIndex,
                        groups = ArcDecoder.groups(),
                        centerPreview = centerPreview,
                        ringThickness = -1.dp,
                        labelBox = 44.dp,
                        labelNudgeX = (-55).dp,
                        labelNudgeY = (-55).dp
                    )

                    // Tiny hint only (to not push things off-screen)
                    Text(
                        text = "tilt = group • clench = key • bezel = next • tap = commit • shake = delete",
                        style = MaterialTheme.typography.caption2
                    )
                }
            }
        }
    }
}

@Composable
fun ChipCarousel(items: List<String>, selected: Int) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, s ->
            val sel = i == selected
            Text(
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .border(if (sel) 2.dp else 1.dp, MaterialTheme.colors.onBackground, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                text = s,
                style = MaterialTheme.typography.body2.copy(
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                )
            )
        }
    }
}
