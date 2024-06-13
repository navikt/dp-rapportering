package no.nav.dagpenger.rapportering.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import mu.KLogging
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.utils.PDFGenerator
import no.nav.sbl.meldekort.model.meldekort.journalpost.AvsenderIdType
import no.nav.sbl.meldekort.model.meldekort.journalpost.AvsenderMottaker
import no.nav.sbl.meldekort.model.meldekort.journalpost.Bruker
import no.nav.sbl.meldekort.model.meldekort.journalpost.BrukerIdType
import no.nav.sbl.meldekort.model.meldekort.journalpost.Dokument
import no.nav.sbl.meldekort.model.meldekort.journalpost.DokumentVariant
import no.nav.sbl.meldekort.model.meldekort.journalpost.Filetype
import no.nav.sbl.meldekort.model.meldekort.journalpost.Journalpost
import no.nav.sbl.meldekort.model.meldekort.journalpost.Journalposttype
import no.nav.sbl.meldekort.model.meldekort.journalpost.Sak
import no.nav.sbl.meldekort.model.meldekort.journalpost.Sakstype
import no.nav.sbl.meldekort.model.meldekort.journalpost.Tema
import no.nav.sbl.meldekort.model.meldekort.journalpost.Tilleggsopplysning
import no.nav.sbl.meldekort.model.meldekort.journalpost.Variantformat
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Base64
import java.util.Locale
import java.util.UUID

class JournalfoeringService {
    companion object : KLogging()

    private val kanal = "NAV_NO"
    private val journalfoerendeEnhet = "9999"
    private val brevkode = "NAV 00-10.02"
    private val brevkodeKorrigert = "NAV 00-10.03"

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.YYYY")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.YYYY HH:mm")
    private var locale: Locale? = Locale.of("nb", "NO") // Vi skal regne ukenummer iht norske regler
    private val woy = WeekFields.of(locale).weekOfWeekBasedYear()

    fun journalfoer(
        ident: String,
        loginLevel: Int,
        rapporteringsperiode: Rapporteringsperiode,
    ) {
        // TODO: Hvordan kan vi hente navn?
        val navn = "NAVN"

        val journalpost =
            Journalpost(
                journalposttype = Journalposttype.INNGAAENDE,
                avsenderMottaker =
                    AvsenderMottaker(
                        id = ident,
                        idType = AvsenderIdType.FNR,
                        navn = navn,
                    ),
                bruker =
                    Bruker(
                        id = ident,
                        idType = BrukerIdType.FNR,
                    ),
                tema = Tema.DAG,
                tittel = getTittle(rapporteringsperiode),
                kanal = kanal,
                journalfoerendeEnhet = journalfoerendeEnhet,
                // Det er duplikatkontroll på eksternReferanseId for inngående dokumenter
                eksternReferanseId = UUID.randomUUID().toString(),
                datoMottatt = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                tilleggsopplysninger = getTilleggsopplysninger(rapporteringsperiode),
                sak =
                    Sak(
                        sakstype = Sakstype.GENERELL_SAK,
                    ),
                dokumenter = getDokumenter(rapporteringsperiode, ident, navn, loginLevel),
            )

        logger.info("Opprettet journalpost for rapporteringsperiode ${rapporteringsperiode.id}")
    }

    private fun getTittle(rapporteringsperiode: Rapporteringsperiode): String {
        val uke1 = rapporteringsperiode.periode.fraOgMed.get(woy)
        val uke2 = rapporteringsperiode.periode.tilOgMed.get(woy)
        val fra = rapporteringsperiode.periode.fraOgMed.format(dateFormatter)
        val til = rapporteringsperiode.periode.tilOgMed.format(dateFormatter)

        val tittel = "Meldekort"
        /*
            TODO: Finne ut om denne perioden er korrigert
            var tittel = "Meldekort"
            if (meldekortDetaljerInn.kortType == KortType.KORRIGERT_ELEKTRONISK) {
                tittel = "Korrigert meldekort"
            }
         */

        return "$tittel for uke $uke1 - $uke2 ($fra - $til) elektronisk mottatt av NAV"
    }

    private fun getTilleggsopplysninger(rapporteringsperiode: Rapporteringsperiode): List<Tilleggsopplysning> =
        mutableListOf(
            // Nøkkel - maksimum 20 tegn
            Tilleggsopplysning(
                "id",
                rapporteringsperiode.id.toString(),
            ),
            Tilleggsopplysning(
                "kanSendesFra",
                rapporteringsperiode.kanSendesFra.format(DateTimeFormatter.ISO_DATE),
            ),
        )

    private fun getDokumenter(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
        navn: String,
        loginLevel: Int,
    ): List<Dokument> {
        val brevkode = brevkode
        /*
            TODO: Finne ut om denne perioden er korrigert
            var brevkode = BREVKODE
            if (meldekortDetaljerInn.kortType == KortType.KORRIGERT_ELEKTRONISK) {
                brevkode = BREVKODE_KORRIGERT
            }
         */

        val meldekort =
            Dokument(
                tittel = getTittle(rapporteringsperiode),
                brevkode = brevkode,
                dokumentvarianter =
                    listOf(
                        getJSON(rapporteringsperiode),
                        getPDF(rapporteringsperiode, ident, navn, loginLevel),
                    ),
            )

        return listOf(meldekort)
    }

    private fun getJSON(rapporteringsperiode: Rapporteringsperiode): DokumentVariant {
        val objectMapper =
            ObjectMapper()
                .registerKotlinModule()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        return DokumentVariant(
            filtype = Filetype.JSON,
            variantformat = Variantformat.ORIGINAL,
            fysiskDokument = Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(rapporteringsperiode)),
        )
    }

    private fun getPDF(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
        navn: String,
        loginLevel: Int,
    ): DokumentVariant {
        val tittel = "Elektronisk innsendt meldekort"
        /*
            TODO: Finne ut om denne perioden er korrigert
            var tittel = "Elektronisk innsendt meldekort"
            if (meldekortDetaljerInn.kortType == KortType.KORRIGERT_ELEKTRONISK) {
                tittel = "Elektronisk korrigert meldekort"
            }
         */

        val logo = this::class.java.getResource("/nav-logo.svg")!!.readText()

        val aktiviteter =
            rapporteringsperiode.dager.joinToString("\n") { dag ->
                "<div>" +
                    "<b>" + dag.dato.format(dateFormatter) + ":</b> " +
                    dag.aktiviteter.joinToString(", ") { aktivitet ->
                        val timer =
                            if (!aktivitet.timer.isNullOrBlank() && aktivitet.timer.toDouble() > 0) {
                                " " + aktivitet.timer + "t"
                            } else {
                                ""
                            }

                        "" + aktivitet.type + timer
                    } +
                    "</div>"
            }

        val html =
            """
                <div class="info">
                   <b>ID:</b> ${rapporteringsperiode.id}<br/>
                   <b>Tema:</b> ${Tema.DAG.tittel}<br/>
                   <b>Tilgangsnivå:</b> $loginLevel
                </div>
                
                $logo
                
                <h1>$tittel</h1>
                <div><b>${getTittle(rapporteringsperiode)}</b></div>
                <div><b>Meldekortet ble mottatt:</b> ${LocalDateTime.now().format(dateTimeFormatter)}</div>
                <div><b>Bruker:</b> $navn ($ident)</div>
                <div><b>Neste meldekort kan sendes inn fra:</b> ${rapporteringsperiode.kanSendesFra.format(dateFormatter)}</div>
                <br>
                $aktiviteter
                """

        val pdf = PDFGenerator().createPDFA(html)

        val actualFile = File("test.pdf")
        actualFile.writeBytes(pdf)

        return DokumentVariant(
            filtype = Filetype.PDFA,
            variantformat = Variantformat.ARKIV,
            fysiskDokument = Base64.getEncoder().encodeToString(pdf),
        )
    }
}
