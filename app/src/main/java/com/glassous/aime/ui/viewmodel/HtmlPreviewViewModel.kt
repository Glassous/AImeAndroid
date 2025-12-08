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
    
    fun setHtmlCode(code: String) {
        _htmlCode.value = code
    }
    
    fun setIsSourceMode(isSource: Boolean) {
        _isSourceMode.value = isSource
    }
}
