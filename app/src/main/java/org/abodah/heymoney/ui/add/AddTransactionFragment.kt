package org.abodah.heymoney.ui.add

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import org.abodah.heymoney.R
import org.abodah.heymoney.databinding.FragmentAddTransactionBinding
import org.abodah.heymoney.domain.model.Transaction
import org.abodah.heymoney.ui.base.BaseFragment
import org.abodah.heymoney.ui.main.viewmodel.TransactionViewModel
import org.abodah.heymoney.utils.Constants
import org.abodah.heymoney.utils.parseDouble
import org.abodah.heymoney.utils.snack
import org.abodah.heymoney.utils.transformIntoDatePicker
import java.text.DateFormat
import java.time.DateTimeException
import java.util.Date

@AndroidEntryPoint
class AddTransactionFragment :
    BaseFragment<FragmentAddTransactionBinding, TransactionViewModel>() {
    override val viewModel: TransactionViewModel by activityViewModels()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    private fun initViews() {

        val transactionTypeAdapter =
            ArrayAdapter(
                requireContext(),
                R.layout.item_autocomplete_layout,
                Constants.transactionType
            )
        val tagsAdapter = ArrayAdapter(
            requireContext(),
            R.layout.item_autocomplete_layout,
            Constants.transactionTags
        )

        with(binding) {
            // Set list to TextInputEditText adapter
            addTransactionLayout.etTransactionType.setAdapter(transactionTypeAdapter)
            addTransactionLayout.etTag.setAdapter(tagsAdapter)

            // Transform TextInputEditText to DatePicker using Ext function
            addTransactionLayout.etWhen.transformIntoDatePicker(
                requireContext(),
                "dd/MM/yyyy",
                Date()
            )

            // Set current date as default for new transactions.
            addTransactionLayout.etWhen.setText(DateFormat.getDateInstance().format(Date()))

            btnSaveTransaction.setOnClickListener {
                binding.addTransactionLayout.apply {
                    val (title, amount, transactionType, tag, date, note) = getTransactionContent()
                    // validate if transaction content is empty or not
                    when {
                        title.isEmpty() -> {
                            this.etTitle.error = "Title must not be empty"
                        }
                        amount.isNaN() -> {
                            this.etAmount.error = "Amount must not be empty"
                        }
                        transactionType.isEmpty() -> {
                            this.etTransactionType.error = "Transaction type must not be empty"
                        }
                        tag.isEmpty() -> {
                            this.etTag.error = "Tag must not be empty"
                        }
                        note.isEmpty() -> {
                            this.etNote.error = "Note must not be empty"
                        }
                        else -> {
                            viewModel.insertTransaction(getTransactionContent()).run {
                                binding.root.snack(
                                    string = R.string.success_expense_saved
                                )
                                findNavController().navigate(
                                    R.id.action_addTransactionFragment_to_dashboardFragment
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getTransactionContent(): Transaction = binding.addTransactionLayout.let {
        val title = it.etTitle.text.toString()
        val amount = parseDouble(it.etAmount.text.toString())
        val transactionType = it.etTransactionType.text.toString()
        val tag = it.etTag.text.toString()
        val date = DateFormat.getDateInstance().parse(it.etWhen.text.toString()).time
        val note = it.etNote.text.toString()

        return Transaction(title, amount, transactionType, tag, date, note)
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = FragmentAddTransactionBinding.inflate(inflater, container, false)
}