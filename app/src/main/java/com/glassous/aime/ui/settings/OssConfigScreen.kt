package com.glassous.aime.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.glassous.aime.data.preferences.OssPreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OssConfigScreen(
    ossPreferences: OssPreferences,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val regionOptions = remember {
        listOf(
            RegionItem("华东1（杭州）", "cn-hangzhou", "https://oss-cn-hangzhou.aliyuncs.com"),
            RegionItem("华东2（上海）", "cn-shanghai", "https://oss-cn-shanghai.aliyuncs.com"),
            RegionItem("华北1（青岛）", "cn-qingdao", "https://oss-cn-qingdao.aliyuncs.com"),
            RegionItem("华北2（北京）", "cn-beijing", "https://oss-cn-beijing.aliyuncs.com"),
            RegionItem("华北3（张家口）", "cn-zhangjiakou", "https://oss-cn-zhangjiakou.aliyuncs.com"),
            RegionItem("华北5（呼和浩特）", "cn-huhehaote", "https://oss-cn-huhehaote.aliyuncs.com"),
            RegionItem("华北6（乌兰察布）", "cn-wulanchabu", "https://oss-cn-wulanchabu.aliyuncs.com"),
            RegionItem("华南1（深圳）", "cn-shenzhen", "https://oss-cn-shenzhen.aliyuncs.com"),
            RegionItem("华南2（河源）", "cn-heyuan", "https://oss-cn-heyuan.aliyuncs.com"),
            RegionItem("华南3（广州）", "cn-guangzhou", "https://oss-cn-guangzhou.aliyuncs.com"),
            RegionItem("西南1（成都）", "cn-chengdu", "https://oss-cn-chengdu.aliyuncs.com"),
            RegionItem("中国香港", "cn-hongkong", "https://oss-cn-hongkong.aliyuncs.com"),
            RegionItem("无地域属性（中国内地）", "rg-china-mainland", "https://oss-rg-china-mainland.aliyuncs.com")
        )
    }

    val selectedRegionName = remember { mutableStateOf("") }
    val selectedRegionId = remember { mutableStateOf("") }
    val endpoint = remember { mutableStateOf("") }
    val bucket = remember { mutableStateOf("") }
    val accessKeyId = remember { mutableStateOf("") }
    val accessKeySecret = remember { mutableStateOf("") }
    val expanded = remember { mutableStateOf(false) }

    // 仅在首次进入时读取一次持久化配置，避免后续持续收集覆盖用户输入
    LaunchedEffect(Unit) {
        val id = ossPreferences.regionId.first()
        selectedRegionId.value = id ?: ""
        val item = regionOptions.find { it.id == selectedRegionId.value }
        selectedRegionName.value = item?.name ?: ""

        val ep = ossPreferences.endpoint.first()
        // 如未保存 endpoint，但已有地域ID，则自动填充该地域默认 endpoint
        endpoint.value = ep ?: (item?.endpoint ?: "")

        bucket.value = ossPreferences.bucket.first() ?: ""
        accessKeyId.value = ossPreferences.accessKeyId.first() ?: ""
        accessKeySecret.value = ossPreferences.accessKeySecret.first() ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阿里云 OSS 配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
            verticalArrangement = Arrangement.Top
        ) {
            Text("选择地域", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expanded.value,
                onExpandedChange = { expanded.value = !expanded.value }
            ) {
                OutlinedTextField(
                    value = selectedRegionName.value,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("地域") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                DropdownMenu(
                    expanded = expanded.value,
                    onDismissRequest = { expanded.value = false }
                ) {
                    regionOptions.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.name) },
                            onClick = {
                                selectedRegionName.value = item.name
                                selectedRegionId.value = item.id
                                endpoint.value = item.endpoint
                                expanded.value = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = endpoint.value,
                onValueChange = { endpoint.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endpoint") },
                placeholder = { Text("例如：https://oss-cn-hangzhou.aliyuncs.com") }
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = bucket.value,
                onValueChange = { bucket.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Bucket 名称") }
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = accessKeyId.value,
                onValueChange = { accessKeyId.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("AccessKey ID") }
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = accessKeySecret.value,
                onValueChange = { accessKeySecret.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("AccessKey Secret") }
            )

            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                scope.launch {
                    // 空字符串按未设置处理，避免保存无效值导致后续读取为空
                    ossPreferences.setRegionId(selectedRegionId.value.ifEmpty { null })
                    ossPreferences.setEndpoint(endpoint.value.ifEmpty { null })
                    ossPreferences.setBucket(bucket.value.ifEmpty { null })
                    ossPreferences.setAccessKeyId(accessKeyId.value.ifEmpty { null })
                    ossPreferences.setAccessKeySecret(accessKeySecret.value.ifEmpty { null })
                    onBack()
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("保存配置")
            }
        }
    }
}

data class RegionItem(val name: String, val id: String, val endpoint: String)