package com.petsocial.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.petsocial.app.ui.PetRosterItem

@Composable
fun PetRosterThumbnail(
    pet: PetRosterItem,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    AsyncImage(
        model = pet.photoUrl,
        contentDescription = "${pet.petName} spotlight photo",
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun PetRosterShowcase(
    title: String,
    pets: List<PetRosterItem>,
    modifier: Modifier = Modifier,
) {
    if (pets.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 6.dp),
            ) {
                items(pets) { pet ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AsyncImage(
                            model = pet.photoUrl,
                            contentDescription = "${pet.petName} photo",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            text = pet.petName,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderRosterChip(
    pet: PetRosterItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.24f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PetRosterThumbnail(pet = pet, size = 30.dp)
        Box(
            modifier = Modifier.height(30.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "New this week",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
