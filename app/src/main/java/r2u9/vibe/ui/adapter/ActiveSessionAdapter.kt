package r2u9.vibe.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import r2u9.vibe.data.model.ActiveSession
import r2u9.vibe.databinding.ItemActiveSessionBinding

class ActiveSessionAdapter(
    private val onOpen: (ActiveSession) -> Unit,
    private val onDisconnect: (ActiveSession) -> Unit
) : ListAdapter<ActiveSession, ActiveSessionAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemActiveSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemActiveSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: ActiveSession) {
            binding.sessionName.text = session.connection.name
            binding.sessionDuration.text = session.getFormattedDuration()

            binding.root.setOnClickListener {
                onOpen(session)
            }

            binding.disconnectButton.setOnClickListener {
                onDisconnect(session)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ActiveSession>() {
        override fun areItemsTheSame(oldItem: ActiveSession, newItem: ActiveSession): Boolean {
            return oldItem.sessionId == newItem.sessionId
        }

        override fun areContentsTheSame(oldItem: ActiveSession, newItem: ActiveSession): Boolean {
            return oldItem == newItem
        }
    }
}
