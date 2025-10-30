package io.github.remote.konfig.sample

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.remote.konfig.OverrideStore
import io.github.remote.konfig.RemoteConfigProvider
import io.github.remote.konfig.sample.databinding.ActivityMainBinding
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var remoteConfigProvider: RemoteConfigProvider
    @Inject lateinit var overrideStore: OverrideStore

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUi()
    }

    private fun setupUi() {
        val welcomeConfig = remoteConfigProvider.getRemoteConfig(WELCOME_KEY)
        binding.currentValue.text = welcomeConfig ?: getString(R.string.no_remote_value)

        val existingOverride = overrideStore.getOverride(WELCOME_KEY)
        binding.overrideInput.setText(existingOverride.orEmpty())

        binding.applyButton.setOnClickListener {
            val override = binding.overrideInput.text?.toString().orEmpty()
            if (override.isNotBlank()) {
                overrideStore.setOverride(WELCOME_KEY, override)
                binding.status.text = getString(R.string.override_saved)
            } else {
                binding.status.text = getString(R.string.override_empty)
            }
        }

        binding.clearButton.setOnClickListener {
            overrideStore.clearOverride(WELCOME_KEY)
            binding.overrideInput.setText("")
            binding.status.text = getString(R.string.override_cleared)
        }

        binding.openMenuButton.setOnClickListener {
            startActivity(Intent(this, ConfigEditorMenuActivity::class.java))
        }
    }

    companion object {
        private const val WELCOME_KEY = "welcome"
    }
}
