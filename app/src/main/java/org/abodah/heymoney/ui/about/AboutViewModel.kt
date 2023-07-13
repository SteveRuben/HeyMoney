package org.abodah.heymoney.ui.about

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor() : ViewModel() {
    private val _url = MutableStateFlow("https://github.com/tryptich/HeyMoney")
    val url: StateFlow<String> = _url

    fun launchLicense() {
        _url.value = "https://raw.githubusercontent.com/git/git-scm.com/main/MIT-LICENSE.txt"
    }

    fun launchRepository() {
        _url.value = "https://github.com/tryptich/HeyMoney"
    }
}