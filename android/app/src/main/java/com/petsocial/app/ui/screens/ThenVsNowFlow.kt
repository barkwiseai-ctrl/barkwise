package com.petsocial.app.ui.screens

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeParseException
import kotlinx.coroutines.delay

private enum class ThenVsNowStep {
    Hook,
    Inputs,
    Generating,
    Result,
}

@Composable
fun ThenVsNowFlow(
    onClose: () -> Unit,
    initialPetName: String = "Milo",
    initialBirthdayInput: String = "",
    initialThenPhotoUri: String? = null,
    initialNowPhotoUri: String? = null,
    generationStepDelayMs: Long = 850,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(ThenVsNowStep.Hook) }
    var petName by rememberSaveable { mutableStateOf(initialPetName) }
    var birthdayInput by rememberSaveable { mutableStateOf(initialBirthdayInput) }
    var thenPhotoUri by rememberSaveable { mutableStateOf(initialThenPhotoUri) }
    var nowPhotoUri by rememberSaveable { mutableStateOf(initialNowPhotoUri) }
    var weightThenInput by rememberSaveable { mutableStateOf("") }
    var weightNowInput by rememberSaveable { mutableStateOf("") }
    var selectedMilestone by rememberSaveable { mutableStateOf("responds to sit consistently") }
    var processingMessageIndex by rememberSaveable { mutableIntStateOf(0) }
    var comparisonSlider by rememberSaveable { mutableFloatStateOf(0.5f) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    val processingMessages = remember {
        listOf(
            "Calculating age...",
            "Comparing photos...",
            "Building your growth summary...",
        )
    }

    val datePicker = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                birthdayInput = LocalDate.of(year, month + 1, dayOfMonth).toString()
            },
            LocalDate.now().year,
            LocalDate.now().monthValue - 1,
            LocalDate.now().dayOfMonth,
        )
    }

    val thenPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        thenPhotoUri = uri?.toString()
    }

    val nowPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        nowPhotoUri = uri?.toString()
    }

    LaunchedEffect(step) {
        if (step == ThenVsNowStep.Generating) {
            processingMessageIndex = 0
            delay(generationStepDelayMs)
            processingMessageIndex = 1
            delay(generationStepDelayMs)
            processingMessageIndex = 2
            delay(generationStepDelayMs)
            step = ThenVsNowStep.Result
        }
    }

    fun startGenerate() {
        validationError = validateThenVsNowInputs(
            birthdayInput = birthdayInput,
            thenPhotoUri = thenPhotoUri,
            nowPhotoUri = nowPhotoUri,
            petName = petName,
        )
        if (validationError == null) {
            step = ThenVsNowStep.Generating
        }
    }

    val parsedBirthday = parseBirthday(birthdayInput)
    val ageLabel = parsedBirthday?.let { toHumanAge(it, LocalDate.now()) } ?: "Age unavailable"
    val whatChangedBullets = buildWhatChangedBullets(
        weightThen = weightThenInput.toFloatOrNull(),
        weightNow = weightNowInput.toFloatOrNull(),
        milestone = selectedMilestone,
    )
    val nextFocus = buildNextFocusTip(parsedBirthday, LocalDate.now())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 90.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Then vs Now",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onClose) {
                    Text("Close")
                }
            }
        }

        when (step) {
            ThenVsNowStep.Hook -> {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("See how much your pet has grown", style = MaterialTheme.typography.titleMedium)
                            Text("Create a shareable Then vs Now card in under a minute.")
                            Text("Est. time: 45-60 seconds", style = MaterialTheme.typography.labelMedium)
                            Button(
                                onClick = { step = ThenVsNowStep.Inputs },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("then_vs_now_create_card_button"),
                            ) {
                                Text("Create my card")
                            }
                        }
                    }
                }
            }

            ThenVsNowStep.Inputs -> {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Let's make your growth card", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = petName,
                                onValueChange = { petName = it },
                                label = { Text("Pet name") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = birthdayInput,
                                onValueChange = { birthdayInput = it },
                                label = { Text("Pet birthday (YYYY-MM-DD)") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(onClick = { datePicker.show() }) {
                                Text("Pick birthday")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { thenPhotoPicker.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (thenPhotoUri == null) "Add Then photo" else "Then photo added")
                                }
                                Button(
                                    onClick = { nowPhotoPicker.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (nowPhotoUri == null) "Add Now photo" else "Now photo added")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = weightThenInput,
                                    onValueChange = { weightThenInput = it },
                                    label = { Text("Weight then (kg, optional)") },
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = weightNowInput,
                                    onValueChange = { weightNowInput = it },
                                    label = { Text("Weight now (kg, optional)") },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Text("Milestone", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "responds to sit consistently",
                                    "better leash walking",
                                    "potty trained",
                                ).forEach { milestone ->
                                    FilterChip(
                                        selected = selectedMilestone == milestone,
                                        onClick = { selectedMilestone = milestone },
                                        label = { Text(milestone) },
                                    )
                                }
                            }
                            validationError?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Button(
                                onClick = ::startGenerate,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("then_vs_now_generate_card_button"),
                            ) {
                                Text("Generate card")
                            }
                        }
                    }
                }
            }

            ThenVsNowStep.Generating -> {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("then_vs_now_generating_state")
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(processingMessages[processingMessageIndex], style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            ThenVsNowStep.Result -> {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("then_vs_now_result_state")
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("$petName Then vs Now", style = MaterialTheme.typography.titleLarge)
                            Text("Age: $ageLabel", style = MaterialTheme.typography.titleSmall)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            ) {
                                thenPhotoUri?.let { uri ->
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Then photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        alpha = 1f - comparisonSlider,
                                    )
                                }
                                nowPhotoUri?.let { uri ->
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Now photo",
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width((220 * comparisonSlider).dp)
                                            .align(Alignment.CenterStart),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Then")
                                Text("Now")
                            }
                            Slider(
                                value = comparisonSlider,
                                onValueChange = { comparisonSlider = it.coerceIn(0f, 1f) },
                            )

                            Text("What changed", style = MaterialTheme.typography.titleSmall)
                            whatChangedBullets.forEach { bullet ->
                                Text("• $bullet", style = MaterialTheme.typography.bodyMedium)
                            }

                            Text("Next 30-day focus", style = MaterialTheme.typography.titleSmall)
                            Text(nextFocus, style = MaterialTheme.typography.bodyMedium)

                            Button(
                                onClick = {
                                    val shareText = buildString {
                                        append("From tiny paws to big personality.\n")
                                        append("$petName is now $ageLabel.\n")
                                        append("Next 30-day focus: $nextFocus\n")
                                        append("#ThenVsNow")
                                    }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share to Stories"))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("then_vs_now_share_button"),
                            ) {
                                Text("Share to Stories")
                            }

                            TextButton(
                                onClick = { step = ThenVsNowStep.Inputs },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Edit card")
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(6.dp)) }
    }
}

internal fun validateThenVsNowInputs(
    birthdayInput: String,
    thenPhotoUri: String?,
    nowPhotoUri: String?,
    petName: String,
): String? {
    val parsedBirthday = parseBirthday(birthdayInput)
    return when {
        parsedBirthday == null -> "Please add a valid birthday."
        thenPhotoUri.isNullOrBlank() -> "Please add a Then photo."
        nowPhotoUri.isNullOrBlank() -> "Please add a Now photo."
        petName.isBlank() -> "Please add your pet name."
        else -> null
    }
}

internal fun parseBirthday(input: String): LocalDate? {
    return try {
        LocalDate.parse(input.trim())
    } catch (_: DateTimeParseException) {
        null
    }
}

internal fun toHumanAge(from: LocalDate, to: LocalDate): String {
    if (from.isAfter(to)) return "0 months"
    val period = Period.between(from, to)
    val yearsPart = if (period.years > 0) "${period.years} year${if (period.years == 1) "" else "s"}" else null
    val monthsPart = if (period.months > 0) "${period.months} month${if (period.months == 1) "" else "s"}" else null
    return listOfNotNull(yearsPart, monthsPart).joinToString(", ").ifBlank { "0 months" }
}

internal fun buildWhatChangedBullets(
    weightThen: Float?,
    weightNow: Float?,
    milestone: String,
): List<String> {
    val sizeBullet = if (weightThen != null && weightNow != null) {
        val delta = weightNow - weightThen
        val direction = if (delta >= 0f) "up" else "down"
        "Weight changed $direction ${"%.1f".format(kotlin.math.abs(delta))} kg"
    } else {
        "Looks taller and leaner"
    }

    return listOf(
        sizeBullet,
        "Coat appears fuller and shinier",
        "Milestone: $milestone",
    )
}

internal fun buildNextFocusTip(birthday: LocalDate?, today: LocalDate): String {
    if (birthday == null) return "Add 10 minutes of sniff walk 3x/week for enrichment."
    val months = Period.between(birthday, today).toTotalMonths()
    return when {
        months < 12 -> "Keep training sessions short: 5-8 minutes, 4x/week."
        months < 84 -> "Add 10 minutes of sniff walk 3x/week for enrichment."
        else -> "Schedule one low-impact mobility session weekly and monitor recovery time."
    }
}
