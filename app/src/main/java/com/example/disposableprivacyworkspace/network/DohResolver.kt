package com.example.disposableprivacyworkspace.network

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class DohResolver {
    fun resolve(host:String):List<String>{
        val url=URL("https://cloudflare-dns.com/dns-query?name="+java.net.URLEncoder.encode(host,"UTF-8")+"&type=A")
        val c=url.openConnection() as HttpURLConnection; c.setRequestProperty("Accept","application/dns-json"); c.connectTimeout=8000; c.readTimeout=8000;
        return c.inputStream.bufferedReader().use{JSONObject(it.readText())}.optJSONArray("Answer")?.let{a->(0 until a.length()).mapNotNull{a.getJSONObject(it).optString("data").takeIf{d->d.isNotBlank() && d.matches(Regex("[0-9.]+"))}}}?:emptyList()
    }
}
