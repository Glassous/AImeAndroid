package com.glassous.aime.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.view.WindowCompat
import com.glassous.aime.AIMeApplication
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.ui.components.CreateGroupDialog
import com.glassous.aime.ui.components.AddModelDialog
import com.glassous.aime.ui.components.EditGroupDialog
import com.glassous.aime.ui.components.EditModelDialog
import com.glassous.aime.ui.viewmodel.ModelConfigViewModel
import com.glassous.aime.ui.viewmodel.ModelConfigViewModelFactory
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import com.glassous.aime.data.repository.FetchStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModelConfigScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as AIMeApplication
    val viewModel: ModelConfigViewModel = viewModel(
        factory = ModelConfigViewModelFactory(application.modelConfigRepository)
    )

    val uiState by viewModel.uiState.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val fetchStatus by viewModel.fetchStatus.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 沉浸式UI设置
    val view = LocalView.current
    LaunchedEffect(Unit) {
        val activity = context as? Activity
        activity?.let {
            // 设置导航栏透明和沉浸式标志
            it.window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            val decorView = it.window.decorView
            decorView.systemUiVisibility = decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.window.isNavigationBarContrastEnforced = false
            }
            it.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型配置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showResetConfirmDialog() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "获取最新推荐模型")
                    }
                    IconButton(onClick = { viewModel.showCreateGroupDialog() }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加分组")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // 修复：使用 MaterialTheme.colorScheme.background 而不是 Transparent
        // 这样可以防止背景透视导致的重叠问题
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 6.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (groups.isEmpty()) {
                    item {
                        EmptyStateCard(
                            onCreateGroup = { viewModel.showCreateGroupDialog() }
                        )
                    }
                } else {
                    items(groups) { group ->
                        ModelGroupCard(
                            group = group,
                            onAddModel = { viewModel.showAddModelDialog(group.id) },
                            onDeleteGroup = { viewModel.deleteGroup(group) },
                            onDeleteModel = { viewModel.deleteModel(it) },
                            onEditGroup = { viewModel.showEditGroupDialog(it) },
                            onEditModel = { viewModel.showEditModelDialog(it) },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    // 对话框
    if (uiState.showCreateGroupDialog) {
        CreateGroupDialog(
            onDismiss = { viewModel.hideCreateGroupDialog() },
            onConfirm = { name, baseUrl, apiKey, providerUrl ->
                viewModel.createGroup(name, baseUrl, apiKey, providerUrl)
            }
        )
    }

    // 添加模型对话框
    if (uiState.showAddModelDialog && uiState.selectedGroupId != null) {
        AddModelDialog(
            onDismiss = { viewModel.hideAddModelDialog() },
            onConfirm = { name, modelName, remark ->
                viewModel.addModelToGroup(uiState.selectedGroupId!!, name, modelName, remark)
            }
        )
    }

    // 编辑分组对话框
    if (uiState.showEditGroupDialog && uiState.selectedGroup != null) {
        EditGroupDialog(
            group = uiState.selectedGroup!!,
            onDismiss = { viewModel.hideEditGroupDialog() },
            onConfirm = { name, baseUrl, apiKey, providerUrl ->
                viewModel.updateGroup(uiState.selectedGroup!!.id, name, baseUrl, apiKey, providerUrl)
            }
        )
    }

    // 编辑模型对话框
    if (uiState.showEditModelDialog && uiState.selectedModel != null) {
        EditModelDialog(
            model = uiState.selectedModel!!,
            onDismiss = { viewModel.hideEditModelDialog() },
            onConfirm = { name, modelName, remark ->
                viewModel.updateModel(
                    uiState.selectedModel!!.id,
                    uiState.selectedModel!!.groupId,
                    name,
                    modelName,
                    remark
                )
            }
        )
    }

    // 进度弹窗
    if (fetchStatus !is FetchStatus.Idle) {
        AlertDialog(
            onDismissRequest = { 
                // 仅在成功或失败时允许点击外部关闭
                if (fetchStatus is FetchStatus.Success || fetchStatus is FetchStatus.Error) {
                    viewModel.closeFetchDialog() 
                }
            },
            title = {
                when (fetchStatus) {
                    is FetchStatus.Fetching -> Text("正在获取模型列表...")
                    is FetchStatus.Merging -> Text("正在更新本地数据...")
                    is FetchStatus.Success -> Text("更新完成")
                    is FetchStatus.Error -> Text("更新失败")
                    else -> {}
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (val status = fetchStatus) {
                        is FetchStatus.Fetching -> {
                            CircularProgressIndicator()
                            Text("正在连接服务器获取最新配置...")
                        }
                        is FetchStatus.Merging -> {
                            LinearProgressIndicator(
                                progress = status.current.toFloat() / status.total.toFloat(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("正在处理第 ${status.current}/${status.total} 个分组...")
                        }
                        is FetchStatus.Success -> {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text("成功更新模型配置！")
                            Text("新增模型数量: ${status.newModelsCount}")
                        }
                        is FetchStatus.Error -> {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Text("发生错误: ${status.message}")
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                if (fetchStatus is FetchStatus.Success || fetchStatus is FetchStatus.Error) {
                    TextButton(onClick = { viewModel.closeFetchDialog() }) {
                        Text("关闭")
                    }
                }
            }
        )
    }

    // 恢复预设确认对话框
    if (uiState.showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideResetConfirmDialog() },
            title = { Text("获取最新推荐模型") },
            text = { 
                Text("将从云端获取最新推荐模型并合并到本地列表，现有配置不会丢失。") 
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.fetchLatestModels() }
                ) {
                    Text("确认获取")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideResetConfirmDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    // 错误提示
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    // 加载状态
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun EmptyStateCard(
    onCreateGroup: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "暂无模型配置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "点击下方按钮创建第一个模型分组",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onCreateGroup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建分组")
            }
        }
    }
}

@Composable
fun ModelGroupCard(
    group: ModelGroup,
    onAddModel: () -> Unit,
    onDeleteGroup: () -> Unit,
    onDeleteModel: (Model) -> Unit,
    onEditGroup: (ModelGroup) -> Unit,
    onEditModel: (Model) -> Unit,
    viewModel: ModelConfigViewModel
) {
    val models by viewModel.getModelsByGroupId(group.id).collectAsState(initial = emptyList())
    var expanded by remember { mutableStateOf(false) }
    var showDeleteGroupConfirm by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 分组头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Base URL: ${group.baseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!group.providerUrl.isNullOrBlank()) {
                        Text(
                            text = "官网: ${group.providerUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable { uriHandler.openUri(group.providerUrl!!) }
                        )
                    }
                    Text(
                        text = "模型数量: ${models.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (expanded) "收起" else "展开"
                        )
                    }
                    IconButton(onClick = { onEditGroup(group) }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "编辑分组",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showDeleteGroupConfirm = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除分组",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 展开的内容
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))

                // 模型列表
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "模型列表",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = onAddModel) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加模型")
                    }
                }

                if (models.isEmpty()) {
                    Text(
                        text = "暂无模型，点击上方按钮添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    models.forEach { model ->
                        ModelItem(
                            model = model,
                            onDelete = { onDeleteModel(model) },
                            onEdit = { onEditModel(model) }
                        )
                    }
                }
            }

            if (showDeleteGroupConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteGroupConfirm = false },
                    title = { Text("确认删除分组") },
                    text = { Text("删除后不可恢复，确定要删除该分组吗？") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteGroupConfirm = false
                                onDeleteGroup()
                            }
                        ) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteGroupConfirm = false }) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ModelItem(
    model: Model,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var showDeleteModelConfirm by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = model.modelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!model.remark.isNullOrBlank()) {
                Text(
                    text = model.remark!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "编辑模型",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { showDeleteModelConfirm = true }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除模型",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteModelConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteModelConfirm = false },
            title = { Text("确认删除模型") },
            text = { Text("删除后不可恢复，确定要删除该模型吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteModelConfirm = false
                        onDelete()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteModelConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}