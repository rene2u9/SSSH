package r2u9.SimpleSSH.ui.adapter

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import r2u9.SimpleSSH.R
import r2u9.SimpleSSH.data.model.SshConnection
import r2u9.SimpleSSH.databinding.ItemConnectionBinding

enum class HostStatus {
    UNKNOWN, ONLINE, OFFLINE
}

class ConnectionAdapter(
    private val onConnect: (SshConnection) -> Unit,
    private val onEdit: (SshConnection) -> Unit,
    private val onDelete: (SshConnection) -> Unit,
    private val onSettings: () -> Unit
) : ListAdapter<SshConnection, ConnectionAdapter.ViewHolder>(DiffCallback()) {

    private val hostStatusMap = mutableMapOf<Long, HostStatus>()

    fun updateHostStatus(connectionId: Long, status: HostStatus) {
        hostStatusMap[connectionId] = status
        val position = currentList.indexOfFirst { it.id == connectionId }
        if (position >= 0) {
            notifyItemChanged(position, status)
        }
    }

    fun resetAllStatus() {
        hostStatusMap.clear()
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConnectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads[0] is HostStatus) {
            holder.updateStatus(payloads[0] as HostStatus)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ViewHolder(
        private val binding: ItemConnectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(connection: SshConnection) {
            binding.connectionName.text = connection.name
            binding.connectionDetails.text = "${connection.username}@${connection.host}:${connection.port}"

            val status = hostStatusMap[connection.id] ?: HostStatus.UNKNOWN
            updateStatus(status)

            connection.lastConnectedAt?.let { timestamp ->
                binding.lastConnected.visibility = android.view.View.VISIBLE
                val relativeTime = DateUtils.getRelativeTimeSpanString(
                    timestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
                binding.lastConnected.text = "Last connected: $relativeTime"
            } ?: run {
                binding.lastConnected.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener {
                onConnect(connection)
            }

            binding.moreButton.setOnClickListener { view ->
                val options = arrayOf("Settings", "Connect", "Edit", "Delete")
                MaterialAlertDialogBuilder(view.context)
                    .setTitle(connection.name)
                    .setSingleChoiceItems(options, -1) { dialog, which ->
                        dialog.dismiss()
                        when (which) {
                            0 -> onSettings()
                            1 -> onConnect(connection)
                            2 -> onEdit(connection)
                            3 -> onDelete(connection)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        fun updateStatus(status: HostStatus) {
            val drawableRes = when (status) {
                HostStatus.UNKNOWN -> R.drawable.bg_status_dot_unknown
                HostStatus.ONLINE -> R.drawable.bg_status_dot_online
                HostStatus.OFFLINE -> R.drawable.bg_status_dot_offline
            }
            binding.statusDot.setBackgroundResource(drawableRes)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SshConnection>() {
        override fun areItemsTheSame(oldItem: SshConnection, newItem: SshConnection): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SshConnection, newItem: SshConnection): Boolean {
            return oldItem == newItem
        }
    }
}
