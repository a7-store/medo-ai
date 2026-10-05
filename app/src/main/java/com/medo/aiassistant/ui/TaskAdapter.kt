package com.medo.aiassistant.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.medo.aiassistant.R
import com.medo.aiassistant.data.TaskEntity

class TaskAdapter(
    private val onTaskToggle: (TaskEntity, Boolean) -> Unit,
    private val onTaskDelete: (TaskEntity) -> Unit
) : ListAdapter<TaskEntity, TaskAdapter.TaskViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardTask: MaterialCardView = itemView.findViewById(R.id.cardTask)
        private val cbDone: CheckBox = itemView.findViewById(R.id.cbDone)
        private val tvRequester: TextView = itemView.findViewById(R.id.tvRequester)
        private val tvTaskDescription: TextView = itemView.findViewById(R.id.tvTaskDescription)
        private val tvDeadline: TextView = itemView.findViewById(R.id.tvDeadline)
        private val tvPriorityBadge: TextView = itemView.findViewById(R.id.tvPriorityBadge)
        private val btnDeleteTask: ImageButton = itemView.findViewById(R.id.btnDeleteTask)

        fun bind(task: TaskEntity) {
            tvRequester.text = "من: ${task.requester}"
            tvTaskDescription.text = task.taskDescription

            // Urgency badge
            if (task.isUrgent) {
                tvPriorityBadge.visibility = View.VISIBLE
                tvPriorityBadge.text = "عاجل ⚠️"
            } else {
                tvPriorityBadge.visibility = View.GONE
            }

            // Deadline
            if (!task.deadline.isNullOrBlank()) {
                tvDeadline.visibility = View.VISIBLE
                tvDeadline.text = "⏰ الموعد: ${task.deadline}"
            } else {
                tvDeadline.visibility = View.GONE
            }

            // Checkbox state & Strike-through
            cbDone.setOnCheckedChangeListener(null)
            cbDone.isChecked = task.isCompleted

            applyCompletedStyle(task.isCompleted)

            cbDone.setOnCheckedChangeListener { _, isChecked ->
                applyCompletedStyle(isChecked)
                onTaskToggle(task, isChecked)
            }

            btnDeleteTask.setOnClickListener {
                onTaskDelete(task)
            }
        }

        private fun applyCompletedStyle(isCompleted: Boolean) {
            if (isCompleted) {
                tvTaskDescription.paintFlags = tvTaskDescription.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                tvTaskDescription.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                cardTask.alpha = 0.6f
            } else {
                tvTaskDescription.paintFlags = tvTaskDescription.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                tvTaskDescription.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                cardTask.alpha = 1.0f
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<TaskEntity>() {
        override fun areItemsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean {
            return oldItem == newItem
        }
    }
}
