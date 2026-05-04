package com.khaga.mobile.ksite.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.*
import androidx.recyclerview.widget.ListAdapter
import com.google.android.material.tabs.TabLayout
import com.khaga.mobile.ksite.R
import com.khaga.mobile.ksite.data.model.*
import com.khaga.mobile.ksite.databinding.FragmentSettingsBinding
import com.khaga.mobile.ksite.util.*
import com.khaga.mobile.ksite.viewmodel.MainViewModel
import java.io.File
import java.util.UUID

class SettingsFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Sites"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Workers"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Backup"))

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.tab_container, SitesTabFragment())
                .commit()
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val fragment: Fragment = when (tab.position) {
                    0    -> SitesTabFragment()
                    1    -> WorkersTabFragment()
                    else -> BackupTabFragment()
                }
                childFragmentManager.beginTransaction()
                    .replace(R.id.tab_container, fragment)
                    .commit()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Sites Tab ─────────────────────────────────────────────────────────────────
class SitesTabFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.tab_sites, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.recycler_sites)
        val btnAdd = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_add_site)
        val adapter = SiteAdapter(
            onEdit   = { showSiteDialog(it) },
            onDelete = { vm.deleteSite(it.id) },
            getStats = { site ->
                val paidOut   = vm.payments.value?.filter { it.siteId == site.id }?.sumOf { it.amount } ?: 0L
                val collected = vm.collections.value?.filter { it.siteId == site.id }?.sumOf { it.received } ?: 0L
                val invoiced  = vm.collections.value?.filter { it.siteId == site.id }?.sumOf { it.amount } ?: 0L
                Triple(collected, paidOut, invoiced - collected)   // (collected, paidOut, pending)
            }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        btnAdd.setOnClickListener { showSiteDialog(null) }
        vm.sites.observe(viewLifecycleOwner) { adapter.submitList(it) }
        vm.payments.observe(viewLifecycleOwner)    { adapter.submitList(vm.sites.value ?: emptyList()) }
        vm.collections.observe(viewLifecycleOwner) { adapter.submitList(vm.sites.value ?: emptyList()) }
    }

    private fun showSiteDialog(existing: Site?) {
        val v = layoutInflater.inflate(R.layout.dialog_site, null)
        val etName = v.findViewById<EditText>(R.id.et_name)
        val etEst  = v.findViewById<EditText>(R.id.et_estimate)
        existing?.let { etName.setText(it.name); etEst.setText(it.estimate.toString()) }
        AlertDialog.Builder(requireContext())
            .setTitle(if (existing != null) "Edit Site" else "Add Site")
            .setView(v)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim(); if (name.isBlank()) return@setPositiveButton
                vm.upsertSite(Site(id = existing?.id ?: UUID.randomUUID().toString(), name = name, estimate = etEst.text.toString().toLongOrNull() ?: 0L))
            }
            .setNegativeButton("Cancel", null).show()
    }
}

class SiteAdapter(
    private val onEdit: (Site) -> Unit,
    private val onDelete: (Site) -> Unit,
    private val getStats: (Site) -> Triple<Long, Long, Long>   // (collected, paidOut, pending)
) : ListAdapter<Site, SiteAdapter.VH>(object : DiffUtil.ItemCallback<Site>() {
    override fun areItemsTheSame(a: Site, b: Site) = a.id == b.id
    override fun areContentsTheSame(a: Site, b: Site) = a == b
}) {
    inner class VH(val v: View) : RecyclerView.ViewHolder(v)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_site, parent, false))
    override fun onBindViewHolder(h: VH, pos: Int) {
        val s = getItem(pos)
        val (col, pay, pending) = getStats(s)
        val gain = col - pay

        h.v.findViewById<TextView>(R.id.tv_name).text      = s.name
        h.v.findViewById<TextView>(R.id.tv_estimate).text  = "Estimate: ${Fmt.money(s.estimate)}"
        h.v.findViewById<TextView>(R.id.tv_collected).text = Fmt.money(col)
        h.v.findViewById<TextView>(R.id.tv_paid).text      = Fmt.money(pay)
        h.v.findViewById<TextView>(R.id.tv_gain).text      = Fmt.money(gain)

        val llPending = h.v.findViewById<View>(R.id.ll_pending)
        val tvPending = h.v.findViewById<TextView>(R.id.tv_pending)
        if (pending > 0) {
            llPending.visibility = View.VISIBLE
            tvPending.text = Fmt.money(pending)
        } else {
            llPending.visibility = View.GONE
        }

        h.v.findViewById<View>(R.id.btn_edit).setOnClickListener { onEdit(s) }
        h.v.findViewById<View>(R.id.btn_delete).setOnClickListener { onDelete(s) }
    }
}

// ── Workers Tab ───────────────────────────────────────────────────────────────
class WorkersTabFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()
    private var pendingWorkerEdit: Worker? = null
    private var photoUri: Uri? = null

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera()
            else Toast.makeText(requireContext(), "Camera permission is required to take a photo", Toast.LENGTH_LONG).show()
        }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) photoUri?.let { showWorkerDialog(pendingWorkerEdit, it.toString()) }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.tab_workers, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.recycler_workers)
        val btnAdd = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_add_worker)
        val adapter = WorkerAdapter(
            onEdit   = { showWorkerDialog(it, it.photoPath) },
            onDelete = { vm.deleteWorker(it.id) },
            getStats = { worker ->
                val workerPayments = vm.payments.value?.filter { it.workerId == worker.id } ?: emptyList()
                val totalPaid  = workerPayments.sumOf { it.amount }
                val lastPayment = workerPayments.maxByOrNull { it.date }
                val lastMonth   = lastPayment?.let { Fmt.date(it.date) }
                val lastSummary = lastPayment?.let { "${Fmt.money(it.amount)} · ${it.head}" }
                Triple(totalPaid, lastMonth, lastSummary)
            }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        btnAdd.setOnClickListener { showWorkerDialog(null, null) }
        vm.workers.observe(viewLifecycleOwner) { adapter.submitList(it) }
        vm.payments.observe(viewLifecycleOwner)    { adapter.submitList(vm.workers.value ?: emptyList()) }
        vm.collections.observe(viewLifecycleOwner) { adapter.submitList(vm.workers.value ?: emptyList()) }
    }

    /** Prepares the output file and fires the camera intent. */
    private fun launchCamera() {
        val file = File(requireContext().filesDir, "worker_${UUID.randomUUID()}.jpg")
        photoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            file
        )
        takePicture.launch(photoUri!!)
    }

    private fun showWorkerDialog(existing: Worker?, currentPhoto: String?) {
        val v = layoutInflater.inflate(R.layout.dialog_worker, null)
        val etName    = v.findViewById<EditText>(R.id.et_name)
        val etWage    = v.findViewById<EditText>(R.id.et_wage)
        val etMobile  = v.findViewById<EditText>(R.id.et_mobile)
        val etAddress = v.findViewById<EditText>(R.id.et_address)
        val btnPhoto  = v.findViewById<Button>(R.id.btn_photo)
        val ivPhoto   = v.findViewById<android.widget.ImageView>(R.id.iv_photo)

        existing?.let {
            etName.setText(it.name)
            etWage.setText(it.wagePerDay.toString())
            etMobile.setText(it.mobile)
            etAddress.setText(it.address)
        }
        currentPhoto?.let { ivPhoto.setImageURI(Uri.parse(it)); ivPhoto.visibility = View.VISIBLE }

        btnPhoto.setOnClickListener {
            pendingWorkerEdit = existing
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                launchCamera()
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existing != null) "Edit Worker" else "Add Worker")
            .setView(v)
            .setPositiveButton("Save", null)   // null → prevents auto-dismiss so we can validate
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text.toString().trim()
                if (name.isBlank()) {
                    etName.error = "Name is required"
                    return@setOnClickListener
                }
                val wage = etWage.text.toString().trim().toLongOrNull()
                if (wage == null || wage <= 0) {
                    etWage.error = "Enter a valid daily wage"
                    return@setOnClickListener
                }
                vm.upsertWorker(
                    Worker(
                        id        = existing?.id ?: UUID.randomUUID().toString(),
                        name      = name,
                        wagePerDay = wage,
                        mobile    = etMobile.text.toString().trim(),
                        address   = etAddress.text.toString().trim(),
                        photoPath = currentPhoto ?: existing?.photoPath
                    )
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}

class WorkerAdapter(
    private val onEdit: (Worker) -> Unit,
    private val onDelete: (Worker) -> Unit,
    private val getStats: (Worker) -> Triple<Long, String?, String?>  // (totalPaid, lastPayDate, lastPaySummary)
) : ListAdapter<Worker, WorkerAdapter.VH>(object : DiffUtil.ItemCallback<Worker>() {
    override fun areItemsTheSame(a: Worker, b: Worker) = a.id == b.id
    override fun areContentsTheSame(a: Worker, b: Worker) = a == b
}) {
    inner class VH(val v: View) : RecyclerView.ViewHolder(v)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_worker, parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val w = getItem(pos)
        val (totalPaid, lastMonth, lastSummary) = getStats(w)

        // Avatar initials (up to 2 chars)
        val initials = w.name.trim().split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
        h.v.findViewById<TextView>(R.id.tv_initials).text = initials

        h.v.findViewById<TextView>(R.id.tv_name).text    = w.name
        h.v.findViewById<TextView>(R.id.tv_wage).text    = "${Fmt.money(w.wagePerDay)}/day · ${w.mobile}"
        h.v.findViewById<TextView>(R.id.tv_address).text = w.address

        h.v.findViewById<TextView>(R.id.tv_total_paid).text = Fmt.money(totalPaid)
        h.v.findViewById<TextView>(R.id.tv_last_close).text = lastMonth ?: "—"

        val llLastMonth   = h.v.findViewById<View>(R.id.ll_last_month)
        val tvLastSummary = h.v.findViewById<TextView>(R.id.tv_last_month_summary)
        if (lastSummary != null) {
            tvLastSummary.text    = lastSummary
            llLastMonth.visibility = View.VISIBLE
        } else {
            llLastMonth.visibility = View.GONE
        }

        h.v.findViewById<View>(R.id.btn_edit).setOnClickListener   { onEdit(w) }
        h.v.findViewById<View>(R.id.btn_delete).setOnClickListener { onDelete(w) }
    }
}

// ── Backup Tab ────────────────────────────────────────────────────────────────
class BackupTabFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.tab_backup, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val etApiKey   = view.findViewById<EditText>(R.id.et_api_key)
        val etBinId    = view.findViewById<EditText>(R.id.et_bin_id)
        val btnBackup  = view.findViewById<Button>(R.id.btn_backup)
        val btnRestore = view.findViewById<Button>(R.id.btn_restore)
        val tvStatus   = view.findViewById<TextView>(R.id.tv_status)
        val tvBinId    = view.findViewById<TextView>(R.id.tv_bin_id)

        etApiKey.setText(vm.apiKey)
        vm.binId?.let { etBinId.setText(it); tvBinId.text = "Saved Bin ID: $it" }

        btnBackup.setOnClickListener {
            vm.apiKey = etApiKey.text.toString().trim()
            tvStatus.text = "Saving backup..."
            vm.backup()
        }

        btnRestore.setOnClickListener {
            vm.apiKey = etApiKey.text.toString().trim()
            val id = etBinId.text.toString().trim()
            if (id.isBlank()) { tvStatus.text = "Enter a Bin ID to restore"; return@setOnClickListener }
            tvStatus.text = "Restoring..."
            vm.restore(id)
        }

        vm.backupStatus.observe(viewLifecycleOwner) { status ->
            when {
                status == null -> {}
                status.startsWith("ok:") -> {
                    val id = status.removePrefix("ok:")
                    tvStatus.text = "✓ Backup saved!"
                    tvBinId.text  = "Bin ID: $id"
                    vm.clearBackupStatus()
                }
                status == "restored"      -> { tvStatus.text = "✓ Data restored!"; vm.clearBackupStatus() }
                status.startsWith("err:") -> { tvStatus.text = "✗ ${status.removePrefix("err:")}"; vm.clearBackupStatus() }
                status == "saving"        -> tvStatus.text = "Saving…"
                status == "restoring"     -> tvStatus.text = "Restoring…"
            }
        }
    }
}
