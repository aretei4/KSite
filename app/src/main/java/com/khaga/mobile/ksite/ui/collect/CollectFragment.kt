package com.khaga.mobile.ksite.ui.collect

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
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
import com.khaga.mobile.ksite.databinding.FragmentCollectBinding
import com.khaga.mobile.ksite.util.*
import com.khaga.mobile.ksite.viewmodel.MainViewModel
import java.util.Calendar
import java.util.UUID

class CollectFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()
    private var _binding: FragmentCollectBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CollectionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCollectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = CollectionAdapter(
            onEdit      = { showDialog(it) },
            onDelete    = { vm.deleteCollection(it.id) },
            getSiteName = { id -> vm.sites.value?.firstOrNull { it.id == id }?.name ?: "" }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.btnRecord.setOnClickListener { showDialog(null) }

        vm.collections.observe(viewLifecycleOwner) { list ->
            binding.layoutLoading.visibility = View.GONE
            binding.recycler.visibility      = View.VISIBLE
            adapter.submitList(list)
            val totalRec = list.sumOf { it.received }
            val totalDue = list.sumOf { it.amount - it.received }.coerceAtLeast(0)
            binding.tvTotalCollected.text = Fmt.money(totalRec)
            binding.tvTotalPending.text   = Fmt.money(totalDue)
        }

        // Rebind when site names arrive
        vm.sites.observe(viewLifecycleOwner) {
            adapter.submitList(vm.collections.value ?: emptyList())
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

        spSite.adapter = ArrayAdapter(requireContext(), R.layout.item_spinner, sites.map { it.name })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spMode.adapter = ArrayAdapter(requireContext(), R.layout.item_spinner, PAY_MODES)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Date picker
        etDate.setOnClickListener {
            val parts = etDate.text.toString().split("-").map { it.toIntOrNull() ?: 0 }
            val cal = Calendar.getInstance()
            if (parts.size == 3 && parts[0] > 0) cal.set(parts[0], parts[1] - 1, parts[2])
            DatePickerDialog(requireContext(), { _, y, m, d ->
                etDate.setText("%04d-%02d-%02d".format(y, m + 1, d))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

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

        val dialog = AlertDialog.Builder(requireContext()).setView(v).create()

        v.findViewById<View>(R.id.tv_dialog_close).setOnClickListener { dialog.dismiss() }

        v.findViewById<View>(R.id.btn_save_collection).setOnClickListener {
            val amt = etAmount.text.toString().toLongOrNull() ?: return@setOnClickListener
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
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class CollectionAdapter(
    private val onEdit: (SiteCollection) -> Unit,
    private val onDelete: (SiteCollection) -> Unit,
    private val getSiteName: (String) -> String
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

        h.v.findViewById<TextView>(R.id.tv_name).text = c.from.ifBlank { getSiteName(c.siteId) }

        // Status badge
        val tvStatus = h.v.findViewById<TextView>(R.id.tv_status)
        val due = c.amount - c.received
        when (c.status) {
            "paid" -> {
                tvStatus.text = "Paid"
                tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
                tvStatus.setTextColor(Color.parseColor("#15803D"))
            }
            "partial" -> {
                tvStatus.text = "Due ${Fmt.money(due)}"
                tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                tvStatus.setTextColor(Color.parseColor("#92400E"))
            }
            else -> {
                tvStatus.text = "Due ${Fmt.money(due)}"
                tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                tvStatus.setTextColor(Color.parseColor("#B91C1C"))
            }
        }

        // site · date
        val siteName = getSiteName(c.siteId)
        h.v.findViewById<TextView>(R.id.tv_site_date).text =
            if (siteName.isNotBlank()) "$siteName · ${Fmt.date(c.date)}" else Fmt.date(c.date)

        // Mode chip
        val tvMode = h.v.findViewById<TextView>(R.id.tv_mode_chip)
        tvMode.text = c.mode
        tvMode.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F3F4F6"))

        // Billed / Got
        h.v.findViewById<TextView>(R.id.tv_billed_got).text =
            "Billed ${Fmt.money(c.amount)}  Got ${Fmt.money(c.received)}"

        // Progress bar
        val pct = if (c.amount > 0) (c.received * 100 / c.amount).toInt().coerceIn(0, 100) else 0
        val pb = h.v.findViewById<android.widget.ProgressBar>(R.id.progress_bar)
        pb.progress = pct
        val pbColor = when (c.status) {
            "paid"    -> Color.parseColor("#15803D")
            "partial" -> Color.parseColor("#D97706")
            else      -> Color.parseColor("#B91C1C")
        }
        pb.progressTintList = ColorStateList.valueOf(pbColor)

        h.v.findViewById<View>(R.id.tv_edit).setOnClickListener { onEdit(c) }
        h.v.findViewById<View>(R.id.tv_del).setOnClickListener  { onDelete(c) }
    }
}
