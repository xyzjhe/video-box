package com.github.tvbox.osc.util.js

import android.content.Context
import android.text.TextUtils
import android.util.Base64
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil.resolve
import com.github.catvod.crawler.Spider
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.MD5
import com.github.tvbox.osc.util.js.Async.Companion.run
import com.whl.quickjs.wrapper.*
import com.whl.quickjs.wrapper.Function
import com.whl.quickjs.wrapper.QuickJSContext.BytecodeModuleLoader
import java9.util.concurrent.CompletableFuture
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Exception
import kotlin.String
import kotlin.Throwable
import kotlin.Throws
import kotlin.arrayOfNulls

class JsSpider(key: String?, private val api: String, private val dex: Class<*>?) : Spider() {
    private val executor: ExecutorService
    private lateinit var ctx: QuickJSContext
    private var jsObject: JSObject? = null
    private val key: String
    private var cat = false
    override fun cancelByTag() {
        Connect.cancelByTag("js_okhttp_tag")
    }

    private fun submit(runnable: Runnable) {
        executor.submit(runnable)
    }

    private fun <T> submit(callable: Callable<T>): Future<T> {
        return executor.submit(callable)
    }

    @Throws(Exception::class)
    private fun call(func: String, vararg args: Any): Any? {
        //return executor.submit((FunCall.call(jsObject, func, args))).get();
        return CompletableFuture.supplyAsync(
            { run(jsObject!!, func, args) }, executor
        ).join().get()
    }

    private fun cfg(ext: String): JSObject {
        val cfg = ctx.createJSObject()
        cfg["stype"] = 3
        cfg["skey"] = key
        if (Json.invalid(ext)) cfg["ext"] = ext else cfg["ext"] = ctx.parse(ext) as JSObject
        return cfg
    }

    @Throws(Exception::class)
    override fun init(context: Context, extend: String) {
        if (cat) call("init", submit<JSObject> { cfg(extend) }.get()) else call(
            "init",
            if (Json.valid(extend)) ctx.parse(extend) else extend
        )
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
        val obj = submit<JSObject> { JSUtils<String>().toObj(ctx, extend) }.get()
        return call("category", tid, pg, filter, obj) as String
    }

    @Throws(Exception::class)
    override fun detailContent(ids: List<String>): String {
        return call("detail", ids[0]) as String
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
    override fun playerContent(flag: String, id: String, vipFlags: List<String>): String {
        val array = submit<JSArray> { JSUtils<String>().toArray(ctx, vipFlags) }.get()
        return call("play", flag, id, array) as String
    }

    @Throws(Exception::class)
    override fun manualVideoCheck(): Boolean {
        return call("sniffer") as Boolean
    }

    @Throws(Exception::class)
    override fun isVideoFormat(url: String): Boolean {
        return call("isVideo", url) as Boolean
    }

    @Throws(Exception::class)
    override fun proxyLocal(params: Map<String, String>): Array<Any?>? {
        return if ("catvod" == params["from"]) proxy2(params) else submit(Callable {
            proxy1(
                params
            )
        }).get()
    }

    override fun destroy() {
        submit {
            executor.shutdownNow()
            ctx.destroy()
        }
    }

    init {
        this.key = "J" + MD5.encode(key)
        executor = Executors.newSingleThreadExecutor()
        initializeJS()
    }

    @Throws(Exception::class)
    private fun initializeJS() {
        submit<Any?> {
            if (::ctx.isInitialized) createCtx()
            if (dex != null) createDex()
            var content = FileUtils.loadModule(api)
            if (TextUtils.isEmpty(content)) {
                return@submit null
            }
            if (content.startsWith("//bb")) {
                cat = true
                val b = Base64.decode(content.replace("//bb", ""), 0)
                ctx.execute(byteFF(b), "$key.js")
                ctx.evaluateModule(
                    String.format(
                        SPIDER_STRING_CODE,
                        "$key.js"
                    ) + "globalThis." + key + " = __JS_SPIDER__;", "tv_box_root.js"
                )
                //ctx.execute(byteFF(b), key + ".js","__jsEvalReturn");
                //ctx.evaluate("globalThis." + key + " = __JS_SPIDER__;");
            } else {
                if (content.contains("__JS_SPIDER__")) {
                    content = content.replace("__JS_SPIDER__\\s*=".toRegex(), "export default ")
                }
                var moduleExtName = "default"
                if (content.contains("__jsEvalReturn") && !content.contains("export default")) {
                    moduleExtName = "__jsEvalReturn"
                    cat = true
                }
                ctx.evaluateModule(content, api)
                ctx.evaluateModule(
                    String.format(SPIDER_STRING_CODE, api) + "globalThis." + key + " = __JS_SPIDER__;",
                    "tv_box_root.js"
                )
                //ctx.evaluateModule(content, api, moduleExtName);
                //ctx.evaluate("globalThis." + key + " = __JS_SPIDER__;");                
            }
            jsObject = ctx[ctx.getGlobalObject(), key] as JSObject
            null
        }.get()
    }

    private fun createCtx() {
        ctx = QuickJSContext.create()
        ctx.setModuleLoader(object : BytecodeModuleLoader() {
            override fun getModuleBytecode(moduleName: String): ByteArray? {
                val ss = FileUtils.loadModule(moduleName)
                if (TextUtils.isEmpty(ss)) {
                    return null
                }
                return if (ss.startsWith("//DRPY")) {
                    Base64.decode(ss.replace("//DRPY", ""), Base64.URL_SAFE)
                } else if (ss.startsWith("//bb")) {
                    val b = Base64.decode(ss.replace("//bb", ""), 0)
                    byteFF(b)
                } else {
                    if (moduleName.contains("cheerio.min.js")) {
                        FileUtils.setCacheByte("cheerio.min", ctx.compileModule(ss, "cheerio.min.js"))
                    } else if (moduleName.contains("crypto-js.js")) {
                        FileUtils.setCacheByte("crypto-js", ctx.compileModule(ss, "crypto-js.js"))
                    }
                    ctx.compileModule(ss, moduleName)
                }
            }

            @UnstableApi
            override fun moduleNormalizeName(moduleBaseName: String, moduleName: String): String {
                return resolve(moduleBaseName, moduleName)
            }
        })
        ctx.setConsole(QuickJSContext.Console { s -> LOG.i("QuJs", s) })
        ctx.getGlobalObject().bind(Global(executor))
        val local = ctx.createJSObject()
        ctx.getGlobalObject()["local"] = local
        local.bind(Local())
        ctx.getGlobalObject().context.evaluate(FileUtils.loadModule("net.js"))
    }

    private fun createDex() {
        try {
            val obj = ctx.createJSObject()
            val clz = dex
            val classes = clz!!.declaredClasses
            ctx.getGlobalObject()["jsapi"] = obj
            if (classes.size == 0) invokeSingle(clz, obj)
            if (classes.size >= 1) invokeMultiple(clz, obj)
        } catch (e: Throwable) {
            e.printStackTrace()
            LOG.e(e)
        }
    }

    @Throws(Throwable::class)
    private fun invokeSingle(clz: Class<*>?, jsObj: JSObject) {
        invoke(clz, jsObj, clz!!.getDeclaredConstructor(QuickJSContext::class.java).newInstance(ctx))
    }

    @Throws(Throwable::class)
    private fun invokeMultiple(clz: Class<*>?, jsObj: JSObject) {
        for (subClz in clz!!.declaredClasses) {
            val javaObj = subClz.getDeclaredConstructor(clz).newInstance(
                clz.getDeclaredConstructor(
                    QuickJSContext::class.java
                ).newInstance(ctx)
            )
            val subObj = ctx.createJSObject()
            invoke(subClz, subObj, javaObj)
            jsObj[subClz.simpleName] = subObj
        }
    }

    private operator fun invoke(clz: Class<*>?, jsObj: JSObject, javaObj: Any) {
        for (method in clz!!.methods) {
            if (!method.isAnnotationPresent(Function::class.java)) continue
            invoke(jsObj, method, javaObj)
        }
    }

    private operator fun invoke(jsObj: JSObject, method: Method, javaObj: Any) {
        jsObj[method.name] = JSCallFunction { objects ->
            try {
                return@JSCallFunction method.invoke(javaObj, *objects)
            } catch (e: Throwable) {
                return@JSCallFunction null
            }
        }
    }

    private val content: String?
        private get() {
            val global = "globalThis.$key"
            val content = FileUtils.loadModule(api)
            if (TextUtils.isEmpty(content)) {
                return null
            }
            return if (content.contains("__jsEvalReturn")) {
                ctx.evaluate("req = http")
                "$content$global = __jsEvalReturn()"
            } else if (content.contains("__JS_SPIDER__")) {
                content.replace("__JS_SPIDER__", global)
            } else {
                content.replace("export default.*?[{]".toRegex(), "$global = {")
            }
        }

    private fun proxy1(params: Map<String, String>): Array<Any?> {
        val `object` = JSUtils<String>().toObj(ctx, params)
        val array = (jsObject!!.getJSFunction("proxy").call(`object`) as JSArray).toJsonArray()
        return arrayOf(
            array.opt(0),
            array.opt(1),
            getStream(array.opt(2)),
        )
    }

    @Throws(Exception::class)
    private fun proxy2(params: Map<String, String>): Array<Any?> {
        val url = params["url"]
        val header = params["header"]
        val array = submit<JSArray> {
            JSUtils<String>().toArray(ctx, Arrays.asList(*url!!.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()))
        }.get()
        val `object` = submit<Any> { ctx.parse(header) }.get()
        val json = call("proxy", array, `object`) as String
        val res = Res.objectFrom(json)
        var contentType = res.getContentType()
        if (TextUtils.isEmpty(contentType)) contentType = "application/octet-stream"
        val result = arrayOfNulls<Any>(3)
        result[0] = 200
        result[1] = contentType
        if (res.buffer == 2) {
            result[2] = ByteArrayInputStream(Base64.decode(res.content, Base64.DEFAULT))
        } else {
            result[2] = ByteArrayInputStream(res.content.toByteArray())
        }
        return result
    }

    /* private Object[] proxy2(Map<String, String> params) throws Exception {
        String url = params.get("url");
        String header = params.get("header");
        JSArray array = submit(() -> new JSUtils<String>().toArray(ctx, Arrays.asList(url.split("/")))).get();
        Object object = submit(() -> ctx.parse(header)).get();
        String json = (String) call("proxy", array, object);
        Res res = Res.objectFrom(json);
        Object[] result = new Object[3];
        result[0] = 200;
        result[1] = "application/octet-stream";
        result[2] = new ByteArrayInputStream(Base64.decode(res.getContent(), Base64.DEFAULT));
        return result;
    }*/
    private fun getStream(o: Any): ByteArrayInputStream {
        return if (o is JSONArray) {
            val a = o
            val bytes = ByteArray(a.length())
            for (i in 0 until a.length()) bytes[i] = a.optInt(i).toByte()
            ByteArrayInputStream(bytes)
        } else {
            ByteArrayInputStream(o.toString().toByteArray())
        }
    }

    companion object {
        private const val SPIDER_STRING_CODE = "import * as spider from '%s'\n\n" +
                "if (!globalThis.__JS_SPIDER__) {\n" +
                "    if (spider.__jsEvalReturn) {\n" +
                "        globalThis.req = http\n" +
                "        globalThis.__JS_SPIDER__ = spider.__jsEvalReturn()\n" +
                "        globalThis.__JS_SPIDER__.is_cat = true\n" +
                "    } else if (spider.default) {\n" +
                "        globalThis.__JS_SPIDER__ = typeof spider.default === 'function' ? spider.default() : spider.default\n" +
                "    }\n" +
                "}"

        fun byteFF(bytes: ByteArray): ByteArray {
            val newBt = ByteArray(bytes.size - 4)
            newBt[0] = 1
            System.arraycopy(bytes, 5, newBt, 1, bytes.size - 5)
            return newBt
        }
    }
}