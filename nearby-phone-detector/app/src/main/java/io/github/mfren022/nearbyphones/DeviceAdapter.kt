package io.github.mfren022.nearbyphones

import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class DeviceAdapter : ListAdapter<NearbyDevice, DeviceAdapter.Holder>(Diff) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.deviceName)
        val distance: TextView = view.findViewById(R.id.deviceDistance)
        val kind: TextView = view.findViewById(R.id.deviceKind)
        val details: TextView = view.findViewById(R.id.deviceDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val device = getItem(position)
        val context = holder.itemView.context

        holder.name.text = device.name ?: context.getString(R.string.unknown_device)
        holder.distance.text = formatDistance(device.estimatedDistanceMeters)
        holder.kind.text = device.classification.label
        holder.kind.setTextColor(
            ContextCompat.getColor(
                context,
                when (device.classification.confidence) {
                    Confidence.CONFIRMED, Confidence.LIKELY -> R.color.phone_high
                    Confidence.POSSIBLE -> R.color.phone_medium
                    else -> R.color.phone_low
                },
            ),
        )

        val ago = (SystemClock.elapsedRealtime() - device.lastSeenMillis) / 1000
        holder.details.text = listOf(
            device.sources.joinToString("+") { it.label },
            "${device.rssi} dBm",
            device.address,
            if (ago < 2) "just now" else "${ago}s ago",
        ).joinToString(" · ")
    }

    private fun formatDistance(meters: Double): String = when {
        meters < 1 -> "< 1 m"
        meters < 10 -> String.format(Locale.getDefault(), "~%.1f m", meters)
        else -> String.format(Locale.getDefault(), "~%.0f m", meters)
    }

    private object Diff : DiffUtil.ItemCallback<NearbyDevice>() {
        override fun areItemsTheSame(oldItem: NearbyDevice, newItem: NearbyDevice) = oldItem.address == newItem.address
        override fun areContentsTheSame(oldItem: NearbyDevice, newItem: NearbyDevice) = oldItem == newItem
    }
}
