package com.khaga.mobile.ksite.ui.give

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.*
import androidx.recyclerview.widget.ListAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.khaga.mobile.ksite.R
import com.khaga.mobile.ksite.data.model.*
import com.khaga.mobile.ksite.databinding.*
import com.khaga.mobile.ksite.util.*
import com.khaga.mobile.ksite.viewmodel.MainViewModel
import java.util.UUID

class GiveFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = PaymentAdapter(
        onEdit = { showDialog(it) },
        onDelete = { vm.deletePayment(it.id) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.fab.setOnClickListener { showDialog(null) }
        binding.tvTitle.text = "Give Payments"

        vm.payments.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            val total = list.sumOf { it.amount }
            binding.tvSummary.text = "Total paid out: ${Fmt.money(total)}  |  ${list.size} entries"
        }
    }

    private fun showDialog(existing: Payment?) {
        val sites = vm.sites.value ?: emptyList()
        val workers = vm.workers.value ?: emptyList()
        if (sites.isEmpty() || workers.isEmpty()) {
            Toast.makeText(requireContext(), "Add sites and workers first in Settings", Toast.LENGTH_LONG).show()
            return
        }

        val v = layoutInflater.inflate(R.layout.dialog_payment, null)
        val spSite    = v.findViewById<Spinner>(R.id.sp_site)
        val spWorker  = v.findViewById<Spinner>(R.id.sp_worker)
        val spHead    = v.findViewById<Spinner>(R.id.sp_head)
        val spMode    = v.findViewById<Spinner>(R.id.sp_mode)
        val etAmount  = v.findViewById<EditText>(R.id.et_amount)
        val etDate    = v.findViewById<EditText>(R.id.et_date)
        val etNote    = v.findViewById<EditText>(R.id.et_note)
        val tvHint    = v.findViewById<TextView>(R.id.tv_wage_hint)

        spSite.adapter   = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sites.map { it.name }).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spWorker.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, workers.map { "${it.name} (${Fmt.money(it.wagePerDay)}/day)" }).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spHead.adapter   = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, PAY_HEADS).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spMode.adapter   = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, PAY_MODES).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Show wage hint when worker selected
        fun updateHint() {
            val w = workers.getOrNull(spWorker.selectedItemPosition)
            if (w != null) tvHint.text = "Rate: ${Fmt.money(w.wagePerDay)}/day"
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

        AlertDialog.Builder(requireContext())
            .setTitle(if (existing != null) "Edit Payment" else "Record Payment")
            .setView(v)
            .setPositiveButton("Save") { _, _ ->
                val amt = etAmount.text.toString().toLongOrNull() ?: return@setPositiveButton
                val payment = Payment(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    workerId = workers[spWorker.selectedItemPosition].id,
                    siteId   = sites[spSite.selectedItemPosition].id,
                    head  = PAY_HEADS[spHead.selectedItemPosition],
                    amount = amt,
                    mode  = PAY_MODES[spMode.selectedItemPosition],
                    date  = etDate.text.toString().ifBlank { Fmt.todayIso() },
                    note  = etNote.text.toString()
                )
                vm.upsertPayment(payment)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class PaymentAdapter(
    private val onEdit: (Payment) -> Unit,
    private val onDelete: (Payment) -> Unit
) : ListAdapter<Payment, PaymentAdapter.VH>(object : DiffUtil.ItemCallback<Payment>() {
    override fun areItemsTheSame(a: Payment, b: Payment) = a.id == b.id
    override fun areContentsTheSame(a: Payment, b: Payment) = a == b
}) {
    inner class VH(val v: View) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_payment, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = getItem(pos)
        h.v.findViewById<TextView>(R.id.tv_name).text = p.head
        h.v.findViewById<TextView>(R.id.tv_sub).text  = "${Fmt.date(p.date)} · ${p.mode} · ${p.note}"
        h.v.findViewById<TextView>(R.id.tv_amount).text = Fmt.money(p.amount)
        h.v.findViewById<View>(R.id.btn_edit).setOnClickListener { onEdit(p) }
        h.v.findViewById<View>(R.id.btn_delete).setOnClickListener { onDelete(p) }
    }
}
