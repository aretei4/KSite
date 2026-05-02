package com.khaga.mobile.ksite.ui.collect

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.khaga.mobile.ksite.R
import com.khaga.mobile.ksite.data.model.SiteCollection
import com.khaga.mobile.ksite.databinding.FragmentListBinding
import com.khaga.mobile.ksite.util.*
import com.khaga.mobile.ksite.viewmodel.MainViewModel
import java.util.UUID

class CollectFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = CollectionAdapter(
        onEdit   = { showDialog(it) },
        onDelete = { vm.deleteCollection(it.id) }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.fab.setOnClickListener { showDialog(null) }
        binding.tvTitle.text = "Collect Payments"

        vm.collections.observe(viewLifecycleOwner) { list: List<SiteCollection> ->
            adapter.submitList(list)
            val totalRec = list.sumOf { it.received }
            val totalDue = list.sumOf { it.amount }
            val pct = if (totalDue > 0) (totalRec * 100 / totalDue) else 0
            binding.tvSummary.text =
                "Collected: ${Fmt.money(totalRec)}  |  Pending: ${Fmt.money(totalDue - totalRec)}  ($pct%)"
        }
    }

    private fun showDialog(existing: SiteCollection?) {
        val sites = vm.sites.value ?: emptyList()
        if (sites.isEmpty()) {
            Toast.makeText(requireContext(), "Add sites first in Settings", Toast.LENGTH_SHORT).show()
            return
        }
        val v          = layoutInflater.inflate(R.layout.dialog_collection, null)
        val spSite     = v.findViewById<Spinner>(R.id.sp_site)
        val spMode     = v.findViewById<Spinner>(R.id.sp_mode)
        val etFrom     = v.findViewById<EditText>(R.id.et_from)
        val etAmount   = v.findViewById<EditText>(R.id.et_amount)
        val etReceived = v.findViewById<EditText>(R.id.et_received)
        val etDate     = v.findViewById<EditText>(R.id.et_date)

        spSite.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, sites.map { it.name }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spMode.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, PAY_MODES
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        if (existing != null) {
            spSite.setSelection(sites.indexOfFirst { it.id == existing.siteId }.coerceAtLeast(0))
            spMode.setSelection(PAY_MODES.indexOf(existing.mode).coerceAtLeast(0))
            etFrom.setText(existing.from)
            etAmount.setText(existing.amount.toString())
            etReceived.setText(existing.received.toString())
            etDate.setText(existing.date)
        } else {
            etDate.setText(Fmt.todayIso())
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (existing != null) "Edit Collection" else "Record Collection")
            .setView(v)
            .setPositiveButton("Save") { _, _ ->
                val amt = etAmount.text.toString().toLongOrNull() ?: return@setPositiveButton
                val rec = etReceived.text.toString().toLongOrNull() ?: 0L
                val status = when { rec >= amt -> "paid"; rec > 0 -> "partial"; else -> "pending" }
                vm.upsertCollection(
                    SiteCollection(
                        id       = existing?.id ?: UUID.randomUUID().toString(),
                        siteId   = sites[spSite.selectedItemPosition].id,
                        from     = etFrom.text.toString(),
                        amount   = amt,
                        received = rec,
                        mode     = PAY_MODES[spMode.selectedItemPosition],
                        date     = etDate.text.toString().ifBlank { Fmt.todayIso() },
                        status   = status
                    )
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// Explicit import of androidx ListAdapter avoids clash with android.widget.ListAdapter
class CollectionAdapter(
    private val onEdit: (SiteCollection) -> Unit,
    private val onDelete: (SiteCollection) -> Unit
) : ListAdapter<SiteCollection, CollectionAdapter.VH>(
    object : DiffUtil.ItemCallback<SiteCollection>() {
        override fun areItemsTheSame(a: SiteCollection, b: SiteCollection) = a.id == b.id
        override fun areContentsTheSame(a: SiteCollection, b: SiteCollection) = a == b
    }
) {
    inner class VH(val v: View) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_collection, parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = getItem(pos)
        h.v.findViewById<TextView>(R.id.tv_name).text   = c.from
        h.v.findViewById<TextView>(R.id.tv_sub).text    = "${Fmt.date(c.date)} · ${c.mode}"
        h.v.findViewById<TextView>(R.id.tv_amount).text =
            "${Fmt.money(c.received)} / ${Fmt.money(c.amount)}"
        val chip = h.v.findViewById<TextView>(R.id.tv_status)
        chip.text = when (c.status) { "paid" -> "Paid"; "partial" -> "Partial"; else -> "Pending" }
        chip.setBackgroundColor(
            when (c.status) {
                "paid"    -> 0xFF15803D.toInt()
                "partial" -> 0xFFB45309.toInt()
                else      -> 0xFFB91C1C.toInt()
            }
        )
        h.v.findViewById<View>(R.id.btn_edit).setOnClickListener { onEdit(c) }
        h.v.findViewById<View>(R.id.btn_delete).setOnClickListener { onDelete(c) }
    }
}