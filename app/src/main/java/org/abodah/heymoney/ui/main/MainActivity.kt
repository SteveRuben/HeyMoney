package org.abodah.heymoney.ui.main

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import org.abodah.heymoney.R
import org.abodah.heymoney.databinding.ActivityMainBinding
import org.abodah.heymoney.domain.local.UIModeImpl
import org.abodah.heymoney.domain.repo.TransactionRepo
import org.abodah.heymoney.services.exportcsv.ExportCsvService
import org.abodah.heymoney.ui.main.viewmodel.TransactionViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var appBarConfiguration: AppBarConfiguration

    @Inject
    lateinit var repo: TransactionRepo
    @Inject
    lateinit var exportCsvService: ExportCsvService
    @Inject
    lateinit var themeManager: UIModeImpl
    private val viewModel: TransactionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /**
         * Just so the viewModel doesn't get removed by the compiler, as it isn't used
         * anywhere here for now
         */
        viewModel

        initViews(binding)
        observeThemeMode()
        observeNavElements(binding, navHostFragment.navController)
    }

    private fun observeThemeMode() {
        lifecycleScope.launchWhenStarted {
            viewModel.getUIMode.collect {
                val mode = when (it) {
                    true -> AppCompatDelegate.MODE_NIGHT_YES
                    false -> AppCompatDelegate.MODE_NIGHT_NO
                }
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
    }

    private fun observeNavElements(
        binding: ActivityMainBinding,
        navController: NavController
    ) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {

                R.id.dashboardFragment -> {
                    supportActionBar!!.setDisplayShowTitleEnabled(false)
                }
                R.id.addTransactionFragment -> {
                    supportActionBar!!.setDisplayShowTitleEnabled(true)
                    binding.toolbar.title = getString(R.string.text_add_transaction)
                }
                else -> {
                    supportActionBar!!.setDisplayShowTitleEnabled(true)
                }
            }
        }
    }

    private fun initViews(binding: ActivityMainBinding) {
        setSupportActionBar(binding.toolbar)
        supportActionBar!!.setDisplayShowTitleEnabled(false)

        navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment?
            ?: return

        with(navHostFragment.navController) {
            appBarConfiguration = AppBarConfiguration(graph)
            setupActionBarWithNavController(this, appBarConfiguration)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        navHostFragment.navController.navigateUp()
        return super.onSupportNavigateUp()
    }
}