package com.bu.paperclip

import com.bu.paperclip.ui.screens.WORK_MODES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkModeTest {
    @Test fun offersTheWebUiModesInOrder() {
        assertEquals(listOf("standard", "planning", "ask"), WORK_MODES)
    }

    @Test fun neverOffersSkillTest() {
        assertFalse("skill_test" in WORK_MODES)
    }
}
