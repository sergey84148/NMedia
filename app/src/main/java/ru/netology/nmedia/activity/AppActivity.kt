package ru.netology.nmedia.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_INDEFINITE
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.messaging.FirebaseMessaging
import ru.netology.nmedia.R
import ru.netology.nmedia.auth.AppAuth
import ru.netology.nmedia.databinding.ActivityAppBinding
import ru.netology.nmedia.viewmodel.AuthViewModel

class AppActivity : AppCompatActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val binding = ActivityAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка навигации
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        navController = navHostFragment.navController

        // Обработка системных вставок (insets)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        requestNotificationsPermission()

        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
                // 👇 Используем authViewModel для определения состояния
                menu.let {
                    it.setGroupVisible(R.id.unauthenticated, !authViewModel.authenticated.value!!)
                    it.setGroupVisible(R.id.authenticated, authViewModel.authenticated.value!!)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
                R.id.signin -> {
                    if (navController.currentDestination?.id == R.id.registerFragment) {
                        navController.popBackStack(R.id.feedFragment, false)
                    }
                    navController.navigate(R.id.action_feedFragment_to_loginFragment)
                    true
                }
                R.id.signup -> {
                    if (navController.currentDestination?.id == R.id.loginFragment) {
                        navController.popBackStack(R.id.feedFragment, false)
                    }
                    navController.navigate(R.id.action_feedFragment_to_registerFragment)
                    true
                }
                R.id.signout -> {
                    authViewModel.logout()
                    Toast.makeText(this@AppActivity, "Выход выполнен", Toast.LENGTH_SHORT).show()
                    navController.popBackStack(R.id.feedFragment, false)
                    true
                }
                else -> false
            }
        })

        // 👇 Наблюдаем за изменением состояния авторизации для обновления меню
        authViewModel.authenticated.observe(this) {
            invalidateOptionsMenu()
        }

        // Обработка интента
        intent?.let {
            if (it.action != Intent.ACTION_SEND) {
                return@let
            }

            val text = it.getStringExtra(Intent.EXTRA_TEXT)
            if (text.isNullOrBlank()) {
                Snackbar.make(binding.root, R.string.error_empty_content, LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok) { finish() }
                    .show()
                return@let
            }

            intent.removeExtra(Intent.EXTRA_TEXT)

            navController.navigate(
                R.id.action_feedFragment_to_newPostFragment,
                Bundle().apply {
                    putString("textArg", text)
                }
            )
        }

        checkGoogleApiAvailability()
    }

    private fun requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) return

        requestPermissions(arrayOf(permission), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.notification_permission_granted, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkGoogleApiAvailability() {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val code = googleApiAvailability.isGooglePlayServicesAvailable(this)

        if (code == ConnectionResult.SUCCESS) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                println("FCM Token: $token")
            }
            return
        }

        if (googleApiAvailability.isUserResolvableError(code)) {
            googleApiAvailability.getErrorDialog(this, code, 9000)?.show()
            return
        }

        Toast.makeText(this, R.string.google_play_unavailable, Toast.LENGTH_LONG).show()
    }
}