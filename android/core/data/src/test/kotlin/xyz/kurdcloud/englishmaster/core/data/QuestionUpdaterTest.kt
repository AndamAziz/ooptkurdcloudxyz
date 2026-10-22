package xyz.kurdcloud.englishmaster.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.LevelPack
import xyz.kurdcloud.englishmaster.core.model.PackVersion
import xyz.kurdcloud.englishmaster.core.model.Question
import xyz.kurdcloud.englishmaster.core.model.QuestionUpdateState.Result
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

class QuestionUpdaterTest {
    private class Online(value: Boolean) : NetworkMonitor { override val isOnline = MutableStateFlow(value) }

    private class Content(
        var server: ApiResult<List<PackVersion>>,
        val local: MutableList<PackVersion>,
    ) : ContentRepository {
        val downloaded = mutableListOf<CefrLevel>()
        override fun observePacks() = kotlinx.coroutines.flow.flowOf(emptyList<LevelPack>())
        override suspend fun downloadLevel(level: CefrLevel, onProgress: (Int) -> Unit): ApiResult<Int> {
            downloaded += level
            return ApiResult.Success((server as ApiResult.Success).value.first { it.level == level }.questions)
        }
        override suspend fun deleteLevel(level: CefrLevel) = Unit
        override suspend fun pickSession(level: CefrLevel, userId: String, count: Int) = emptyList<Question>()
        override suspend fun questions(ids: List<String>) = emptyList<Question>()
        override suspend fun serverVersions() = server
        override suspend fun localVersions() = local
    }

    private val server = listOf(
        PackVersion(CefrLevel.A1, 120, 2_000),
        PackVersion(CefrLevel.A2, 90, 1_000),
        PackVersion(CefrLevel.LIFE_IN_UK, 1466, 3_000),
    )

    private fun TestScope.updater(content: Content, online: Boolean = true, now: () -> Long = { 0 }) =
        QuestionUpdater(content, Online(online), this, now)

    @Test
    fun backgroundUpdateRefreshesOnlyChangedDownloadedPacks() = runTest {
        // A1 gained questions, A2 is current, Life in the UK was never downloaded.
        val content = Content(ApiResult.Success(server), mutableListOf(PackVersion(CefrLevel.A1, 100, 1_500), PackVersion(CefrLevel.A2, 90, 1_000)))
        val u = updater(content)

        u.autoUpdate()
        advanceUntilIdle()

        assertEquals(listOf(CefrLevel.A1), content.downloaded)
        assertEquals(Result.UPDATED, u.state.value.result)
        assertEquals(120, u.state.value.updatedQuestions)
        assertEquals(false, u.state.value.manual)
    }

    @Test
    fun oneButtonUpdatesEverySectionIncludingNewOnes() = runTest {
        val content = Content(ApiResult.Success(server), mutableListOf(PackVersion(CefrLevel.A1, 100, 1_500), PackVersion(CefrLevel.A2, 90, 1_000)))
        val u = updater(content)

        u.updateAll()
        advanceUntilIdle()

        assertEquals(listOf(CefrLevel.A1, CefrLevel.LIFE_IN_UK), content.downloaded)
        assertEquals(2, u.state.value.total)
        assertEquals(true, u.state.value.manual)
        assertEquals(false, u.state.value.running)
    }

    @Test
    fun nothingChangedIsReportedAsUpToDate() = runTest {
        val content = Content(ApiResult.Success(server.take(2)), server.take(2).toMutableList())
        val u = updater(content)

        u.updateAll()
        advanceUntilIdle()

        assertEquals(emptyList<CefrLevel>(), content.downloaded)
        assertEquals(Result.UP_TO_DATE, u.state.value.result)
    }

    @Test
    fun offlineAndServerErrorsAreReported() = runTest {
        val offline = updater(Content(ApiResult.Success(server), mutableListOf()), online = false)
        offline.updateAll()
        assertEquals(Result.OFFLINE, offline.state.value.result)

        val failing = updater(Content(ApiResult.Failure(ApiError.Server(503)), mutableListOf()))
        failing.updateAll()
        advanceUntilIdle()
        assertEquals(Result.FAILED, failing.state.value.result)
    }

    @Test
    fun backgroundChecksAreSpacedOut() = runTest {
        var now = 0L
        val content = Content(ApiResult.Success(server), mutableListOf(PackVersion(CefrLevel.A1, 100, 1_500)))
        val u = updater(content) { now }

        u.autoUpdate(); advanceUntilIdle()
        content.local[0] = PackVersion(CefrLevel.A1, 50, 1)
        now = QuestionUpdater.AUTO_INTERVAL_MILLIS - 1
        u.autoUpdate(); advanceUntilIdle()
        assertEquals(1, content.downloaded.size)

        now = QuestionUpdater.AUTO_INTERVAL_MILLIS
        u.autoUpdate(); advanceUntilIdle()
        assertEquals(2, content.downloaded.size)
    }
}
