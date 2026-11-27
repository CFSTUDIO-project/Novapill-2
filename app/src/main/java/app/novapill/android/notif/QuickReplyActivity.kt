package app.novapill.android.notif

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.NovaGraph
import app.novapill.android.R
import app.novapill.android.ui.components.NovaIcon
import app.novapill.android.ui.components.NovaIconView
import app.novapill.android.ui.components.pressable
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType

/**
 * A small translucent screen with a text field. It sends the text through the reply action the notification
 * itself offers, then closes. Nothing is stored: the text lives in this screen until it is sent.
 */
class QuickReplyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra(EXTRA_KEY)
        val target = key?.let { ReplyRegistry.get(it) }
        if (target == null) {
            // The notification was dismissed or replaced since the peek: nothing to reply to.
            Toast.makeText(this, R.string.reply_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val who = intent.getStringExtra(EXTRA_WHO).orEmpty()
        setContent {
            QuickReplyContent(who = who, onSend = { text -> send(target, text) }, onCancel = { finish() })
        }
    }

    private fun send(target: ReplyRegistry.Target, text: String) {
        val message = text.trim()
        if (message.isEmpty()) return
        val fillIn = Intent()
        val results = Bundle().apply { putCharSequence(target.resultKey, message) }
        RemoteInput.addResultsToIntent(target.inputs, fillIn, results)
        try {
            target.pending.send(this, 0, fillIn)
            NovaGraph.notifications.dismissCurrent()
        } catch (e: PendingIntent.CanceledException) {
            NovaGraph.diagnostics.error("reply", e)
            Toast.makeText(this, R.string.reply_failed, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        private const val EXTRA_KEY = "key"
        private const val EXTRA_WHO = "who"

        fun intent(context: Context, key: String, who: String): Intent =
            Intent(context, QuickReplyActivity::class.java).putExtra(EXTRA_KEY, key).putExtra(EXTRA_WHO, who)
    }
}

@Composable
private fun QuickReplyContent(who: String, onSend: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val shape = RoundedCornerShape(24.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(NovaColors.Black.copy(alpha = 0.55f))
            .pressable(label = "Close reply", pressedScale = 1f, onClick = onCancel),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(12.dp)
                .clip(shape)
                .background(NovaColors.Surface1)
                .border(1.dp, NovaColors.Hairline, shape)
                .pressable(label = null, pressedScale = 1f, onClick = {})
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText("REPLY" + if (who.isNotBlank()) " · " + who.uppercase() else "", style = NovaType.Label)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NovaColors.Surface3)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (text.isEmpty()) {
                        BasicText(
                            stringResource(R.string.reply_hint),
                            style = NovaType.Body.copy(color = NovaColors.Text3, fontSize = 15.sp),
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        textStyle = NovaType.Body.copy(color = NovaColors.Text1, fontSize = 15.sp),
                        cursorBrush = SolidColor(NovaColors.Crimson),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend(text) }),
                        maxLines = 4,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .pressable(label = stringResource(R.string.reply_send), onClick = { onSend(text) })
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (text.isBlank()) NovaColors.Surface3 else NovaColors.Crimson),
                    contentAlignment = Alignment.Center,
                ) {
                    NovaIconView(NovaIcon.SEND, if (text.isBlank()) NovaColors.Text3 else Color.White, size = 22.dp)
                }
            }
        }
    }
}
