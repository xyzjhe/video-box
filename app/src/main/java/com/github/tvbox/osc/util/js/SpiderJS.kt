package com.github.tvbox.osc.util.js

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import com.github.catvod.crawler.Spider
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.MD5
import com.whl.quickjs.wrapper.*
import com.whl.quickjs.wrapper.Function
import com.whl.quickjs.wrapper.QuickJSContext.DefaultModuleLoader
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Exception
import kotlin.NullPointerException
import kotlin.String
import kotlin.Throwable
import kotlin.Throws
import kotlin.arrayOf
import kotlin.arrayOfNulls

class SpiderJS(key: String?, private val js: String, cls: Class<*>?) : Spider() {
    private val key: String
    private var jsObject: JSObject? = null
    var runtime: QuickJSContext? = null
    var executor: ExecutorService

    init {
        executor = Executors.newSingleThreadExecutor()
        this.key = "J" + MD5.encode(key)
        initjs(cls)
    }

    override fun destroy() {
        submit {
            executor.shutdownNow()
            runtime!!.destroy()
        }
    }

    private fun submit(runnable: Runnable) {
        executor.submit(runnable)
    }

    private fun <T> submit(callable: Callable<T>): Future<T> {
        return executor.submit(callable)
    }

    @Throws(Exception::class)
    private fun call(func: String, vararg args: Any): Any {
        return executor.submit(FunCall.call(jsObject, func, *args)).get()
    }

    @Throws(Exception::class)
    private fun initjs(cls: Class<*>?) {
        submit<Any?> {
            if (runtime == null) runtime = QuickJSContext.create()
            runtime!!.setModuleLoader(object : DefaultModuleLoader() {
                override fun getModuleStringCode(moduleName: String): String {
                    return FileUtils.loadModule(moduleName)
                }

                @UnstableApi
                override fun  moduleNormalizeName(moduleBaseName: String, moduleName: String): String {
                    return UriUtil.resolve(moduleBaseName, moduleName)
                }
            })
            initConsole()
            runtime!!.getGlobalObject().bind(Global(executor))
            if (cls != null) {
                val classes = cls.declaredClasses
                val apiObj = runtime!!.createJSObject()
                LOG.e("cls", "" + classes.size)
                for (classe in classes) {
                    var javaObj: Any? = null
                    try {
                        javaObj = classe.getDeclaredConstructor(cls)
                            .newInstance(cls.getDeclaredConstructor(QuickJSContext::class.java).newInstance(runtime))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    if (javaObj == null) {
                        throw NullPointerException("The JavaObj cannot be null. An error occurred in newInstance!")
                    }
                    val claObj = runtime!!.createJSObject()
                    val methods = classe.declaredMethods
                    for (method in methods) {
                        if (method.isAnnotationPresent(Function::class.java)) {
                            val finalJavaObj: Any = javaObj
                            claObj[method.name] = JSCallFunction { objects ->
                                try {
                                    return@JSCallFunction method.invoke(finalJavaObj, *objects)
                                } catch (e: Throwable) {
                                    return@JSCallFunction null
                                }
                            }
                        }
                    }
                    apiObj[classe.simpleName] = claObj
                    LOG.e("cls", classe.simpleName)
                }
                runtime!!.getGlobalObject()["jsapi"] = apiObj
            }
            var jsContent = FileUtils.loadModule(js)
            jsContent = if (jsContent.contains("__jsEvalReturn")) {
                runtime!!.evaluate("req = http")
                "$jsContent\n\nglobalThis.$key = __jsEvalReturn()"
            } else if (jsContent.contains("export default{") || jsContent.contains("export default {")) {
                jsContent.replace("export default.*?[{]".toRegex(), "globalThis.$key = {")
            } else {
                jsContent.replace("__JS_SPIDER__", "globalThis.$key")
            }
            //LOG.e("cls", jsContent);
            runtime!!.evaluateModule(
                "$jsContent\n\n;console.log(typeof($key.init));\n\nconsole.log(typeof(req));\n\nconsole.log(Object.keys($key));",
                js
            )
            jsObject = runtime!![runtime!!.getGlobalObject(), key] as JSObject
            null
        }.get()
    }

    private fun initConsole() {
        val local = runtime!!.createJSObject()
        runtime!!.getGlobalObject()["local"] = local
        local.bind(Local())
        runtime!!.setConsole { s -> LOG.i("QuJs", s) }
        runtime!!.evaluate(FileUtils.loadModule("net.js"))
    }

    override fun cancelByTag() {
        Connect.cancelByTag("js_okhttp_tag")
    }

    @Throws(Exception::class)
    override fun init(context: Context, extend: String) {
        super.init(context, extend)
        val ext = FileUtils.loadModule(extend)
        call("init", if (Json.valid(ext)) runtime!!.parse(ext) else ext)
    }

    @Throws(Exception::class)
    override fun homeContent(filter: Boolean): String {
        return call("home", filter) as String
    }

    @Throws(Exception::class)
    override fun homeVideoContent(): String {
        return call("homeVod") as String
    }

    @Throws(Exception::class)
    override fun categoryContent(tid: String, pg: String, filter: Boolean, extend: HashMap<String, String>): String {
        val obj = submit<JSObject> { JSUtils<String>().toObj(runtime, extend) }.get()
        return call("category", tid, pg, filter, obj) as String
    }

    @Throws(Exception::class)
    override fun detailContent(ids: List<String>): String {
        return call("detail", ids[0]) as String
    }

    @Throws(Exception::class)
    override fun playerContent(flag: String, id: String, vipFlags: List<String>): String {
        val array = submit<JSArray> { JSUtils<String>().toArray(runtime, vipFlags) }.get()
        return call("play", flag, id, array) as String
    }

    @Throws(Exception::class)
    override fun searchContent(key: String, quick: Boolean): String {
        return call("search", key, quick) as String
    }

    @Throws(Exception::class)
    override fun searchContent(key: String, quick: Boolean, pg: String): String {
        return call("search", key, quick, pg) as String
    }

    @Throws(Exception::class)
    override fun proxyLocal(params: Map<String, String>): Array<Any?> {
        return submit<Array<Any?>> {
            try {
                val o = JSUtils<String>().toObj(runtime, params)
                val jsFunction = jsObject!!.getJSFunction("proxy")
                val opt = JSONArray(jsFunction.call(null, arrayOf<Any>(o)).toString())
                val result = arrayOfNulls<Any>(3)
                result[0] = opt.opt(0)
                result[1] = opt.opt(1)
                val obj = opt.opt(2)
                val baos: ByteArrayInputStream
                if (obj is JSONArray) {
                    val json = obj
                    val b = ByteArray(json.length())
                    for (i in 0 until json.length()) {
                        b[i] = json.optInt(i).toByte()
                    }
                    baos = ByteArrayInputStream(b)
                } else {
                    baos = ByteArrayInputStream(opt.opt(2).toString().toByteArray())
                }
                result[2] = baos
                return@submit result
            } catch (throwable: Throwable) {
                LOG.e(throwable)
                return@submit arrayOfNulls<Any>(0)
            }
        }.get()
    }

    @Throws(Exception::class)
    override fun manualVideoCheck(): Boolean {
        return call("sniffer") as Boolean
    }

    @Throws(Exception::class)
    override fun isVideoFormat(url: String): Boolean {
        return call("isVideo", url) as Boolean
    }
}
