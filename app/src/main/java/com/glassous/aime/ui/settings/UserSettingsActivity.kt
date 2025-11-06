package com.glassous.aime.ui.settings

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import android.view.WindowManager
import com.glassous.aime.ui.theme.AImeTheme
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.data.model.UserProfile
import com.glassous.aime.ui.viewmodel.UserProfileViewModel
import com.glassous.aime.ui.viewmodel.UserProfileViewModelFactory
import kotlinx.coroutines.launch

class UserSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val selectedTheme by themeViewModel.selectedTheme.collectAsState()
            val darkTheme = when (selectedTheme) {
                ThemePreferences.THEME_LIGHT -> false
                ThemePreferences.THEME_DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            AImeTheme(darkTheme = darkTheme) {
                val context = LocalContext.current
                val vm: UserProfileViewModel = viewModel(
                    factory = UserProfileViewModelFactory(context.applicationContext as android.app.Application)
                )
                UserSettingsScreen(
                    onNavigateBack = { finish() },
                    viewModel = vm
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: UserProfileViewModel
) {
    val profile by viewModel.profile.collectAsState(initial = UserProfile())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 本地可编辑状态
    var nickname by remember(profile.nickname) { mutableStateOf(profile.nickname ?: "") }
    var city by remember(profile.city) { mutableStateOf(profile.city ?: "") }
    var preferredLanguage by remember(profile.preferredLanguage) { mutableStateOf(profile.preferredLanguage ?: "") }
    var ageText by remember(profile.age) { mutableStateOf(profile.age?.toString() ?: "") }
    var email by remember(profile.email) { mutableStateOf(profile.email ?: "") }
    var phone by remember(profile.phone) { mutableStateOf(profile.phone ?: "") }
    var gender by remember(profile.gender) { mutableStateOf(profile.gender ?: "") }
    var birthday by remember(profile.birthday) { mutableStateOf(profile.birthday ?: "") }
    var occupation by remember(profile.occupation) { mutableStateOf(profile.occupation ?: "") }
    var company by remember(profile.company) { mutableStateOf(profile.company ?: "") }
    var timezone by remember(profile.timezone) { mutableStateOf(profile.timezone ?: "") }
    var website by remember(profile.website) { mutableStateOf(profile.website ?: "") }
    var address by remember(profile.address) { mutableStateOf(profile.address ?: "") }
    var hobbies by remember(profile.hobbies) { mutableStateOf(profile.hobbies ?: "") }
    var bio by remember(profile.bio) { mutableStateOf(profile.bio ?: "") }
    var avatarUrl by remember(profile.avatarUrl) { mutableStateOf(profile.avatarUrl ?: "") }

    // 可编辑的自定义字段列表
    val customFieldsList = remember(profile.customFields) {
        mutableStateListOf<MutablePair>().apply {
            profile.customFields.forEach { (k, v) -> add(MutablePair(k, v)) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用户设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    TextButton(onClick = {
                        val customMap = customFieldsList
                            .filter { it.key.isNotBlank() }
                            .associate { it.key to it.value }
                        val age = ageText.toIntOrNull()
                        val updated = UserProfile(
                            nickname = nickname.ifBlank { null },
                            city = city.ifBlank { null },
                            preferredLanguage = preferredLanguage.ifBlank { null },
                            age = age,
                            email = email.ifBlank { null },
                            phone = phone.ifBlank { null },
                            gender = gender.ifBlank { null },
                            birthday = birthday.ifBlank { null },
                            occupation = occupation.ifBlank { null },
                            company = company.ifBlank { null },
                            timezone = timezone.ifBlank { null },
                            website = website.ifBlank { null },
                            address = address.ifBlank { null },
                            hobbies = hobbies.ifBlank { null },
                            bio = bio.ifBlank { null },
                            avatarUrl = avatarUrl.ifBlank { null },
                            customFields = customMap
                        )
                        viewModel.saveProfile(updated) { ok, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }) { Text("保存") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 基本信息
            SectionCard(title = "基础信息") {
                OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("昵称") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("所在城市") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = preferredLanguage, onValueChange = { preferredLanguage = it }, label = { Text("首选语言") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = ageText, onValueChange = { ageText = it.filter { ch -> ch.isDigit() } }, label = { Text("年龄") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }

            // 联系与社交
            SectionCard(title = "联系与社交") {
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("邮箱") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("电话") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = website, onValueChange = { website = it }, label = { Text("个人网站") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = avatarUrl, onValueChange = { avatarUrl = it }, label = { Text("头像URL") }, modifier = Modifier.fillMaxWidth())
            }

            // 个人偏好与背景
            SectionCard(title = "偏好与背景") {
                OutlinedTextField(value = gender, onValueChange = { gender = it }, label = { Text("性别") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = birthday, onValueChange = { birthday = it }, label = { Text("生日(YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = occupation, onValueChange = { occupation = it }, label = { Text("职业") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("公司") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = timezone, onValueChange = { timezone = it }, label = { Text("时区") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("地址") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = hobbies, onValueChange = { hobbies = it }, label = { Text("兴趣爱好(逗号分隔)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("个人简介") }, modifier = Modifier.fillMaxWidth())
            }

            // 自定义字段
            SectionCard(title = "自定义字段") {
                customFieldsList.forEachIndexed { index, pair ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = pair.key,
                            onValueChange = { newKey -> customFieldsList[index] = pair.copy(key = newKey) },
                            label = { Text("字段名") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = pair.value,
                            onValueChange = { newValue -> customFieldsList[index] = pair.copy(value = newValue) },
                            label = { Text("字段值") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { customFieldsList.removeAt(index) }) { Text("移除") }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = { customFieldsList.add(MutablePair("", "")) }) { Text("添加字段") }
            }

            // 操作区
            var showClearDialog by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showClearDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("清空") }
            }

            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text("确认清空？") },
                    text = { Text("这将清除所有已填写的用户信息，且不可撤销。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                // 清空本地并保存空对象
                                nickname = ""; city = ""; preferredLanguage = ""; ageText = "";
                                email = ""; phone = ""; gender = ""; birthday = "";
                                occupation = ""; company = ""; timezone = ""; website = "";
                                address = ""; hobbies = ""; bio = ""; avatarUrl = "";
                                customFieldsList.clear()
                                viewModel.clearProfile { ok, msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                                showClearDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("清空") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDialog = false }) { Text("取消") }
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// 简单可变键值对用于编辑自定义字段
private data class MutablePair(var key: String, var value: String)