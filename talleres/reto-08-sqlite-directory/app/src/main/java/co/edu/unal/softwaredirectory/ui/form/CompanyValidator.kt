package co.edu.unal.softwaredirectory.ui.form

import co.edu.unal.softwaredirectory.data.Classification
import co.edu.unal.softwaredirectory.data.Company
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Lo que el usuario lleva escrito en el formulario, tal cual (sin validar ni limpiar). */
data class CompanyDraft(
    val name: String = "",
    val website: String = "",
    val phone: String = "",
    val email: String = "",
    val products: String = "",
    val classifications: Set<Classification> = emptySet()
) {
    companion object {
        fun from(company: Company) = CompanyDraft(
            company.name, company.website, company.phone, company.email, company.products, company.classifications
        )
    }
}

enum class CompanyField { NAME, CLASSIFICATIONS, WEBSITE, PHONE, EMAIL }

enum class FieldError { REQUIRED, TOO_LONG, INVALID, DUPLICATE }

sealed interface Validation {
    /** [company] trae los valores limpios: sin espacios de más, URL con esquema, email en minúsculas. */
    data class Valid(val company: Company) : Validation
    data class Invalid(val errors: Map<CompanyField, FieldError>) : Validation
}

/**
 * Obligatorios: nombre y al menos una clasificación. Web, teléfono y email son opcionales, pero si se
 * llenan tienen que ser válidos. Kotlin puro (sin android.util.Patterns) para poder testearlo en la JVM.
 */
object CompanyValidator {

    const val MAX_NAME_LENGTH = 100
    const val MAX_PRODUCTS_LENGTH = 1000

    private const val MIN_PHONE_DIGITS = 7
    private const val MAX_PHONE_DIGITS = 15

    private val SCHEME = "^[a-zA-Z][a-zA-Z0-9+.-]*://".toRegex()
    private val EMAIL = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$".toRegex()
    // "+" solo al principio; espacios, guiones y paréntesis para que se pueda escribir como se dicta
    private val PHONE = "^\\+?[0-9 ()-]+$".toRegex()

    fun validate(draft: CompanyDraft, id: Long = Company.NEW_ID): Validation {
        val errors = mutableMapOf<CompanyField, FieldError>()

        val name = draft.name.trim()
        when {
            name.isEmpty() -> errors[CompanyField.NAME] = FieldError.REQUIRED
            name.length > MAX_NAME_LENGTH -> errors[CompanyField.NAME] = FieldError.TOO_LONG
        }
        if (draft.classifications.isEmpty()) errors[CompanyField.CLASSIFICATIONS] = FieldError.REQUIRED

        val website = normalizeWebsite(draft.website)
        if (website == null) errors[CompanyField.WEBSITE] = FieldError.INVALID

        val phone = draft.phone.trim()
        if (phone.isNotEmpty() && !isValidPhone(phone)) errors[CompanyField.PHONE] = FieldError.INVALID

        val email = draft.email.trim().lowercase(Locale.ROOT)
        if (email.isNotEmpty() && !isValidEmail(email)) errors[CompanyField.EMAIL] = FieldError.INVALID

        if (errors.isNotEmpty()) return Validation.Invalid(errors)
        return Validation.Valid(
            Company(
                id = id,
                name = name,
                website = website.orEmpty(),
                phone = phone,
                email = email,
                products = draft.products.trim().take(MAX_PRODUCTS_LENGTH),
                classifications = draft.classifications
            )
        )
    }

    /**
     * "" si está vacía, null si no es válida. Sin esquema se asume https ("example.com" → "https://example.com").
     * Válida = http(s) con un host que tenga al menos un punto.
     */
    fun normalizeWebsite(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        if (text.any { it.isWhitespace() }) return null
        val url = if (SCHEME.containsMatchIn(text)) text else "https://$text"
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        if ('.' !in host || host.startsWith('.') || host.endsWith('.')) return null
        return url
    }

    fun isValidPhone(phone: String): Boolean =
        PHONE.matches(phone) && phone.count { it.isDigit() } in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS

    fun isValidEmail(email: String): Boolean = EMAIL.matches(email)
}
