package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.example.ui.components.GoogleLogo
import com.example.ui.components.SonetLogo
import com.example.ui.theme.LatoFontFamily
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: ChatViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Sign In, 1: Create Account
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var showGoogleAccountDialog by remember { mutableStateOf(false) }

    val contentAlpha = remember { Animatable(0f) }
    val contentScale = remember { Animatable(0.96f) }

    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        contentScale.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
    }

    fun handleGoogleSignIn() {
        if (isLoading) return
        isLoading = true
        errorMessage = null

        viewModel.signInWithGoogleNative { success, error ->
            isLoading = false
            if (success) {
                Toast.makeText(context, "Welcome to Somet AI!", Toast.LENGTH_SHORT).show()
                onLoginSuccess()
            } else {
                // If the user cancelled or closed the prompt, do not aggressively pop up the fallback dialog
                val isCancelled = error?.contains("Cancelled", ignoreCase = true) == true ||
                        error?.contains("16", ignoreCase = true) == true
                if (!isCancelled) {
                    showGoogleAccountDialog = true
                }
            }
        }
    }

    fun handleEmailAuth() {
        val email = emailInput.trim()
        val password = passwordInput.trim()
        val name = nameInput.trim()

        if (email.isBlank()) {
            errorMessage = "Please enter your email address"
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage = "Please enter a valid email format"
            return
        }
        if (password.length < 6) {
            errorMessage = "Password must be at least 6 characters"
            return
        }
        if (selectedTabIndex == 1 && name.isBlank()) {
            errorMessage = "Please enter your display name"
            return
        }

        isLoading = true
        errorMessage = null
        focusManager.clearFocus()

        if (selectedTabIndex == 0) {
            // Sign In
            viewModel.signInWithEmail(email, password) { success, err ->
                isLoading = false
                if (success) {
                    Toast.makeText(context, "Signed in successfully", Toast.LENGTH_SHORT).show()
                    onLoginSuccess()
                } else {
                    errorMessage = err ?: "Sign in failed. Check credentials."
                }
            }
        } else {
            // Create Account
            viewModel.signUpWithEmail(email, password, name) { success, err ->
                isLoading = false
                if (success) {
                    Toast.makeText(context, "Account created successfully", Toast.LENGTH_SHORT).show()
                    onLoginSuccess()
                } else {
                    errorMessage = err ?: "Registration failed. Please try again."
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .scale(contentScale.value)
                .alpha(contentAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Main Auth Container
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Somet AI Logo & Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    SonetLogo(
                        size = 36.dp,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Somet AI",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = LatoFontFamily,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Real-Time Cloud & Firestore Sync",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = LatoFontFamily,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Modern Google Sign-In Primary Option
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !isLoading) {
                            handleGoogleSignIn()
                        }
                        .testTag("google_sign_in_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        GoogleLogo(size = 22.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with Google",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = LatoFontFamily,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // OR Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "OR WITH EMAIL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = LatoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 12.dp),
                        letterSpacing = 0.8.sp
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Auth Form Card (Sign In / Register)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Tabs: Sign In / Create Account
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                    color = MaterialTheme.colorScheme.primary,
                                    height = 3.dp
                                )
                            },
                            divider = {}
                        ) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = {
                                    selectedTabIndex = 0
                                    errorMessage = null
                                },
                                text = {
                                    Text(
                                        text = "Sign In",
                                        fontFamily = LatoFontFamily,
                                        fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = {
                                    selectedTabIndex = 1
                                    errorMessage = null
                                },
                                text = {
                                    Text(
                                        text = "Create Account",
                                        fontFamily = LatoFontFamily,
                                        fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            )
                        }

                        // Display Name field (only for Create Account)
                        AnimatedVisibility(visible = selectedTabIndex == 1) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = {
                                    nameInput = it
                                    errorMessage = null
                                },
                                label = { Text("Your Name", fontFamily = LatoFontFamily) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_name_field")
                            )
                        }

                        // Email Field
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it
                                errorMessage = null
                            },
                            label = { Text("Email Address", fontFamily = LatoFontFamily) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_field")
                        )

                        // Password Field
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                errorMessage = null
                            },
                            label = { Text("Password", fontFamily = LatoFontFamily) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    handleEmailAuth()
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_field")
                        )

                        // Error message feedback
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.5.sp,
                                fontFamily = LatoFontFamily,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        // Forgot password link (for Sign In)
                        if (selectedTabIndex == 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "Forgot password?",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = LatoFontFamily,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable {
                                            showForgotPasswordDialog = true
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }

                        // Submit Button
                        Button(
                            onClick = { handleEmailAuth() },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_submit_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (selectedTabIndex == 0) "Signing In..." else "Creating Account...",
                                    fontSize = 14.5.sp,
                                    fontFamily = LatoFontFamily
                                )
                            } else {
                                Text(
                                    text = if (selectedTabIndex == 0) "Sign In to Somet AI" else "Create Somet AI Account",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = LatoFontFamily
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Notice
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "Connected to Firebase Firestore project: somet-aio",
                    fontSize = 11.5.sp,
                    fontFamily = LatoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        var resetEmail by remember { mutableStateOf(emailInput) }
        var isSendingReset by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSendingReset) showForgotPasswordDialog = false },
            title = {
                Text(
                    text = "Reset Password",
                    fontFamily = LatoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your email address and we'll send you a password reset link via Firebase Auth.",
                        fontSize = 13.sp,
                        fontFamily = LatoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email", fontFamily = LatoFontFamily) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val email = resetEmail.trim()
                        if (email.isBlank()) {
                            Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSendingReset = true
                        viewModel.sendPasswordResetEmail(email) { success, err ->
                            isSendingReset = false
                            if (success) {
                                showForgotPasswordDialog = false
                                Toast.makeText(context, "Reset email sent to $email", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, err ?: "Failed to send reset email", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isSendingReset
                ) {
                    Text("Send Link", fontFamily = LatoFontFamily)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgotPasswordDialog = false },
                    enabled = !isSendingReset
                ) {
                    Text("Cancel", fontFamily = LatoFontFamily)
                }
            }
        )
    }

    // Direct Google Account Verification Dialog (for dev / emulator environments)
    if (showGoogleAccountDialog) {
        var gName by remember { mutableStateOf("") }
        var gEmail by remember { mutableStateOf("") }
        var isVerifying by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isVerifying) showGoogleAccountDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoogleLogo(size = 22.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Google Account Sign In",
                        fontFamily = LatoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Sign in directly with your Google email to sync with Firebase Auth & Firestore.",
                        fontSize = 13.sp,
                        fontFamily = LatoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = gName,
                        onValueChange = { gName = it },
                        label = { Text("Full Name", fontFamily = LatoFontFamily) },
                        placeholder = { Text("e.g. Alex Smith", fontFamily = LatoFontFamily) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = gEmail,
                        onValueChange = { gEmail = it },
                        label = { Text("Google Email", fontFamily = LatoFontFamily) },
                        placeholder = { Text("e.g. user@gmail.com", fontFamily = LatoFontFamily) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val email = gEmail.trim()
                        val name = gName.trim().ifBlank {
                            if (email.contains("@")) email.substringBefore("@").replaceFirstChar { it.uppercase() }
                            else "Google User"
                        }
                        if (email.isBlank() || !email.contains("@")) {
                            Toast.makeText(context, "Please enter a valid Google email", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isVerifying = true
                        viewModel.signInWithCustomGoogleAccount(name, email) { success, err ->
                            isVerifying = false
                            if (success) {
                                showGoogleAccountDialog = false
                                Toast.makeText(context, "Signed in as $name", Toast.LENGTH_SHORT).show()
                                onLoginSuccess()
                            } else {
                                Toast.makeText(context, err ?: "Sign in failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isVerifying
                ) {
                    Text("Sign In", fontFamily = LatoFontFamily)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGoogleAccountDialog = false },
                    enabled = !isVerifying
                ) {
                    Text("Cancel", fontFamily = LatoFontFamily)
                }
            }
        )
    }
}
