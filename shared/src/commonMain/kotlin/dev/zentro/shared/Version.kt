package dev.zentro.shared

/** Versão semântica simples ("v1.2.3", "1.2.3", "1.2"). */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        fun parse(text: String): Version? {
            val parts = text.trim().removePrefix("v").removePrefix("V").substringBefore('-').split('.')
            if (parts.isEmpty() || parts.size > 3) return null
            val numbers = parts.map { it.toIntOrNull() ?: return null }
            return Version(numbers[0], numbers.getOrElse(1) { 0 }, numbers.getOrElse(2) { 0 })
        }

        /** True quando [candidate] é mais nova que [current]. Versões ilegíveis nunca contam como mais novas. */
        fun isNewer(candidate: String, current: String = BuildVersion.CURRENT): Boolean {
            val c = parse(candidate) ?: return false
            val now = parse(current) ?: return true
            return c > now
        }
    }
}
