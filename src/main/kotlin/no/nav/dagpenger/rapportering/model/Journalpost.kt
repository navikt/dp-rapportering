package no.nav.sbl.meldekort.model.meldekort.journalpost

import com.fasterxml.jackson.annotation.JsonInclude

// Se https://confluence.adeo.no/display/BOA/opprettJournalpost
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Journalpost(
    val journalposttype: Journalposttype,
    val avsenderMottaker: AvsenderMottaker? = null,
    val bruker: Bruker? = null,
    val tema: Tema? = null,
    val behandlingstema: String? = null,
    val tittel: String? = null,
    val kanal: String? = null,
    val journalfoerendeEnhet: String? = null,
    val eksternReferanseId: String? = null,
    val datoMottatt: String? = null,
    val tilleggsopplysninger: List<Tilleggsopplysning>? = null,
    val sak: Sak? = null,
    val dokumenter: List<Dokument>? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AvsenderMottaker(
    val id: String,
    val idType: AvsenderIdType,
    val navn: String? = null,
    val land: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Bruker(
    val id: String,
    val idType: BrukerIdType,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Tilleggsopplysning(
    val nokkel: String,
    val verdi: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Sak(
    val sakstype: Sakstype,
    val fagsakId: String? = null,
    val fagsaksystem: FagsaksSystem? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Dokument(
    val tittel: String? = null,
    val brevkode: String? = null,
    val dokumentKategori: String? = null,
    val dokumentvarianter: List<DokumentVariant> = mutableListOf(),
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DokumentVariant(
    val filtype: Filetype,
    val variantformat: Variantformat,
    val fysiskDokument: String,
    val filnavn: String? = null,
    val batchnavn: String? = null,
)

enum class Journalposttype {
    INNGAAENDE,
    UTGAAENDE,
    NOTAT,
}

// Se https://confluence.adeo.no/display/BOA/Tema
enum class Tema(val tittel: String) {
    DAG("Dagpenger"),
    AAP("Arbeidsavklaringspenger"),
    IND("Individstønad"),
    OPP("Oppfølging"),
    GEN("Generell"),
}

enum class AvsenderIdType {
    FNR,
    ORGNR,
    HPRNR,
    UTL_ORG,
}

enum class BrukerIdType {
    FNR,
    ORGNR,
    AKTOERID,
}

enum class Sakstype {
    FAGSAK,
    GENERELL_SAK,
    ARKIVSAK,
}

enum class FagsaksSystem {
    AO01,
    AO11,
    BISYS,
    FS36,
    FS38,
    IT01,
    K9,
    OB36,
    OEBS,
    PP01,
    UFM,
    BA,
    EF,
    KONT,
    SUPSTONAD,
    OMSORGSPENGER,
}

// Se https://confluence.adeo.no/display/BOA/Filtype
enum class Filetype {
    PDF,
    PDFA,
    XML,
    RTF,
    DLF,
    JPEG,
    TIFF,
    AXML,
    DXML,
    JSON,
    PNG,
}

// Se https://confluence.adeo.no/display/BOA/Variantformat
enum class Variantformat {
    ARKIV,
    ORIGINAL,
    SLADDET,
    FULLVERSJON,
    SKANNING_META,
    BREVBESTILLING,
    PRODUKSJON,
    PRODUKSJON_DLF,
}
