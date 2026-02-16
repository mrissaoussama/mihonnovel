package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.gzip
import okio.source
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException

class BackupProtoReader(
    private val context: Context,
) {
    suspend fun read(uri: Uri, onField: suspend (fieldNumber: Int, data: ByteArray) -> Unit) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)!!.use { inputStream ->
                val source = inputStream.source().buffer()

                val peeked = source.peek().apply {
                    require(2)
                }
                val id1id2 = peeked.readShort().toInt()

                val payloadSource = when (id1id2) {
                    GZIP_MAGIC -> source.gzip().buffer()
                    MAGIC_JSON_SIGNATURE1, MAGIC_JSON_SIGNATURE2, MAGIC_JSON_SIGNATURE3 -> {
                        throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
                    }
                    else -> source
                }

                while (true) {
                    val tag = readVarint(payloadSource) ?: break
                    val wireType = (tag and 0x07).toInt()
                    val fieldNumber = (tag ushr 3).toInt()

                    when (wireType) {
                        WIRE_TYPE_LENGTH_DELIMITED -> {
                            val length = readVarint(payloadSource)
                                ?: throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
                            if (length > Int.MAX_VALUE) {
                                throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
                            }
                            val bytes = payloadSource.readByteArray(length)
                            onField(fieldNumber, bytes)
                        }
                        WIRE_TYPE_VARINT -> {
                            readVarint(payloadSource)
                                ?: throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
                        }
                        WIRE_TYPE_64BIT -> payloadSource.skip(8)
                        WIRE_TYPE_32BIT -> payloadSource.skip(4)
                        else -> throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
                    }
                }
            }
        }
    }

    private fun readVarint(source: okio.BufferedSource): Long? {
        if (source.exhausted()) return null

        var shift = 0
        var result = 0L
        while (shift < 64) {
            val b = source.readByte().toInt() and 0xff
            result = result or ((b and 0x7f).toLong() shl shift)
            if (b and 0x80 == 0) {
                return result
            }
            shift += 7
        }
        throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
    }

    companion object {
        private const val WIRE_TYPE_VARINT = 0
        private const val WIRE_TYPE_64BIT = 1
        private const val WIRE_TYPE_LENGTH_DELIMITED = 2
        private const val WIRE_TYPE_32BIT = 5
        private const val GZIP_MAGIC = 0x1f8b
        private const val MAGIC_JSON_SIGNATURE1 = 0x7b7d // "{}"
        private const val MAGIC_JSON_SIGNATURE2 = 0x7b22 // "{\""
        private const val MAGIC_JSON_SIGNATURE3 = 0x7b0a // "{\n"
    }
}
