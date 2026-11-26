package co.edu.unal.softwaredirectory.ui.form

import co.edu.unal.softwaredirectory.data.Classification
import co.edu.unal.softwaredirectory.data.Company
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyValidatorTest {

    private val minimal = CompanyDraft(name = "Acme", classifications = setOf(Classification.CONSULTING))

    private fun errorsOf(draft: CompanyDraft) = (CompanyValidator.validate(draft) as Validation.Invalid).errors

    @Test
    fun nameAndOneClassification_areEnough() {
        val company = (CompanyValidator.validate(minimal, id = 7L) as Validation.Valid).company
        assertEquals(Company(id = 7L, name = "Acme", classifications = setOf(Classification.CONSULTING)), company)
    }

    @Test
    fun emptyForm_requiresNameAndClassification() {
        assertEquals(
            mapOf(CompanyField.NAME to FieldError.REQUIRED, CompanyField.CLASSIFICATIONS to FieldError.REQUIRED),
            errorsOf(CompanyDraft(name = "   "))
        )
    }

    @Test
    fun nameLongerThanTheLimit_isRejected() {
        val errors = errorsOf(minimal.copy(name = "x".repeat(CompanyValidator.MAX_NAME_LENGTH + 1)))
        assertEquals(mapOf(CompanyField.NAME to FieldError.TOO_LONG), errors)
    }

    @Test
    fun validValues_areCleanedUp() {
        val draft = minimal.copy(
            name = "  Acme  ",
            website = " acme.example.com ",
            phone = " +57 (601) 000-0101 ",
            email = " Ventas@Acme.Example.COM ",
            products = "  Apps  "
        )
        val company = (CompanyValidator.validate(draft) as Validation.Valid).company
        assertEquals("Acme", company.name)
        assertEquals("https://acme.example.com", company.website)
        assertEquals("+57 (601) 000-0101", company.phone)
        assertEquals("ventas@acme.example.com", company.email)
        assertEquals("Apps", company.products)
    }

    @Test
    fun invalidContactFields_areReportedEachOnItsField() {
        val errors = errorsOf(minimal.copy(website = "not a url", phone = "12ab", email = "ana@"))
        assertEquals(
            mapOf(
                CompanyField.WEBSITE to FieldError.INVALID,
                CompanyField.PHONE to FieldError.INVALID,
                CompanyField.EMAIL to FieldError.INVALID
            ),
            errors
        )
    }

    @Test
    fun website_acceptsHttpAndHttpsWithADottedHost() {
        assertEquals("", CompanyValidator.normalizeWebsite("  "))
        assertEquals("https://example.com", CompanyValidator.normalizeWebsite("example.com"))
        assertEquals("http://example.com/path?q=1", CompanyValidator.normalizeWebsite("http://example.com/path?q=1"))
        assertEquals("https://sub.example.co:8080", CompanyValidator.normalizeWebsite("sub.example.co:8080"))
        assertNull(CompanyValidator.normalizeWebsite("ftp://example.com"))
        assertNull(CompanyValidator.normalizeWebsite("localhost"))
        assertNull(CompanyValidator.normalizeWebsite("example."))
        assertNull(CompanyValidator.normalizeWebsite("exa mple.com"))
        assertNull(CompanyValidator.normalizeWebsite("javascript:alert(1)"))
    }

    @Test
    fun phone_needsSevenToFifteenDigits_andPlusOnlyAtTheStart() {
        assertTrue(CompanyValidator.isValidPhone("6010000101"))
        assertTrue(CompanyValidator.isValidPhone("+57 300 000 0202"))
        assertTrue(CompanyValidator.isValidPhone("1234567"))
        assertFalse(CompanyValidator.isValidPhone("123456"))
        assertFalse(CompanyValidator.isValidPhone("1234567890123456"))
        assertFalse(CompanyValidator.isValidPhone("300+0000000"))
        assertFalse(CompanyValidator.isValidPhone("300 000 000 ext 2"))
    }

    @Test
    fun email_needsUserAndDomainWithTld() {
        assertTrue(CompanyValidator.isValidEmail("a.b+c@sub.example.co"))
        assertFalse(CompanyValidator.isValidEmail("ana@example"))
        assertFalse(CompanyValidator.isValidEmail("@example.com"))
        assertFalse(CompanyValidator.isValidEmail("ana@@example.com"))
        assertFalse(CompanyValidator.isValidEmail("ana @example.com"))
    }
}
