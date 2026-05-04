package com.khaga.mobile.ksite.ui.monthclose

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import androidx.recyclerview.widget.ListAdapter
import com.khaga.mobile.ksite.R
import com.khaga.mobile.ksite.data.model.MonthClose
import com.khaga.mobile.ksite.databinding.FragmentMonthCloseBinding
import com.khaga.mobile.ksite.util.*
import com.khaga.mobile.ksite.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

// Sealed list item for grouped display
internal sealed class ListItem {
    data class Header(val month: String) : ListItem()
    data class Entry(val mc: MonthClose)  : ListItem()
}

class MonthCloseFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()
    private var _binding: FragmentMonthCloseBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MonthCloseAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMonthCloseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = MonthCloseAdapter(
            onDelete      = { vm.deleteMonthClose(it.id) },
            getWorkerName = { id -> vm.workers.value?.firstOrNull { it.id == id }?.name ?: id },
            getSiteName   = { id -> vm.sites.value?.firstOrNull { it.id == id }?.name ?: "" }
        )

        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.btnCloseMonth.setOnClickListener { showCloseDialog() }

        vm.monthCloses.observe(viewLifecycleOwner) { list ->
            // Group by month (descending) and build a flat list with headers
            val items = mutableListOf<ListItem>()
            list.sortedByDescending { it.month }
                .groupBy { it.month }
                .forEach { (month, entries) ->
                    items.add(ListItem.Header(month))
                    entries.forEach { items.add(ListItem.Entry(it)) }
                }
            adapter.submitList(items)
        }
    }

    private fun showCloseDialog() {
        val sites   = vm.sites.value   ?: emptyList()
        val workers = vm.workers.value ?: emptyList()
        if (sites.isEmpty() || workers.isEmpty()) {
            Toast.makeText(requireContext(), "Add sites and workers in Settings first", Toast.LENGTH_LONG).show()
            return
        }

        val v         = layoutInflater.inflate(R.layout.dialog_month_close, null)
        val spSite    = v.findViewById<Spinner>(R.id.sp_site)
        val spWorker  = v.findViewById<Spinner>(R.id.sp_worker)
        val etMonth   = v.findViewById<EditText>(R.id.et_month)
        val etPresent = v.findViewById<EditText>(R.id.et_present)
        val etHalf    = v.findViewById<EditText>(R.id.et_half)
        val etAbsent  = v.findViewById<EditText>(R.id.et_absent)

        val tvMonthInfo        = v.findViewById<TextView>(R.id.tv_month_info)
        val tvRemaining        = v.findViewById<TextView>(R.id.tv_remaining)
        val tvTakenHeader      = v.findViewById<TextView>(R.id.tv_taken_header)
        val tvAdvanceTaken     = v.findViewById<TextView>(R.id.tv_advance_taken)
        val tvTravelTaken      = v.findViewById<TextView>(R.id.tv_travel_taken)
        val tvWagesPaid        = v.findViewById<TextView>(R.id.tv_wages_paid)
        val tvOtherTaken       = v.findViewById<TextView>(R.id.tv_other_taken)
        val tvTotalTakenDisplay= v.findViewById<TextView>(R.id.tv_total_taken_display)
        val rowCarryForward    = v.findViewById<View>(R.id.row_carry_forward)
        val tvCarryForward     = v.findViewById<TextView>(R.id.tv_carry_forward)
        val tvWageEarned       = v.findViewById<TextView>(R.id.tv_wage_earned)
        val tvNetPayable       = v.findViewById<TextView>(R.id.tv_net_payable)
        val tvWageFormula      = v.findViewById<TextView>(R.id.tv_wage_formula)

        etMonth.isFocusable = false
        etMonth.isClickable = true

        spSite.adapter   = ArrayAdapter(requireContext(), R.layout.item_spinner, sites.map { it.name }).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spWorker.adapter = ArrayAdapter(requireContext(), R.layout.item_spinner, workers.map { "${it.name} (${Fmt.money(it.wagePerDay)}/day)" }).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        etMonth.setText(Fmt.currentMonthIso())

        // Shared state updated by updateTaken(), consumed by recalc()
        var takenTotal   = 0L
        var carryForward = 0L

        fun updateTaken() {
            val w     = workers.getOrNull(spWorker.selectedItemPosition) ?: return
            val s     = sites.getOrNull(spSite.selectedItemPosition) ?: return
            val month = etMonth.text.toString().trim()

            // Live preview from in-memory payments
            val payments = vm.payments.value?.filter {
                it.workerId == w.id && it.siteId == s.id && it.date.startsWith(month)
            } ?: emptyList()

            val adv   = payments.filter { it.head == "Advance" }.sumOf { it.amount }
            val trav  = payments.filter { it.head == "Travel"  }.sumOf { it.amount }
            val wpaid = payments.filter { it.head == "Wages"   }.sumOf { it.amount }
            val oth   = payments.filter { it.head !in listOf("Wages", "Advance", "Travel") }.sumOf { it.amount }
            takenTotal = adv + trav + wpaid + oth

            // Carry forward = previous month close net for same worker+site
            carryForward = vm.monthCloses.value
                ?.filter { it.workerId == w.id && it.siteId == s.id && it.month < month }
                ?.maxByOrNull { it.month }
                ?.netPayable ?: 0L

            tvTakenHeader.text       = "Amounts already taken (${Fmt.monthLabel(month)})"
            tvAdvanceTaken.text      = Fmt.money(adv)
            tvTravelTaken.text       = Fmt.money(trav)
            tvWagesPaid.text         = Fmt.money(wpaid)
            tvOtherTaken.text        = Fmt.money(oth)
            tvTotalTakenDisplay.text = Fmt.money(takenTotal)
            tvCarryForward.text      = Fmt.money(carryForward)
            rowCarryForward.visibility = if (carryForward != 0L) View.VISIBLE else View.GONE
        }

        fun recalc() {
            val w         = workers.getOrNull(spWorker.selectedItemPosition) ?: return
            val month     = etMonth.text.toString().trim()
            val totalDays = Fmt.daysInMonth(month)
            val p         = etPresent.text.toString().toIntOrNull() ?: 0
            val h         = etHalf.text.toString().toIntOrNull() ?: 0
            val a         = etAbsent.text.toString().toIntOrNull() ?: 0
            val remaining = totalDays - p - h - a
            val earned    = ((p + h * 0.5) * w.wagePerDay).toLong()
            val net       = earned - takenTotal + carryForward

            tvMonthInfo.text   = "📅 ${Fmt.monthLabel(month)} — $totalDays days total"
            tvRemaining.text   = "Remaining: $remaining days"
            tvWageEarned.text  = Fmt.money(earned)
            tvNetPayable.text  = Fmt.money(net)
            tvWageFormula.text = "${p}d + ${h}×½ × ${Fmt.money(w.wagePerDay)}/day"
        }

        // Wire month date picker here, after local funs are declared
        etMonth.setOnClickListener {
            val parts = etMonth.text.toString().split("-")
            val y = parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            val m = (parts.getOrNull(1)?.toIntOrNull() ?: (Calendar.getInstance().get(Calendar.MONTH) + 1)) - 1
            DatePickerDialog(requireContext(), { _, year, month, _ ->
                etMonth.setText("%04d-%02d".format(year, month + 1))
                updateTaken()
                recalc()
            }, y, m, 1).show()
        }

        val attendanceWatcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = recalc()
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        }

        val selectionListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                updateTaken(); recalc()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        spWorker.onItemSelectedListener = selectionListener
        spSite.onItemSelectedListener   = selectionListener
        listOf(etPresent, etHalf, etAbsent).forEach { it.addTextChangedListener(attendanceWatcher) }
        etMonth.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateTaken(); recalc() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // Initial render
        updateTaken()
        recalc()

        val dialog = AlertDialog.Builder(requireContext())
            .setView(v)
            .create()

        v.findViewById<View>(R.id.tv_dialog_close).setOnClickListener { dialog.dismiss() }

        v.findViewById<View>(R.id.btn_save_month).setOnClickListener {
            val w     = workers[spWorker.selectedItemPosition]
            val s     = sites[spSite.selectedItemPosition]
            val month = etMonth.text.toString().trim()
            val p     = etPresent.text.toString().toIntOrNull() ?: 0
            val h     = etHalf.text.toString().toIntOrNull() ?: 0
            val a     = etAbsent.text.toString().toIntOrNull() ?: 0
            val totalDays = Fmt.daysInMonth(month)
            val earned    = ((p + h * 0.5) * w.wagePerDay).toLong()

            lifecycleScope.launch {
                val taken = vm.calcMonthClose(w.id, s.id, month)
                vm.insertMonthClose(MonthClose(
                    id           = UUID.randomUUID().toString(),
                    workerId     = w.id, siteId = s.id, month = month,
                    daysPresent  = p, daysHalf = h, daysAbsent = a, totalDays = totalDays,
                    wageEarned   = earned,
                    advanceTaken = taken.advanceTaken,
                    travelTaken  = taken.travelTaken,
                    otherTaken   = taken.wagesPaid + taken.otherTaken,  // wages paid + misc
                    totalTaken   = taken.totalTaken,
                    netPayable   = earned - taken.totalTaken + taken.carryForward
                ))
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

internal class MonthCloseAdapter(
    private val onDelete: (MonthClose) -> Unit,
    private val getWorkerName: (String) -> String,
    private val getSiteName: (String) -> String
) : ListAdapter<ListItem, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val VT_HEADER = 0
        private const val VT_ENTRY  = 1

        private val DIFF = object : DiffUtil.ItemCallback<ListItem>() {
            override fun areItemsTheSame(a: ListItem, b: ListItem) = when {
                a is ListItem.Header && b is ListItem.Header -> a.month == b.month
                a is ListItem.Entry  && b is ListItem.Entry  -> a.mc.id == b.mc.id
                else -> false
            }
            override fun areContentsTheSame(a: ListItem, b: ListItem) = a == b
        }
    }

    override fun getItemViewType(position: Int) =
        if (getItem(position) is ListItem.Header) VT_HEADER else VT_ENTRY

    // ── ViewHolders ──────────────────────────────────────────────────────────

    inner class HeaderVH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    inner class EntryVH(val v: View) : RecyclerView.ViewHolder(v)

    // ── Create ────────────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == VT_HEADER) {
            HeaderVH(inf.inflate(R.layout.item_month_header, parent, false) as TextView)
        } else {
            EntryVH(inf.inflate(R.layout.item_month_close, parent, false))
        }
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.Header -> bindHeader(holder as HeaderVH, item)
            is ListItem.Entry  -> bindEntry(holder as EntryVH, item.mc)
        }
    }

    private fun bindHeader(h: HeaderVH, item: ListItem.Header) {
        h.tv.text = Fmt.monthLabel(item.month).uppercase()
    }

    private fun bindEntry(h: EntryVH, mc: MonthClose) {
        val workerName = getWorkerName(mc.workerId)
        val siteName   = getSiteName(mc.siteId)

        // Avatar initials
        val initials = workerName.trim().split(" ")
            .filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
        h.v.findViewById<TextView>(R.id.tv_initials).text = initials

        h.v.findViewById<TextView>(R.id.tv_worker_name).text = workerName

        // Site name (shown only when non-blank)
        val tvSite = h.v.findViewById<TextView>(R.id.tv_site_name)
        if (siteName.isNotBlank()) {
            tvSite.text       = siteName
            tvSite.visibility = View.VISIBLE
        } else {
            tvSite.visibility = View.GONE
        }

        // Present chip — always shown (green)
        val tvPresent = h.v.findViewById<TextView>(R.id.tv_chip_present)
        tvPresent.text = "✓ ${mc.daysPresent} Present"
        tvPresent.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
        tvPresent.setTextColor(Color.parseColor("#15803D"))

        // Half-day chip — hidden when 0 (amber)
        val tvHalf = h.v.findViewById<TextView>(R.id.tv_chip_half)
        if (mc.daysHalf > 0) {
            tvHalf.text = "½ ${mc.daysHalf} Half"
            tvHalf.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
            tvHalf.setTextColor(Color.parseColor("#92400E"))
            tvHalf.visibility = View.VISIBLE
        } else {
            tvHalf.visibility = View.GONE
        }

        // Total chip (grey)
        val tvTotal = h.v.findViewById<TextView>(R.id.tv_chip_total)
        tvTotal.text = "${mc.totalDays} total"
        tvTotal.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F3F4F6"))
        tvTotal.setTextColor(Color.parseColor("#6B7280"))

        // Stat boxes
        h.v.findViewById<TextView>(R.id.tv_earned).text = Fmt.money(mc.wageEarned)
        h.v.findViewById<TextView>(R.id.tv_taken).text  = Fmt.money(mc.totalTaken)
        h.v.findViewById<TextView>(R.id.tv_net).text    = Fmt.money(mc.netPayable)

        // Delete
        h.v.findViewById<View>(R.id.tv_del).setOnClickListener { onDelete(mc) }
    }
}
