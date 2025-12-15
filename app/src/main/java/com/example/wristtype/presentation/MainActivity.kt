package com.example.wristtype.presentation

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.example.wristtype.presentation.theme.WristTheme
import kotlin.math.roundToInt


private enum class FlowScreen { CONSENT, INSTRUCTIONS, CALIBRATION, TYPING, SURVEY, SUMMARY }

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WristFlickApp() {
    val context = LocalContext.current
    val classifier = remember { FlickClassifier(context) }

    // Flow state
    var screen by remember { mutableStateOf(FlowScreen.CONSENT) }

    // Safe-area padding (your original)
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val isRound = LocalConfiguration.current.isScreenRound
    val radial = if (isRound) PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    else PaddingValues(horizontal = 8.dp, vertical = 8.dp)

    // --- Typing state (your original) ---
    var typed by remember { mutableStateOf("") }
    var codeBuffer by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf(listOf<String>()) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    var hoverAngle by remember { mutableStateOf(0f) }
    var hiIndex by remember { mutableIntStateOf(0) }

    val focusRequester = remember { FocusRequester() }

    // --- Metrics (lightweight, session-level) ---
    var sessionStartMs by remember { mutableStateOf<Long?>(null) }
    var sessionEndMs by remember { mutableStateOf<Long?>(null) }
    var selectCount by remember { mutableIntStateOf(0) }
    var deleteCount by remember { mutableIntStateOf(0) }
    var commitCount by remember { mutableIntStateOf(0) }

    // --- Survey answers ---
    var comfort by remember { mutableIntStateOf(4) }     // 1..7
    var fatigue by remember { mutableIntStateOf(4) }     // 1..7
    var control by remember { mutableIntStateOf(4) }     // 1..7
    var frustration by remember { mutableIntStateOf(4) } // 1..7

    fun nowMs(): Long = SystemClock.elapsedRealtime()

    fun ensureSessionStarted() {
        if (sessionStartMs == null) sessionStartMs = nowMs()
    }

    fun resetSession() {
        typed = ""
        codeBuffer = ""
        candidates = emptyList()
        selectedIndex = 0
        sessionStartMs = null
        sessionEndMs = null
        selectCount = 0
        deleteCount = 0
        commitCount = 0
        comfort = 4; fatigue = 4; control = 4; frustration = 4
    }

    fun commitCurrentWord() {
        if (codeBuffer.isEmpty() && candidates.isEmpty()) return
        ensureSessionStarted()

        val commit = candidates.getOrNull(selectedIndex) ?: ArcDecoder.expand(codeBuffer)
        typed += (if (typed.isEmpty()) "" else " ") + commit
        codeBuffer = ""
        candidates = emptyList()
        selectedIndex = 0

        commitCount += 1
        sessionEndMs = nowMs()
    }

    fun finishTypingToSurvey() {
        // Long-press anywhere on typing screen to finish the session
        if (sessionStartMs != null) sessionEndMs = nowMs()
        screen = FlowScreen.SURVEY
    }

    // Start sensors only on calibration + typing (so calibration uses real orientation)
    DisposableEffect(screen) {
        if (screen == FlowScreen.CALIBRATION || screen == FlowScreen.TYPING) {
            classifier.onHoverAngle = { ang ->
                hoverAngle = ang
                hiIndex = ArcDecoder.arcIndexForAngle(ang)
            }
            classifier.start { dir ->
                when (dir) {
                    Direction.SHAKE -> {
                        ensureSessionStarted()
                        // delete last word (your original)
                        val parts = typed.trimEnd().split(" ")
                        typed = parts.dropLast(1).joinToString(" ")
                        codeBuffer = ""
                        candidates = emptyList()
                        selectedIndex = 0
                        deleteCount += 1
                    }
                    Direction.CENTER -> {
                        ensureSessionStarted()
                        val token = ArcDecoder.tokenForArcIndex(hiIndex)
                        codeBuffer += token
                        candidates = ArcDecoder.candidatesFor(codeBuffer)
                        selectedIndex = 0
                        selectCount += 1
                    }
                }
            }
        }
        onDispose { classifier.stop(); classifier.onHoverAngle = null }
    }

    Scaffold(timeText = { TimeText() }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(safe)
                .padding(radial)
        ) {
            when (screen) {

                FlowScreen.CONSENT -> ConsentScreen(
                    onAccept = {
                        resetSession()
                        screen = FlowScreen.INSTRUCTIONS
                    }
                )

                FlowScreen.INSTRUCTIONS -> InstructionsScreen(
                    onContinue = { screen = FlowScreen.CALIBRATION }
                )

                FlowScreen.CALIBRATION -> CalibrationScreen(
                    onCalibrate = {
                        classifier.calibrateNorth()
                        screen = FlowScreen.TYPING
                    }
                )

                FlowScreen.TYPING -> {
                    // --- YOUR EXISTING MAIN UI (kept same layout / offsets) ---

                    val rawTyped = if (codeBuffer.isNotEmpty()) ArcDecoder.expand(codeBuffer) else ""

                    val predicted = when {
                        candidates.isNotEmpty() -> {
                            val idx = selectedIndex.coerceIn(0, candidates.lastIndex)
                            candidates[idx]
                        }
                        codeBuffer.isNotEmpty() -> rawTyped
                        else -> ""
                    }

                    val centerPreview =
                        if (rawTyped.isNotEmpty() || predicted.isNotEmpty()) {
                            val top = if (predicted.isNotEmpty()) predicted else rawTyped
                            val bottom = if (rawTyped.isNotEmpty()) "[${rawTyped}]" else ""
                            if (bottom.isNotEmpty()) "$top\n$bottom" else top
                        } else ""

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.background)
                            .onRotaryScrollEvent { event ->
                                if (candidates.isNotEmpty()) {
                                    val delta = if (event.verticalScrollPixels > 0) 1 else -1
                                    selectedIndex = (selectedIndex + delta).mod(candidates.size)
                                    true
                                } else false
                            }
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (!it.isFocused) focusRequester.requestFocus() }
                            .combinedClickable(
                                onClick = { commitCurrentWord() },      // tap = commit word (same behavior)
                                onLongClick = { finishTypingToSurvey() } // long press = end + survey
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = typed,
                                style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.SemiBold)
                            )

                            ArcKeyboard(
                                modifier = Modifier.size(232.dp),
                                hoverAngleRad = hoverAngle,
                                highlightedIndex = hiIndex,
                                groups = ArcDecoder.groups(),
                                centerPreview = centerPreview,
                                ringThickness = (-1).dp,
                                labelBox = 44.dp,
                                labelNudgeX = (-55).dp,
                                labelNudgeY = (-55).dp
                            )

                            Text(
                                text = "tilt=group • clench=key • bezel=next • tap=commit • shake=delete\n(long press = finish)",
                                style = MaterialTheme.typography.caption2
                            )
                        }
                    }
                }

                FlowScreen.SURVEY -> SurveyScreen(
                    comfort = comfort,
                    fatigue = fatigue,
                    control = control,
                    frustration = frustration,
                    onChangeComfort = { comfort = it },
                    onChangeFatigue = { fatigue = it },
                    onChangeControl = { control = it },
                    onChangeFrustration = { frustration = it },
                    onSubmit = { screen = FlowScreen.SUMMARY }
                )

                FlowScreen.SUMMARY -> {
                    val start = sessionStartMs
                    val end = sessionEndMs
                    val durationSec = if (start != null && end != null && end > start) (end - start) / 1000.0 else 0.0
                    val chars = typed.length.coerceAtLeast(0)
                    val wpm = if (durationSec > 0.0 && chars > 1) ((chars - 1) / durationSec) * (60.0 / 5.0) else 0.0

                    SummaryScreen(
                        wpm = wpm,
                        selects = selectCount,
                        deletes = deleteCount,
                        commits = commitCount,
                        comfort = comfort,
                        fatigue = fatigue,
                        control = control,
                        frustration = frustration,
                        onRestart = {
                            resetSession()
                            screen = FlowScreen.CALIBRATION
                        }
                    )
                }
            }
        }
    }
}

/* ---------------- Screens (watch-friendly + scrollable) ---------------- */

@Composable
private fun ConsentScreen(onAccept: () -> Unit) {
    val listState = rememberScalingLazyListState()

    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp)
        ) {
            item {
                Text("Consent", style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.SemiBold))
            }
            item { Spacer(Modifier.height(6.dp)) }
            item { Text("WristFlick is a research prototype.", style = MaterialTheme.typography.caption2) }
            item { Spacer(Modifier.height(6.dp)) }
            item { Text("If you agree, we record:", style = MaterialTheme.typography.caption2) }
            item { Text("• timing (for WPM)", style = MaterialTheme.typography.caption2) }
            item { Text("• your input actions", style = MaterialTheme.typography.caption2) }
            item { Text("• final typed text", style = MaterialTheme.typography.caption2) }
            item { Text("• short comfort ratings", style = MaterialTheme.typography.caption2) }
            item { Spacer(Modifier.height(6.dp)) }
            item { Text("No audio, photos, or video.", style = MaterialTheme.typography.caption2) }
            item { Text("You can stop anytime.", style = MaterialTheme.typography.caption2) }
        }

        PositionIndicator(
            scalingLazyListState = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        Chip(
            onClick = onAccept,
            label = { Text("I Agree") },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        )
    }
}

@Composable
private fun InstructionsScreen(onContinue: () -> Unit) {
    val listState = rememberScalingLazyListState()

    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp)
        ) {
            item { Text("Instructions", style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.SemiBold)) }
            item { Spacer(Modifier.height(6.dp)) }
            item { Text("1) Tilt to choose a group", style = MaterialTheme.typography.caption2) }
            item { Text("2) Clench = add one key", style = MaterialTheme.typography.caption2) }
            item { Text("3) Bezel = change prediction", style = MaterialTheme.typography.caption2) }
            item { Text("4) Tap = commit word", style = MaterialTheme.typography.caption2) }
            item { Text("5) Shake = delete word", style = MaterialTheme.typography.caption2) }
            item { Spacer(Modifier.height(6.dp)) }
            item { Text("Tip: long press to finish.", style = MaterialTheme.typography.caption2) }
        }

        PositionIndicator(
            scalingLazyListState = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        Chip(
            onClick = onContinue,
            label = { Text("Continue") },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalibrationScreen(onCalibrate: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .combinedClickable(onClick = onCalibrate, onLongClick = onCalibrate),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Calibration", style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(8.dp))
            Text("Hold a comfy neutral pose,", style = MaterialTheme.typography.caption2)
            Text("then tap to calibrate.", style = MaterialTheme.typography.caption2)
            Spacer(Modifier.height(10.dp))
            Text("(Tap anywhere)", style = MaterialTheme.typography.caption2)
        }
    }
}

@Composable
private fun SurveyScreen(
    comfort: Int,
    fatigue: Int,
    control: Int,
    frustration: Int,
    onChangeComfort: (Int) -> Unit,
    onChangeFatigue: (Int) -> Unit,
    onChangeControl: (Int) -> Unit,
    onChangeFrustration: (Int) -> Unit,
    onSubmit: () -> Unit
) {
    val listState = rememberScalingLazyListState()

    val titles = listOf(
        "Comfort (4-direction tilt)",
        "Fatigue",
        "Control",
        "Frustration"
    )
    val subtitles = listOf(
        "How comfortable were Up/Right/Down/Left?",
        "How tiring was it overall?",
        "Same movement → same result?",
        "How frustrating was it overall?"
    )
    val setters = listOf(onChangeComfort, onChangeFatigue, onChangeControl, onChangeFrustration)

    var idx by remember { mutableIntStateOf(0) }

    val saved = when (idx) {
        0 -> comfort
        1 -> fatigue
        2 -> control
        else -> frustration
    }
    var v by remember(idx, comfort, fatigue, control, frustration) { mutableIntStateOf(saved) }

    Box(Modifier.fillMaxSize()) {

        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 14.dp, bottom = 16.dp)
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Survey",
                        style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("${idx + 1}/4", style = MaterialTheme.typography.caption2)
                }
            }

            item { Spacer(Modifier.height(10.dp)) }

            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        titles[idx],
                        style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(subtitles[idx], style = MaterialTheme.typography.caption2)
                }
            }

            item { Spacer(Modifier.height(14.dp)) }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // smaller buttons so it doesn't feel cramped
                    Button(
                        onClick = { v = (v - 1).coerceAtLeast(1) },
                        enabled = v > 1,
                        modifier = Modifier.size(44.dp)
                    ) { Text("–") }

                    Text(
                        text = "$v / 7",
                        style = MaterialTheme.typography.title2.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                    )

                    Button(
                        onClick = { v = (v + 1).coerceAtMost(7) },
                        enabled = v < 7,
                        modifier = Modifier.size(44.dp)
                    ) { Text("+") }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            item {
                Chip(
                    onClick = {
                        setters[idx](v)
                        if (idx < 3) idx += 1 else onSubmit()
                    },
                    label = { Text(if (idx < 3) "Next" else "Submit") }
                )
            }
        }

        PositionIndicator(
            scalingLazyListState = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}




@Composable
private fun SummaryScreen(
    wpm: Double,
    selects: Int,
    deletes: Int,
    commits: Int,
    comfort: Int,
    fatigue: Int,
    control: Int,
    frustration: Int,
    onRestart: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Summary", style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.SemiBold))
        Text("WPM: ${"%.1f".format(wpm)}", style = MaterialTheme.typography.body2)
        Text("Selections: $selects", style = MaterialTheme.typography.caption2)
        Text("Deletes: $deletes", style = MaterialTheme.typography.caption2)
        Text("Commits: $commits", style = MaterialTheme.typography.caption2)

        Text("Comfort $comfort/7 • Fatigue $fatigue/7", style = MaterialTheme.typography.caption2)
        Text("Control $control/7 • Frustration $frustration/7", style = MaterialTheme.typography.caption2)

        Chip(onClick = onRestart, label = { Text("Restart") })
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
