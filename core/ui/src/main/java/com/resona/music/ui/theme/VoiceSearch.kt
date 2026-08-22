package com.resona.music.ui.theme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.util.Locale

/** [VoiceState.Listening]'s rmsLevel starts here and is only ever written by
 *  [RecognitionListener.onRmsChanged]; a plain 0f default so the overlay's
 *  audio-reactive scale has something to animate from before the first
 *  callback arrives. */
private const val INITIAL_RMS = 0f

private sealed interface VoiceState {
    data object Hidden : VoiceState
    data class Listening(val partialText: String = "", val rmsLevel: Float = INITIAL_RMS) : VoiceState
    data class Failed(val message: String) : VoiceState
}

/**
 * Runs speech recognition through [SpeechRecognizer] directly rather than
 * firing a [RecognizerIntent.ACTION_RECOGNIZE_SPEECH] activity -- the Intent
 * form hands the whole interaction off to the system's own voice-search app
 * (generic Google mic popup, out of Resona's control); [SpeechRecognizer]
 * recognizes in-process against [RECORD_AUDIO][Manifest.permission.RECORD_AUDIO]
 * and delivers everything (listening state, live volume, partial transcript,
 * errors) through callbacks, so the UI showing while it listens -- and when
 * it can't -- is Resona's own. See [VoiceListeningOverlay] below.
 *
 * The overlay opens the instant the returned trigger is called, before
 * anything about permission or recognizer availability is even known --
 * earlier versions waited for [RecognitionListener.onReadyForSpeech] before
 * showing anything, which meant any failure ahead of that callback (no
 * recognizer on this device, permission denied, no network for a device
 * whose recognizer needs it, recognizer already busy) was completely
 * silent: nothing visibly happened when the button was tapped. Every one of
 * those paths now lands on [VoiceState.Failed] instead, shown in the same
 * overlay rather than swallowed.
 *
 * Still just speech-to-text fed into the existing text search, not an
 * audio-fingerprint/melody-humming matcher -- that needs a paid third-party
 * ID service this app doesn't have.
 */
@Composable
fun rememberVoiceSearchLauncher(onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(recognizer) {
        onDispose { recognizer?.destroy() }
    }

    var state by remember { mutableStateOf<VoiceState>(VoiceState.Hidden) }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
        state = VoiceState.Hidden
    }

    fun beginListening() {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                state = VoiceState.Listening()
            }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                val current = state as? VoiceState.Listening ?: return
                // Observed practical range is roughly -2..10; normalized to
                // 0..1 for the overlay's audio-reactive scale.
                state = current.copy(rmsLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                state = VoiceState.Failed(speechErrorMessage(error))
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val heard = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                val current = state as? VoiceState.Listening ?: return
                state = current.copy(partialText = heard)
            }
            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                state = VoiceState.Hidden
                if (!heard.isNullOrBlank()) onResult(heard)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        val started = runCatching { recognizer?.startListening(intent) }.isSuccess
        if (!started) state = VoiceState.Failed("Couldn't start voice search")
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            beginListening()
        } else {
            state = VoiceState.Failed("Microphone permission is needed for voice search")
        }
    }

    // A Failed state means nothing is actually listening -- auto-dismiss it
    // after a beat rather than leaving a dead overlay up requiring a tap, the
    // same way a toast would, but still visible long enough to actually read.
    val failedState = state as? VoiceState.Failed
    LaunchedEffect(failedState) {
        if (failedState != null) {
            delay(2200)
            state = VoiceState.Hidden
        }
    }

    when (val current = state) {
        VoiceState.Hidden -> Unit
        is VoiceState.Listening -> VoiceListeningOverlay(
            partialText = current.partialText,
            rmsLevel = current.rmsLevel,
            errorMessage = null,
            onDismiss = ::stopListening
        )
        is VoiceState.Failed -> VoiceListeningOverlay(
            partialText = "",
            rmsLevel = 0f,
            errorMessage = current.message,
            onDismiss = { state = VoiceState.Hidden }
        )
    }

    return {
        // Opens immediately regardless of what happens next -- see kdoc above.
        if (recognizer == null) {
            state = VoiceState.Failed("Voice search isn't available on this device")
        } else {
            val hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            state = VoiceState.Listening()
            if (hasPermission) beginListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

private fun speechErrorMessage(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that -- try again"
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No connection for voice search"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice search"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice search is busy -- try again"
    SpeechRecognizer.ERROR_AUDIO -> "Couldn't access the microphone"
    else -> "Couldn't start voice search"
}

/**
 * Resona's own listening UI -- concentric rings in the app's current accent
 * color (the same one the top bar button itself is filled with: whatever's
 * playing's album-art color, or plain white when nothing is) pulse gently at
 * rest and scale further with live mic volume, with the same music-note mark
 * from the button at their center and the live partial transcript underneath.
 * A non-null [errorMessage] swaps that mark for a muted mic-off glyph and
 * stops the audio-reactive scaling, since nothing is actually listening then.
 * Tapping anywhere (or the system back gesture, via [Dialog]'s own
 * dismiss-on-back) cancels.
 */
@Composable
private fun VoiceListeningOverlay(
    partialText: String,
    rmsLevel: Float,
    errorMessage: String?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "voiceBreathing")
                val breathing by infiniteTransition.animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.06f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "breathing"
                )
                val audioScale by animateFloatAsState(
                    targetValue = 1f + rmsLevel * 0.6f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 200f),
                    label = "audioScale"
                )
                val ringColor = if (errorMessage != null) {
                    Color.White.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.tertiary
                }

                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(176.dp)
                            .scale(if (errorMessage != null) breathing else breathing * audioScale)
                            .clip(CircleShape)
                            .background(ringColor.copy(alpha = 0.16f))
                    )
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .scale(breathing)
                            .clip(CircleShape)
                            .background(ringColor.copy(alpha = 0.28f))
                    )
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(if (errorMessage != null) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.tertiary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (errorMessage != null) Icons.Outlined.MicOff else Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = if (errorMessage != null) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = errorMessage ?: "Listening…",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                if (errorMessage == null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = partialText.ifBlank { "Say a song or artist" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
