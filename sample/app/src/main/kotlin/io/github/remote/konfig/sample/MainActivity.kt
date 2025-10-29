package io.github.remote.konfig.sample

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.remote.konfig.OverrideStore
import io.github.remote.konfig.sample.databinding.ActivityMainBinding
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var welcomeCfg: WelcomeCfg?

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateDisplayedConfig()
        binding.overrideInput.setText(OverrideStore.getOverride(WELCOME_KEY) ?: "")
        binding.status.setText(R.string.status_idle)

        binding.applyButton.setOnClickListener {
            val overrideText = binding.overrideInput.text?.toString()?.trim().orEmpty()
            if (overrideText.isEmpty()) {
                binding.status.setText(R.string.override_empty)
                return@setOnClickListener
            }
            OverrideStore.setOverride(WELCOME_KEY, overrideText)
            OverrideStore.persistToPreferences(preferences())
            binding.status.setText(R.string.override_saved)
        }

        binding.clearButton.setOnClickListener {
            OverrideStore.clearOverride(WELCOME_KEY)
            OverrideStore.persistToPreferences(preferences())
            binding.overrideInput.text?.clear()
            binding.status.setText(R.string.override_cleared)
        }
    }

    private fun updateDisplayedConfig() {
        binding.currentValue.text = welcomeCfg?.text ?: getString(R.string.no_remote_value)
    }

    private fun preferences() = getSharedPreferences(RemoteKonfigSampleApp.PREFS_NAME, MODE_PRIVATE)

    companion object {
        private const val WELCOME_KEY = "welcome"
    }
}
