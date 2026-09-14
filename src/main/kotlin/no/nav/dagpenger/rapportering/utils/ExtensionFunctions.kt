package no.nav.dagpenger.rapportering.utils

import kotlin.enums.EnumEntries

fun <E : Enum<E>> EnumEntries<E>.valueOfOrNull(name: String?): E? = firstOrNull { it.name == name }
