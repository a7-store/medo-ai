package com.medo.aiassistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medo.aiassistant.MedoApplication
import com.medo.aiassistant.R
import com.medo.aiassistant.ai.GeminiAgent
import com.medo.aiassistant.data.TaskEntity
import com.medo.aiassistant.databinding.ActivityMainBinding
import com.medo.aiassistant.service.TaskAlertManager
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val geminiAgent = GeminiAgent()
    private val notificationAdapter = NotificationAdapter()
    private lateinit var taskAdapter: TaskAdapter

    private val requestPostNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAdapters()
        setupBottomNavigation()
        setupListeners()
        observeData()
        checkPermissions()

        // Handle intent if opened from alert notification
        if (intent?.getStringExtra("TARGET_TAB") == "TASKS") {
            switchToTab(R.id.nav_tasks)
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun setupAdapters() {
        // History RecyclerView
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = notificationAdapter
        }

        // Tasks RecyclerView
        taskAdapter = TaskAdapter(
            onTaskToggle = { task, isChecked ->
                lifecycleScope.launch {
                    val app = application as MedoApplication
                    task.isCompleted = isChecked
                    app.database.taskDao().update(task)
                }
            },
            onTaskDelete = { task ->
                lifecycleScope.launch {
                    val app = application as MedoApplication
                    app.database.taskDao().delete(task)
                }
            }
        )
        binding.rvTasks.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = taskAdapter
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            switchToTab(item.itemId)
            true
        }
    }

    private fun switchToTab(itemId: Int) {
        binding.viewSummary.visibility = if (itemId == R.id.nav_summary) View.VISIBLE else View.GONE
        binding.viewTasks.visibility = if (itemId == R.id.nav_tasks) View.VISIBLE else View.GONE
        binding.viewHistory.visibility = if (itemId == R.id.nav_history) View.VISIBLE else View.GONE
        binding.viewSettings.visibility = if (itemId == R.id.nav_settings) View.VISIBLE else View.GONE
        binding.bottomNavigation.selectedItemId = itemId
    }

    private fun setupListeners() {
        val app = application as MedoApplication

        // Permission Grant Button
        binding.btnGrantPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        // Summarize Button
        binding.btnSummarizeNow.setOnClickListener {
            summarizeMessages()
        }

        // Extract Tasks Button
        binding.btnExtractTasksNow.setOnClickListener {
            extractTasksFromMessages()
        }

        // Save API Key Button
        binding.etApiKey.setText(app.prefsManager.apiKey)
        binding.btnSaveApiKey.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim() ?: ""
            app.prefsManager.apiKey = key
            Toast.makeText(this, getString(R.string.api_key_saved), Toast.LENGTH_SHORT).show()
        }

        // Clear History
        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("تأكيد المسح")
                .setMessage("هل تريد حقاً مسح جميع الرسائل والإشعارات المخزنة محلياً؟")
                .setPositiveButton("نعم") { _, _ ->
                    lifecycleScope.launch {
                        app.database.notificationDao().clearAll()
                        Toast.makeText(this@MainActivity, "تم مسح السجل", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        // Restore last summary if available
        if (app.prefsManager.lastSummaryText.isNotBlank()) {
            binding.tvSummaryResult.text = app.prefsManager.lastSummaryText
        }
    }

    private fun observeData() {
        val app = application as MedoApplication

        // Observe notification history
        app.database.notificationDao().getAllLive().observe(this) { list ->
            notificationAdapter.submitList(list)
            binding.tvHistoryCount.text = "سجل الرسائل المستلمة (${list.size})"
        }

        // Observe tasks
        app.database.taskDao().getAllLive().observe(this) { tasks ->
            taskAdapter.submitList(tasks)
            binding.tvEmptyTasks.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
            binding.rvTasks.visibility = if (tasks.isEmpty()) View.GONE else View.VISIBLE
        }

        // Observe pending task count
        app.database.taskDao().getPendingCountLive().observe(this) { pendingCount ->
            binding.tvTaskCount.text = "$pendingCount مهام معلقة"
        }
    }

    private fun summarizeMessages() {
        val app = application as MedoApplication
        val apiKey = app.prefsManager.apiKey

        if (apiKey.isBlank()) {
            Toast.makeText(this, getString(R.string.api_key_missing_msg), Toast.LENGTH_LONG).show()
            switchToTab(R.id.nav_settings)
            return
        }

        val sinceTimestamp = calculateFilterTimestamp()

        binding.layoutLoading.visibility = View.VISIBLE
        binding.btnSummarizeNow.isEnabled = false

        lifecycleScope.launch {
            val notifications = app.database.notificationDao().getNotificationsSince(sinceTimestamp)
            val result = geminiAgent.generateSummary(notifications, apiKey)

            binding.layoutLoading.visibility = View.GONE
            binding.btnSummarizeNow.isEnabled = true

            result.onSuccess { summary ->
                binding.tvSummaryResult.text = summary
                app.prefsManager.lastSummaryText = summary
                app.prefsManager.lastSummaryTimestamp = System.currentTimeMillis()
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "حدث خطأ: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun extractTasksFromMessages() {
        val app = application as MedoApplication
        val apiKey = app.prefsManager.apiKey

        if (apiKey.isBlank()) {
            Toast.makeText(this, getString(R.string.api_key_missing_msg), Toast.LENGTH_LONG).show()
            switchToTab(R.id.nav_settings)
            return
        }

        val sinceTimestamp = calculateFilterTimestamp()

        binding.layoutLoading.visibility = View.VISIBLE
        binding.btnExtractTasksNow.isEnabled = false

        lifecycleScope.launch {
            val notifications = app.database.notificationDao().getNotificationsSince(sinceTimestamp)
            val result = geminiAgent.extractTasks(notifications, apiKey)

            binding.layoutLoading.visibility = View.GONE
            binding.btnExtractTasksNow.isEnabled = true

            result.onSuccess { newTasks ->
                if (newTasks.isNotEmpty()) {
                    app.database.taskDao().insertAll(newTasks)
                    // Send phone notification alert
                    TaskAlertManager.sendTaskAlertNotification(
                        this@MainActivity,
                        newTasks.size,
                        newTasks.first().taskDescription
                    )
                    Toast.makeText(this@MainActivity, "تم اكتشاف ${newTasks.size} مهام جديدة بنجاح!", Toast.LENGTH_SHORT).show()
                    // Switch to tasks tab to show them to the user
                    switchToTab(R.id.nav_tasks)
                } else {
                    Toast.makeText(this@MainActivity, "لم يتم العثور على طلبات أو مهام في الرسائل المحددة.", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "حدث خطأ: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun calculateFilterTimestamp(): Long {
        val now = System.currentTimeMillis()
        return when {
            binding.chip6Hours.isChecked -> now - (6 * 3600 * 1000L)
            binding.chip24Hours.isChecked -> now - (24 * 3600 * 1000L)
            else -> {
                // Today since 00:00 AM
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                calendar.timeInMillis
            }
        }
    }

    private fun checkPermissions() {
        // 1. Notification Listener Permission
        val isListenerGranted = isNotificationServiceEnabled(this)
        binding.bannerPermission.visibility = if (isListenerGranted) View.GONE else View.VISIBLE

        // 2. Android 13+ Notification Post Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPostNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(pkgName)
    }
}
