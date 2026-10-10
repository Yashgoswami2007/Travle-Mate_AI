package com.example.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthState
import com.example.ui.components.TactileButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onAuthSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val authState by viewModel.authState.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()

    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // When authenticated, trigger navigation
    LaunchedEffect(currentSession) {
        if (currentSession != null) {
            onAuthSuccess()
            viewModel.navigateBack()
        }
    }

    val isLoading = authState is AuthState.Loading

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TravelSandBackground,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TravelNavyDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TravelSandBackground)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // App Logo & Header in Warm Terracotta Orange & Navy
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(TravelTerracotta),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Explore,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isSignUp) "Join TripMate AI" else "Welcome Back",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TravelNavyDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            com.example.ui.components.TravelBadge(
                text = "Powered by Supabase Auth",
                containerColor = TravelNavyContainer,
                contentColor = TravelNavyPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isSignUp)
                    "Plan dynamic AI itineraries and collaborate with friends"
                else
                    "Sign in with Supabase to access shared trips, expenses, and chats",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Mode Selector Pill (Sign In / Sign Up)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TravelNavyContainer)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isSignUp) TravelNavyPrimary else Color.Transparent)
                        .clickable {
                            isSignUp = false
                            viewModel.resetAuthState()
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign In",
                        fontWeight = if (!isSignUp) FontWeight.Bold else FontWeight.Medium,
                        color = if (!isSignUp) Color.White else TravelNavyDark,
                        fontSize = 14.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSignUp) TravelTerracotta else Color.Transparent)
                        .clickable {
                            isSignUp = true
                            viewModel.resetAuthState()
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Create Account",
                        fontWeight = if (isSignUp) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSignUp) Color.White else TravelNavyDark,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error Display Banner
            AnimatedVisibility(
                visible = authState !is AuthState.Idle && authState !is AuthState.Loading && authState !is AuthState.Success
            ) {
                val errorMessage = when (val state = authState) {
                    is AuthState.InvalidCredentials -> state.message
                    is AuthState.EmailAlreadyRegistered -> state.message
                    is AuthState.WeakPassword -> state.message
                    is AuthState.NetworkFailure -> state.message
                    is AuthState.GoogleSignInFailed -> state.message
                    is AuthState.SupabaseError -> state.message
                    else -> "An error occurred during authentication."
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelWarningRedLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = TravelWarningRed)
                        Text(
                            text = errorMessage,
                            color = TravelWarningRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Google OAuth Button (Supabase Auth)
            OutlinedButton(
                onClick = {
                    viewModel.launchGoogleOAuth(context)
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = TravelNavyDark
                ),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, TravelBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_google_oauth")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Stylized Google "G" Badge
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEA4335)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "G",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isSignUp) "Sign up with Google" else "Continue with Google",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TravelNavyDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Divider: "or with email"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = TravelBorderSubtle)
                Text(
                    text = "  or with email  ",
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = TravelBorderSubtle)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Form Inputs
            if (isSignUp) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = TravelNavySecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = TravelTerracotta,
                        unfocusedBorderColor = TravelBorderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_full_name")
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = TravelNavySecondary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = TravelTerracotta,
                    unfocusedBorderColor = TravelBorderSubtle
                ),
                modifier = Modifier.fillMaxWidth().testTag("input_email")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = TravelNavySecondary) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle password",
                            tint = TravelNavySecondary
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    if (isSignUp) {
                        viewModel.signUpWithEmail(email, password, fullName)
                    } else {
                        viewModel.signInWithEmail(email, password)
                    }
                }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = TravelTerracotta,
                    unfocusedBorderColor = TravelBorderSubtle
                ),
                modifier = Modifier.fillMaxWidth().testTag("input_password")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Button (Orange / Terracotta)
            TactileButton(
                text = if (isSignUp) "Create TripMate Account" else "Sign In",
                isLoading = isLoading,
                onClick = {
                    focusManager.clearFocus()
                    if (isSignUp) {
                        viewModel.signUpWithEmail(email, password, fullName)
                    } else {
                        viewModel.signInWithEmail(email, password)
                    }
                },
                containerColor = TravelTerracotta,
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("btn_auth_submit")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Continue as guest
            TextButton(
                onClick = { viewModel.navigateBack() },
                modifier = Modifier.testTag("btn_continue_guest")
            ) {
                Text(
                    text = "Continue as Guest",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
