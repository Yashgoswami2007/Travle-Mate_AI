package com.example.domain.expense

import kotlin.math.abs

data class ExpenseSplitAllocation(
    val memberId: String,
    val memberName: String,
    val shareAmountInPaise: Long
) {
    val shareAmountInRupees: Double get() = shareAmountInPaise / 100.0
}

data class MemberBalance(
    val memberId: String,
    val memberName: String,
    val totalPaidInPaise: Long = 0L,
    val totalShareInPaise: Long = 0L,
    val settlementsPaidInPaise: Long = 0L,
    val settlementsReceivedInPaise: Long = 0L
) {
    val netBalanceInPaise: Long
        get() = (totalPaidInPaise + settlementsPaidInPaise) - (totalShareInPaise + settlementsReceivedInPaise)

    val totalPaidInRupees: Double get() = totalPaidInPaise / 100.0
    val totalShareInRupees: Double get() = totalShareInPaise / 100.0
    val netBalanceInRupees: Double get() = netBalanceInPaise / 100.0
}

data class PairwiseDebt(
    val debtorId: String,
    val debtorName: String,
    val creditorId: String,
    val creditorName: String,
    val amountInPaise: Long
) {
    val amountInRupees: Double get() = amountInPaise / 100.0
}

data class ExpenseCalculationData(
    val memberId: String,
    val memberName: String,
    val paidAmountInPaise: Long,
    val shareAmountInPaise: Long
)

data class SettlementRecordData(
    val payerMemberId: String,
    val receiverMemberId: String,
    val amountInPaise: Long
)

object ExpenseEngine {

    fun rupeesToPaise(rupees: Double): Long = Math.round(rupees * 100.0)

    fun paiseToRupees(paise: Long): Double = paise / 100.0

    /**
     * Calculates equal splits in integer paise with remainder distribution.
     * Guarantees sum(shares) == amountInPaise with 0 paise rounding error.
     */
    fun calculateEqualSplits(
        amountInPaise: Long,
        participantIds: List<String>,
        participantNames: Map<String, String>
    ): List<ExpenseSplitAllocation> {
        if (participantIds.isEmpty() || amountInPaise <= 0L) return emptyList()

        val count = participantIds.size
        val baseShare = amountInPaise / count
        val remainder = (amountInPaise % count).toInt()

        return participantIds.mapIndexed { index, memberId ->
            val share = baseShare + (if (index < remainder) 1L else 0L)
            ExpenseSplitAllocation(
                memberId = memberId,
                memberName = participantNames[memberId] ?: "Member",
                shareAmountInPaise = share
            )
        }
    }

    /**
     * Single source of truth for calculating all member balances for a trip.
     */
    fun calculateNetBalances(
        members: List<Pair<String, String>>, // list of (memberId, memberName)
        expenseRecords: List<ExpenseCalculationData>,
        settlementRecords: List<SettlementRecordData>
    ): List<MemberBalance> {
        val balancesMap = members.associate { (id, name) ->
            id to MemberBalance(memberId = id, memberName = name)
        }.toMutableMap()

        // 1. Process Expenses
        for (expense in expenseRecords) {
            val payerId = expense.memberId
            val existingPayer = balancesMap[payerId]
            if (existingPayer != null) {
                balancesMap[payerId] = existingPayer.copy(
                    totalPaidInPaise = existingPayer.totalPaidInPaise + expense.paidAmountInPaise
                )
            }
        }

        // 2. Process Splits
        for (expense in expenseRecords) {
            val participantId = expense.memberId
            val existing = balancesMap[participantId]
            if (existing != null) {
                balancesMap[participantId] = existing.copy(
                    totalShareInPaise = existing.totalShareInPaise + expense.shareAmountInPaise
                )
            }
        }

        // 3. Process Settlements
        for (settlement in settlementRecords) {
            val payer = balancesMap[settlement.payerMemberId]
            if (payer != null) {
                balancesMap[settlement.payerMemberId] = payer.copy(
                    settlementsPaidInPaise = payer.settlementsPaidInPaise + settlement.amountInPaise
                )
            }
            val receiver = balancesMap[settlement.receiverMemberId]
            if (receiver != null) {
                balancesMap[settlement.receiverMemberId] = receiver.copy(
                    settlementsReceivedInPaise = receiver.settlementsReceivedInPaise + settlement.amountInPaise
                )
            }
        }

        return members.map { (id, _) -> balancesMap[id] ?: MemberBalance(id, "Member") }
    }

    /**
     * Resolves member balances into minimal "Who Owes Whom" pairwise debts.
     */
    fun calculatePairwiseDebts(balances: List<MemberBalance>): List<PairwiseDebt> {
        val namesMap = balances.associate { it.memberId to it.memberName }

        // Debtors have netBalance < 0 (they owe money)
        val debtors = balances.filter { it.netBalanceInPaise < 0 }
            .map { Pair(it.memberId, abs(it.netBalanceInPaise)) }
            .toMutableList()

        // Creditors have netBalance > 0 (they are owed money)
        val creditors = balances.filter { it.netBalanceInPaise > 0 }
            .map { Pair(it.memberId, it.netBalanceInPaise) }
            .toMutableList()

        val debts = mutableListOf<PairwiseDebt>()

        var dIdx = 0
        var cIdx = 0

        while (dIdx < debtors.size && cIdx < creditors.size) {
            val (debtorId, debtorOwed) = debtors[dIdx]
            val (creditorId, creditorDue) = creditors[cIdx]

            val settleAmount = minOf(debtorOwed, creditorDue)
            if (settleAmount > 0) {
                debts.add(
                    PairwiseDebt(
                        debtorId = debtorId,
                        debtorName = namesMap[debtorId] ?: "Member",
                        creditorId = creditorId,
                        creditorName = namesMap[creditorId] ?: "Member",
                        amountInPaise = settleAmount
                    )
                )
            }

            if (debtorOwed == settleAmount) {
                dIdx++
            } else {
                debtors[dIdx] = Pair(debtorId, debtorOwed - settleAmount)
            }

            if (creditorDue == settleAmount) {
                cIdx++
            } else {
                creditors[cIdx] = Pair(creditorId, creditorDue - settleAmount)
            }
        }

        return debts
    }
}
