package com.example.cradetv6.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cradetv6.MainViewModel
import com.example.cradetv6.data.EmergencyContactEntity

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, viewModel: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (isSignUp) "Create Account" else "Welcome Back",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isSignUp) "Sign up to start monitoring" else "Login to your account",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(Modifier.height(32.dp))
                
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(Modifier.height(32.dp))
                
                Button(
                    onClick = { 
                        if (email.isNotEmpty() && password.isNotEmpty()) {
                            onLoginSuccess() 
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isSignUp) "Sign Up" else "Login",
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold
                    )
                }
                
                TextButton(onClick = { isSignUp = !isSignUp }) {
                    Text(
                        text = if (isSignUp) "Already have an account? Login" else "Don't have an account? Sign Up",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileSetupScreen(onComplete: () -> Unit, viewModel: MainViewModel) {
    var bloodType by remember { mutableStateOf("") }
    var abnormalities by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    
    val contacts by viewModel.contacts.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Health Profile Setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        
        OutlinedTextField(value = bloodType, onValueChange = { bloodType = it }, label = { Text("Blood Type (e.g., O+)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = abnormalities, onValueChange = { abnormalities = it }, label = { Text("Major Abnormalities") }, modifier = Modifier.fillMaxWidth())
        
        Spacer(Modifier.height(32.dp))
        Text("Emergency Contacts (Add up to 5)", style = MaterialTheme.typography.titleMedium)
        
        OutlinedTextField(value = contactName, onValueChange = { contactName = it }, label = { Text("Contact Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = contactPhone, onValueChange = { contactPhone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
        
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (contactName.isNotEmpty() && contactPhone.isNotEmpty() && contacts.size < 5) {
                    viewModel.addContact(contactName, contactPhone)
                    contactName = ""
                    contactPhone = ""
                }
            },
            enabled = contacts.size < 5
        ) {
            Text("Add Contact (${contacts.size}/5)")
        }

        Spacer(Modifier.height(16.dp))
        contacts.forEach { contact ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${contact.name}: ${contact.phone}", modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.deleteContact(contact) }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                viewModel.saveProfile("user@gmail.com", "password", bloodType, abnormalities)
                onComplete()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = contacts.isNotEmpty()
        ) {
            Text("Finish Setup")
        }
    }
}
