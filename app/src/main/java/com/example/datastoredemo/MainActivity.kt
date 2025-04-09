// A Jetpack Compose demo app using DataStore to persist and apply dark mode preference

package com.example.datastoredemo

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Create the DataStore instance as an extension property on Context
val Context.dataStore by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Read the dark mode preference as Compose state
            val context = this
            val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
            val FONT_SIZE_KEY = longPreferencesKey("font_size")

            val darkModeFlow: Flow<Boolean> = context.dataStore.data
                .map { preferences -> preferences[DARK_MODE_KEY] ?: false }

            val fontSizeFlow: Flow<Long> = context.dataStore.data
                .map { preferences -> preferences[FONT_SIZE_KEY] ?: 16 }

            val isDarkMode by darkModeFlow.collectAsState(initial = false)
            val fontSize by fontSizeFlow.collectAsState(initial = 16)

            // Apply the theme based on the preference
            DataStoreDemoApp(isDarkMode = isDarkMode, fontSize = fontSize)
        }
    }
}

@Composable
fun DataStoreDemoApp(isDarkMode: Boolean, fontSize: Long) {

    // Dynamically apply dark/light theme using Material3
    val colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            SettingsScreen()
        }
    }
}

// https://developer.android.com/develop/ui/compose/components/datepickers
// This was the resource used for the DatePicker composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(
    onDateSelected: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDateSelected(datePickerState.selectedDateMillis?.plus(0L))
                // day difference due to mismatch between recorded time and actual time...
                onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var showModal by remember { mutableStateOf(true) }

    var fileContent by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var textToSave by remember { mutableStateOf("") }

    val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
    val FONT_SIZE_KEY = longPreferencesKey("font_size")

    val darkModeFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[DARK_MODE_KEY] ?: false }
    val fontSizeFlow: Flow<Long> = context.dataStore.data
        .map { preferences -> preferences[FONT_SIZE_KEY] ?: 16 }

    val isDarkMode by darkModeFlow.collectAsState(initial = false)
    val fontSize by fontSizeFlow.collectAsState(initial = 16)

    var sliderPosition by remember { mutableStateOf(fontSize) }

    Column(modifier = Modifier.safeContentPadding()) {
        Row() {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { newValue ->
                        scope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[DARK_MODE_KEY] = newValue
                            }
                        }
                    }
                )

                Text(text = if (isDarkMode) "Dark Mode: ON" else "Dark Mode: OFF")
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = {
                        newValue -> // change slider to desired position
                        sliderPosition = newValue.toLong()
                        scope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[FONT_SIZE_KEY] = sliderPosition
                            }
                        }},
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                        inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    steps = 16,
                    valueRange = 16f..48f
                )
                Text(text = "Font Size: $fontSize")
            }
        } // Settings

        if(showModal) { // show date
            DatePickerModal(
                onDateSelected = {
                    selectedDate = it
                    showModal = false
                },
                onDismiss = { showModal = false}
            )
        }
        else { // show journal entry for that specified date
            if (selectedDate != null) {
                val date = Date(selectedDate!!)
                val formattedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
                val fileName = "{$formattedDate}.txt" // access fileName to save

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(modifier = Modifier.weight(.9f), onClick = {
                        showModal = true
                    }) {
                        Column {
                            Icon(
                                imageVector = Icons.Default.DateRange, // Example icon (Home)
                                contentDescription = "Select Date",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Select")
                        }

                    }


                    // Read button
                    Button(modifier = Modifier.weight(.9f), onClick = {
                        fileContent = readFromFile(context, fileName)
                        textToSave = if (fileContent == "File not found") "" else fileContent
                        message = if (fileContent == "File not found") fileContent else "Status: Read Successfully"
                    }) {
                        Column {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Read",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Read")
                        }
                    }



                    // Delete button
                    Button(modifier = Modifier.weight(.9f), onClick = {
                        val deleted = deleteFile(context, fileName)
                        message = if (deleted) "File deleted" else "File not found"
                    }) {
                        Column {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Clear")
                        }
                    }


                    // Save button
                    Button(modifier = Modifier.weight(.9f), onClick = {
                        saveToFile(context, fileName, textToSave)
                        message = "Text saved successfully"
                    }) {
                        Column {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Save",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Save")
                        }
                    }

                }

                Text("Status: $message")

                OutlinedTextField(
                    value = textToSave,
                    onValueChange = { textToSave = it },
                    label = { Text(formattedDate) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = fontSize.toInt().sp),
                )


            } else {
                Text("No date selected")
            }


        }

    }

}

/**
 * File methods are extracted from the InternalStorageApp demo
 */
// Function to save text to internal storage
fun saveToFile(context: Context, filename: String, content: String) {
    // MODE_PRIVATE means the file is only accessible to this app
    context.openFileOutput(filename, Context.MODE_PRIVATE).use { outputStream ->
        outputStream.write(content.toByteArray())
    }
}

// Function to read text from internal storage
fun readFromFile(context: Context, filename: String): String {
    return try {
        context.openFileInput(filename).bufferedReader().useLines { lines ->
            lines.joinToString("\n")
        }
    } catch (e: FileNotFoundException) {
        "File not found"
    }
}

// Function to delete file from internal storage
fun deleteFile(context: Context, filename: String): Boolean {
    return context.deleteFile(filename)
}
