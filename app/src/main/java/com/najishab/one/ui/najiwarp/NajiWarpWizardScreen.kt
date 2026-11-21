package com.najishab.one.ui.najiwarp

import com.najishab.one.R
import com.najishab.one.utils.S

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.najishab.one.engines.najiwarp.AccountType
import com.najishab.one.engines.najiwarp.NajiWarpEngine
import com.najishab.one.engines.najiwarp.NajiWarpPreferences
import com.najishab.one.engines.najiwarp.NajiWarpZtCallbackHolder
import com.najishab.one.ui.settings.IosScreen
import kotlinx.coroutines.launch
private val UsqueOrange = Color(0xFFFFB26B)
private val UsqueOrangeBadgeBg = Color(0xFF3A2818)
private val UsqueDivider = Color.White.copy(alpha = 0.12f)
private val UsqueMuted = Color.White.copy(alpha = 0.65f)
@Composable
fun NajiWarpWizardScreen(
onFinished: () -> Unit,
onCancel: () -> Unit
) {
val context = LocalContext.current
val prefs = remember { NajiWarpPreferences(context) }
val scope = rememberCoroutineScope()
var step by remember { mutableIntStateOf(1) }
var termsAccepted by remember { mutableStateOf(false) }
var selectedAccountType by remember { mutableStateOf(AccountType.CONSUMER_FREE) }
var licenseKey by remember { mutableStateOf("") }
var zeroTrustTeam by remember { mutableStateOf("") }
var ztCallbackInput by remember { mutableStateOf("") }
val capturedZtUri by NajiWarpZtCallbackHolder.pendingCallbackUri.collectAsState()
LaunchedEffect(capturedZtUri) {
    if (!capturedZtUri.isNullOrBlank()) {
        ztCallbackInput = capturedZtUri!!
    }
}
var isRegistering by remember { mutableStateOf(false) }
var errorMessage by remember { mutableStateOf<String?>(null) }
var notificationGranted by remember {
    mutableStateOf(
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    )
}
val notifLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    notificationGranted = granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    step = 3
}
BackHandler {
    if (!isRegistering) {
        if (step > 1) step-- else onCancel()
    }
}
IosScreen(title = null, onBack = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (i in 1..4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(if (i <= step) UsqueOrange else Color.White.copy(alpha = 0.16f))
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val nextLabel = when (step) {
            1 -> S(R.string.nw_wiz_next_permissions)
            2 -> S(R.string.nw_wiz_next_terms)
            3 -> S(R.string.nw_wiz_next_account)
            else -> ""
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "$step / 4", color = UsqueMuted, fontSize = 12.sp)
            if (nextLabel.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                Text(text = nextLabel, color = UsqueMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(26.dp))
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "UsqueWizardStep"
        ) { currentStep ->
            when (currentStep) {
                1 -> Step1Welcome()
                2 -> Step2Permissions(notificationGranted = notificationGranted)
                3 -> Step3Terms(
                    onOpenUrl = { url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                )
                4 -> Step4AccountSetup(
                    selectedType = selectedAccountType,
                    onSelectType = {
                        selectedAccountType = it
                        errorMessage = null
                    },
                    licenseKey = licenseKey,
                    onLicenseKeyChange = { licenseKey = it },
                    zeroTrustTeam = zeroTrustTeam,
                    onZeroTrustTeamChange = { zeroTrustTeam = it },
                    ztCallback = ztCallbackInput,
                    onZtCallbackChange = { ztCallbackInput = it },
                    onOpenZtLogin = {
                        val team = zeroTrustTeam.trim().lowercase()
                        if (team.isNotEmpty()) {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://$team.cloudflareaccess.com/warp"))
                                )
                            }
                        }
                    }
                )
            }
        }
        errorMessage?.let { err ->
            Spacer(Modifier.height(14.dp))
            Text(text = err, color = Color(0xFFFF453A), fontSize = 13.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = S(R.string.nw_wiz_trademark),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(16.dp))
        when (step) {
            1 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, UsqueDivider),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text(S(R.string.nw_wiz_exit), fontSize = 14.sp)
                    }
                    Button(
                        onClick = { step = 2 },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UsqueOrange,
                            contentColor = Color(0xFF1A120B)
                        )
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(S(R.string.nw_wiz_continue), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
            2 -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                step = 3
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UsqueOrange,
                            contentColor = Color(0xFF1A120B)
                        )
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(S(R.string.nw_wiz_grant_continue), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    OutlinedButton(
                        onClick = { step = 1 },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, UsqueDivider),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(S(R.string.nw_wiz_back), fontSize = 14.sp)
                    }
                }
            }
            3 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { termsAccepted = !termsAccepted }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = termsAccepted,
                        onCheckedChange = { termsAccepted = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = UsqueOrange,
                            uncheckedColor = Color.White.copy(alpha = 0.5f),
                            checkmarkColor = Color(0xFF1A120B)
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = S(R.string.nw_wiz_accept_terms),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { step = 2 },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, UsqueDivider),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(S(R.string.nw_wiz_back), fontSize = 14.sp)
                    }
                    Button(
                        onClick = { step = 4 },
                        enabled = termsAccepted,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UsqueOrange,
                            contentColor = Color(0xFF1A120B),
                            disabledContainerColor = Color.White.copy(alpha = 0.12f),
                            disabledContentColor = Color.White.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(S(R.string.nw_wiz_continue), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
            4 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (!isRegistering) step = 3 },
                        enabled = !isRegistering,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, UsqueDivider),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(S(R.string.nw_wiz_back), fontSize = 14.sp)
                    }
                    Button(
                        onClick = {
                            isRegistering = true
                            errorMessage = null
                            val current = prefs.loadConfig().copy(
                                accountType = selectedAccountType,
                                warpLicenseKey = licenseKey.trim(),
                                zeroTrustTeam = zeroTrustTeam.trim()
                            )
                            scope.launch {
                                val res = NajiWarpEngine.setupAccount(
                                    context = context,
                                    config = current,
                                    zeroTrustCallbackUri = ztCallbackInput.trim()
                                )
                                isRegistering = false
                                if (res.isSuccess) {
                                    NajiWarpZtCallbackHolder.pendingCallbackUri.value = null
                                    prefs.isOnboardingCompleted = true
                                    onFinished()
                                } else {
                                    errorMessage = res.exceptionOrNull()?.message ?: S(R.string.nw_wiz_setup_error)
                                }
                            }
                        },
                        enabled = !isRegistering,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UsqueOrange,
                            contentColor = Color(0xFF1A120B)
                        )
                    ) {
                        if (isRegistering) {
                            CircularProgressIndicator(
                                color = Color(0xFF1A120B),
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(S(R.string.nw_wiz_registering), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        } else {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(S(R.string.nw_wiz_finish_setup), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
}
@Composable
private fun StepIconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
        .size(46.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(UsqueOrangeBadgeBg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = UsqueOrange, modifier = Modifier.size(24.dp))
}
}
@Composable
private fun Step1Welcome() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
        modifier = Modifier
        .size(54.dp)
        .clip(CircleShape)
        .border(3.dp, UsqueOrange, CircleShape)
        .background(UsqueOrangeBadgeBg),
        contentAlignment = Alignment.Center
    ) {
Text("U", color = UsqueOrange, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
}
    Spacer(Modifier.height(20.dp))
    Text(
        text = S(R.string.nw_wiz_welcome),
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = S(R.string.nw_wiz_welcome_sub),
        color = UsqueMuted,
        fontSize = 14.sp,
        lineHeight = 22.sp
    )
    Spacer(Modifier.height(26.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(UsqueDivider))
    WelcomeStepPreviewRow(icon = Icons.Default.Shield, title = S(R.string.nw_wiz_permissions_title), number = "2")
    Box(Modifier.fillMaxWidth().height(1.dp).background(UsqueDivider))
    WelcomeStepPreviewRow(icon = Icons.Default.Description, title = S(R.string.nw_wiz_terms_title), number = "3")
    Box(Modifier.fillMaxWidth().height(1.dp).background(UsqueDivider))
    WelcomeStepPreviewRow(icon = Icons.Default.VpnKey, title = S(R.string.nw_wiz_account_title), number = "4")
    Box(Modifier.fillMaxWidth().height(1.dp).background(UsqueDivider))
}
}
@Composable
private fun WelcomeStepPreviewRow(icon: ImageVector, title: String, number: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = UsqueMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(number, color = UsqueMuted, fontSize = 14.sp)
    }
}
@Composable
private fun Step2Permissions(notificationGranted: Boolean) {
Column(modifier = Modifier.fillMaxWidth()) {
StepIconBadge(Icons.Default.Shield)
Spacer(Modifier.height(18.dp))
Text(
text = S(R.string.nw_wiz_permissions_title),
color = Color.White,
fontSize = 24.sp,
fontWeight = FontWeight.Bold
)
Spacer(Modifier.height(10.dp))
Text(
text = S(R.string.nw_wiz_permissions_body),
color = UsqueMuted,
fontSize = 14.sp,
lineHeight = 22.sp
)
Spacer(Modifier.height(24.dp))
Row(
modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
verticalAlignment = Alignment.CenterVertically
) {
Icon(Icons.Default.Notifications, contentDescription = null, tint = UsqueMuted, modifier = Modifier.size(22.dp))
Spacer(Modifier.width(14.dp))
Column {
Text(S(R.string.nw_wiz_notifications), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
Spacer(Modifier.height(3.dp))
Text(
text = if (notificationGranted) S(R.string.nw_wiz_notif_granted) else S(R.string.nw_wiz_notif_optional),
color = UsqueMuted,
fontSize = 13.sp
)
}
}
}
}
@Composable
private fun Step3Terms(onOpenUrl: (String) -> Unit) {
Column(modifier = Modifier.fillMaxWidth()) {
StepIconBadge(Icons.Default.Description)
Spacer(Modifier.height(18.dp))
Text(
text = S(R.string.nw_wiz_terms_title),
color = Color.White,
fontSize = 24.sp,
fontWeight = FontWeight.Bold
)
Spacer(Modifier.height(10.dp))
Text(
text = S(R.string.nw_wiz_terms_body),
color = UsqueMuted,
fontSize = 14.sp,
lineHeight = 22.sp
)
Spacer(Modifier.height(22.dp))
Box(Modifier.fillMaxWidth().height(1.dp).background(UsqueDivider))
TermsLinkRow(Icons.Default.Description, S(R.string.nw_wiz_terms_app)) {
onOpenUrl("https://www.cloudflare.com/application/terms/")
}
Box(Modifier.fillMaxWidth().height(1.dp).background(UsqueDivider))
TermsLinkRow(Icons.Default.VerifiedUser, S(R.string.nw_wiz_terms_warp_privacy)) {
onOpenUrl("https://www.cloudflare.com/application/privacypolicy/")
}
Box(Modifier.fillMaxWidth().height(1.dp).background(UsqueDivider))
TermsLinkRow(Icons.Default.Shield, S(R.string.nw_wiz_terms_zt_privacy)) {
onOpenUrl("https://www.cloudflare.com/privacypolicy/")
}
Box(Modifier.fillMaxWidth().height(1.dp).background(UsqueDivider))
}
}
@Composable
private fun TermsLinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
Row(
modifier = Modifier
.fillMaxWidth()
.clickable(onClick = onClick)
.padding(vertical = 15.dp),
verticalAlignment = Alignment.CenterVertically
) {
Icon(icon, contentDescription = null, tint = UsqueMuted, modifier = Modifier.size(20.dp))
Spacer(Modifier.width(14.dp))
Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
Icon(Icons.Default.OpenInNew, contentDescription = null, tint = UsqueMuted, modifier = Modifier.size(18.dp))
}
}
@Composable
private fun Step4AccountSetup(
selectedType: AccountType,
onSelectType: (AccountType) -> Unit,
licenseKey: String,
onLicenseKeyChange: (String) -> Unit,
zeroTrustTeam: String,
onZeroTrustTeamChange: (String) -> Unit,
ztCallback: String,
onZtCallbackChange: (String) -> Unit,
onOpenZtLogin: () -> Unit
) {
Column(modifier = Modifier.fillMaxWidth()) {
StepIconBadge(Icons.Default.VpnKey)
Spacer(Modifier.height(18.dp))
Text(
text = S(R.string.nw_wiz_account_heading),
color = Color.White,
fontSize = 24.sp,
fontWeight = FontWeight.Bold
)
Spacer(Modifier.height(18.dp))
    AccountRadioRow(
        selected = selectedType == AccountType.CONSUMER_FREE,
        title = S(R.string.nw_wiz_free_account),
        subtitle = null,
        badge = null,
        onClick = { onSelectType(AccountType.CONSUMER_FREE) }
    )
    AccountRadioRow(
        selected = selectedType == AccountType.WARP_PLUS,
        title = S(R.string.nw_wiz_warp_plus),
        subtitle = null,
        badge = null,
        onClick = { onSelectType(AccountType.WARP_PLUS) }
    )
    if (selectedType == AccountType.WARP_PLUS) {
        WizardInputBox(
            value = licenseKey,
            onValueChange = onLicenseKeyChange,
            placeholder = S(R.string.nw_wiz_license_placeholder)
        )
        Spacer(Modifier.height(8.dp))
    }
    AccountRadioRow(
        selected = selectedType == AccountType.ZERO_TRUST,
        title = "Cloudflare Zero Trust",
        subtitle = S(R.string.nw_wiz_zt_subtitle),
        badge = S(R.string.nw_wiz_experimental),
        onClick = { onSelectType(AccountType.ZERO_TRUST) }
    )
    if (selectedType == AccountType.ZERO_TRUST) {
        WizardInputBox(
            value = zeroTrustTeam,
            onValueChange = onZeroTrustTeamChange,
            placeholder = S(R.string.nw_wiz_team_placeholder)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenZtLogin,
            enabled = zeroTrustTeam.isNotBlank(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, UsqueOrange.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = UsqueOrange),
            modifier = Modifier.fillMaxWidth().padding(start = 36.dp)
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(S(R.string.nw_wiz_open_zt_login), fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        WizardInputBox(
            value = ztCallback,
            onValueChange = onZtCallbackChange,
            placeholder = S(R.string.nw_wiz_callback_placeholder)
        )
    }
}
}
@Composable
private fun AccountRadioRow(
selected: Boolean,
title: String,
subtitle: String?,
badge: String?,
onClick: () -> Unit
) {
Row(
modifier = Modifier
.fillMaxWidth()
.clickable(onClick = onClick)
.padding(vertical = 10.dp),
verticalAlignment = Alignment.Top
) {
RadioButton(
selected = selected,
onClick = onClick,
colors = RadioButtonDefaults.colors(
selectedColor = UsqueOrange,
unselectedColor = Color.White.copy(alpha = 0.5f)
),
modifier = Modifier.size(22.dp)
)
Spacer(Modifier.width(14.dp))
Column {
Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
if (subtitle != null) {
Spacer(Modifier.height(2.dp))
Text(subtitle, color = UsqueMuted, fontSize = 13.sp)
}
if (badge != null) {
Spacer(Modifier.height(4.dp))
Text(badge, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}
}
}
}
@Composable
private fun WizardInputBox(
value: String,
onValueChange: (String) -> Unit,
placeholder: String
) {
Box(
modifier = Modifier
.fillMaxWidth()
.padding(start = 36.dp)
.clip(RoundedCornerShape(10.dp))
.background(Color.White.copy(alpha = 0.08f))
.border(1.dp, UsqueDivider, RoundedCornerShape(10.dp))
.padding(horizontal = 14.dp, vertical = 12.dp)
) {
BasicTextField(
value = value,
onValueChange = onValueChange,
singleLine = true,
textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
cursorBrush = SolidColor(UsqueOrange),
modifier = Modifier.fillMaxWidth(),
decorationBox = { inner ->
if (value.isEmpty()) {
Text(placeholder, color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp)
}
inner()
}
)
}
}