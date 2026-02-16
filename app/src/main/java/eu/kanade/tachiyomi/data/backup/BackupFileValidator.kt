package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.serialization.protobuf.ProtoBuf
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BackupFileValidator(
    private val context: Context,

    private val sourceManager: SourceManager = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val parser: ProtoBuf = Injekt.get(),
) {

    /**
     * Checks for critical backup file data.
     *
     * @return List of missing sources or missing trackers.
     */
    suspend fun validate(uri: Uri): Results {
        val backupSources = mutableListOf<BackupSource>()
        val trackerIds = mutableSetOf<Long>()
        val reader = BackupProtoReader(context)
        try {
            reader.read(uri) { fieldNumber, data ->
                when (fieldNumber) {
                    1 -> {
                        val manga = parser.decodeFromByteArray(BackupManga.serializer(), data)
                        manga.tracking.forEach { trackerIds.add(it.syncId.toLong()) }
                    }
                    101 -> {
                        val source = parser.decodeFromByteArray(BackupSource.serializer(), data)
                        backupSources.add(source)
                    }
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }

        val sources = backupSources.associate { it.sourceId to it.name }
        val missingSources = sources
            .filter { sourceManager.get(it.key) == null }
            .values.map {
                val id = it.toLongOrNull()
                if (id == null) {
                    it
                } else {
                    sourceManager.getOrStub(id).toString()
                }
            }
            .distinct()
            .sorted()

        val missingTrackers = trackerIds
            .mapNotNull { trackerManager.get(it) }
            .filter { !it.isLoggedIn }
            .map { it.name }
            .sorted()

        return Results(missingSources, missingTrackers)
    }

    data class Results(
        val missingSources: List<String>,
        val missingTrackers: List<String>,
    )
}
