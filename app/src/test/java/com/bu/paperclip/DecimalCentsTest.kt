package com.bu.paperclip

import com.bu.paperclip.data.*
import com.bu.paperclip.net.AppJson
import org.junit.Assert.assertEquals
import org.junit.Test

class DecimalCentsTest {
    @Test
    fun parsesDecimalAndIntegerCents() {
        val c = AppJson.decodeFromString(Company.serializer(), """{"id":"c","budgetMonthlyCents":60000,"spentMonthlyCents":75.4335444}""")
        assertEquals(60000L, c.budgetMonthlyCents)
        assertEquals(75L, c.spentMonthlyCents)
    }

    @Test
    fun parsesDecimalCostSummaryAndTokens() {
        val s = AppJson.decodeFromString(CostSummary.serializer(), """{"spendCents":1234.5,"budgetCents":"100","utilizationPercent":36.4}""")
        assertEquals(1235L, s.spendCents)
        assertEquals(100L, s.budgetCents)
        val i = AppJson.decodeFromString(IssueCostSummary.serializer(), """{"costCents":42.7,"inputTokens":1.0E3,"runCount":2.0,"runtimeMs":360000.4}""")
        assertEquals(43L, i.costCents)
        assertEquals(1000L, i.inputTokens)
        assertEquals(2, i.runCount)
        assertEquals(360000L, i.runtimeMs)
    }

    @Test
    fun agentDecimalCentsAndNulls() {
        val a = AppJson.decodeFromString(Agent.serializer(), """{"id":"a","spentMonthlyCents":0.5,"budgetMonthlyCents":null}""")
        assertEquals(1L, a.spentMonthlyCents)
        assertEquals(null, a.budgetMonthlyCents)
    }
}
