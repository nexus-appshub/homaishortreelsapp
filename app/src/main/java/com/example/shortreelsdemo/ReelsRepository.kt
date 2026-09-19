package com.example.shortreelsdemo

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Reel(val id:String,val mediaUrl:String,val type:String,val title:String?,val sourceUrl:String?)
data class FeedResponse(val sessionId:String?,val items:List<Reel>,val newItems:List<Reel>)

class ReelsRepository {
    companion object {
        private const val BASE="https://shortreels-scraper-1.onrender.com"
        private const val SOURCE="https://dashreels.com/"
    }
    fun initialFeed()=request("$BASE/v1/feed?url="+URLEncoder.encode(SOURCE,"UTF-8")+"&limit=10")
    fun nextFeed(sessionId:String)=request("$BASE/v1/feed?sessionId="+URLEncoder.encode(sessionId,"UTF-8")+"&limit=10")
    private fun request(urlString:String):FeedResponse {
        val c=URL(urlString).openConnection() as HttpURLConnection
        c.connectTimeout=30000
        c.readTimeout=90000
        c.requestMethod="GET"
        c.setRequestProperty("Accept","application/json")
        try {
            val code=c.responseCode
            val body=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}?:""
            if(code !in 200..299) throw IllegalStateException("Server returned HTTP $code")
            val root=JSONObject(body)
            if(!root.optBoolean("success",false)) throw IllegalStateException(root.optString("error","Feed request failed"))
            return FeedResponse(root.optString("sessionId").takeIf{it.isNotBlank()},parse(root.optJSONArray("items")),parse(root.optJSONArray("newItems")))
        } finally { c.disconnect() }
    }
    private fun parse(a:org.json.JSONArray?):List<Reel>{
        if(a==null)return emptyList()
        return buildList {
            for(i in 0 until a.length()){
                val x=a.optJSONObject(i)?:continue
                val media=x.optString("mediaUrl")
                val type=x.optString("type")
                if(media.isBlank()||type.equals("segment",true))continue
                add(Reel(x.optString("id",media),media,type,x.optString("title").takeIf{it.isNotBlank()},x.optString("sourceUrl").takeIf{it.isNotBlank()}))
            }
        }
    }
}
