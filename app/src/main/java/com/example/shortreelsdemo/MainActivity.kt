package com.example.shortreelsdemo

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity:ComponentActivity(){
    private lateinit var pager:ViewPager2
    private lateinit var loading:ProgressBar
    private lateinit var error:TextView
    private lateinit var adapter:ReelPagerAdapter
    private val repo=ReelsRepository()
    private val reels=mutableListOf<Reel>()
    private var sessionId:String?=null
    private var loadingMore=false

    override fun onCreate(b:Bundle?){
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        pager=findViewById(R.id.reelsPager)
        loading=findViewById(R.id.loading)
        error=findViewById(R.id.errorText)
        adapter=ReelPagerAdapter(reels)
        pager.adapter=adapter
        pager.registerOnPageChangeCallback(object:ViewPager2.OnPageChangeCallback(){
            override fun onPageSelected(p:Int){
                adapter.setActive(p)
                if(p>=reels.size-3) loadMore()
            }
        })
        loadInitial()
    }

    private fun loadInitial(){
        loading.visibility=View.VISIBLE
        error.visibility=View.GONE
        lifecycleScope.launch{
            try{
                val r=withContext(Dispatchers.IO){repo.initialFeed()}
                sessionId=r.sessionId
                append(r.items)
                loading.visibility=View.GONE
                if(reels.isEmpty()){
                    error.text="No playable reels found."
                    error.visibility=View.VISIBLE
                }
            }catch(e:Exception){
                loading.visibility=View.GONE
                error.text="Could not connect to the reels server."
                error.visibility=View.VISIBLE
            }
        }
    }

    private fun loadMore(){
        if(loadingMore||sessionId==null)return
        loadingMore=true
        lifecycleScope.launch{
            try{
                val r=withContext(Dispatchers.IO){repo.nextFeed(sessionId!!)}
                append(if(r.newItems.isEmpty()) r.items else r.newItems)
            }catch(_:Exception){}finally{loadingMore=false}
        }
    }

    private fun append(xs:List<Reel>){
        val known=reels.map{it.mediaUrl}.toHashSet()
        val add=xs.filter{it.mediaUrl.isNotBlank()&&known.add(it.mediaUrl)}
        if(add.isNotEmpty()){
            val old=reels.size
            reels.addAll(add)
            adapter.notifyItemRangeInserted(old,add.size)
        }
    }

    override fun onPause(){super.onPause();adapter.pauseActive()}
    override fun onResume(){super.onResume();adapter.resumeActive()}
    override fun onDestroy(){adapter.releaseAll();super.onDestroy()}
}
