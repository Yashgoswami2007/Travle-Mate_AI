package com.example

import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.ExpenseSplitEntity
import com.example.data.local.entity.GroupMemberEntity
import com.example.domain.expense.ExpenseCalculationData
import com.example.domain.expense.ExpenseEngine
import com.example.domain.expense.SettlementRecordData
import com.example.ui.components.QrCodeUtils
import org.junit.Assert.*
import org.junit.Test

class ExpenseEngineTest {

    @Test
    fun testExactPaiseRoundingThreeAndSevenWaySplits() {
        // 1. Split ₹100.00 (10,000 paise) among 3 members
        val amountInPaise1 = 10000L
        val participants3 = listOf("m1", "m2", "m3")
        val names3 = mapOf("m1" to "Alice", "m2" to "Bob", "m3" to "Charlie")

        val splits3 = ExpenseEngine.calculateEqualSplits(amountInPaise1, participants3, names3)

        assertEquals(3, splits3.size)
        // 10000 / 3 = 3333 with remainder 1. First gets 3334, others get 3333.
        assertEquals(3334L, splits3[0].shareAmountInPaise)
        assertEquals(3333L, splits3[1].shareAmountInPaise)
        assertEquals(3333L, splits3[2].shareAmountInPaise)

        // Exact sum verification
        val totalSum3 = splits3.sumOf { it.shareAmountInPaise }
        assertEquals(amountInPaise1, totalSum3)

        // 2. Split ₹100.00 (10,000 paise) among 7 members
        val participants7 = (1..7).map { "m$it" }
        val names7 = participants7.associateWith { "Member $it" }

        val splits7 = ExpenseEngine.calculateEqualSplits(amountInPaise1, participants7, names7)
        assertEquals(7, splits7.size)

        // 10000 / 7 = 1428 with remainder 4. First 4 get 1429, last 3 get 1428.
        assertEquals(1429L, splits7[0].shareAmountInPaise)
        assertEquals(1429L, splits7[1].shareAmountInPaise)
        assertEquals(1429L, splits7[2].shareAmountInPaise)
        assertEquals(1429L, splits7[3].shareAmountInPaise)
        assertEquals(1428L, splits7[4].shareAmountInPaise)
        assertEquals(1428L, splits7[5].shareAmountInPaise)
        assertEquals(1428L, splits7[6].shareAmountInPaise)

        val totalSum7 = splits7.sumOf { it.shareAmountInPaise }
        assertEquals(amountInPaise1, totalSum7)
    }

    @Test
    fun testMultiDeviceTwoParticipantExpenseBalance() {
        val members = listOf("dev_A" to "Device A", "dev_B" to "Device B")

        // Device A pays ₹1200 (120,000 paise) split equally (60,000 paise each)
        // Device B pays ₹600 (60,000 paise) split equally (30,000 paise each)
        val expenseRecords = listOf(
            ExpenseCalculationData("dev_A", "Device A", paidAmountInPaise = 120000L, shareAmountInPaise = 0L),
            ExpenseCalculationData("dev_A", "Device A", paidAmountInPaise = 0L, shareAmountInPaise = 60000L),
            ExpenseCalculationData("dev_B", "Device B", paidAmountInPaise = 0L, shareAmountInPaise = 60000L),

            ExpenseCalculationData("dev_B", "Device B", paidAmountInPaise = 60000L, shareAmountInPaise = 0L),
            ExpenseCalculationData("dev_A", "Device A", paidAmountInPaise = 0L, shareAmountInPaise = 30000L),
            ExpenseCalculationData("dev_B", "Device B", paidAmountInPaise = 0L, shareAmountInPaise = 30000L)
        )

        val balances = ExpenseEngine.calculateNetBalances(members, expenseRecords, emptyList())

        val balanceA = balances.first { it.memberId == "dev_A" }
        val balanceB = balances.first { it.memberId == "dev_B" }

        // Device A: Total Paid = 120000, Total Share = 90000. Net = +30000 (+₹300)
        assertEquals(120000L, balanceA.totalPaidInPaise)
        assertEquals(90000L, balanceA.totalShareInPaise)
        assertEquals(30000L, balanceA.netBalanceInPaise)

        // Device B: Total Paid = 60000, Total Share = 90000. Net = -30000 (-₹300)
        assertEquals(60000L, balanceB.totalPaidInPaise)
        assertEquals(90000L, balanceB.totalShareInPaise)
        assertEquals(-30000L, balanceB.netBalanceInPaise)

        // Pairwise debt calculation
        val debts = ExpenseEngine.calculatePairwiseDebts(balances)
        assertEquals(1, debts.size)
        assertEquals("dev_B", debts[0].debtorId)
        assertEquals("dev_A", debts[0].creditorId)
        assertEquals(30000L, debts[0].amountInPaise)
        assertEquals(300.0, debts[0].amountInRupees, 0.001)
    }

    @Test
    fun testCustomParticipantSubsets() {
        val members = listOf("m1" to "A", "m2" to "B", "m3" to "C", "m4" to "D")

        // ₹1000 (100,000 paise) paid by A, but split ONLY between A and B (50,000 paise each)
        val expenseRecords = listOf(
            ExpenseCalculationData("m1", "A", paidAmountInPaise = 100000L, shareAmountInPaise = 0L),
            ExpenseCalculationData("m1", "A", paidAmountInPaise = 0L, shareAmountInPaise = 50000L),
            ExpenseCalculationData("m2", "B", paidAmountInPaise = 0L, shareAmountInPaise = 50000L)
        )

        val balances = ExpenseEngine.calculateNetBalances(members, expenseRecords, emptyList())

        val balanceA = balances.first { it.memberId == "m1" }
        val balanceB = balances.first { it.memberId == "m2" }
        val balanceC = balances.first { it.memberId == "m3" }
        val balanceD = balances.first { it.memberId == "m4" }

        assertEquals(50000L, balanceA.netBalanceInPaise)
        assertEquals(-50000L, balanceB.netBalanceInPaise)
        assertEquals(0L, balanceC.netBalanceInPaise)
        assertEquals(0L, balanceD.netBalanceInPaise)
    }

    @Test
    fun testSettlementsReduceNetDebts() {
        val members = listOf("dev_A" to "Device A", "dev_B" to "Device B")

        // Initial state: B owes A ₹300 (30,000 paise)
        val expenseRecords = listOf(
            ExpenseCalculationData("dev_A", "Device A", paidAmountInPaise = 60000L, shareAmountInPaise = 0L),
            ExpenseCalculationData("dev_A", "Device A", paidAmountInPaise = 0L, shareAmountInPaise = 30000L),
            ExpenseCalculationData("dev_B", "Device B", paidAmountInPaise = 0L, shareAmountInPaise = 30000L)
        )

        // Device B pays Device A a settlement payment of ₹300 (30,000 paise)
        val settlementRecords = listOf(
            SettlementRecordData(payerMemberId = "dev_B", receiverMemberId = "dev_A", amountInPaise = 30000L)
        )

        val balances = ExpenseEngine.calculateNetBalances(members, expenseRecords, settlementRecords)

        val balanceA = balances.first { it.memberId == "dev_A" }
        val balanceB = balances.first { it.memberId == "dev_B" }

        assertEquals(0L, balanceA.netBalanceInPaise)
        assertEquals(0L, balanceB.netBalanceInPaise)

        val debts = ExpenseEngine.calculatePairwiseDebts(balances)
        assertTrue("Pairwise debts should be empty after full settlement", debts.isEmpty())
    }

    @Test
    fun testQrCodePayloadValidationAndBackendVerification() {
        val payloadStr = QrCodeUtils.encodeParticipantPayload("dev_12345", "Alex", "alex_traveler")
        assertTrue("Payload should start with tripmate URI scheme", payloadStr.startsWith("tripmate://participant"))

        val parsed = QrCodeUtils.parseParticipantPayload(payloadStr)
        assertNotNull("Parsed payload must not be null", parsed)
        assertEquals("dev_12345", parsed!!.id)
        assertEquals("Alex", parsed.name)
        assertEquals("alex_traveler", parsed.username)

        // Test invalid payload
        val invalid = QrCodeUtils.parseParticipantPayload("random_invalid_string")
        assertNull("Invalid payload should return null or fallback safely", invalid)
    }
}
