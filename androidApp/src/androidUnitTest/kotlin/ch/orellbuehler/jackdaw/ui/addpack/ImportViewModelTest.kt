package ch.orellbuehler.jackdaw.ui.addpack

import android.net.Uri
import app.cash.turbine.test
import ch.orellbuehler.jackdaw.JackdawSdk
import ch.orellbuehler.jackdaw.MainDispatcherRule
import ch.orellbuehler.jackdaw.data.PackTextReader
import ch.orellbuehler.jackdaw.data.ReadOutcome
import ch.orellbuehler.jackdaw.format.ImportError
import ch.orellbuehler.jackdaw.format.ImportErrorCode
import ch.orellbuehler.jackdaw.packSummary
import ch.orellbuehler.jackdaw.store.ImportCounts
import ch.orellbuehler.jackdaw.store.ImportPreview
import ch.orellbuehler.jackdaw.store.ImportResult
import ch.orellbuehler.jackdaw.store.SamplePack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImportViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val sdk = mockk<JackdawSdk>()
    private var readOutcome: ReadOutcome = ReadOutcome.Text("{}")
    private val reader = PackTextReader { readOutcome }
    private val fetched = HashMap<String, String>()

    private fun counts() = ImportCounts(0, 0, 0, 0)

    private fun valid(
        isUpdate: Boolean = false,
        isDowngrade: Boolean = false,
    ) = ImportPreview.Valid(
        packId = "p",
        title = "Pack",
        descriptionText = null,
        icon = null,
        color = null,
        language = null,
        revision = 1,
        itemCount = 3,
        sectionCount = 0,
        typeCounts = emptyList(),
        skippedCount = 0,
        warnings = emptyList(),
        isUpdate = isUpdate,
        installedTitle = null,
        installedRevision = 0,
        isDowngrade = isDowngrade,
        counts = counts(),
    )

    private fun success(isUpdate: Boolean = false) = ImportResult.Success("p", "Pack", 3, 0, isUpdate, counts(), emptyList())

    private fun viewModel(): ImportViewModel {
        every { sdk.samplePacks() } returns emptyList()
        every { sdk.observePacks() } returns flowOf(listOf(packSummary("p")))
        return ImportViewModel(sdk, reader) { url -> fetched[url] }
    }

    private fun sample(
        id: String,
        revision: Int = 1,
        downloadUrl: String? = null,
    ) = SamplePack(id, "Sample $id", "", null, null, 3, revision, downloadUrl)

    @Test
    fun previewsValidTextWithoutImporting() =
        runTest {
            coEvery { sdk.previewImport("text") } returns valid()
            val vm = viewModel()
            vm.openText("text")
            val state = assertIs<ImportUiState.Preview>(vm.state.value)
            assertEquals("Pack", state.preview.title)
            coVerify(exactly = 0) { sdk.importPack(any()) }
        }

    @Test
    fun reportsInvalidPacksAsFailures() =
        runTest {
            val error = ImportError(ImportErrorCode.NOT_JSON, "The file is not valid JSON.")
            coEvery { sdk.previewImport("oops") } returns ImportPreview.Invalid(error)
            val vm = viewModel()
            vm.openText("oops")
            val state = assertIs<ImportUiState.Failed>(vm.state.value)
            assertEquals(ImportProblem.Sdk(error), state.problem)
            assertEquals("The file is not valid JSON.", state.problem.technicalDetail())
        }

    @Test
    fun blankTextIsRejectedLocally() =
        runTest {
            val vm = viewModel()
            vm.openText("   ")
            assertEquals(ImportUiState.Failed(ImportProblem.Empty), vm.state.value)
            coVerify(exactly = 0) { sdk.previewImport(any()) }
        }

    @Test
    fun readFailuresSurfaceWithoutCallingTheSdk() =
        runTest {
            readOutcome = ReadOutcome.Failed(ImportProblem.NotUtf8)
            val vm = viewModel()
            vm.openUri(mockk<Uri>())
            assertEquals(ImportUiState.Failed(ImportProblem.NotUtf8), vm.state.value)
        }

    @Test
    fun filesAreReadThenPreviewed() =
        runTest {
            readOutcome = ReadOutcome.Text("file text")
            coEvery { sdk.previewImport("file text") } returns valid(isUpdate = true)
            val vm = viewModel()
            vm.openUri(mockk<Uri>())
            assertTrue(assertIs<ImportUiState.Preview>(vm.state.value).preview.isUpdate)
        }

    @Test
    fun confirmingImportsAndEmitsAnEvent() =
        runTest {
            coEvery { sdk.previewImport("text") } returns valid()
            coEvery { sdk.importPack("text") } returns success()
            val vm = viewModel()
            vm.openText("text")
            vm.events.test {
                vm.confirm()
                val event = assertIs<ImportEvent.Imported>(awaitItem())
                assertEquals("p", event.result.packId)
                assertEquals(false, event.fromSample)
            }
            assertEquals(ImportUiState.Idle, vm.state.value)
        }

    @Test
    fun downgradesGoThroughTheDowngradeEntryPoint() =
        runTest {
            coEvery { sdk.previewImport("old") } returns valid(isDowngrade = true)
            coEvery { sdk.importPackAllowingDowngrade("old") } returns success()
            val vm = viewModel()
            vm.openText("old")
            vm.confirm()
            coVerify { sdk.importPackAllowingDowngrade("old") }
            coVerify(exactly = 0) { sdk.importPack(any()) }
        }

    @Test
    fun importFailureKeepsTheUserInformed() =
        runTest {
            val error = ImportError(ImportErrorCode.STORAGE_ERROR, "disk full")
            coEvery { sdk.previewImport("text") } returns valid()
            coEvery { sdk.importPack("text") } returns ImportResult.Failure(error)
            val vm = viewModel()
            vm.openText("text")
            vm.confirm()
            assertEquals(ImportUiState.Failed(ImportProblem.Sdk(error)), vm.state.value)
        }

    @Test
    fun dismissResetsToIdle() =
        runTest {
            coEvery { sdk.previewImport("text") } returns valid()
            val vm = viewModel()
            vm.openText("text")
            vm.dismiss()
            assertEquals(ImportUiState.Idle, vm.state.value)
        }

    @Test
    fun samplesImportDirectlyAndReportTheOutcome() =
        runTest {
            coEvery { sdk.importSample("s1") } returns success()
            coEvery { sdk.importSample("nope") } returns ImportResult.Failure(ImportError(ImportErrorCode.UNKNOWN_SAMPLE, "unknown"))
            val vm = viewModel()
            vm.events.test {
                vm.addSample("s1")
                assertEquals(true, assertIs<ImportEvent.Imported>(awaitItem()).fromSample)
                vm.addSample("nope")
                assertIs<ImportEvent.SampleFailed>(awaitItem())
            }
        }

    @Test
    fun aLinkIsResolvedDownloadedAndImportedWithItsUrl() =
        runTest {
            val url = "https://example.org/pack.jackdaw"
            every { sdk.resolvePackUrl("nonsense") } returns null
            every { sdk.resolvePackUrl("example.org/pack.jackdaw") } returns url
            coEvery { sdk.previewImport("pack text") } returns valid(isUpdate = true, isDowngrade = true)
            coEvery { sdk.importPackFromUrl("pack text", url, true) } returns success(isUpdate = true)
            val vm = viewModel()

            vm.openUrl("nonsense")
            assertEquals(ImportProblem.InvalidUrl, assertIs<ImportUiState.Failed>(vm.state.value).problem)
            vm.openUrl("example.org/pack.jackdaw")
            assertEquals(ImportProblem.DownloadFailed, assertIs<ImportUiState.Failed>(vm.state.value).problem)

            fetched[url] = "pack text"
            vm.openUrl("example.org/pack.jackdaw")
            assertEquals(url, assertIs<ImportUiState.Preview>(vm.state.value).url)
            vm.events.test {
                vm.confirm()
                assertIs<ImportEvent.Imported>(awaitItem())
            }
            coVerify(exactly = 1) { sdk.importPackFromUrl("pack text", url, true) }
            coVerify(exactly = 0) { sdk.importPack(any()) }
        }

    @Test
    fun theCatalogReplacesTheLibraryAndOnlinePacksAreDownloaded() =
        runTest {
            val online = sample("s2", revision = 2, downloadUrl = "https://example.org/s2.jackdaw")
            every { sdk.catalogUrl() } returns "https://example.org/index.json"
            every { sdk.readCatalog("index") } returns listOf(sample("s1"), online)
            coEvery { sdk.importPack("pack text") } returns success()
            coEvery { sdk.importSample("s1") } returns success()
            val vm = viewModel()

            vm.loadCatalog()
            assertEquals(CatalogState.Failed, vm.catalog.value)
            assertEquals(emptyList(), vm.library.value)

            fetched["https://example.org/index.json"] = "index"
            vm.loadCatalog()
            assertEquals(CatalogState.Loaded, vm.catalog.value)
            assertEquals(listOf("s1", "s2"), vm.library.value.map { it.id })

            vm.events.test {
                vm.addSample("s2")
                assertEquals(ImportProblem.DownloadFailed, assertIs<ImportEvent.SampleFailed>(awaitItem()).problem)
                fetched["https://example.org/s2.jackdaw"] = "pack text"
                vm.addSample("s2")
                assertEquals(true, assertIs<ImportEvent.Imported>(awaitItem()).fromSample)
                vm.addSample("s1")
                assertIs<ImportEvent.Imported>(awaitItem())
            }
            coVerify(exactly = 1) { sdk.importPack("pack text") }
            coVerify(exactly = 1) { sdk.importSample("s1") }
            assertEquals(emptySet(), vm.adding.value)
        }

    @Test
    fun aCatalogThatCannotBeReadCountsAsFailed() =
        runTest {
            every { sdk.catalogUrl() } returns "https://example.org/index.json"
            every { sdk.readCatalog("<html>") } returns null
            fetched["https://example.org/index.json"] = "<html>"
            val vm = viewModel()
            vm.loadCatalog()
            assertEquals(CatalogState.Failed, vm.catalog.value)
        }

    @Test
    fun installedRevisionsFollowTheLibrary() =
        runTest {
            every { sdk.samplePacks() } returns emptyList()
            every { sdk.observePacks() } returns flowOf(listOf(packSummary("p", revision = 3)))
            val vm = ImportViewModel(sdk, reader) { null }
            vm.installedRevisions.test {
                var revisions = awaitItem()
                while (revisions.isEmpty()) revisions = awaitItem()
                assertEquals(mapOf("p" to 3), revisions)
            }
        }

    @Test
    fun installedIdsFollowTheLibrary() =
        runTest {
            val vm = viewModel()
            vm.installedIds.test {
                var ids = awaitItem()
                while (ids.isEmpty()) ids = awaitItem()
                assertEquals(setOf("p"), ids)
            }
        }
}
