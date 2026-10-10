package com.example.ui.screens.expenses

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.example.data.local.entity.GroupMemberEntity
import com.example.ui.components.BudgetProgressBar
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
    val members by viewModel.currentMembers.collectAsState()
    val splits by viewModel.currentSplits.collectAsState()

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }

    val totalSpent = expenses.sumOf { it.amount }
    val tripBudget = trip?.totalBudget ?: 25000.0

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TravelSandBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddExpenseDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) },
                text = { Text("Add Expense", fontWeight = FontWeight.Bold, color = Color.White) },
                containerColor = TravelNavyPrimary,
                modifier = Modifier
                    .testTag("add_expense_fab")
                    .padding(bottom = 72.dp)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    TextButton(onClick = { showAddMemberDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Member", fontSize = 12.sp, color = TravelNavyPrimary)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    members.forEach { mem ->
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
                                        .background(TravelNavyPrimary),
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
                                Text(mem.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                val paidByThisMem = expenses.filter { it.paidByMemberId == mem.id }.sumOf { it.amount }
                                Text("Paid: ₹${paidByThisMem.toInt()}", fontSize = 11.sp, color = TravelForestGreen)
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

            val unsettledSplits = splits.filter { !it.isSettled }
            if (unsettledSplits.isEmpty()) {
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
                items(unsettledSplits) { split ->
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
                                    text = "${split.memberName} owes ₹${split.shareAmount.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text("For shared group expense", fontSize = 11.sp, color = TextSecondary)
                            }

                            Button(
                                onClick = { viewModel.settleSplit(split.id) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TravelForestGreen),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Settle Up", fontSize = 11.sp)
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
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
                        Text(
                            text = "₹${exp.amount.toInt()}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = TravelNavyPrimary
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Add Expense Dialog
    if (showAddExpenseDialog) {
        var title by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf("Food") }
        var selectedPayer by remember { mutableStateOf(members.firstOrNull()) }

        AlertDialog(
            onDismissRequest = { showAddExpenseDialog = false },
            title = { Text("Record New Expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Expense Title (e.g. Dinner at LMB)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                        colors = travelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (₹)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                        colors = travelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Category:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Food", "Stay", "Transit", "Sightseeing").forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (title.isNotBlank() && amt > 0 && selectedPayer != null) {
                            viewModel.addExpense(title, amt, selectedCategory, selectedPayer!!)
                            showAddExpenseDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary)
                ) {
                    Text("Save Expense")
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
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email (optional)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                        colors = travelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary)
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
