package org.abodah.heymoney.share

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.dao.write.WriteTransactionDao
import com.ivy.data.db.entity.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

enum class SharePeriod(val label: String, val days: Long) {
    TODAY("Today", 1), WEEK("7 days", 7), MONTH("30 days", 30)
}

data class SendState(
    val period: SharePeriod = SharePeriod.WEEK,
    val count: Int = 0,
    val link: String? = null,
    val code: String? = null,
    val qr: Bitmap? = null,
    val qrTooBig: Boolean = false,
)

data class ReceiveState(
    val link: String = "",
    val code: String = "",
    val preview: ShareBatch? = null,
    val newCount: Int = 0,
    val error: String? = null,
    val imported: Int? = null,
)

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val writeTransactionDao: WriteTransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
) : ViewModel() {

    private val _send = MutableStateFlow(SendState())
    val send: StateFlow<SendState> = _send

    private val _receive = MutableStateFlow(ReceiveState())
    val receive: StateFlow<ReceiveState> = _receive

    init {
        selectPeriod(SharePeriod.WEEK)
    }

    // ---- send ----

    fun selectPeriod(period: SharePeriod) {
        _send.value = SendState(period = period)
        viewModelScope.launch {
            val items = collect(period)
            _send.update { it.copy(count = items.size) }
        }
    }

    fun generate() {
        viewModelScope.launch {
            val period = _send.value.period
            val items = collect(period)
            if (items.isEmpty()) return@launch
            val code = ShareCodec.generateCode()
            val link = withContext(Dispatchers.Default) {
                ShareCodec.encode(ShareBatch("", System.currentTimeMillis(), items), code)
            }
            val qr = withContext(Dispatchers.Default) { qr(ShareCodec.qrPayload(link, code)) }
            _send.update { it.copy(count = items.size, link = link, code = code, qr = qr, qrTooBig = qr == null) }
        }
    }

    private suspend fun collect(period: SharePeriod): List<SharedTransaction> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val since = LocalDate.now(zone).minusDays(period.days - 1).atStartOfDay(zone).toInstant()
        val accounts = accountDao.findAll().associate { it.id to (it.currency ?: "") }
        val categories = categoryDao.findAll().associate { it.id to it.name }
        transactionDao.findAll()
            .filter { it.dateTime != null && !it.dateTime!!.isBefore(since) }
            .filter { it.type == TransactionType.INCOME || it.type == TransactionType.EXPENSE }
            .map {
                SharedTransaction(
                    title = it.title.orEmpty(),
                    amount = it.amount,
                    currency = accounts[it.accountId].orEmpty(),
                    isIncome = it.type == TransactionType.INCOME,
                    categoryName = categories[it.categoryId].orEmpty(),
                    dateTime = it.dateTime!!.toEpochMilli(),
                )
            }
    }

    private fun qr(content: String): Bitmap? = try {
        val matrix = QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, 720, 720,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                EncodeHintType.MARGIN to 1
            )
        )
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix.get(i % matrix.width, i / matrix.width)) Color.BLACK else Color.WHITE
        }
        Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) {
        null
    }

    // ---- receive ----

    fun onLinkChange(value: String) = _receive.update { it.copy(link = value, error = null, preview = null) }

    fun onCodeChange(value: String) = _receive.update { it.copy(code = value, error = null, preview = null) }

    /** A scanned QR carries both the link and the code. */
    fun onScanned(text: String) {
        val parsed = ShareCodec.parseQrPayload(text)
        if (parsed == null) {
            _receive.update { it.copy(error = "This is not a HeyMoney code.") }
            return
        }
        _receive.update { it.copy(code = parsed.first, link = parsed.second) }
        preview()
    }

    fun preview() {
        val state = _receive.value
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { ShareCodec.decode(state.link, state.code) }
            }
            result.onSuccess { batch ->
                val existing = withContext(Dispatchers.IO) { transactionDao.findAll().map { it.id }.toSet() }
                val fresh = batch.items.count { idOf(it) !in existing }
                _receive.update { it.copy(preview = batch, newCount = fresh, error = null, imported = null) }
            }.onFailure { e ->
                val message = when ((e as? ShareException)?.reason) {
                    ShareException.Reason.BAD_CODE -> "Wrong code, or the data was altered."
                    ShareException.Reason.UNSUPPORTED -> "This link comes from a newer version of the app."
                    ShareException.Reason.EMPTY -> "Nothing to import in this link."
                    else -> "This does not look like a HeyMoney link."
                }
                _receive.update { it.copy(error = message, preview = null) }
            }
        }
    }

    fun import() {
        val batch = _receive.value.preview ?: return
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { save(batch) }
            _receive.value = ReceiveState(imported = count)
        }
    }

    private suspend fun save(batch: ShareBatch): Int {
        val accounts = accountDao.findAll()
        if (accounts.isEmpty()) return 0
        val categories = categoryDao.findAll()
        val existing = transactionDao.findAll().map { it.id }.toSet()
        val entities = batch.items.mapNotNull { item ->
            val id = idOf(item)
            if (id in existing) return@mapNotNull null
            val account = accounts.firstOrNull { item.currency.isNotBlank() && it.currency == item.currency }
                ?: accounts.minByOrNull { it.orderNum }!!
            TransactionEntity(
                accountId = account.id,
                type = if (item.isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                amount = item.amount,
                title = item.title.ifBlank { null },
                description = "Shared with me",
                dateTime = Instant.ofEpochMilli(item.dateTime),
                categoryId = categories.firstOrNull {
                    item.categoryName.isNotBlank() && it.name.equals(item.categoryName, ignoreCase = true)
                }?.id,
                id = id
            )
        }
        writeTransactionDao.saveMany(entities)
        return entities.size
    }

    // same content on both phones gives the same id, so re-importing never duplicates
    private fun idOf(item: SharedTransaction): UUID = UUID.nameUUIDFromBytes(
        "${item.dateTime}|${item.amount}|${item.currency}|${item.isIncome}|${item.title.lowercase(Locale.ROOT)}"
            .toByteArray(StandardCharsets.UTF_8)
    )
}
