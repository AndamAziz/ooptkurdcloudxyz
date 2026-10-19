package xyz.kurdcloud.englishmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StaffAccessTest {
    @Test
    fun `access follows roles`() {
        assertFalse(StaffAccess.from(setOf(Role.STUDENT)).any)
        assertEquals(StaffAccess(canReviewQuestions = true, canManageUsers = false), StaffAccess.from(setOf(Role.STUDENT, Role.TEACHER)))
        assertEquals(StaffAccess(canReviewQuestions = true, canManageUsers = true), StaffAccess.from(setOf(Role.ADMIN)))
    }

    @Test
    fun `unknown api roles are ignored`() {
        assertEquals(null, Role.fromApi("owner"))
        assertEquals(Role.TEACHER, Role.fromApi("teacher"))
    }
}
