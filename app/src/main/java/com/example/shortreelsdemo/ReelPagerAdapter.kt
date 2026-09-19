package com.example.shortreelsdemo

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView

class ReelPagerAdapter(private val items:List<Reel>):RecyclerView.Adapter<ReelPagerAdapter.Holder>(){
    private var activePosition=RecyclerView.NO_POSITION
    private var player:ExoPlayer?=null
    private var activeView:PlayerView?=null
    private var muted=true

    override fun onCreateViewHolder(parent:ViewGroup,viewType:Int)=Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_reel,parent,false))
    override fun getItemCount()=items.size

    override fun onBindViewHolder(h:Holder,p:Int){
        val r=items[p]
        h.title.text=r.title ?: "Short Reel"
        h.source.text=if(r.sourceUrl!=null) "Public source" else ""
        h.mute.setOnClickListener {
            muted=!muted
            player?.volume=if(muted) 0f else 1f
            h.mute.alpha=if(muted) .65f else 1f
        }
        if(p==activePosition) attach(h.player,r)
    }

    override fun onViewRecycled(h:Holder){
        if(h.player==activeView) release()
        super.onViewRecycled(h)
    }

    fun setActive(p:Int){
        if(p==activePosition)return
        release()
        activePosition=p
        notifyItemChanged(p)
    }

    private fun attach(v:PlayerView,r:Reel){
        release()
        player=ExoPlayer.Builder(v.context).build().apply{
            repeatMode=Player.REPEAT_MODE_ONE
            volume=if(muted)0f else 1f
            setMediaItem(MediaItem.fromUri(Uri.parse(r.mediaUrl)))
            prepare()
            playWhenReady=true
        }
        v.player=player
        activeView=v
    }

    fun pauseActive(){player?.pause()}
    fun resumeActive(){player?.play()}

    private fun release(){
        activeView?.player=null
        player?.release()
        player=null
        activeView=null
    }

    fun releaseAll(){release()}

    class Holder(v:android.view.View):RecyclerView.ViewHolder(v){
        val player:PlayerView=v.findViewById(R.id.player)
        val title:TextView=v.findViewById(R.id.title)
        val source:TextView=v.findViewById(R.id.source)
        val mute:ImageButton=v.findViewById(R.id.muteButton)
    }
}
