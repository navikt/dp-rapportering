package no.nav.dagpenger.rapportering.model

data class SporsmalOmRegistrertArbeidssoker(
    val svarFraBruker: Boolean?,
    val arsakBrukerHarIkkeSvart: ÅrsakTilAtBrukerIkkeSkalSvarePåSpørsmålOmArbeidssøkerstatus?,
)
