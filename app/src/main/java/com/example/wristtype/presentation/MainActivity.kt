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
    var hiIndex by remember { mutableIntStateOf(0) }     // 0..5

    // Focus to receive rotary events
    val focusRequester = remember { FocusRequester() }

    fun commitCurrentWord() {
        if (codeBuffer.isEmpty() && candidates.isEmpty()) return
        val commit = candidates.getOrNull(selectedIndex) ?: ArcDecoder.expand(codeBuffer)
        typed += (if (typed.isEmpty()) "" else " ") + commit
        codeBuffer = ""; candidates = emptyList(); selectedIndex = 0
    }

    // SINGLE effect: wire hover + start sensor stream + handle flicks
    DisposableEffect(Unit) {
        classifier.onHoverAngle = { ang ->
            hoverAngle = ang
            hiIndex = ArcDecoder.arcIndexForAngle(ang)  // 0..3
        }
        classifier.start { dir ->
            when (dir) {
                Direction.SHAKE -> { /* delete last word */ }
                Direction.CENTER -> {
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
            // tuned for 46mm: keeps the ring + chips comfortably inside the circle
            PaddingValues(horizontal = 18.dp, vertical = 12.dp)
        } else {
            PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(safe)      // safe-area first
                .padding(radial)    // then round-edge cushion
                .onRotaryScrollEvent { event ->
                    if (candidates.isNotEmpty()) {
                        val delta = if (event.verticalScrollPixels > 0) 1 else -1
                        selectedIndex = (selectedIndex + delta).mod(candidates.size)
                        true
                    } else false
                }
                .focusRequester(focusRequester)
                .onFocusChanged { if (!it.isFocused) focusRequester.requestFocus() }
                .clickable { commitCurrentWord() }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (candidates.isNotEmpty()) {
                    Chip(onClick = { commitCurrentWord() }, label = { Text("Commit") })
                }
            }
            Chip(onClick = { classifier.calibrateNorth() }, label = { Text("Calibrate") })

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp), // small inner breathing room for text & chips
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = typed,
                    style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.SemiBold)
                )

                // Keep the ring comfortably inside after radial padding
                // A 240–260dp square fits GW4 Classic well after padding.
                // If you still see clipping, try 240.dp; if it looks tiny, try 268.dp.
                ArcKeyboard(
                    modifier = Modifier.size(232.dp),  // 224–240.dp fits GW4 Classic 46mm well
                    hoverAngleRad = hoverAngle,
                    highlightedIndex = hiIndex,
                    groups = ArcDecoder.groups(),
                    centerPreview = candidates.getOrNull(selectedIndex),
//                        ?: ArcDecoder.groupForArcIndex(hiIndex),
                    ringThickness = -1.dp,
                    labelBox = 44.dp,
                    labelNudgeX = (-55).dp,   // move left (negative), right (positive)
                    labelNudgeY= (-55).dp    // move up (negative), down (positive)

                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "buffer: $codeBuffer", style = MaterialTheme.typography.caption2)
                    Spacer(Modifier.height(6.dp))
                    if (candidates.isNotEmpty()) {
                        ChipCarousel(candidates, selectedIndex)
                        Spacer(Modifier.height(8.dp))
                        Chip(onClick = { commitCurrentWord() }, label = { Text("Commit") })
                    } else {
                        Text(
                            "point → flick • bezel → cycle • tap → commit • shake → delete",
                            style = MaterialTheme.typography.caption2
                        )
                    }
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
