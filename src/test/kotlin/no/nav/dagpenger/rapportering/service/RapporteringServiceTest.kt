package no.nav.dagpenger.rapportering.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectReader
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.date.shouldBeBefore
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.Headers
import io.ktor.server.plugins.BadRequestException
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.ApplicationBuilder
import no.nav.dagpenger.rapportering.ApplicationBuilder.Companion.getRapidsConnection
import no.nav.dagpenger.rapportering.api.ApiTestSetup.Companion.setEnvConfig
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.config.Configuration.unleash
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiode
import no.nav.dagpenger.rapportering.connector.AnsvarligSystem
import no.nav.dagpenger.rapportering.connector.toAdapterRapporteringsperiode
import no.nav.dagpenger.rapportering.connector.toAdapterRapporteringsperioder
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Utdanning
import no.nav.dagpenger.rapportering.model.ArbeidssøkerperiodeResponse
import no.nav.dagpenger.rapportering.model.BrukerResponse
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.MetadataResponse
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.PeriodeData
import no.nav.dagpenger.rapportering.model.PeriodeData.Kilde
import no.nav.dagpenger.rapportering.model.PeriodeData.OpprettetAv
import no.nav.dagpenger.rapportering.model.PeriodeData.PeriodeDag
import no.nav.dagpenger.rapportering.model.PeriodeData.Type
import no.nav.dagpenger.rapportering.model.Person
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Endret
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Ferdig
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Innsendt
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Midlertidig
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.repository.InnsendingtidspunktRepository
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import no.nav.dagpenger.rapportering.utils.februar
import no.nav.dagpenger.rapportering.utils.januar
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class RapporteringServiceTest {
    private val meldepliktService = mockk<MeldepliktService>()
    private val rapporteringRepository = mockk<RapporteringRepository>()
    private val innsendingtidspunktRepository = mockk<InnsendingtidspunktRepository>()
    private val journalfoeringService = mockk<JournalfoeringService>()
    private val kallLoggService = mockk<KallLoggService>()
    private val arbeidssøkerService = mockk<ArbeidssøkerService>()
    private val personregisterService = mockk<PersonregisterService>()
    private val meldekortregisterService = mockk<MeldekortregisterService>()
    private val pdlService = mockk<PdlService>()
    private val rapporteringService =
        RapporteringService(
            meldepliktService,
            rapporteringRepository,
            innsendingtidspunktRepository,
            journalfoeringService,
            kallLoggService,
            arbeidssøkerService,
            personregisterService,
            meldekortregisterService,
        )

    private val ident = "12345678910"
    private val token = "jwtToken"
    private val loginLevel = 4
    private val headers = Headers.Empty

    companion object {
        private val testRapid = TestRapid()

        @BeforeAll
        @JvmStatic
        fun setup() {
            setEnvConfig()

            mockkObject(ApplicationBuilder.Companion)
            mockkObject(unleash)
            every { getRapidsConnection() } returns testRapid
        }
    }

    @BeforeEach
    fun reset() {
        testRapid.reset()

        coEvery { personregisterService.hentAnsvarligSystem(any(), any()) } returns AnsvarligSystem.ARENA
        coEvery { pdlService.hentNavn(any()) } returns "Test Testesen"
    }

    @Test
    fun `harDpMeldeplikt returnerer det samme som meldepliktConnector returnerer`() {
        // True
        coEvery { meldepliktService.harDpMeldeplikt(ident, token) } returns "true"

        var harDpMeldeplikt = runBlocking { rapporteringService.harDpMeldeplikt(ident, token) }

        harDpMeldeplikt shouldBe "true"

        // False
        coEvery { meldepliktService.harDpMeldeplikt(ident, token) } returns "false"

        harDpMeldeplikt = runBlocking { rapporteringService.harDpMeldeplikt(ident, token) }

        harDpMeldeplikt shouldBe "false"
    }

    @Test
    fun `hent periode henter spesifisert periode som ikke er sendt inn og lagrer denne i databasen hvis den ikke finnes`() {
        coEvery { meldepliktService.hentRapporteringsperioder(ident, token) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns null
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode("2", ident, token, hentOriginal = true) }

        with(gjeldendePeriode!!) {
            id shouldBe "2"
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager.first().aktiviteter shouldBe emptyList()
            kanSendesFra shouldBe 27.januar
            sisteFristForTrekk shouldBe 5.februar
            kanSendes shouldBe true
            kanEndres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe TilUtfylling
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent periode henter spesifisert periode som er sendt inn`() {
        coEvery { meldepliktService.hentRapporteringsperioder(ident, token) } returns null
        coEvery { meldepliktService.hentInnsendteRapporteringsperioder(ident, token) } returns
            rapporteringsperiodeListe.map { it.copy(status = Innsendt) }.toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns emptyList()

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode("2", ident, token, hentOriginal = true) }

        with(gjeldendePeriode!!) {
            id shouldBe "2"
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager.first().aktiviteter shouldBe emptyList()
            kanSendesFra shouldBe 27.januar
            sisteFristForTrekk shouldBe 5.februar
            kanSendes shouldBe true
            kanEndres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe Innsendt
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent periode henter spesifisert periode og populerer denne med data fra databasen hvis den finnes`() {
        val rapporteringsperiodeFraDb =
            rapporteringsperiodeListe.first { it.id == "2" }.copy(
                dager =
                    getDager(
                        startDato = 14.januar,
                        aktivitet =
                            Aktivitet(
                                id = UUID.randomUUID(),
                                type = Utdanning,
                                timer = null,
                            ),
                    ),
            )
        coEvery { meldepliktService.hentRapporteringsperioder(ident, token) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns rapporteringsperiodeFraDb
        coJustRun { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode("2", ident, token, hentOriginal = true) }

        with(gjeldendePeriode!!) {
            id shouldBe "2"
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager shouldBe rapporteringsperiodeFraDb.dager
            kanSendesFra shouldBe 27.januar
            sisteFristForTrekk shouldBe 5.februar
            kanSendes shouldBe true
            kanEndres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe TilUtfylling
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent periode justerer når perioden kan sendes inn hvis innsendingstidspunktet skal justeres`() {
        coEvery { meldepliktService.hentRapporteringsperioder(ident, token) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns null
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt("201803") } returns -2

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode("2", ident, token, hentOriginal = true) }

        with(gjeldendePeriode!!) {
            id shouldBe "2"
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager.first().aktiviteter shouldBe emptyList()
            kanSendesFra shouldBe 26.januar
            sisteFristForTrekk shouldBe 5.februar
            kanSendes shouldBe true
            kanEndres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe TilUtfylling
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent periode henter spesifisert periode kun fra databasen hvis hentOriginal er false`() {
        val rapporteringsperiodeFraDb =
            rapporteringsperiodeListe.first { it.id == "2" }.copy(
                dager =
                    getDager(
                        startDato = 14.januar,
                        aktivitet =
                            Aktivitet(
                                id = UUID.randomUUID(),
                                type = Utdanning,
                                timer = null,
                            ),
                    ),
            )

        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns rapporteringsperiodeFraDb
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null

        val gjeldendePeriode = runBlocking { rapporteringService.hentPeriode("2", ident, token, hentOriginal = false) }

        with(gjeldendePeriode!!) {
            id shouldBe "2"
            periode.fraOgMed shouldBe 15.januar
            periode.tilOgMed shouldBe 28.januar
            dager.size shouldBe 14
            dager shouldBe rapporteringsperiodeFraDb.dager
            kanSendesFra shouldBe 27.januar
            sisteFristForTrekk shouldBe 5.februar
            kanSendes shouldBe true
            kanEndres shouldBe false
            bruttoBelop shouldBe null
            status shouldBe TilUtfylling
            registrertArbeidssoker shouldBe null
        }
    }

    @Test
    fun `hent alle rapporteringsperioder henter alle rapporteringsperioder og sorterer listen fra eldst til nyest`() {
        coEvery { meldepliktService.hentRapporteringsperioder(any(), any()) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns null
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null

        val rapporteringsperioder = runBlocking { rapporteringService.hentOgOppdaterRapporteringsperioder(ident, token)!! }

        rapporteringsperioder[0].id shouldBe "1"
        rapporteringsperioder[1].id shouldBe "2"
        rapporteringsperioder[2].id shouldBe "3"
        rapporteringsperioder.size shouldBe 3
    }

    @Test
    fun `hent alle rapporteringsperioder kan hente perioder fra dp-meldekortregister`() {
        coEvery { personregisterService.hentAnsvarligSystem(any(), any()) } returns AnsvarligSystem.DP
        coEvery { meldekortregisterService.hentRapporteringsperioder(any(), any(), MeldekortStatus.TilUtfylling) } returns
            meldekortregisterRapporteringsperiodeListe
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), ident) } returns null
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null

        val rapporteringsperioder = runBlocking { rapporteringService.hentOgOppdaterRapporteringsperioder(ident, token)!! }

        rapporteringsperioder[0].id shouldBe "3"
        rapporteringsperioder[1].id shouldBe "2"
        rapporteringsperioder[2].id shouldBe "1"
        rapporteringsperioder.size shouldBe 3
    }

    @Test
    fun `hent alle rapporteringsperioder populeres med data fra databasen hvis perioden finnes`() {
        coEvery { meldepliktService.hentRapporteringsperioder(any(), any()) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentRapporteringsperiode("1", ident) } returns
            lagRapporteringsperiode(
                id = "1",
                periode = Periode(fraOgMed = 1.januar, tilOgMed = 14.januar),
            ).copy(dager = getDager(startDato = 1.januar, aktivitet = Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null)))
        coEvery { rapporteringRepository.hentRapporteringsperiode("2", ident) } returns null
        coEvery { rapporteringRepository.hentRapporteringsperiode("3", ident) } returns null
        coJustRun { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null

        val rapporteringsperioder = runBlocking { rapporteringService.hentOgOppdaterRapporteringsperioder(ident, token)!! }

        // Rapporteringsperiode med ID = 1 oppdateres siden meldepliktConnector returnerer data med høyere status (Innsendt)
        coVerify(exactly = 1) { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }

        rapporteringsperioder[0].id shouldBe "1"
        rapporteringsperioder[0]
            .dager
            .first()
            .aktiviteter
            .first()
            .type shouldBe Utdanning
        rapporteringsperioder[1].id shouldBe "2"
        rapporteringsperioder[2].id shouldBe "3"
        rapporteringsperioder.size shouldBe 3
    }

    @Test
    fun `startUtfylling lagrer perioden i databasen`() {
        coEvery { meldepliktService.hentRapporteringsperioder(any(), any()) } returns null
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns null

        shouldThrow<RuntimeException> {
            runBlocking { rapporteringService.startUtfylling("1", ident, token) }
        }
    }

    @Test
    fun `startUtfylling feiler hvis perioden ikke finnes`() {
        coEvery { meldepliktService.hentRapporteringsperioder(any(), any()) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns null
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
        coEvery { innsendingtidspunktRepository.hentInnsendingtidspunkt(any()) } returns null

        runBlocking { rapporteringService.startUtfylling("1", ident, token) }

        coVerify(exactly = 1) { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
    }

    @Test
    fun `kan lagre aktivitet på eksisterende rapporteringsperiode`() {
        val aktiviteter = listOf(Aktivitet(id = UUID.randomUUID(), type = Utdanning, timer = null))
        coEvery { rapporteringRepository.hentDagId(any(), any()) } returns UUID.randomUUID()
        coJustRun { rapporteringRepository.slettOgLagreAktiviteter(any(), any(), any()) }

        runBlocking {
            rapporteringService.lagreEllerOppdaterAktiviteter(
                rapporteringId = "1",
                dag =
                    Dag(
                        dato = 1.januar,
                        aktiviteter = aktiviteter,
                        dagIndex = 0,
                    ),
            )
        }

        coVerify(exactly = 1) { rapporteringRepository.slettOgLagreAktiviteter(any(), any(), any()) }
        coVerify(exactly = 1) { rapporteringRepository.hentDagId(any(), any()) }
    }

    @Test
    fun `kan oppdatere om bruker vil fortsette som registrert arbeidssoker`() {
        coJustRun { rapporteringRepository.oppdaterRegistrertArbeidssoker(any(), any(), any()) }

        runBlocking { rapporteringService.oppdaterRegistrertArbeidssoker("1", "12345678910", true) }

        coVerify(exactly = 1) { rapporteringRepository.oppdaterRegistrertArbeidssoker("1", "12345678910", true) }
    }

    @Test
    fun `kan oppdatere begrunnelse`() {
        coJustRun { rapporteringRepository.oppdaterBegrunnelse(any(), any(), any()) }

        runBlocking { rapporteringService.oppdaterBegrunnelse("1", "12345678910", "Begrunnelse") }

        coVerify(exactly = 1) { rapporteringRepository.oppdaterBegrunnelse("1", "12345678910", "Begrunnelse") }
    }

    @Test
    fun `kan oppdatere rapporteringstype`() {
        coJustRun { rapporteringRepository.oppdaterRapporteringstype(any(), any(), any()) }

        runBlocking { rapporteringService.oppdaterRapporteringstype("1", "12345678910", "harIngenAktivitet") }

        coVerify(exactly = 1) { rapporteringRepository.oppdaterRapporteringstype("1", "12345678910", "harIngenAktivitet") }
    }

    @Test
    fun `kan endre rapporteringsperiode`() {
        coEvery { meldepliktService.hentInnsendteRapporteringsperioder(any(), any()) } returns
            listOf(rapporteringsperiodeListe.first().copy(id = "123", kanEndres = true).toAdapterRapporteringsperiode())
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns null
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(ident) } returns emptyList()
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
        coEvery { rapporteringRepository.finnesRapporteringsperiode(any(), any()) } returns true andThen true andThen false

        val response = runBlocking { rapporteringService.startEndring("123", ident, token) }

        response.id shouldNotBe "123"
        response.status shouldBe TilUtfylling
        response.originalId shouldBe "123"
        coVerify(exactly = 2) { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
        coVerify(exactly = 3) { rapporteringRepository.finnesRapporteringsperiode(any(), any()) }
    }

    @Test
    fun `kan ikke endre rapporteringsperiode som ikke kan endres`() {
        coEvery { meldepliktService.hentInnsendteRapporteringsperioder(any(), any()) } returns
            listOf(rapporteringsperiodeListe.first().copy(id = "123", kanEndres = false).toAdapterRapporteringsperiode())
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns emptyList()

        shouldThrow<IllegalArgumentException> {
            runBlocking { rapporteringService.startEndring("123", ident, token) }
        }
    }

    @Test
    fun `kan ikke endre rapporteringsperiode hvis perioden ikke finnes`() {
        coEvery { meldepliktService.hentInnsendteRapporteringsperioder(any(), any()) } returns null
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns null

        shouldThrow<RuntimeException> {
            runBlocking { rapporteringService.startEndring("123", ident, token) }
        }
    }

    @Test
    fun `kan hente innsendte rapporteringsperioder fra dp-meldekortregister`() {
        coEvery { personregisterService.hentAnsvarligSystem(any(), any()) } returns AnsvarligSystem.DP
        coEvery { meldekortregisterService.hentRapporteringsperioder(any(), any(), MeldekortStatus.Innsendt) } returns
            meldekortregisterRapporteringsperiodeListe
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns emptyList()

        val innsendteRapporteringsperioder = runBlocking { rapporteringService.hentInnsendteRapporteringsperioder(ident, token)!! }

        innsendteRapporteringsperioder.size shouldBe 3
        innsendteRapporteringsperioder[0].id shouldBe "1"
        innsendteRapporteringsperioder[1].id shouldBe "2"
        innsendteRapporteringsperioder[2].id shouldBe "3"
    }

    @Test
    fun `kan hente innsendte rapporteringsperioder`() {
        coEvery {
            meldepliktService.hentInnsendteRapporteringsperioder(any(), any())
        } returns
            rapporteringsperiodeListe
                .map { it.copy(status = Innsendt, kanSendes = false, kanEndres = true) }
                .toAdapterRapporteringsperioder()
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns emptyList()

        val innsendteRapporteringsperioder = runBlocking { rapporteringService.hentInnsendteRapporteringsperioder(ident, token)!! }

        innsendteRapporteringsperioder.size shouldBe 3
        innsendteRapporteringsperioder[0].id shouldBe "3"
        innsendteRapporteringsperioder[1].id shouldBe "2"
        innsendteRapporteringsperioder[2].id shouldBe "1"
    }

    @Test
    fun `liste med innsendte rapporteringsperioder blir populert med manglende perioder fra databasen som har riktig status`() {
        val perioderFraArena =
            rapporteringsperiodeListe
                .map { it.copy(status = Innsendt, kanSendes = false, kanEndres = true) }
                .toAdapterRapporteringsperioder()
        coEvery { meldepliktService.hentInnsendteRapporteringsperioder(any(), any()) } returns perioderFraArena
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns
            listOf(
                lagRapporteringsperiode("4", Periode(1.januar, 14.januar), status = Innsendt),
                lagRapporteringsperiode("5", Periode(15.januar, 28.januar)),
            )

        val innsendteRapporteringsperioder = runBlocking { rapporteringService.hentInnsendteRapporteringsperioder(ident, token)!! }

        innsendteRapporteringsperioder.size shouldBe 4
        innsendteRapporteringsperioder[0].id shouldBe "3"
        innsendteRapporteringsperioder[1].id shouldBe "2"
        innsendteRapporteringsperioder[2].id shouldBe "1"
        innsendteRapporteringsperioder[3].id shouldBe "4"
    }

    @Test
    fun `liste med innsendte rapporteringsperioder blir populert med siste versjoner av perioder fra databasen`() {
        val perioderFraArena =
            rapporteringsperiodeListe
                .map { it.copy(status = Ferdig, kanSendes = false, kanEndres = true) }
                .toAdapterRapporteringsperioder()
        coEvery { meldepliktService.hentInnsendteRapporteringsperioder(any(), any()) } returns perioderFraArena
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns
            listOf(
                lagRapporteringsperiode("1", Periode(1.januar, 14.januar), status = Ferdig, registrertArbeidssoker = true),
                lagRapporteringsperiode("2", Periode(15.januar, 28.januar), status = Innsendt, registrertArbeidssoker = true),
            )

        val innsendteRapporteringsperioder = runBlocking { rapporteringService.hentInnsendteRapporteringsperioder(ident, token)!! }

        // Perioden med ID = 3 finnes ikke i databasen og skal ikke endres
        // Perioden med ID = 2 har lavere status (Innsendt) i databasen enn i Arena (Ferdig) og skal ikke endres
        // Perioden med ID = 1 har samme status i databasen og Arena og skal populeres fra databasen
        innsendteRapporteringsperioder.size shouldBe 3
        innsendteRapporteringsperioder[0].id shouldBe "3"
        innsendteRapporteringsperioder[0].registrertArbeidssoker shouldBe null
        innsendteRapporteringsperioder[1].id shouldBe "2"
        innsendteRapporteringsperioder[1].registrertArbeidssoker shouldBe null
        innsendteRapporteringsperioder[2].id shouldBe "1"
        innsendteRapporteringsperioder[2].registrertArbeidssoker shouldBe true
    }

    @Test
    fun `liste med innsendte rapporteringsperioder blir sortert riktig med endret meldekort før originalt meldekort`() {
        val perioderFraArena =
            rapporteringsperiodeListe
                .map {
                    it.copy(
                        status =
                            if (it.id == "3") {
                                Endret
                            } else {
                                Innsendt
                            },
                        kanSendes = false,
                        kanEndres = true,
                        mottattDato = it.kanSendesFra,
                    )
                }.toAdapterRapporteringsperioder() +
                rapporteringsperiodeListe
                    .first()
                    .copy(
                        id = "10",
                        status = Innsendt,
                        kanSendes = false,
                        begrunnelseEndring = "Korrigert",
                        mottattDato = LocalDate.now(),
                    ).toAdapterRapporteringsperiode()
        coEvery { meldepliktService.hentInnsendteRapporteringsperioder(any(), any()) } returns perioderFraArena
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns
            listOf(
                lagRapporteringsperiode("4", Periode(1.januar, 14.januar), status = Innsendt),
                lagRapporteringsperiode("5", Periode(15.januar, 28.januar)),
            )

        val innsendteRapporteringsperioder = runBlocking { rapporteringService.hentInnsendteRapporteringsperioder(ident, token)!! }

        innsendteRapporteringsperioder.size shouldBe 5
        innsendteRapporteringsperioder[0].id shouldBe "10"
        innsendteRapporteringsperioder[0].periode shouldBeEqual innsendteRapporteringsperioder[1].periode
        innsendteRapporteringsperioder[1].id shouldBe "3"
        innsendteRapporteringsperioder[2].id shouldBe "2"
        innsendteRapporteringsperioder[3].id shouldBe "1"
        innsendteRapporteringsperioder[4].id shouldBe "4"
    }

    @Test
    fun `kan sende inn rapporteringsperiode`() {
        val rapporteringsperiode = rapporteringsperiodeListe.first().copy(registrertArbeidssoker = true)

        coEvery { meldepliktService.sendinnRapporteringsperiode(any(), token) } returns
            InnsendingResponse(
                id = rapporteringsperiode.id,
                status = "OK",
                feil = listOf(),
            )

        sendInn(rapporteringsperiode)
    }

    @Test
    fun `kan sende inn rapporteringsperiode til dp-meldekortregister`() {
        val rapporteringsperiode = rapporteringsperiodeListe.first().copy(registrertArbeidssoker = true)

        coEvery { personregisterService.hentAnsvarligSystem(any(), any()) } returns AnsvarligSystem.DP
        coEvery { meldekortregisterService.sendinnRapporteringsperiode(any(), token) } returns
            InnsendingResponse(
                id = rapporteringsperiode.id,
                status = "OK",
                feil = listOf(),
            )

        sendInn(rapporteringsperiode, "Dagpenger")
    }

    @Test
    fun `kan override registrertArbeidssoker i rapporteringsperiode`() {
        val rapporteringsperiode = rapporteringsperiodeListe.first().copy(registrertArbeidssoker = false)

        coEvery { journalfoeringService.journalfoer(any(), any(), any(), any(), any()) } returns mockk()
        coEvery { rapporteringRepository.hentKanSendes(any()) } returns true
        coJustRun { rapporteringRepository.settKanSendes(rapporteringsperiode.id, ident, false) }
        coJustRun { rapporteringRepository.oppdaterPeriodeEtterInnsending(rapporteringsperiode.id, ident, any(), false, any(), false) }
        coJustRun { rapporteringRepository.oppdaterPeriodeEtterInnsending(rapporteringsperiode.id, ident, true, false, Innsendt) }
        coEvery { meldepliktService.hentPerson(any(), any()) } returns Person(1L, "TESTESSEN", "TEST", "NO", "EMELD")
        val sendtPeriode = slot<AdapterRapporteringsperiode>()
        coEvery { meldepliktService.sendinnRapporteringsperiode(capture(sendtPeriode), token) } returns
            InnsendingResponse(
                id = rapporteringsperiode.id,
                status = "OK",
                feil = listOf(),
            )
        coEvery { kallLoggService.lagreKafkaUtKallLogg(eq(ident)) } returns 1
        coEvery { kallLoggService.lagreRequest(eq(1), any()) } just runs
        coEvery { kallLoggService.lagreResponse(eq(1), eq(200), eq("")) } just runs
        coEvery { arbeidssøkerService.hentCachedArbeidssøkerperioder(eq(ident)) } returns
            listOf(
                ArbeidssøkerperiodeResponse(
                    UUID.randomUUID(),
                    MetadataResponse(
                        LocalDateTime.now(),
                        BrukerResponse("", ""),
                        "Kilde",
                        "Årsak",
                        null,
                    ),
                    null,
                ),
            )
        coEvery { arbeidssøkerService.sendBekreftelse(eq(ident), any(), any(), any()) } just runs
        every { unleash.isEnabled(eq("dp-rapportering-sp5-true")) } returns true

        runBlocking {
            rapporteringService.sendRapporteringsperiode(rapporteringsperiode, token, ident, loginLevel, headers)
        }

        sendtPeriode.captured.registrertArbeidssoker shouldBe true
    }

    @Test
    fun `kan ikke sende inn rapporteringsperiode som allerede ble sendt`() {
        every { unleash.isEnabled(eq("dp-rapportering-tillat-innsending-uavhengig-av-kansendes")) } returns false
        coEvery { rapporteringRepository.hentKanSendes(any()) } returns false

        shouldThrow<BadRequestException> {
            runBlocking {
                rapporteringService.sendRapporteringsperiode(
                    rapporteringsperiodeListe.first().copy(kanSendes = true),
                    token,
                    ident,
                    loginLevel,
                    headers,
                )
            }
        }
    }

    @Test
    fun `kan ikke sende inn rapporteringsperiode som ikke kan sendes`() {
        every { unleash.isEnabled(eq("dp-rapportering-tillat-innsending-uavhengig-av-kansendes")) } returns false
        coEvery { rapporteringRepository.hentKanSendes(any()) } returns false

        shouldThrow<BadRequestException> {
            runBlocking {
                rapporteringService.sendRapporteringsperiode(
                    rapporteringsperiodeListe.first().copy(kanSendes = false),
                    token,
                    ident,
                    loginLevel,
                    headers,
                )
            }
        }
    }

    @Test
    fun `kan ikke sende inn rapporteringsperiode som ikke finnes i databasen`() {
        every { unleash.isEnabled(eq("dp-rapportering-tillat-innsending-uavhengig-av-kansendes")) } returns false
        coEvery { rapporteringRepository.hentKanSendes(any()) } returns null

        shouldThrow<BadRequestException> {
            runBlocking {
                rapporteringService.sendRapporteringsperiode(
                    rapporteringsperiodeListe.first().copy(kanSendes = true),
                    token,
                    ident,
                    loginLevel,
                    headers,
                )
            }
        }
    }

    @Test
    fun `kan sende inn endret rapporteringsperiode med begrunnelse`() {
        val endringId = "4"
        val originalPeriode = rapporteringsperiodeListe.first()
        val rapporteringsperiode =
            originalPeriode.copy(
                status = TilUtfylling,
                begrunnelseEndring = "Endring",
                originalId = originalPeriode.id,
                registrertArbeidssoker = true,
            )
        coEvery { journalfoeringService.journalfoer(any(), any(), any(), any(), any()) } returns mockk()
        coEvery { rapporteringRepository.hentKanSendes(any()) } returns true
        coJustRun { rapporteringRepository.settKanSendes(rapporteringsperiode.id, ident, false) }
        coJustRun { rapporteringRepository.oppdaterPeriodeEtterInnsending(any(), any(), any(), any(), any()) }
        coJustRun { rapporteringRepository.oppdaterPeriodeEtterInnsending(any(), any(), any(), any(), any(), false) }
        coEvery { meldepliktService.hentEndringId(any(), any()) } returns endringId
        coEvery { meldepliktService.hentPerson(any(), any()) } returns Person(1L, "TESTESSEN", "TEST", "NO", "EMELD")
        coJustRun { rapporteringRepository.slettRaporteringsperiode(any()) }
        val periode = slot<Rapporteringsperiode>()
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(capture(periode), ident) }
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns emptyList()
        coEvery { meldepliktService.hentInnsendteRapporteringsperioder(any(), any()) } returns
            rapporteringsperiodeListe.toAdapterRapporteringsperioder()
        coEvery { meldepliktService.sendinnRapporteringsperiode(any(), token) } returns
            InnsendingResponse(
                id = endringId,
                status = "OK",
                feil = listOf(),
            )
        coEvery { kallLoggService.lagreKafkaUtKallLogg(eq(ident)) } returns 1
        coEvery { kallLoggService.lagreRequest(eq(1), any()) } just runs
        coEvery { kallLoggService.lagreResponse(eq(1), eq(200), eq("")) } just runs
        coEvery { arbeidssøkerService.hentCachedArbeidssøkerperioder(eq(ident)) } returns
            listOf(
                ArbeidssøkerperiodeResponse(
                    UUID.randomUUID(),
                    MetadataResponse(
                        rapporteringsperiode.periode.fraOgMed.atStartOfDay(),
                        BrukerResponse("", ""),
                        "Kilde",
                        "Årsak",
                        null,
                    ),
                    null,
                ),
            )
        coEvery { arbeidssøkerService.sendBekreftelse(eq(ident), any(), any(), any()) } just runs
        every { unleash.isEnabled(eq("send-periodedata")) } returns true

        val innsendingResponse =
            runBlocking {
                rapporteringService.sendRapporteringsperiode(rapporteringsperiode, token, ident, loginLevel, headers)
            }

        innsendingResponse.id shouldBe endringId
        innsendingResponse.status shouldBe "OK"

        verify(exactly = 1) {
            runBlocking {
                journalfoeringService.journalfoer(any(), any(), any(), any(), any())
            }
        }
        coVerify(exactly = 1) {
            rapporteringRepository
                .oppdaterPeriodeEtterInnsending(rapporteringsperiode.id, ident, false, false, Midlertidig)
        }
        coVerify(exactly = 1) {
            rapporteringRepository
                .oppdaterPeriodeEtterInnsending(endringId, ident, false, false, Innsendt)
        }
        coVerify(exactly = 1) {
            rapporteringRepository
                .oppdaterPeriodeEtterInnsending(originalPeriode.id, ident, false, false, Innsendt, false)
        }
        coVerify(exactly = 1) { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), ident) }
        periode.captured.id shouldBe endringId
        periode.captured.dager.forEachIndexed { dagIndex, dag ->
            dag.dato shouldBe rapporteringsperiode.dager[dagIndex].dato
            dag.dagIndex shouldBe rapporteringsperiode.dager[dagIndex].dagIndex
            dag.aktiviteter.forEachIndexed { index, aktivitet ->
                aktivitet.id shouldNotBe rapporteringsperiode.dager[dagIndex].aktiviteter[index].id
                aktivitet.type shouldBe rapporteringsperiode.dager[dagIndex].aktiviteter[index].type
                aktivitet.timer shouldBe rapporteringsperiode.dager[dagIndex].aktiviteter[index].timer
            }
        }

        checkRapid(rapporteringsperiode, endringId)
    }

    @Test
    fun `kan sende inn endret rapporteringsperiode med begrunnelse når ansvarlig system er DP`() {
        val endringId = "4"
        val originalPeriode = rapporteringsperiodeListe.first()
        val rapporteringsperiode =
            originalPeriode.copy(
                status = TilUtfylling,
                begrunnelseEndring = "Endring",
                originalId = originalPeriode.id,
                registrertArbeidssoker = true,
            )
        coEvery { personregisterService.hentAnsvarligSystem(any(), any()) } returns AnsvarligSystem.DP
        coEvery { journalfoeringService.journalfoer(any(), any(), any(), any(), AnsvarligSystem.DP) } returns mockk()
        coEvery { rapporteringRepository.hentKanSendes(any()) } returns true
        coJustRun { rapporteringRepository.settKanSendes(rapporteringsperiode.id, ident, false) }
        coJustRun { rapporteringRepository.oppdaterPeriodeEtterInnsending(any(), any(), any(), any(), any()) }
        coJustRun { rapporteringRepository.oppdaterPeriodeEtterInnsending(any(), any(), any(), any(), any(), false) }
        coJustRun { rapporteringRepository.slettRaporteringsperiode(any()) }
        val periode = slot<Rapporteringsperiode>()
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(capture(periode), ident) }
        coEvery { rapporteringRepository.hentLagredeRapporteringsperioder(any()) } returns emptyList()
        coEvery { meldekortregisterService.sendKorrigertMeldekort(any(), token) } returns
            InnsendingResponse(
                id = endringId,
                status = "OK",
                feil = listOf(),
            )
        coEvery { kallLoggService.lagreKafkaUtKallLogg(eq(ident)) } returns 1
        coEvery { kallLoggService.lagreRequest(eq(1), any()) } just runs
        coEvery { kallLoggService.lagreResponse(eq(1), eq(200), eq("")) } just runs
        coEvery { arbeidssøkerService.hentCachedArbeidssøkerperioder(eq(ident)) } returns
            listOf(
                ArbeidssøkerperiodeResponse(
                    UUID.randomUUID(),
                    MetadataResponse(
                        rapporteringsperiode.periode.fraOgMed.atStartOfDay(),
                        BrukerResponse("", ""),
                        "Kilde",
                        "Årsak",
                        null,
                    ),
                    null,
                ),
            )
        coEvery { arbeidssøkerService.sendBekreftelse(eq(ident), any(), any(), any()) } just runs
        every { unleash.isEnabled(eq("send-periodedata")) } returns true

        val innsendingResponse =
            runBlocking {
                rapporteringService.sendRapporteringsperiode(rapporteringsperiode, token, ident, loginLevel, headers)
            }

        innsendingResponse.id shouldBe endringId
        innsendingResponse.status shouldBe "OK"

        verify(exactly = 1) {
            runBlocking {
                journalfoeringService.journalfoer(any(), any(), any(), any(), AnsvarligSystem.DP)
            }
        }
        coVerify(exactly = 1) {
            rapporteringRepository
                .oppdaterPeriodeEtterInnsending(rapporteringsperiode.id, ident, false, false, Midlertidig)
        }
        coVerify(exactly = 1) {
            rapporteringRepository
                .oppdaterPeriodeEtterInnsending(endringId, ident, false, false, Innsendt)
        }
        coVerify(exactly = 1) {
            rapporteringRepository
                .oppdaterPeriodeEtterInnsending(originalPeriode.id, ident, false, false, Innsendt, false)
        }
        coVerify(exactly = 1) { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), ident) }
        periode.captured.id shouldBe endringId
        periode.captured.dager.forEachIndexed { dagIndex, dag ->
            dag.dato shouldBe rapporteringsperiode.dager[dagIndex].dato
            dag.dagIndex shouldBe rapporteringsperiode.dager[dagIndex].dagIndex
            dag.aktiviteter.forEachIndexed { index, aktivitet ->
                aktivitet.id shouldNotBe rapporteringsperiode.dager[dagIndex].aktiviteter[index].id
                aktivitet.type shouldBe rapporteringsperiode.dager[dagIndex].aktiviteter[index].type
                aktivitet.timer shouldBe rapporteringsperiode.dager[dagIndex].aktiviteter[index].timer
            }
        }

        checkRapid(rapporteringsperiode, endringId, ansvarligSystem = "DP")
    }

    @Test
    fun `kan ikke sende inn endret rapporteringsperiode uten begrunnelse`() {
        coEvery { rapporteringRepository.hentKanSendes(any()) } returns true
        coJustRun { rapporteringRepository.settKanSendes(any(), any(), false) }

        shouldThrow<BadRequestException> {
            runBlocking {
                rapporteringService.sendRapporteringsperiode(
                    rapporteringsperiodeListe.first().copy(status = TilUtfylling, begrunnelseEndring = null, originalId = "125"),
                    token,
                    ident,
                    loginLevel,
                    headers,
                )
            }
        }
    }

    @Test
    fun `lagreEllerOppdaterPeriode lagrer perioden hvis den ikke finnes i databasen`() {
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns null
        coJustRun { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }

        runBlocking { rapporteringService.lagreEllerOppdaterPeriode(rapporteringsperiodeListe.first(), ident) }

        coVerify(exactly = 1) { rapporteringRepository.lagreRapporteringsperiodeOgDager(any(), any()) }
    }

    @Test
    fun `lagreEllerOppdaterPeriode oppdaterer perioden hvis den finnes i databasen fra før`() {
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns rapporteringsperiodeListe.first()
        coJustRun { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }

        runBlocking { rapporteringService.lagreEllerOppdaterPeriode(rapporteringsperiodeListe.first(), ident) }

        coVerify(exactly = 1) { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }
    }

    @Test
    fun `lagreEllerOppdaterPeriode oppdaterer ikke perioden hvis den finnes i databasen fra før med en høyere status`() {
        coEvery { rapporteringRepository.hentRapporteringsperiode(any(), any()) } returns
            rapporteringsperiodeListe.first().copy(status = Innsendt)

        runBlocking { rapporteringService.lagreEllerOppdaterPeriode(rapporteringsperiodeListe.first(), ident) }

        coVerify(exactly = 0) { rapporteringRepository.oppdaterRapporteringsperiodeFraArena(any(), any()) }
    }

    @Test
    fun `kan slette alle aktiviteter for en periode`() {
        val dagPairList: List<Pair<UUID, Dag>> = getDager().map { UUID.randomUUID() to it }
        coEvery { rapporteringRepository.finnesRapporteringsperiode(any(), any()) } returns true
        coEvery { rapporteringRepository.hentDagerUtenAktivitet(any()) } returns dagPairList
        coJustRun { rapporteringRepository.slettAktiviteter(any()) }

        runBlocking { rapporteringService.resettAktiviteter("1", ident) }

        coVerify { rapporteringRepository.finnesRapporteringsperiode("1", ident) }
        coVerify { rapporteringRepository.hentDagerUtenAktivitet("1") }
        coVerify(exactly = 14) { rapporteringRepository.slettAktiviteter(any()) }
    }

    @Test
    fun `sletter ikke aktiviteter hvis rapporteringsperioden ikke finnes`() {
        coEvery { rapporteringRepository.finnesRapporteringsperiode(any(), any()) } returns false

        shouldThrow<RuntimeException> {
            runBlocking { rapporteringService.resettAktiviteter("1", ident) }
        }
    }

    @Test
    fun `kan slette mellomlagrede rapporteringsperioder som er sendt inn`() {
        coEvery { rapporteringRepository.hentRapporteringsperiodeIdForInnsendtePerioder() } returns
            listOf(rapporteringsperiodeListe.first().id)
        coEvery { rapporteringRepository.hentRapporteringsperiodeIdForMidlertidigePerioder() } returns emptyList()
        coEvery { rapporteringRepository.hentRapporteringsperiodeIdForPerioderEtterSisteFrist() } returns emptyList()
        coJustRun { rapporteringRepository.slettRaporteringsperiode(any()) }

        val slettedePerioder = runBlocking { rapporteringService.slettMellomlagredeRapporteringsperioder() }

        coVerify(exactly = 1) { rapporteringRepository.slettRaporteringsperiode("3") }
        slettedePerioder shouldBe 1
    }

    @Test
    fun `kan slette midlertidige rapporteringsperioder`() {
        coEvery { rapporteringRepository.hentRapporteringsperiodeIdForInnsendtePerioder() } returns emptyList()
        coEvery { rapporteringRepository.hentRapporteringsperiodeIdForMidlertidigePerioder() } returns
            listOf(rapporteringsperiodeListe.first().id)
        coEvery { rapporteringRepository.hentRapporteringsperiodeIdForPerioderEtterSisteFrist() } returns emptyList()
        coJustRun { rapporteringRepository.slettRaporteringsperiode(any()) }

        val slettedePerioder = runBlocking { rapporteringService.slettMellomlagredeRapporteringsperioder() }

        coVerify(exactly = 1) { rapporteringRepository.slettRaporteringsperiode("3") }
        slettedePerioder shouldBe 1
    }

    @Test
    fun `kan slette mellomlagrede rapporteringsperioder som er ikke er sendt inn innen siste frist`() {
        coEvery { rapporteringRepository.hentRapporteringsperiodeIdForInnsendtePerioder() } returns emptyList()
        coEvery { rapporteringRepository.hentRapporteringsperiodeIdForMidlertidigePerioder() } returns emptyList()
        coEvery { rapporteringRepository.hentRapporteringsperiodeIdForPerioderEtterSisteFrist() } returns
            rapporteringsperiodeListe.map { it.id }
        coJustRun { rapporteringRepository.slettRaporteringsperiode(any()) }

        val slettedePerioder = runBlocking { rapporteringService.slettMellomlagredeRapporteringsperioder() }

        coVerify(exactly = 3) { rapporteringRepository.slettRaporteringsperiode(any()) }
        slettedePerioder shouldBe 3
    }

    private fun sendInn(
        rapporteringsperiode: Rapporteringsperiode,
        ansvarligSystem: String = "Arena",
    ) {
        coEvery { journalfoeringService.journalfoer(any(), any(), any(), any(), any()) } returns mockk()
        coEvery { rapporteringRepository.hentKanSendes(any()) } returns true
        coJustRun { rapporteringRepository.settKanSendes(rapporteringsperiode.id, ident, false) }
        coJustRun { rapporteringRepository.oppdaterPeriodeEtterInnsending(rapporteringsperiode.id, ident, any(), false, any(), false) }
        coJustRun { rapporteringRepository.oppdaterPeriodeEtterInnsending(rapporteringsperiode.id, ident, true, false, Innsendt) }
        coEvery { meldepliktService.hentPerson(any(), any()) } returns Person(1L, "TESTESSEN", "TEST", "NO", "EMELD")
        coEvery { kallLoggService.lagreKafkaUtKallLogg(eq(ident)) } returns 1
        coEvery { kallLoggService.lagreRequest(eq(1), any()) } just runs
        coEvery { kallLoggService.lagreResponse(eq(1), eq(200), eq("")) } just runs
        coEvery { arbeidssøkerService.hentCachedArbeidssøkerperioder(eq(ident)) } returns
            listOf(
                ArbeidssøkerperiodeResponse(
                    UUID.randomUUID(),
                    MetadataResponse(
                        LocalDateTime.now(),
                        BrukerResponse("", ""),
                        "Kilde",
                        "Årsak",
                        null,
                    ),
                    null,
                ),
            )
        coEvery { arbeidssøkerService.sendBekreftelse(eq(ident), any(), any(), any()) } just runs
        every { unleash.isEnabled(eq("send-periodedata")) } returns true

        val innsendingResponse =
            runBlocking {
                rapporteringService.sendRapporteringsperiode(rapporteringsperiode, token, ident, loginLevel, headers)
            }

        innsendingResponse.id shouldBe rapporteringsperiode.id
        innsendingResponse.status shouldBe "OK"

        verify(exactly = 1) {
            runBlocking {
                journalfoeringService.journalfoer(any(), any(), any(), any(), any())
            }
        }
        coVerify(exactly = 1) { rapporteringRepository.oppdaterPeriodeEtterInnsending(any(), any(), any(), any(), any()) }

        checkRapid(rapporteringsperiode, null, false, ansvarligSystem)
    }

    private fun checkRapid(
        rapporteringsperiode: Rapporteringsperiode,
        endringId: String? = null,
        meldt: Boolean = true,
        ansvarligSystem: String = "Arena",
    ) {
        if (ansvarligSystem != "Arena") {
            testRapid.inspektør.size shouldBe 0
            return
        }

        testRapid.inspektør.size shouldBe 1

        val message = testRapid.inspektør.message(0)

        if (endringId == null) {
            message["@event_name"].asText() shouldBe "meldekort_innsendt"
            message["id"].asText() shouldBe rapporteringsperiode.id
            message["type"].asText() shouldBe "Original"
            message["korrigeringAv"].isNull shouldBe true
        } else {
            message["@event_name"].asText() shouldBe "meldekort_innsendt"
            message["id"].asText() shouldBe endringId
            message["type"].asText() shouldBe "Korrigert"
            message["korrigeringAv"].asText() shouldBe rapporteringsperiode.id
        }

        message["ident"].asText() shouldBe ident

        val periode = message["periode"]
        periode["fraOgMed"].asLocalDate() shouldBe rapporteringsperiode.periode.fraOgMed
        periode["tilOgMed"].asLocalDate() shouldBe rapporteringsperiode.periode.tilOgMed

        val reader: ObjectReader = defaultObjectMapper.readerFor(object : TypeReference<List<PeriodeDag>>() {})
        val dager: List<PeriodeDag> = reader.readValue(message["dager"])
        dager.forEachIndexed { i, dag ->
            dag.dato shouldBeEqual rapporteringsperiode.dager[i].dato
            dag.dagIndex shouldBeEqual rapporteringsperiode.dager[i].dagIndex
            dag.aktiviteter shouldBeEqual rapporteringsperiode.dager[i].aktiviteter
            dag.meldt shouldBe meldt
        }

        message["kanSendesFra"].asLocalDate() shouldBe rapporteringsperiode.kanSendesFra
        message["opprettetAv"].asText() shouldBe ansvarligSystem

        val kilde = message["kilde"]
        kilde["rolle"].asText() shouldBe "Bruker"
        kilde["ident"].asText() shouldBe ident

        message["status"].asText() shouldBe "Innsendt"
        val innsendtTidspunkt = message["innsendtTidspunkt"].asLocalDateTime()
        innsendtTidspunkt.toLocalDate() shouldBe LocalDate.now()
        innsendtTidspunkt shouldBeBefore LocalDateTime.now()
    }
}

val rapporteringsperiodeListe =
    listOf(
        lagRapporteringsperiode(
            id = "3",
            periode = Periode(fraOgMed = 29.januar, tilOgMed = 11.februar),
        ),
        lagRapporteringsperiode(
            id = "1",
            periode = Periode(fraOgMed = 1.januar, tilOgMed = 14.januar),
            status = Innsendt,
        ),
        lagRapporteringsperiode(
            id = "2",
            periode = Periode(fraOgMed = 15.januar, tilOgMed = 28.januar),
        ),
    )

val meldekortregisterRapporteringsperiodeListe =
    listOf(
        lagPeriodeData("1", Periode(LocalDate.now(), LocalDate.now().plusDays(13))),
        lagPeriodeData("2", Periode(LocalDate.now().minusDays(14), LocalDate.now().minusDays(1))),
        lagPeriodeData("3", Periode(LocalDate.now().minusDays(28), LocalDate.now().minusDays(15))),
    )

val fremtidigeRaporteringsperioder =
    listOf(
        lagRapporteringsperiode(
            id = "1",
            periode =
                Periode(
                    fraOgMed = LocalDate.now().minusWeeks(3).plusDays(1),
                    tilOgMed = LocalDate.now().minusWeeks(1),
                ),
        ),
        lagRapporteringsperiode(
            id = "2",
            periode =
                Periode(
                    fraOgMed = LocalDate.now().minusWeeks(2),
                    tilOgMed = LocalDate.now().minusDays(1),
                ),
        ),
        lagRapporteringsperiode(
            id = "3",
            periode =
                Periode(
                    fraOgMed = LocalDate.now(),
                    tilOgMed = LocalDate.now().plusWeeks(2).minusDays(1),
                ),
        ),
    )

fun lagRapporteringsperiode(
    id: String,
    periode: Periode,
    status: RapporteringsperiodeStatus = TilUtfylling,
    registrertArbeidssoker: Boolean? = null,
) = Rapporteringsperiode(
    id = id,
    type = "05",
    periode = periode,
    dager = getDager(startDato = periode.fraOgMed),
    kanSendesFra = periode.tilOgMed.minusDays(1),
    kanSendes = true,
    kanEndres = false,
    bruttoBelop = null,
    status = status,
    registrertArbeidssoker = registrertArbeidssoker,
    begrunnelseEndring = null,
    originalId = null,
    rapporteringstype = null,
    mottattDato = null,
)

fun lagPeriodeData(
    id: String,
    periode: Periode,
) = PeriodeData(
    id = id,
    ident = "01020312345",
    periode = periode,
    dager = listOf(PeriodeDag(LocalDate.now(), emptyList(), 0)),
    kanSendesFra = LocalDate.now(),
    opprettetAv = OpprettetAv.Dagpenger,
    kilde = Kilde(PeriodeData.Rolle.Bruker, "01020312345"),
    type = Type.Original,
    status = "TilUtfylling",
    innsendtTidspunkt = null,
    korrigeringAv = null,
)

private fun getDager(
    startDato: LocalDate = 1.januar,
    aktivitet: Aktivitet? = null,
): List<Dag> =
    (0..13)
        .map { i ->
            Dag(
                dato = startDato.plusDays(i.toLong()),
                aktiviteter = aktivitet?.let { listOf(it) } ?: emptyList(),
                dagIndex = i,
            )
        }
