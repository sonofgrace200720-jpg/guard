package com.example.disposableprivacyworkspace.sandbox

import android.content.Context
import com.example.disposableprivacyworkspace.security.SecureSessionKeyStore
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SandboxManager(private val context: Context) {
    private val random=SecureRandom(); private val keyStore=SecureSessionKeyStore()
    private val root get()=File(context.filesDir,"sessions")
    private val prefs get()=context.getSharedPreferences("sandbox_meta",Context.MODE_PRIVATE)
    suspend fun create(mode:NetworkMode):SandboxSession=withContext(Dispatchers.IO){
        root.mkdirs(); val id="SESSION-"+randomBytes(10); val dir=File(root,id); dir.mkdirs();
        keyStore.getOrCreate(alias(id)); prefs.edit().putString("active_id",id).putString("mode",mode.name).apply();
        SandboxSession(id,SessionState.STARTING,mode)
    }
    suspend fun markActive(id:String,mode:NetworkMode)=withContext(Dispatchers.IO){ SandboxSession(id,SessionState.ACTIVE,mode) }
    suspend fun destroy(id:String)=withContext(Dispatchers.IO){
        val dir=File(root,id); dir.deleteRecursively(); keyStore.delete(alias(id));
        if(prefs.getString("active_id",null)==id) prefs.edit().clear().apply();
        SandboxSession(id,SessionState.DESTROYED,NetworkMode.CLOUDFLARE)
    }
    suspend fun cleanupOrphaned()=withContext(Dispatchers.IO){
        val active=prefs.getString("active_id",null); root.listFiles()?.forEach{ if(it.name!=active) it.deleteRecursively() };
        active?.let{ if(!File(root,it).exists()){ keyStore.delete(alias(it)); prefs.edit().clear().apply() } }
    }
    fun activeId():String?=prefs.getString("active_id",null)
    fun sessionDir(id:String)=File(root,id)
    private fun alias(id:String)="dpw.session."+id
    private fun randomBytes(n:Int):String=ByteArray(n).also(random::nextBytes).joinToString(""){ "%02x".format(it) }.uppercase()
}
