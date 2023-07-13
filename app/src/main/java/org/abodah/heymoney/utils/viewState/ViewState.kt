package org.abodah.heymoney.utils.viewState

import org.abodah.heymoney.domain.model.Transaction

sealed class ViewState {
    object Loading : ViewState()
    object Empty : ViewState()
    data class Success(val transaction: List<Transaction>) : ViewState()
    data class Error(val exception: Throwable) : ViewState()
}
