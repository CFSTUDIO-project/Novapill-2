package app.novapill.android

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import app.novapill.android.lyrics.ImportResult
import kotlinx.coroutines.launch

/**
 * An invisible helper that lets the user pick an .lrc file with the system file picker (no storage
 * permission needed) and attaches it to the track that is playing. The track is fixed when this opens,
 * so skipping to the next song while the picker is up cannot attach the file to the wrong one.
 */
class ImportLrcActivity : ComponentActivity() {
    private var title: String = ""
    private var artist: String = ""

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) finish() else attachFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // The picker result is delivered to the recreated activity, so the track must be restored too.
            title = savedInstanceState.getString(STATE_TITLE).orEmpty()
            artist = savedInstanceState.getString(STATE_ARTIST).orEmpty()
            return
        }
        val media = NovaGraph.media.current.value
        if (media == null || !media.hasTitle) {
            toast("Play a track first, then import its lyrics.")
            finish()
            return
        }
        title = media.title
        artist = media.artist
        picker.launch(arrayOf("*/*"))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TITLE, title)
        outState.putString(STATE_ARTIST, artist)
    }

    private fun attachFile(uri: Uri) {
        lifecycleScope.launch {
            val result = NovaGraph.lyrics.importLrc(uri, title, artist)
            if (result == ImportResult.OK) NovaGraph.lyrics.retry()
            toast(
                when (result) {
                    ImportResult.OK -> "Lyrics imported for this track."
                    ImportResult.EMPTY -> "That file has no readable lyric lines."
                    ImportResult.TOO_LARGE -> "That file is too large to be a lyrics file."
                    ImportResult.UNREADABLE -> "Could not read that file."
                    ImportResult.NO_TRACK -> "Play a track first, then import its lyrics."
                },
            )
            finish()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val STATE_TITLE = "title"
        const val STATE_ARTIST = "artist"
    }
}
