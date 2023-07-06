package no.nav.dagpenger.rapportering

import java.time.LocalDateTime
import java.util.UUID

class Godkjenningslogg(godkjenninger: List<Godkjenning> = emptyList()) {
    private val godkjenninger = godkjenninger.toMutableList()

    fun leggTil(godkjenning: Godkjenning) {
        require(!godkjent()) { "Kan ikke godkjenne noe som allerede er godkjent" }
        godkjenninger.add(godkjenning)
    }

    fun avgodkjenn() {
        require(godkjent()) { "Må være godkjent for å kunne avgodkjenne" }
        gjeldende().avgodkjenn()
    }

    private fun gjeldende() = godkjenninger.last()
    fun godkjent() = godkjenninger.lastOrNull()?.godkjent() ?: false
}

class Godkjenning(
    val id: UUID,
    private val utførtAv: Kilde,
    private val opprettet: LocalDateTime,
    private var avgodkjent: LocalDateTime? = null,
    private val begrunnelse: String? = null,
) {
    constructor(utførtAv: Sluttbruker) : this(UUID.randomUUID(), utførtAv, LocalDateTime.now())

    constructor(utførtAv: Saksbehandler, begrunnelse: String) : this(
        UUID.randomUUID(),
        utførtAv,
        LocalDateTime.now(),
        begrunnelse = begrunnelse,
    )

    fun avgodkjenn() {
        avgodkjent = LocalDateTime.now()
    }

    fun godkjent() = avgodkjent == null
    fun kanEndre(kilde: Kilde) = kilde == utførtAv

    sealed class Kilde(val id: String) {
        abstract fun kanEndre(godkjenning: Godkjenning): Boolean
    }

    data class Saksbehandler(private val saksbehandlerId: String) : Kilde(saksbehandlerId) {
        override fun kanEndre(godkjenning: Godkjenning) = true
    }

    data class Sluttbruker(private val ident: String) : Kilde(ident) {
        override fun kanEndre(godkjenning: Godkjenning) = godkjenning.kanEndre(this)
    }
}
