package com.example

import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.ExpenseSplitEntity
import com.example.data.local.entity.GroupMemberEntity
import com.example.domain.expense.ExpenseCalculationData
import com.example.domain.expense.ExpenseEngine
import com.example.domain.expense.ExpenseSplitAllocation
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CrossDeviceExpenseSyncTest {

    private fun toUuid(raw: String): String {
        val trimmed = raw.trim()
        val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        return if (trimmed.matches(uuidRegex)) {
            trimmed
        } else {
            UUID.nameUUIDFromBytes(trimmed.toByteArray(Charsets.UTF_8)).toString()
        }
    }

    @Test
    fun testDeterministicUuidConversion() {
        val canonicalUuid = "c84f4e72-1b1a-4299-8cf0-51829e28f321"
        assertEquals(canonicalUuid, toUuid(canonicalUuid))

        // Non-UUID strings map to valid UUIDs deterministically across devices
        val legacyId = "trip_jaipur_couple"
        val mapped1 = toUuid(legacyId)
        val mapped2 = toUuid(legacyId)
        assertEquals(mapped1, mapped2)

        val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        assertTrue("Mapped UUID must conform to RFC 4122 standard", mapped1.matches(uuidRegex))
    }

    @Test
    fun testDeviceAToDeviceBExpenseAndEqualSplitSync() {
        val tripId = UUID.randomUUID().toString()
        val deviceIdA = "device_A_1234"
        val deviceIdB = "device_B_5678"

        // 1. Members setup
        val memberA = GroupMemberEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            participantId = deviceIdA,
            name = "User A",
            isCurrentUser = true
        )
        val memberB = GroupMemberEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            participantId = deviceIdB,
            name = "User B",
            isCurrentUser = false
        )

        val membersList = listOf(memberA, memberB)
        val memberPairs = membersList.map { it.id to it.name }

        // 2. User A adds ₹5,000 expense split equally between User A and User B
        val amountInRupees = 5000.0
        val amountInPaise = ExpenseEngine.rupeesToPaise(amountInRupees)
        val expenseId = UUID.randomUUID().toString()

        val equalAllocations = ExpenseEngine.calculateEqualSplits(
            amountInPaise = amountInPaise,
            participantIds = listOf(memberA.id, memberB.id),
            participantNames = mapOf(memberA.id to memberA.name, memberB.id to memberB.name)
        )

        assertEquals(2, equalAllocations.size)
        assertEquals(250000L, equalAllocations[0].shareAmountInPaise) // ₹2,500
        assertEquals(250000L, equalAllocations[1].shareAmountInPaise) // ₹2,500
        assertEquals(amountInPaise, equalAllocations.sumOf { it.shareAmountInPaise })

        val expense = ExpenseEntity(
            id = expenseId,
            tripId = tripId,
            title = "Resort Booking",
            category = "Accommodation",
            amountInPaise = amountInPaise,
            paidByMemberId = memberA.id,
            paidByMemberName = memberA.name,
            splitType = "EQUAL",
            participantIdsJson = "${memberA.id},${memberB.id}"
        )

        val splits = equalAllocations.map { alloc ->
            ExpenseSplitEntity(
                id = UUID.randomUUID().toString(),
                expenseId = expenseId,
                tripId = tripId,
                memberId = alloc.memberId,
                memberName = alloc.memberName,
                shareAmountInPaise = alloc.shareAmountInPaise,
                isSettled = alloc.memberId == memberA.id
            )
        }

        // 3. Verify balance on Device A
        val calcDataA = mutableListOf<ExpenseCalculationData>()
        calcDataA.add(ExpenseCalculationData(expense.paidByMemberId, expense.paidByMemberName, expense.amountInPaise, 0L))
        splits.forEach { s ->
            calcDataA.add(ExpenseCalculationData(s.memberId, s.memberName, 0L, s.shareAmountInPaise))
        }

        val balancesA = ExpenseEngine.calculateNetBalances(memberPairs, calcDataA, emptyList())
        val balA_UserA = balancesA.first { it.memberId == memberA.id }
        val balA_UserB = balancesA.first { it.memberId == memberB.id }

        assertEquals(250000L, balA_UserA.netBalanceInPaise) // +₹2,500
        assertEquals(-250000L, balA_UserB.netBalanceInPaise) // -₹2,500

        val debtsA = ExpenseEngine.calculatePairwiseDebts(balancesA)
        assertEquals(1, debtsA.size)
        assertEquals(memberB.id, debtsA[0].debtorId)
        assertEquals(memberA.id, debtsA[0].creditorId)
        assertEquals(250000L, debtsA[0].amountInPaise)
        assertEquals(2500.0, debtsA[0].amountInRupees, 0.001)

        // 4. Device B fetches the EXACT same records from Supabase
        val fetchedExpensesOnB = listOf(expense)
        val fetchedSplitsOnB = splits

        val calcDataB = mutableListOf<ExpenseCalculationData>()
        fetchedExpensesOnB.forEach { exp ->
            calcDataB.add(ExpenseCalculationData(exp.paidByMemberId, exp.paidByMemberName, exp.amountInPaise, 0L))
        }
        fetchedSplitsOnB.forEach { s ->
            calcDataB.add(ExpenseCalculationData(s.memberId, s.memberName, 0L, s.shareAmountInPaise))
        }

        val balancesB = ExpenseEngine.calculateNetBalances(memberPairs, calcDataB, emptyList())
        val balB_UserA = balancesB.first { it.memberId == memberA.id }
        val balB_UserB = balancesB.first { it.memberId == memberB.id }

        // Both devices must match 100%
        assertEquals(balA_UserA.netBalanceInPaise, balB_UserA.netBalanceInPaise)
        assertEquals(balA_UserB.netBalanceInPaise, balB_UserB.netBalanceInPaise)

        val debtsB = ExpenseEngine.calculatePairwiseDebts(balancesB)
        assertEquals(debtsA.size, debtsB.size)
        assertEquals(debtsA[0].debtorId, debtsB[0].debtorId)
        assertEquals(debtsA[0].creditorId, debtsB[0].creditorId)
        assertEquals(debtsA[0].amountInPaise, debtsB[0].amountInPaise)
    }

    @Test
    fun testRefreshReconciliationPreventsDuplicateSplits() {
        val tripId = UUID.randomUUID().toString()
        val memberA = GroupMemberEntity(id = "mA", tripId = tripId, name = "User A")
        val memberB = GroupMemberEntity(id = "mB", tripId = tripId, name = "User B")
        val memberPairs = listOf(memberA.id to memberA.name, memberB.id to memberB.name)

        val expense1 = ExpenseEntity(
            id = "exp1",
            tripId = tripId,
            title = "Lunch",
            category = "Food",
            amountInPaise = 200000L,
            paidByMemberId = "mA",
            paidByMemberName = "User A",
            participantIdsJson = "mA,mB"
        )
        val split1A = ExpenseSplitEntity(id = "sp1", expenseId = "exp1", tripId = tripId, memberId = "mA", memberName = "User A", shareAmountInPaise = 100000L)
        val split1B = ExpenseSplitEntity(id = "sp2", expenseId = "exp1", tripId = tripId, memberId = "mB", memberName = "User B", shareAmountInPaise = 100000L)

        // Simulate local database with existing records
        val localExpenses = mutableListOf(expense1)
        val localSplits = mutableListOf(split1A, split1B)

        // Multiple sync/refresh cycles should reconcile rather than append duplicates
        val remoteExpenses = listOf(expense1)
        val remoteSplits = listOf(split1A, split1B)

        // Reconciliation simulation: replace for tripId
        localExpenses.clear()
        localExpenses.addAll(remoteExpenses)
        localSplits.clear()
        localSplits.addAll(remoteSplits)

        assertEquals(1, localExpenses.size)
        assertEquals(2, localSplits.size)

        val calcData = mutableListOf<ExpenseCalculationData>()
        localExpenses.forEach { exp ->
            calcData.add(ExpenseCalculationData(exp.paidByMemberId, exp.paidByMemberName, exp.amountInPaise, 0L))
        }
        localSplits.forEach { s ->
            calcData.add(ExpenseCalculationData(s.memberId, s.memberName, 0L, s.shareAmountInPaise))
        }

        val balances = ExpenseEngine.calculateNetBalances(memberPairs, calcData, emptyList())
        val balA = balances.first { it.memberId == "mA" }
        val balB = balances.first { it.memberId == "mB" }

        // Must still be +₹1,000 and -₹1,000, NOT duplicated to +₹2,000 or -₹2,000
        assertEquals(100000L, balA.netBalanceInPaise)
        assertEquals(-100000L, balB.netBalanceInPaise)
    }

    @Test
    fun testSecondExpenseWithUnequalCustomSplit() {
        val tripId = UUID.randomUUID().toString()
        val memberA = GroupMemberEntity(id = "mA", tripId = tripId, name = "User A")
        val memberB = GroupMemberEntity(id = "mB", tripId = tripId, name = "User B")
        val memberPairs = listOf(memberA.id to memberA.name, memberB.id to memberB.name)

        // Expense 1: ₹5,000 paid by User A, equal split (₹2,500 each)
        val exp1 = ExpenseEntity(
            id = "exp1",
            tripId = tripId,
            title = "Hotel Stay",
            category = "Accommodation",
            amountInPaise = 500000L,
            paidByMemberId = "mA",
            paidByMemberName = "User A"
        )
        val sp1A = ExpenseSplitEntity(id = "sp1", expenseId = "exp1", tripId = tripId, memberId = "mA", memberName = "User A", shareAmountInPaise = 250000L)
        val sp1B = ExpenseSplitEntity(id = "sp2", expenseId = "exp1", tripId = tripId, memberId = "mB", memberName = "User B", shareAmountInPaise = 250000L)

        // Expense 2: ₹3,000 paid by User B, UNEQUAL split: User A owes ₹2,000, User B owes ₹1,000
        val customAllocations = listOf(
            ExpenseSplitAllocation(memberId = "mA", memberName = "User A", shareAmountInPaise = 200000L), // ₹2,000
            ExpenseSplitAllocation(memberId = "mB", memberName = "User B", shareAmountInPaise = 100000L)  // ₹1,000
        )
        val exp2AmountPaise = 300000L
        assertEquals(exp2AmountPaise, customAllocations.sumOf { it.shareAmountInPaise })

        val exp2 = ExpenseEntity(
            id = "exp2",
            tripId = tripId,
            title = "Special Safari",
            category = "Activities",
            amountInPaise = exp2AmountPaise,
            paidByMemberId = "mB",
            paidByMemberName = "User B",
            splitType = "CUSTOM"
        )
        val sp2A = ExpenseSplitEntity(id = "sp3", expenseId = "exp2", tripId = tripId, memberId = customAllocations[0].memberId, memberName = customAllocations[0].memberName, shareAmountInPaise = customAllocations[0].shareAmountInPaise)
        val sp2B = ExpenseSplitEntity(id = "sp4", expenseId = "exp2", tripId = tripId, memberId = customAllocations[1].memberId, memberName = customAllocations[1].memberName, shareAmountInPaise = customAllocations[1].shareAmountInPaise)

        val allExpenses = listOf(exp1, exp2)
        val allSplits = listOf(sp1A, sp1B, sp2A, sp2B)

        val calcData = mutableListOf<ExpenseCalculationData>()
        allExpenses.forEach { exp ->
            calcData.add(ExpenseCalculationData(exp.paidByMemberId, exp.paidByMemberName, exp.amountInPaise, 0L))
        }
        allSplits.forEach { s ->
            calcData.add(ExpenseCalculationData(s.memberId, s.memberName, 0L, s.shareAmountInPaise))
        }

        val balances = ExpenseEngine.calculateNetBalances(memberPairs, calcData, emptyList())
        val balA = balances.first { it.memberId == "mA" }
        val balB = balances.first { it.memberId == "mB" }

        // User A:
        // Paid: ₹5,000 (500,000 paise)
        // Share: ₹2,500 + ₹2,000 = ₹4,500 (450,000 paise)
        // Net: +₹500 (+50,000 paise)
        assertEquals(500000L, balA.totalPaidInPaise)
        assertEquals(450000L, balA.totalShareInPaise)
        assertEquals(50000L, balA.netBalanceInPaise)
        assertEquals(500.0, balA.netBalanceInRupees, 0.001)

        // User B:
        // Paid: ₹3,000 (300,000 paise)
        // Share: ₹2,500 + ₹1,000 = ₹3,500 (350,000 paise)
        // Net: -₹500 (-50,000 paise)
        assertEquals(300000L, balB.totalPaidInPaise)
        assertEquals(350000L, balB.totalShareInPaise)
        assertEquals(-50000L, balB.netBalanceInPaise)
        assertEquals(-500.0, balB.netBalanceInRupees, 0.001)

        // Resolved Debt: User B owes User A ₹500
        val debts = ExpenseEngine.calculatePairwiseDebts(balances)
        assertEquals(1, debts.size)
        assertEquals("mB", debts[0].debtorId)
        assertEquals("mA", debts[0].creditorId)
        assertEquals(50000L, debts[0].amountInPaise)
        assertEquals(500.0, debts[0].amountInRupees, 0.001)
    }
}
