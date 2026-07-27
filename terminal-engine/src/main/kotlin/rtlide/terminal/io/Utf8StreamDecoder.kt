package rtlide.terminal.io

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import kotlin.text.Charsets.UTF_8

/**
 * Incrementally decodes UTF-8 byte chunks from a process, carrying over any
 * trailing PARTIAL multi-byte sequence to the next chunk. Without this, an
 * Arabic code point whose bytes straddle a read boundary would decode to
 * replacement characters and corrupt the Bidi run.
 *
 * Not thread-safe; call from a single reader coroutine.
 */
class Utf8StreamDecoder {

    private val decoder = UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    private var carry = ByteArray(0)

    fun decode(chunk: ByteArray, length: Int): String {
        val input = ByteBuffer.allocate(carry.size + length).apply {
            put(carry)
            put(chunk, 0, length)
            flip()
        }
        // Worst case, every byte becomes one char.
        val out = CharBuffer.allocate(carry.size + length + 1)
        decoder.decode(input, out, /* endOfInput = */ false)

        // Whatever bytes remain form an incomplete sequence; keep them for later.
        carry = ByteArray(input.remaining()).also { input.get(it) }

        out.flip()
        return out.toString()
    }
}
