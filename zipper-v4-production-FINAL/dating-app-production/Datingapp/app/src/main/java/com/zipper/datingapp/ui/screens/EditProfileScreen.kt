package com.zipper.datingapp.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zipper.datingapp.data.UserProfile
import com.zipper.datingapp.data.UserRole
import com.zipper.datingapp.data.profileFrames
import com.zipper.datingapp.ui.theme.AppResponsiveContentMaxWidth
import com.zipper.datingapp.ui.theme.LocalWindowWidthClass
import com.zipper.datingapp.ui.theme.defaultHorizontalContentPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    user: UserProfile,
    onSave: (String, Int, String, String, String, String, String, String, List<String>, String) -> Unit,
    onUploadVideo: (Uri) -> Unit,
    onSelectFrame: (String?) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var name by remember { mutableStateOf(user.name) }
    var age by remember { mutableStateOf(user.age.toString()) }
    var city by remember { mutableStateOf(user.city) }
    var gender by remember {
        mutableStateOf(
            when (user.gender.trim().lowercase()) {
                "male", "m" -> "male"
                "female", "f" -> "female"
                else -> user.genderText.takeIf { it == "male" || it == "female" } ?: ""
            }
        )
    }
    var email by remember { mutableStateOf(user.email) }
    var mobile by remember { mutableStateOf(user.mobile) }
    var bio by remember { mutableStateOf(user.bio) }
    var referral by remember { mutableStateOf(user.referralCode) }

    // Firestore may load after first frame; Auth-backed email/phone arrives via updated [user].
    LaunchedEffect(user.email, user.mobile) {
        if (email.isBlank() && user.email.isNotBlank()) email = user.email
        if (mobile.isBlank() && user.mobile.isNotBlank()) mobile = user.mobile
    }

    // Image states
    var profilePhotoUrl by remember { mutableStateOf(user.photoUrl) }
    var galleryPhotos by remember { mutableStateOf(user.galleryPhotos) }

    val profilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { profilePhotoUrl = it.toString() }
    }

    val maxGalleryPhotos = 6
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxGalleryPhotos)
    ) { uris: List<Uri> ->
        val newUris = uris.map { it.toString() }
        galleryPhotos = (galleryPhotos + newUris).distinct().take(maxGalleryPhotos)
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onUploadVideo(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            onSave(name, age.toIntOrNull() ?: 0, city, gender, email, mobile, bio, profilePhotoUrl, galleryPhotos, referral)
                        },
                        enabled = gender.isNotBlank(),
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val widthClass = LocalWindowWidthClass.current
        val hPad = widthClass.defaultHorizontalContentPadding()
        val context = LocalContext.current
        val scheme = MaterialTheme.colorScheme
        val profilePlaceholder = remember(scheme) { ColorPainter(scheme.surfaceVariant) }
        val profileError = remember(scheme) { ColorPainter(scheme.errorContainer.copy(alpha = 0.4f)) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = AppResponsiveContentMaxWidth)
                .fillMaxSize()
                .padding(horizontal = hPad)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            
            // Profile Photo Selection
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.clickable {
                    profilePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Surface(modifier = Modifier.size(100.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    if (profilePhotoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(profilePhotoUrl)
                                .crossfade(300)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholder = profilePlaceholder,
                            error = profileError
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
                Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(6.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Tap to change profile photo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(Modifier.height(32.dp))

            // Intro video: hidden for female users — verification is via instant face flow (FaceVerificationScreen).
            if (user.role == UserRole.GIRL && user.genderText != "female") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Profile Verification", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Upload a 30-second introduction video to get a verified badge.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (user.isVerified) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                contentColor = if (user.isVerified) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                }
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(if (user.isVerified) Icons.Default.CheckCircle else Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (user.isVerified) "Verified" else "Upload Intro Video")
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }

            // Gallery Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Photo Gallery", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            if (galleryPhotos.size >= maxGalleryPhotos) {
                                Toast.makeText(
                                    context,
                                    "Maximum 6 photos allowed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                galleryPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Images", color = MaterialTheme.colorScheme.primary)
                    }
                }
                
                if (galleryPhotos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 88.dp, max = 160.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No gallery images added", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(galleryPhotos) { photoUrl ->
                            Box {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(photoUrl)
                                        .crossfade(300)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                    placeholder = profilePlaceholder,
                                    error = profileError
                                )
                                IconButton(
                                    onClick = { galleryPhotos = galleryPhotos.filter { it != photoUrl } },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            EditField(label = "Display Name", value = name, onValueChange = { name = it })
            EditField(label = "Age", value = age, onValueChange = { age = it }, keyboardType = KeyboardType.Number)
            EditField(label = "City", value = city, onValueChange = { city = it })
            GenderSelectorRow(
                selectedGender = gender,
                onSelectGender = { gender = it }
            )
            EditField(label = "Email Address", value = email, onValueChange = { email = it })
            EditField(label = "Mobile Number", value = mobile, onValueChange = { mobile = it })
            EditField(label = "Bio", value = bio, onValueChange = { bio = it }, singleLine = false)
            EditField(
                label = "Referral Code",
                value = referral,
                onValueChange = { referral = it },
                enabled = user.referralCode.isEmpty()
            )
            if (user.referralCode.isNotEmpty()) {
                Text(
                    "Referral code can only be set once",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(24.dp))
            
            Text("Select Profile Frame", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(12.dp))
            
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(profileFrames, key = { it.id }) { frame ->
                    Surface(
                        modifier = Modifier.size(60.dp).clickable { onSelectFrame(frame.id) },
                        shape = CircleShape,
                        color = if (user.profileFrameId == frame.id) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = if (user.profileFrameId == frame.id) {
                            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else null
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (frame.imageUrl.isNotEmpty()) {
                                AsyncImage(model = frame.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                            } else {
                                Text(frame.name.take(1), color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(48.dp))
        }
        }
    }
}

@Composable
private fun GenderSelectorRow(
    selectedGender: String,
    onSelectGender: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Gender", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedGender == "male",
                onClick = { onSelectGender("male") },
                label = { Text("Male") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            )
            FilterChip(
                selected = selectedGender == "female",
                onClick = { onSelectGender("female") },
                label = { Text("Female") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            )
        }
        if (selectedGender.isBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Select gender to keep profile and CRM filters accurate.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun EditField(label: String, value: String, onValueChange: (String) -> Unit, keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = scheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = scheme.onSurface,
                unfocusedTextColor = scheme.onSurface,
                focusedIndicatorColor = scheme.primary,
                unfocusedIndicatorColor = scheme.outline.copy(alpha = 0.5f),
                cursorColor = scheme.primary
            )
        )
    }
}
