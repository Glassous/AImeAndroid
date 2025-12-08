package com.glassous.aime.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData

class HtmlPreviewViewModel : ViewModel() {
    // 用于存储 HTML 代码，避免通过导航参数传递大量数据
    private val _htmlCode = MutableLiveData("")
    val htmlCode: LiveData<String> = _htmlCode
    
    fun setHtmlCode(code: String) {
        _htmlCode.value = code
    }
}
