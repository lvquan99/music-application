package com.quanlv.musicplayer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.quanlv.musicplayer.R
import com.quanlv.musicplayer.databinding.FragmentFolderDetailBinding
import com.quanlv.musicplayer.extensions.deepEquals
import com.quanlv.musicplayer.extensions.inflateWithBinding
import com.quanlv.musicplayer.extensions.observe
import com.quanlv.musicplayer.extensions.toIDList
import com.quanlv.musicplayer.models.Song
import com.quanlv.musicplayer.ui.adapters.SongAdapter
import com.quanlv.musicplayer.ui.fragments.base.BaseFragment
import com.quanlv.musicplayer.ui.viewmodels.FavoriteViewModel
import com.quanlv.musicplayer.ui.viewmodels.FolderViewModel
import com.quanlv.musicplayer.ui.viewmodels.PlaylistViewModel
import com.quanlv.musicplayer.utils.BeatConstants.FOLDER_KEY
import com.quanlv.musicplayer.utils.BeatConstants.PLAY_ALL_SHUFFLED
import com.quanlv.musicplayer.utils.GeneralUtils.getExtraBundle
import kotlinx.android.synthetic.main.layout_recyclerview.*
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class FolderDetailFragment : BaseFragment<Song>() {

    private lateinit var binding: FragmentFolderDetailBinding
    private lateinit var songAdapter: SongAdapter

    private val folderViewModel by viewModel<FolderViewModel>()
    private val favoriteViewModel by inject<FavoriteViewModel>()
    private val playlistViewModel by inject<PlaylistViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = inflater.inflateWithBinding(R.layout.fragment_folder_detail, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        init()
        retainInstance = true
    }

    fun init() {
        val id = arguments?.getLong(FOLDER_KEY)!!
        val folder = folderViewModel.getFolder(id)

        songAdapter = SongAdapter().apply {
            showHeader = true
            isAlbumDetail = true
            itemClickListener = this@FolderDetailFragment
        }

        initNeeded(Song(), emptyList(), id)

        folderViewModel.getSongsByFolder(folder.ids).observe(this) {
            if (!songAdapter.songList.deepEquals(it)) {
                mainViewModel.reloadQueueIds(it.toIDList(), binding.folder!!.name)
                songAdapter.updateDataSet(it)
            }

        }

        list.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = songAdapter
            clipToOutline = true
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }

        binding.addFavorites.setOnClickListener { toggleAddFav() }

        binding.let {
            it.mainViewModel = mainViewModel
            it.lifecycleOwner = this
            it.folder = folder
            it.executePendingBindings()
        }
    }

    override fun onItemClick(view: View, position: Int, item: Song) {
        val extras = getExtraBundle(songAdapter.songList.toIDList(), binding.folder!!.name)
        mainViewModel.mediaItemClicked(item.toMediaItem(), extras)
    }

    override fun onShuffleClick(view: View) {
        val extras = getExtraBundle(songAdapter.songList.toIDList(), binding.folder!!.name)
        mainViewModel.transportControls()?.sendCustomAction(PLAY_ALL_SHUFFLED, extras)
    }

    override fun onPlayAllClick(view: View) {
        val extras = getExtraBundle(songAdapter.songList.toIDList(), binding.folder!!.name)
        mainViewModel.mediaItemClicked(songAdapter.songList.first().toMediaItem(), extras)
    }

    override fun onPopupMenuClick(view: View, position: Int, item: Song, itemList: List<Song>) {
        super.onPopupMenuClick(view, position, item, itemList)
        powerMenu!!.showAsAnchorRightTop(view)
        playlistViewModel.playLists().observe(this) {
            buildPlaylistMenu(it, item)
        }
    }

    private fun toggleAddFav() {
        if (favoriteViewModel.favExist(binding.folder!!.id)) {
            val resp = favoriteViewModel.deleteFavorites(longArrayOf(binding.folder!!.id))
            showSnackBar(view, resp, R.string.folder_no_fav_ok)
        } else {
            val resp = favoriteViewModel.create(binding.folder!!.toFavorite())
            showSnackBar(view, resp, R.string.folder_fav_ok)
        }
    }
}
