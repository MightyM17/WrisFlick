# WristType (WrisText-inspired) — Directional Palm Typing on Galaxy Watch

WristType is a research prototype for **one-handed text entry on a round smartwatch** (tested on **Galaxy Watch 4 Classic**) that maps **simple wrist/palm directional movement** into **4 character groups**. A circular on-screen ring highlights the currently targeted group, and the UI shows both:

- **PREDICTED** word
- **[ACTUALLY TYPED]** raw group-code expansion

This project is inspired by prior smartwatch text-entry work (e.g., **WrisText**) and focuses on **consistency, low-latency feedback, and study-ready instrumentation**.

---

## Demo (what it looks like)
- Circular ring UI with 4 groups (Up/Right/Down/Left)
- Live pointer showing current direction
- Center preview:  
  **predicted**  
  **[raw typed]**

> Add your screenshots / gifs here:
- `docs/ui_ring.png`
- `docs/calibration.png`
- `docs/survey.png`

---

## How it works (high level)

### Input
- Uses watch IMU sensors (rotation vector + gyroscope + accelerometer) to estimate a **direction angle**.
- The angle is converted into one of **4 groups**:
  - **UP** → `ABCDEF`
  - **RIGHT** → `GHIJKL`
  - **DOWN** → `MNOPQR`
  - **LEFT** → `STUVWXYZ`

### Selection
- **Clench / select gesture** triggers a “key” selection (adds the current group-token).
- **Tap anywhere** commits the current predicted word.
- **Shake** deletes the last committed word.

### Decoder
- Stores a short token sequence (e.g., `ABBC...`), expands it into a raw string (e.g., `GAGGM...`), then produces a predicted candidate (currently lightweight/stubbed).

---

## User Flow (study-friendly)

1. **Consent screen**  
   Participant agrees to participate.

2. **Instructions screen**  
   Shows how to aim (4 directions), how to select, how to commit.

3. **Calibration screen**  
   User holds a comfortable neutral pose and taps to set “UP”.

4. **Typing screen**  
   - Move wrist/palm to change the highlighted group
   - Perform selection gesture to add a token
   - Tap screen to commit the predicted word
   - Bezel rotates through candidates (if enabled)

5. **Survey screen**  
   Quick ratings (comfort, workload, ease) + performance summary (WPM, accuracy, errors).

---

## Metrics collected (intended)
- **WPM** (typing speed)
- **Word accuracy** (committed word vs target)
- **Error rate** (incorrect commits / backspaces / deletions)
- **Comfort by direction** (Up/Right/Down/Left comfort ratings)
- Optional: **NASA-TLX** (short form)

---

## Project Structure

**Key files**
- `MainActivity.kt`  
  App screens + typing flow + survey flow.
- `FlickClassifier.kt`  
  Sensor processing + direction estimation + selection/shake detection.
- `ArcKeyboard.kt`  
  Circular ring UI + labels + center preview.
- `ArcDecoder.kt`  
  Group mapping + token expansion + candidates.

---

## Setup / Build

### Requirements
- Android Studio (latest stable)
- Wear OS tooling installed
- A Wear OS device (Galaxy Watch 4 Classic recommended) or emulator

### Run
1. Open the project in Android Studio.
2. Select your Wear OS run configuration.
3. Deploy to watch / emulator.

---

## Usage (quick)

- **Calibrate**: hold neutral pose → tap
- **Aim**: move palm/wrist **UP / RIGHT / DOWN / LEFT**
- **Select**: perform the selection gesture (clench)
- **Commit**: tap screen
- **Delete**: shake

---

## Known limitations (prototype)
- Direction sensing can vary across users and strap tightness.
- Extreme directions (especially LEFT) may be less comfortable for some users.
- Candidate prediction is minimal unless you plug in a dictionary/language model.
- Sensor noise and drift can affect long sessions without recalibration.

---

## Future work
- Better “selection” gesture (pinch/hand-close detection alternatives)
- Adaptive per-user calibration (gain + dead-zone tuning)
- Lightweight on-device language model / dictionary candidates
- Longer study with logging + repeat sessions to measure learning effects

---

## References (starter)
- MacKenzie, I. S. “Methods for Evaluating Text Entry Techniques.” TOCHI, 2002.
- Hart & Staveland. NASA-TLX. 1988.
- Smartwatch text-entry surveys + WrisText-style gesture typing work (to be added in paper bibliography).

---

## License
Prototype code for academic / course use. Add a LICENSE file if you plan to share publicly.

