package com.moifinansy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moifinansy.R
import com.moifinansy.core.format.RateFormat
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.currencyLabel
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.model.CurrencyRate
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.CurrencyRateRepository
import com.moifinansy.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RateRow(val code: String, val micros: Long?)
data class RatesState(val loading: Boolean = true, val base: String = Currencies.DEFAULT, val rows: List<RateRow> = emptyList())

@HiltViewModel
class RatesViewModel @Inject constructor(
    private val rates: CurrencyRateRepository,
    accounts: AccountRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<RatesState> = combine(settings.baseCurrency, accounts.observeUsedCurrencies(), rates.observeAll()) { base, used, all ->
        val b = base ?: Currencies.DEFAULT
        val known = all.associate { it.code to it.rateToBaseMicros }
        RatesState(false, b, (used + all.map { it.code }).distinct().filter { it != b }.sorted().map { RateRow(it, known[it]) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RatesState())

    fun save(code: String, micros: Long) {
        viewModelScope.launch { rates.save(CurrencyRate(code, micros)) }
    }
}

@Composable
fun RatesScreen(onBack: () -> Unit, viewModel: RatesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackTopBar(stringResource(R.string.rates_title), onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
            verticalArrangement = Arrangement.spacedBy(Spacing.grid),
        ) {
            item { Text(stringResource(R.string.rates_hint, currencyLabel(state.base)), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!state.loading && state.rows.isEmpty()) item { Text(stringResource(R.string.rates_empty)) }
            items(state.rows, key = { it.code }) { row ->
                var text by remember(row.code, row.micros) { mutableStateOf(row.micros?.let(RateFormat::format).orEmpty()) }
                val parsed = RateFormat.parse(text)
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        text = v.filter { it.isDigit() || it == ',' || it == '.' }
                        RateFormat.parse(text)?.let { viewModel.save(row.code, it) }
                    },
                    label = { Text(stringResource(R.string.rates_label, row.code, Currencies.byCode(state.base).symbol)) },
                    isError = text.isNotEmpty() && parsed == null,
                    supportingText = if (text.isNotEmpty() && parsed == null) ({ Text(stringResource(R.string.rates_invalid)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
