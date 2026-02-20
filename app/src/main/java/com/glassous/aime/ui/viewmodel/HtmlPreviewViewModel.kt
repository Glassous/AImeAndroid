package com.glassous.aime.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData

class HtmlPreviewViewModel : ViewModel() {
    // 用于存储 HTML 代码，避免通过导航参数传递大量数据
    private val _htmlCode = MutableLiveData("")
    val htmlCode: LiveData<String> = _htmlCode
    
    // 用于存储预览模式，true为源码模式，false为预览模式
    private val _isSourceMode = MutableLiveData(false)
    val isSourceMode: LiveData<Boolean> = _isSourceMode
    
    // 用于存储是否为受限模式（如网页分析预览），仅保留核心功能
    private val _isRestrictedMode = MutableLiveData(false)
    val isRestrictedMode: LiveData<Boolean> = _isRestrictedMode

    // 用于存储网页分析的URL，若存在则直接预览该URL
    private val _previewUrl = MutableLiveData<String?>(null)
    val previewUrl: LiveData<String?> = _previewUrl
    
    fun setHtmlCode(code: String) {
        _htmlCode.value = code
    }
    
    fun setIsSourceMode(isSource: Boolean) {
        _isSourceMode.value = isSource
    }

    fun setIsRestrictedMode(restricted: Boolean) {
        _isRestrictedMode.value = restricted
    }

    fun setPreviewUrl(url: String?) {
        _previewUrl.value = url
    }
}
