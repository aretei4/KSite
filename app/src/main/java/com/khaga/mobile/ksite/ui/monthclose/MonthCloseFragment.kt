package com.khaga.mobile.ksite.ui.monthclose

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.khaga.mobile.ksite.R
import com.khaga.mobile.ksite.data.model.MonthClose
import com.khaga.mobile.ksite.databinding.FragmentListBinding
import com.khaga.mobile.ksite.util.*
import com.khaga.mobile.ksite.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.UUID

class MonthCloseFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = MonthCloseAdapter(onDelete = { vm.deleteMonthClose(it.id) })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.fab.setOnClickListener { showCloseDialog() }
        binding.tvTitle.text = "Month Close"
        binding.tvSummary.text = "Close a worker's month to calculate net wages"

        vm.monthCloses.observe(viewLifecycleOwner) { adapter.submitList(it) }
    }

    private fun showCloseDialog() {
        val sites = vm.sites.value ?: emptyList()
        val workers = vm.workers.value ?: emptyList()
        if (sites.isEmpty() || workers.isEmpty()) {
            Toast.makeText(requireContext(), "Add sites and workers in Settings first", Toast.LENGTH_LONG).show()
            return
        }

        val v = layoutInflater.inflate(R.layout.dialog_month_close, null)
        val spSite    = v.findViewById<Spinner>(R.id.sp_site)
        val spWorker  = v.findViewById<Spinner>(R.id.sp_worker)
        val etMonth   = v.findViewById<EditText>(R.id.et_month)
        val etPresent = v.findViewById<EditText>(R.id.et_present)
        val etHalf    = v.findViewById<EditText>(R.id.et_half)
        val etAbsent  = v.findViewById<EditText>(R.id.et_absent)
        val tvSummary = v.findViewById<TextView>(R.id.tv_calc_summary)

        spSite.adapter   = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sites.map { it.name }).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spWorker.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, workers.map { "${it.name} (${Fmt.money(it.wagePerDay)}/day)" }).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        etMonth.setText(Fmt.currentMonthIso())

        fun recalc() {
            val w = workers.getOrNull(spWorker.selectedItemPosition) ?: return
            val p = etPresent.text.toString().toIntOrNull() ?: 0
            val h = etHalf.text.toString().toIntOrNull() ?: 0
            val totalDays = Fmt.daysInMonth(etMonth.text.toString())
            val remaining = totalDays - p - h - (etAbsent.text.toString().toIntOrNull() ?: 0)
            val earned = ((p + h * 0.5) * w.wagePerDay).toLong()
            tvSummary.text = "Wage earned: ${Fmt.money(earned)}\nRemaining/unaccounted: $remaining days"
        }

        spWorker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = recalc()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        listOf(etPresent, etHalf, etAbsent).forEach { et ->
            et.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) = recalc()
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Close Month")
            .setView(v)
            .setPositiveButton("Save") { _, _ ->
                val w = workers[spWorker.selectedItemPosition]
                val s = sites[spSite.selectedItemPosition]
                val month = etMonth.text.toString()
                val p = etPresent.text.toString().toIntOrNull() ?: 0
                val h = etHalf.text.toString().toIntOrNull() ?: 0
                val a = etAbsent.text.toString().toIntOrNull() ?: 0
                val totalDays = Fmt.daysInMonth(month)
                val earned = ((p + h * 0.5) * w.wagePerDay).toLong()

                lifecycleScope.launch {
                    val taken = vm.calcMonthClose(w.id, s.id, month)
                    vm.insertMonthClose(MonthClose(
                        id = UUID.randomUUID().toString(),
                        workerId = w.id, siteId = s.id, month = month,
                        daysPresent = p, daysHalf = h, daysAbsent = a, totalDays = totalDays,
                        wageEarned = earned,
                        advanceTaken = taken.advanceTaken, travelTaken = taken.travelTaken,
                        otherTaken = taken.otherTaken, totalTaken = taken.totalTaken,
                        netPayable = earned - taken.totalTaken
                    ))
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class MonthCloseAdapter(
    private val onDelete: (MonthClose) -> Unit
) : ListAdapter<MonthClose, MonthCloseAdapter.VH>(object : DiffUtil.ItemCallback<MonthClose>() {
    override fun areItemsTheSame(a: MonthClose, b: MonthClose) = a.id == b.id
    override fun areContentsTheSame(a: MonthClose, b: MonthClose) = a == b
}) {
    inner class VH(val v: View) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_month_close, parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val mc = getItem(pos)
        h.v.findViewById<TextView>(R.id.tv_month).text   = Fmt.monthLabel(mc.month)
        h.v.findViewById<TextView>(R.id.tv_att).text     = "P:${mc.daysPresent}  H:${mc.daysHalf}  A:${mc.daysAbsent}  / ${mc.totalDays} days"
        h.v.findViewById<TextView>(R.id.tv_earned).text  = "Earned: ${Fmt.money(mc.wageEarned)}"
        h.v.findViewById<TextView>(R.id.tv_taken).text   = "Taken: ${Fmt.money(mc.totalTaken)}"
        h.v.findViewById<TextView>(R.id.tv_net).text     = "Net payable: ${Fmt.money(mc.netPayable)}"
        h.v.findViewById<View>(R.id.btn_delete).setOnClickListener { onDelete(mc) }
    }
}
