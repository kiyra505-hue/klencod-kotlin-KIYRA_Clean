package com.lumina.player

import android.Manifest
import android.app.Dialog
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.util.Locale

class MainActivity : AppCompatActivity() {

    data class VideoItem(
        val id: Long,
        val title: String,
        val uri: Uri,
        val durationMs: Long,
        val sizeBytes: Long,
        val folderName: String
    )

    private lateinit var btnRefreshTop: MaterialButton
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: TextView
    private lateinit var heroCard: FrameLayout
    private lateinit var tvHeroTitle: TextView
    private lateinit var tvHeroMeta: TextView
    private lateinit var btnHeroPlay: MaterialButton
    private lateinit var permissionCard: LinearLayout
    private lateinit var btnGrantPermission: MaterialButton
    private lateinit var tvVideoCount: TextView
    private lateinit var emptyStateCard: LinearLayout
    private lateinit var rvVideos: RecyclerView

    private var allVideos: List<VideoItem> = emptyList()
    private var featuredVideo: VideoItem? = null
    private lateinit var adapter: VideoAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        checkPermissionAndLoad()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRefreshTop = findViewById(R.id.btnRefreshTop)
        etSearch = findViewById(R.id.etSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        heroCard = findViewById(R.id.heroCard)
        tvHeroTitle = findViewById(R.id.tvHeroTitle)
        tvHeroMeta = findViewById(R.id.tvHeroMeta)
        btnHeroPlay = findViewById(R.id.btnHeroPlay)
        permissionCard = findViewById(R.id.permissionCard)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        tvVideoCount = findViewById(R.id.tvVideoCount)
        emptyStateCard = findViewById(R.id.emptyStateCard)
        rvVideos = findViewById(R.id.rvVideos)

        adapter = VideoAdapter(emptyList()) { video ->
            openPlayer(video)
        }
        rvVideos.layoutManager = LinearLayoutManager(this)
        rvVideos.adapter = adapter

        btnRefreshTop.setOnClickListener { checkPermissionAndLoad() }
        btnGrantPermission.setOnClickListener { requestVideoPermission() }
        btnClearSearch.setOnClickListener { etSearch.text.clear() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                btnClearSearch.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                filterList(q)
            }
        })

        btnHeroPlay.setOnClickListener { featuredVideo?.let { openPlayer(it) } }
        heroCard.setOnClickListener { featuredVideo?.let { openPlayer(it) } }

        checkPermissionAndLoad()
    }

    private fun hasVideoPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestVideoPermission() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(perms)
    }

    private fun checkPermissionAndLoad() {
        if (hasVideoPermission()) {
            permissionCard.visibility = View.GONE
            loadVideos()
        } else {
            permissionCard.visibility = View.VISIBLE
            heroCard.visibility = View.GONE
            emptyStateCard.visibility = View.GONE
            tvVideoCount.text = getString(R.string.videos_count_format, 0)
            requestVideoPermission()
        }
    }

    private fun loadVideos() {
        val list = mutableListOf<VideoItem>()
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        try {
            contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val folderIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val title = cursor.getString(nameIdx) ?: "Video"
                    val duration = cursor.getLong(durIdx)
                    val size = cursor.getLong(sizeIdx)
                    val folder = cursor.getString(folderIdx) ?: "Videos"
                    val contentUri = ContentUris.withAppendedId(uri, id)
                    list.add(VideoItem(id, title, contentUri, duration, size, folder))
                }
            }
        } catch (_: Exception) {
        }

        allVideos = list
        featuredVideo = allVideos.firstOrNull()
        if (featuredVideo != null) {
            heroCard.visibility = View.VISIBLE
            tvHeroTitle.text = featuredVideo!!.title.substringBeforeLast('.')
            tvHeroMeta.text = "${featuredVideo!!.folderName} • ${formatTime(featuredVideo!!.durationMs)}"
        } else {
            heroCard.visibility = View.GONE
        }

        filterList(etSearch.text?.toString()?.trim().orEmpty())
    }

    private fun filterList(query: String) {
        val filtered = if (query.isEmpty()) {
            allVideos
        } else {
            val lower = query.lowercase(Locale.getDefault())
            allVideos.filter {
                it.title.lowercase(Locale.getDefault()).contains(lower) ||
                    it.folderName.lowercase(Locale.getDefault()).contains(lower)
            }
        }
        adapter.submit(filtered)
        tvVideoCount.text = getString(R.string.videos_count_format, filtered.size)
        emptyStateCard.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openPlayer(video: VideoItem) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_video_player)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))

        val videoView = dialog.findViewById<VideoView>(R.id.videoView)
        val btnClose = dialog.findViewById<TextView>(R.id.btnClosePlayer)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvPlayerTitle)
        val btnPlayPause = dialog.findViewById<TextView>(R.id.btnPlayPause)
        val tvTotalTime = dialog.findViewById<TextView>(R.id.tvTotalTime)

        tvTitle.text = video.title.substringBeforeLast('.')
        tvTotalTime.text = formatTime(video.durationMs)

        videoView.setVideoURI(video.uri)
        videoView.setOnPreparedListener {
            videoView.start()
            btnPlayPause.text = "❚❚"
        }

        btnPlayPause.setOnClickListener {
            if (videoView.isPlaying) {
                videoView.pause()
                btnPlayPause.text = "▶"
            } else {
                videoView.start()
                btnPlayPause.text = "❚❚"
            }
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            try {
                videoView.stopPlayback()
            } catch (_: Exception) {
            }
        }

        dialog.show()
    }

    private fun formatTime(ms: Long): String {
        val sec = (ms / 1000L).coerceAtLeast(0L)
        val min = sec / 60L
        val remSec = sec % 60L
        return String.format(Locale.US, "%02d:%02d", min, remSec)
    }

    inner class VideoAdapter(
        private var items: List<VideoItem>,
        private val onClick: (VideoItem) -> Unit
    ) : RecyclerView.Adapter<VideoAdapter.VH>() {

        fun submit(newItems: List<VideoItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val cardRoot: LinearLayout = v.findViewById(R.id.cardRoot)
            val ivThumbnail: ImageView = v.findViewById(R.id.ivThumbnail)
            val tvDuration: TextView = v.findViewById(R.id.tvDuration)
            val tvTitle: TextView = v.findViewById(R.id.tvTitle)
            val tvSubtitle: TextView = v.findViewById(R.id.tvSubtitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_video_card, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.title.substringBeforeLast('.')
            holder.tvDuration.text = formatTime(item.durationMs)
            val sizeMb = item.sizeBytes / (1024.0 * 1024.0)
            holder.tvSubtitle.text = "${item.folderName} • ${String.format(Locale.US, "%.0f MB", sizeMb)}"
            holder.ivThumbnail.setImageDrawable(null)
            holder.cardRoot.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}