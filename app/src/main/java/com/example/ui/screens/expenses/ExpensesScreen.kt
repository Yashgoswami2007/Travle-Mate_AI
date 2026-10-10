package com.example.ui.screens.expenses

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.ExpenseSplitEntity
import com.example.data.local.entity.GroupMemberEntity
import com.example.data.local.entity.SavedParticipantEntity
import com.example.domain.expense.MemberBalance
import com.example.domain.expense.PairwiseDebt
import com.example.ui.components.BudgetProgressBar
import com.example.ui.components.QrScannerDialog
import com.example.ui.components.TravelBadge
import com.example.ui.components.travelTextFieldColors
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val trip by viewModel.currentTrip.collectAsState()
    val expenses by viewModel.currentExpenses.collectAsState()
    val splits by viewModel.currentSplits.collectAsState()
    val members by viewModel.currentMembers.collectAsState()
    val savedParticipants by viewModel.savedParticipants.collectAsState()
    val netBalances by viewModel.currentNetBalances.collectAsState()
    val pairwiseDebts by viewModel.currentPairwiseDebts.collectAsState()
    val deviceIdentity by viewModel.deviceUserIdentity.collectAsState()
    val isRefreshing by viewModel.isRefreshingExpenses.collectAsState()

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showQrScannerDialog by remember { mutableStateOf(false) }
    var settlingDebt by remember { mutableStateOf<PairwiseDebt?>(null) }

    LaunchedEffect(trip?.id) {
        viewModel.refreshExpenses()
    }

    val totalSpent = if (expenses.isNotEmpty()) expenses.sumOf { it.amount } else (trip?.spentBudget ?: 0.0)
    val tripBudget = trip?.totalBudget ?: 25000.0

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TravelSandBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingExpense = null
                    showAddExpenseDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) },
                text = { Text("Add Expense", fontWeight = FontWeight.Bold, color = Color.White) },
                containerColor = TravelNavyPrimary,
                modifier = Modifier
                    .testTag("add_expense_fab")
                    .padding(bottom = 72.dp)
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshExpenses() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Trip Budget & Split",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "Track shared expenses and automatic who-owes-whom balances",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            // Budget Progress Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        BudgetProgressBar(
                            totalBudget = tripBudget,
                            spentBudget = totalSpent
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val perPerson = if (members.isNotEmpty()) totalSpent / members.size else totalSpent
                            val perDay = if (trip != null && trip!!.numberOfDays > 0) totalSpent / trip!!.numberOfDays else totalSpent
                            Text("Per Person: ₹${perPerson.toInt()}", fontSize = 12.sp, color = TextSecondary)
                            Text("Per Day: ₹${perDay.toInt()}", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }

            // Group Members Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Travel Group (${members.size} Members)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { showQrScannerDialog = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = TravelNavyPrimary)
                        }
                        TextButton(onClick = { showAddMemberDialog = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontSize = 12.sp, color = TravelNavyPrimary)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    members.forEach { mem ->
                        val balance = netBalances.firstOrNull { it.memberId == mem.id }
                        val netPaise = balance?.netBalanceInPaise ?: 0L
                        val paidByThisMem = expenses.filter { it.paidByMemberId == mem.id }.sumOf { it.amount }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = TravelSurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TravelBorderSubtle),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (mem.isCurrentUser) TravelTerracotta else TravelNavyPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mem.name.take(2).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(mem.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                Text("Paid: ₹${paidByThisMem.toInt()}", fontSize = 11.sp, color = TextSecondary)

                                if (netPaise > 0L) {
                                    Text("+₹${(netPaise / 100.0).toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TravelForestGreen)
                                } else if (netPaise < 0L) {
                                    Text("-₹${(-netPaise / 100.0).toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TravelWarningRed)
                                } else {
                                    Text("Settled", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }
                    }
                }
            }

            // Who Owes Whom Settlement Cards
            item {
                Text(
                    text = "Who Owes Whom (Balances)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            if (pairwiseDebts.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = TravelForestGreenLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TravelForestGreen)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "All group balances are completely settled!",
                                fontWeight = FontWeight.Bold,
                                color = TravelForestGreen,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(pairwiseDebts) { debt ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${debt.debtorName} owes ${debt.creditorName} ₹${debt.amountInRupees.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text("Net pairwise balance", fontSize = 11.sp, color = TextSecondary)
                            }

                            Button(
                                onClick = { settlingDebt = debt },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TravelForestGreen),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("btn_settle_${debt.debtorId}")
                            ) {
                                Text("Settle Up", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Expenses History
            item {
                Text(
                    text = "Expenses Record (${expenses.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            items(expenses) { exp ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth().testTag("expense_card_${exp.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(TravelNavyContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        when (exp.category) {
                                            "Accommodation" -> Icons.Default.Hotel
                                            "Transport" -> Icons.Default.DirectionsTransit
                                            "Food" -> Icons.Default.Restaurant
                                            "Activities" -> Icons.Default.Attractions
                                            else -> Icons.Default.Receipt
                                        },
                                        contentDescription = null,
                                        tint = TravelNavyDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(exp.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Paid by ${exp.paidByMemberName} • ${exp.category}", fontSize = 11.sp, color = TextSecondary)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "₹${exp.amount.toInt()}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = TravelNavyPrimary
                                )

                                IconButton(
                                    onClick = {
                                        editingExpense = exp
                                        showAddExpenseDialog = true
                                    },
                                    modifier = Modifier.size(28.dp).testTag("btn_edit_expense_${exp.id}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TravelNavySecondary, modifier = Modifier.size(16.dp))
                                }

                                IconButton(
                                    onClick = { viewModel.deleteExpense(exp.id) },
                                    modifier = Modifier.size(28.dp).testTag("btn_delete_expense_${exp.id}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TravelWarningRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
        } // End PullToRefreshBox
    }

    // QR Scanner Dialog
    if (showQrScannerDialog) {
        QrScannerDialog(
            currentUserId = deviceIdentity.deviceId,
            onParticipantConfirmed = { payload ->
                viewModel.addParticipantFromQr(payload)
                showQrScannerDialog = false
            },
            onDismiss = { showQrScannerDialog = false }
        )
    }

    // Settle Up Dialog
    settlingDebt?.let { debt ->
        AlertDialog(
            onDismissRequest = { settlingDebt = null },
            title = { Text("Confirm Settlement Payment") },
            text = {
                Text("Record settlement payment of ₹${debt.amountInRupees.toInt()} from ${debt.debtorName} to ${debt.creditorName}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val payer = members.firstOrNull { it.id == debt.debtorId } ?: members.first()
                        viewModel.recordSettlement(
                            payer = payer,
                            receiverName = debt.creditorName,
                            receiverId = debt.creditorId,
                            amountInRupees = debt.amountInRupees
                        )
                        settlingDebt = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TravelForestGreen)
                ) {
                    Text("Confirm Settlement")
                }
            },
            dismissButton = {
                TextButton(onClick = { settlingDebt = null }) { Text("Cancel") }
            }
        )
    }

    // Add / Edit Expense Dialog
    if (showAddExpenseDialog) {
        var title by remember { mutableStateOf(editingExpense?.title ?: "") }
        var amountText by remember { mutableStateOf(editingExpense?.amount?.toInt()?.toString() ?: "") }
        var selectedCategory by remember { mutableStateOf(editingExpense?.category ?: "Food") }
        var selectedPayer by remember { mutableStateOf(members.firstOrNull { it.id == editingExpense?.paidByMemberId } ?: members.firstOrNull()) }
        var selectedParticipants by remember {
            mutableStateOf(
                if (editingExpense != null) {
                    val pIds = editingExpense!!.participantIdsJson.split(",").map { it.trim() }
                    members.filter { pIds.contains(it.id) }.ifEmpty { members }
                } else members
            )
        }
        var splitType by remember { mutableStateOf(editingExpense?.splitType ?: "EQUAL") }
        var customAmounts by remember {
            mutableStateOf<Map<String, String>>(
                if (editingExpense != null && editingExpense!!.splitType == "CUSTOM") {
                    splits.filter { it.expenseId == editingExpense!!.id }.associate { it.memberId to (it.shareAmountInPaise / 100.0).toInt().toString() }
                } else emptyMap()
            )
        }

        AlertDialog(
            onDismissRequest = { showAddExpenseDialog = false },
            title = { Text(if (editingExpense == null) "Record New Expense" else "Edit Expense") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Expense Title (e.g. Dinner at LMB)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                        colors = travelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("input_expense_title")
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (₹)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                        colors = travelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("input_expense_amount")
                    )

                    Text("Paid By:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        members.forEach { mem ->
                            FilterChip(
                                selected = selectedPayer?.id == mem.id,
                                onClick = { selectedPayer = mem },
                                label = { Text(mem.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    Text("Split Method:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = splitType == "EQUAL",
                            onClick = { splitType = "EQUAL" },
                            label = { Text("Split Equally", fontSize = 11.sp) },
                            modifier = Modifier.testTag("chip_split_equal")
                        )
                        FilterChip(
                            selected = splitType == "CUSTOM",
                            onClick = {
                                splitType = "CUSTOM"
                                if (customAmounts.isEmpty() && selectedParticipants.isNotEmpty()) {
                                    val total = amountText.toDoubleOrNull() ?: 0.0
                                    val each = if (selectedParticipants.isNotEmpty()) (total / selectedParticipants.size).toInt().toString() else "0"
                                    customAmounts = selectedParticipants.associate { it.id to each }
                                }
                            },
                            label = { Text("Unequal / Custom", fontSize = 11.sp) },
                            modifier = Modifier.testTag("chip_split_custom")
                        )
                    }

                    Text("Split Among Participants:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        members.forEach { mem ->
                            val isChecked = selectedParticipants.any { it.id == mem.id }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedParticipants = if (isChecked) {
                                            selectedParticipants.filter { it.id != mem.id }
                                        } else {
                                            selectedParticipants + mem
                                        }
                                    }
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedParticipants = if (checked) {
                                            selectedParticipants + mem
                                        } else {
                                            selectedParticipants.filter { it.id != mem.id }
                                        }
                                    }
                                )
                                Text(mem.name, fontSize = 13.sp)
                            }
                        }
                    }

                    if (splitType == "CUSTOM" && selectedParticipants.isNotEmpty()) {
                        val enteredTotalAmt = amountText.toDoubleOrNull() ?: 0.0
                        val currentAllocatedSum = selectedParticipants.sumOf { mem ->
                            customAmounts[mem.id]?.toDoubleOrNull() ?: 0.0
                        }
                        val remaining = enteredTotalAmt - currentAllocatedSum

                        Text("Assign Split Amounts (₹):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        selectedParticipants.forEach { mem ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(mem.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = customAmounts[mem.id] ?: "",
                                    onValueChange = { newAmt ->
                                        customAmounts = customAmounts + (mem.id to newAmt)
                                    },
                                    placeholder = { Text("0") },
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 13.sp),
                                    colors = travelTextFieldColors(),
                                    modifier = Modifier.width(110.dp).testTag("input_custom_split_${mem.id}")
                                )
                            }
                        }

                        if (kotlin.math.abs(remaining) > 0.01) {
                            Text(
                                text = "Allocated ₹${currentAllocatedSum.toInt()} of ₹${enteredTotalAmt.toInt()} (Difference: ₹${remaining.toInt()})",
                                fontSize = 11.sp,
                                color = TravelWarningRed,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "Split amounts match total expense exactly! ✓",
                                fontSize = 11.sp,
                                color = TravelForestGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (title.isNotBlank() && amt > 0 && selectedPayer != null && selectedParticipants.isNotEmpty()) {
                            val customAllocations = if (splitType == "CUSTOM") {
                                val currentSum = selectedParticipants.sumOf { customAmounts[it.id]?.toDoubleOrNull() ?: 0.0 }
                                if (kotlin.math.abs(currentSum - amt) > 1.0) return@Button
                                selectedParticipants.map { mem ->
                                    val shareVal = customAmounts[mem.id]?.toDoubleOrNull() ?: 0.0
                                    com.example.domain.expense.ExpenseSplitAllocation(
                                        memberId = mem.id,
                                        memberName = mem.name,
                                        shareAmountInPaise = Math.round(shareVal * 100.0)
                                    )
                                }
                            } else null

                            if (editingExpense == null) {
                                viewModel.addExpense(
                                    title = title,
                                    amountInRupees = amt,
                                    category = selectedCategory,
                                    paidBy = selectedPayer!!,
                                    selectedParticipants = selectedParticipants,
                                    splitType = splitType,
                                    customAllocations = customAllocations
                                )
                            } else {
                                viewModel.updateExpense(
                                    expenseId = editingExpense!!.id,
                                    title = title,
                                    amountInRupees = amt,
                                    category = selectedCategory,
                                    paidBy = selectedPayer!!,
                                    selectedParticipants = selectedParticipants,
                                    splitType = splitType,
                                    customAllocations = customAllocations
                                )
                            }
                            showAddExpenseDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                    modifier = Modifier.testTag("btn_save_expense")
                ) {
                    Text("Save Expense", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExpenseDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add Member Dialog
    if (showAddMemberDialog) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Add Group Member") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Friend's Name") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                        colors = travelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("input_member_name")
                    )

                    if (savedParticipants.isNotEmpty()) {
                        Text("Or select from saved contacts:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            savedParticipants.take(4).forEach { p ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        viewModel.addParticipantFromSaved(p)
                                        showAddMemberDialog = false
                                    },
                                    label = { Text(p.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.addMemberToTrip(name, email)
                            showAddMemberDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                    modifier = Modifier.testTag("btn_save_member")
                ) {
                    Text("Add Member")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false }) { Text("Cancel") }
            }
        )
    }
}
