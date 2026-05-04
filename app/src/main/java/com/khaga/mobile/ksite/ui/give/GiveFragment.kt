package com.khaga.mobile.ksite.ui.give

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.*
import androidx.recyclerview.widget.ListAdapter
import com.khaga.mobile.ksite.R
import com.khaga.mobile.ksite.data.model.*
import com.khaga.mobile.ksite.databinding.FragmentGiveBinding
import com.khaga.mobile.ksite.util.*
import com.khaga.mobile.ksite.viewmodel.MainViewModel
import java.util.Calendar
import java.util.UUID

class GiveFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()
    private var _binding: FragmentGiveBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: PaymentAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = PaymentAdapter(
            onEdit        = { showDialog(it) },
            onDelete      = { vm.deletePayment(it.id) },
            getWorkerName = { id -> vm.workers.value?.firstOrNull { it.id == id }?.name ?: id },
            getSiteName   = { id -> vm.sites.value?.firstOrNull { it.id == id }?.name ?: id }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.btnRecord.setOnClickListener { showDialog(null) }

        vm.payments.observe(viewLifecycleOwner) { list ->
            // Hide loader, show list on first data arrival
            binding.layoutLoading.visibility = View.GONE
            binding.recycler.visibility      = View.VISIBLE
            adapter.submitList(list)
            binding.tvTotalAmount.text  = Fmt.money(list.sumOf { it.amount })
            binding.tvEntriesCount.text = list.size.toString()
        }
    }

    private fun showDialog(existing: Payment?) {
        val sites   = vm.sites.value   ?: emptyList()
        val workers = vm.workers.value ?: emptyList()
        if (sites.isEmpty() || workers.isEmpty()) {
            Toast.makeText(requireContext(), "Add sites and workers first in Settings", Toast.LENGTH_LONG).show()
            return
        }

        val v        = layoutInflater.inflate(R.layout.dialog_payment, null)
        val spSite   = v.findViewById<Spinner>(R.id.sp_site)
        val spWorker = v.findViewById<Spinner>(R.id.sp_worker)
        val spHead   = v.findViewById<Spinner>(R.id.sp_head)
        val spMode   = v.findViewById<Spinner>(R.id.sp_mode)
        val etAmount = v.findViewById<EditText>(R.id.et_amount)
        val etDate   = v.findViewById<EditText>(R.id.et_date)
        val etNote   = v.findViewById<EditText>(R.id.et_note)
        val tvHint   = v.findViewById<TextView>(R.id.tv_wage_hint)

        spSite.adapter   = ArrayAdapter(requireContext(), R.layout.item_spinner, sites.map { it.name }).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spWorker.adapter = ArrayAdapter(requireContext(), R.layout.item_spinner, workers.map { "${it.name} (${Fmt.money(it.wagePerDay)}/day)" }).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spHead.adapter   = ArrayAdapter(requireContext(), R.layout.item_spinner, PAY_HEADS).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spMode.adapter   = ArrayAdapter(requireContext(), R.layout.item_spinner, PAY_MODES).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Date picker — tap field to choose date
        etDate.setOnClickListener {
            val parts = etDate.text.toString().split("-").map { it.toIntOrNull() ?: 0 }
            val cal = Calendar.getInstance()
            if (parts.size == 3 && parts[0] > 0) cal.set(parts[0], parts[1] - 1, parts[2])
            DatePickerDialog(requireContext(), { _, y, m, d ->
                etDate.setText("%04d-%02d-%02d".format(y, m + 1, d))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        fun updateHint() {
            val w = workers.getOrNull(spWorker.selectedItemPosition)
            if (w != null) {
                tvHint.text = "Rate: ${Fmt.money(w.wagePerDay)}/day"
                tvHint.visibility = View.VISIBLE
            }
        }
        spWorker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = updateHint()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        if (existing != null) {
            val sIdx = sites.indexOfFirst { it.id == existing.siteId }.coerceAtLeast(0)
            val wIdx = workers.indexOfFirst { it.id == existing.workerId }.coerceAtLeast(0)
            spSite.setSelection(sIdx); spWorker.setSelection(wIdx)
            spHead.setSelection(PAY_HEADS.indexOf(existing.head).coerceAtLeast(0))
            spMode.setSelection(PAY_MODES.indexOf(existing.mode).coerceAtLeast(0))
            etAmount.setText(existing.amount.toString())
            etDate.setText(existing.date)
            etNote.setText(existing.note)
        } else {
            etDate.setText(Fmt.todayIso())
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(v)
            .create()

        v.findViewById<View>(R.id.tv_dialog_close).setOnClickListener { dialog.dismiss() }

        v.findViewById<View>(R.id.btn_save_payment).setOnClickListener {
            val amt = etAmount.text.toString().toLongOrNull() ?: return@setOnClickListener
            vm.upsertPayment(Payment(
                id       = existing?.id ?: UUID.randomUUID().toString(),
                workerId = workers[spWorker.selectedItemPosition].id,
                siteId   = sites[spSite.selectedItemPosition].id,
                head     = PAY_HEADS[spHead.selectedItemPosition],
                amount   = amt,
                mode     = PAY_MODES[spMode.selectedItemPosition],
                date     = etDate.text.toString().ifBlank { Fmt.todayIso() },
                note     = etNote.text.toString()
            ))
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class PaymentAdapter(
    private val onEdit: (Payment) -> Unit,
    private val onDelete: (Payment) -> Unit,
    private val getWorkerName: (String) -> String,
    private val getSiteName: (String) -> String
) : ListAdapter<Payment, PaymentAdapter.VH>(object : DiffUtil.ItemCallback<Payment>() {
    override fun areItemsTheSame(a: Payment, b: Payment) = a.id == b.id
    override fun areContentsTheSame(a: Payment, b: Payment) = a == b
}) {
    inner class VH(val v: View) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_payment, parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val p          = getItem(pos)
        val workerName = getWorkerName(p.workerId)

        // Avatar initials (up to 2 chars)
        val initials = workerName.trim().split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
        h.v.findViewById<TextView>(R.id.tv_initials).text = initials

        h.v.findViewById<TextView>(R.id.tv_worker_name).text = workerName

        // Head chip with colour coding
        val tvHead = h.v.findViewById<TextView>(R.id.tv_head_chip)
        tvHead.text = p.head
        val (headBg, headFg) = headChipColors(p.head)
        tvHead.backgroundTintList = ColorStateList.valueOf(Color.parseColor(headBg))
        tvHead.setTextColor(Color.parseColor(headFg))

        // Mode chip (neutral grey)
        val tvMode = h.v.findViewById<TextView>(R.id.tv_mode_chip)
        tvMode.text = p.mode
        tvMode.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F3F4F6"))
        tvMode.setTextColor(Color.parseColor("#374151"))

        // Site · date
        h.v.findViewById<TextView>(R.id.tv_site_date).text =
            "${getSiteName(p.siteId)} · ${Fmt.date(p.date)}"

        // Note (hidden when blank)
        val tvNote = h.v.findViewById<TextView>(R.id.tv_note)
        if (p.note.isNotBlank()) {
            tvNote.text       = p.note
            tvNote.visibility = View.VISIBLE
        } else {
            tvNote.visibility = View.GONE
        }

        h.v.findViewById<TextView>(R.id.tv_amount).text = Fmt.money(p.amount)
        h.v.findViewById<View>(R.id.tv_edit).setOnClickListener { onEdit(p) }
        h.v.findViewById<View>(R.id.tv_del).setOnClickListener  { onDelete(p) }
    }

    private fun headChipColors(head: String): Pair<String, String> = when (head.lowercase()) {
        "wages"       -> "#DCFCE7" to "#16A34A"   // green
        "advance"     -> "#EDE9FE" to "#7C3AED"   // purple
        "travel"      -> "#FEF3C7" to "#D97706"   // amber
        "maintenance" -> "#DBEAFE" to "#2563EB"   // blue
        else          -> "#F3F4F6" to "#374151"   // grey
    }
}
