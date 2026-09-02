package org.dhis2.mobile.plugin.sample.data

import org.hisp.dhis.android.core.maintenance.D2Error
import org.hisp.dhis.android.core.maintenance.D2ErrorCode
import org.hisp.dhis.android.core.maintenance.D2ErrorComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The repository's promise is that it never throws. These run on the JVM with no `D2` at all: the
 * translation is a function, so it can be handed a failure directly.
 */
class CatchingD2Test {

    private fun d2Error(code: D2ErrorCode, description: String): D2Error =
        D2Error.builder()
            .errorCode(code)
            .errorDescription(description)
            .errorComponent(D2ErrorComponent.SDK)
            .build()

    @Test
    fun `passes a value through`() {
        assertEquals("ok", catchingD2 { "ok" }.getOrNull())
    }

    @Test
    fun `reads a D2Error's code and description, because its message is null`() {
        val error = d2Error(D2ErrorCode.OBJECT_CANT_BE_UPDATED, "Write refused")
        // Proving the premise, not assuming it: this is why the translation exists.
        assertEquals(null, error.message)

        val failure = catchingD2 { throw error }.exceptionOrNull()

        assertEquals("[OBJECT_CANT_BE_UPDATED] Write refused", failure?.message)
    }

    @Test
    fun `catches anything, not only D2Error`() {
        // The regression this guards: catching D2Error alone let an unexpected null escape into the
        // ViewModel's launch and take the host's screen with it.
        val failure = catchingD2 { throw IllegalStateException("unexpected null while mapping") }

        assertTrue(failure.isFailure)
        assertEquals("unexpected null while mapping", failure.exceptionOrNull()?.message)
    }

    @Test
    fun `keeps a non-D2 exception's own type`() {
        val failure = catchingD2 { throw NoSuchElementException("empty") }.exceptionOrNull()

        assertIs<NoSuchElementException>(failure)
    }
}
