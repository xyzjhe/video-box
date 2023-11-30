package com.github.tvbox.osc.api

import android.app.Activity
import android.net.Uri
import android.text.TextUtils
import android.util.Base64
import com.github.catvod.crawler.JarLoader
import com.github.catvod.crawler.JsLoader
import com.github.catvod.crawler.Spider
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.bean.*
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.ui.activity.HomeActivity.Companion.res
import com.github.tvbox.osc.util.*
import com.github.tvbox.osc.util.DefaultConfig.checkReplaceProxy
import com.github.tvbox.osc.util.DefaultConfig.safeJsonInt
import com.github.tvbox.osc.util.DefaultConfig.safeJsonString
import com.github.tvbox.osc.util.DefaultConfig.safeJsonStringList
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.Response
import com.orhanobut.hawk.Hawk
import org.apache.commons.lang3.StringUtils
import org.json.JSONObject
import java.io.*
import java.util.*
import java.util.regex.Pattern

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
class ApiConfig private constructor() {
    private val sourceBeanMap: LinkedHashMap<String, SourceBean>?
    private var mHomeSource: SourceBean? = null
    var defaultParse: ParseBean? = null
        private set
    private val liveChannelGroupList: MutableList<LiveChannelGroup>
    private val parseBeanList: MutableList<ParseBean>?
    var vipParseFlags: List<String>? = null
        private set
    private var ijkCodes: MutableList<IJKCode>? = null
    var spider: String? = null
        private set
    @JvmField
    var wallpaper = ""
    private val emptyHome = SourceBean()
    private val jarLoader = JarLoader()
    private val jsLoader = JsLoader()
    private val userAgent = "okhttp/3.15"
    private val requestAccept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"

    init {
        sourceBeanMap = LinkedHashMap()
        liveChannelGroupList = ArrayList()
        parseBeanList = ArrayList()
    }


    fun loadConfig(useCache: Boolean, callback: LoadConfigCallback, activity: Activity?) {
        // Embedded Source : Update in Strings.xml if required
        val apiUrl = Hawk.get(HawkConfig.API_URL, res!!.getString(R.string.app_source))
        if (apiUrl.isEmpty()) {
            callback.error("源地址为空")
            return
        }
        val cache = File(App.getInstance().filesDir.absolutePath + "/" + MD5.encode(apiUrl))
        if (useCache && cache.exists()) {
            try {
                parseJson(apiUrl, cache)
                callback.success()
                return
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }
        var TempKey: String? = null
        var configUrl = ""
        val pk = ";pk;"
        if (apiUrl.contains(pk)) {
            val a = apiUrl.split(pk.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            TempKey = a[1]
            configUrl = if (apiUrl.startsWith("clan")) {
                clanToAddress(a[0])
            } else if (apiUrl.startsWith("http")) {
                a[0]
            } else {
                "http://" + a[0]
            }
        } else if (apiUrl.startsWith("clan")) {
            configUrl = clanToAddress(apiUrl)
        } else if (!apiUrl.startsWith("http")) {
            configUrl = "http://$configUrl"
        } else {
            configUrl = apiUrl
        }
        println("API URL :$configUrl")
        val configKey = TempKey
        OkGo.get<String>(configUrl)
                .headers("User-Agent", userAgent)
                .headers("Accept", requestAccept)
                .execute(object : AbsCallback<String?>() {
                    override fun onSuccess(response: Response<String?>?) {
                        try {
                            val json = response?.body()
                            if (json != null) {
                                parseJson(apiUrl, json)
                            }
                            try {
                                val cacheDir = cache.parentFile
                                if (cacheDir != null) {
                                    if (!cacheDir.exists()) cacheDir.mkdirs()
                                }
                                if (cache.exists()) cache.delete()
                                val fos = FileOutputStream(cache)
                                if (json != null) {
                                    fos.write(json.toByteArray(charset("UTF-8")))
                                }
                                fos.flush()
                                fos.close()
                            } catch (th: Throwable) {
                                th.printStackTrace()
                            }
                            callback.success()
                        } catch (th: Throwable) {
                            th.printStackTrace()
                            callback.error("解析配置失败")
                        }
                    }

                    override fun onError(response: Response<String?>) {
                        super.onError(response)
                        if (cache.exists()) {
                            try {
                                parseJson(apiUrl, cache)
                                callback.success()
                                return
                            } catch (th: Throwable) {
                                th.printStackTrace()
                            }
                        }
                        callback.error("""
    拉取配置失败
    ${if (response.exception != null) response.exception.message else ""}
    """.trimIndent())
                    }

                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): String {
                        var result = ""
                        result = if (response.body() == null) {
                            ""
                        } else {
                            FindResult(response.body()!!.string(), configKey)
                        }
                        if (apiUrl.startsWith("clan")) {
                            result = clanContentFix(clanToAddress(apiUrl), result)
                        }
                        result = fixContentPath(apiUrl, result)
                        return result
                    }
                })
    }

    fun loadJar(useCache: Boolean, spider: String, callback: LoadConfigCallback) {
        val urls = spider.split(";md5;".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var jarUrl = urls[0]
        val md5 = if (urls.size > 1) urls[1].trim { it <= ' ' } else ""
        val cache = File(App.getInstance().filesDir.absolutePath + "/csp.jar")
        if (!md5.isEmpty() || useCache) {
            if (cache.exists() && (useCache || MD5.getFileMd5(cache).equals(md5, ignoreCase = true))) {
                if (jarLoader.load(cache.absolutePath)) {
                    callback.success()
                } else {
                    callback.error("从缓存加载jar失败")
                }
                return
            }
        }
        val isJarInImg = jarUrl.startsWith("img+")
        jarUrl = jarUrl.replace("img+", "")
        OkGo.get<File>(jarUrl)
                .headers("User-Agent", userAgent)
                .headers("Accept", requestAccept)
                .execute(object : AbsCallback<File?>() {
                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): File {
                        val cacheDir = cache.parentFile
                        if (!cacheDir.exists()) cacheDir.mkdirs()
                        if (cache.exists()) cache.delete()
                        val fos = FileOutputStream(cache)
                        if (isJarInImg) {
                            val respData = response.body()!!.string()
                            val imgJar = getImgJar(respData)
                            fos.write(imgJar)
                        } else {
                            fos.write(response.body()!!.bytes())
                        }
                        fos.flush()
                        fos.close()
                        return cache
                    }

                    override fun onSuccess(response: Response<File?>?) {
                        if (response?.body()?.exists() == true) {
                            if (jarLoader.load(response.body()?.absolutePath)) {
                                callback.success()
                            } else {
                                callback.error("从网络上加载jar写入缓存后加载失败")
                            }
                        } else {
                            callback.error("从网络上加载jar地址字节数据为空")
                        }
                    }

                    override fun onError(response: Response<File?>) {
                        super.onError(response)
                        callback.error("从网络上加载jar失败：" + response.exception.message)
                    }
                })
    }

    @Throws(Throwable::class)
    private fun parseJson(apiUrl: String, f: File) {
        println("从本地缓存加载" + f.absolutePath)
        val bReader = BufferedReader(InputStreamReader(FileInputStream(f), "UTF-8"))
        val sb = StringBuilder()
        var s = ""
        while (bReader.readLine().also { s = it } != null) {
            sb.append(s + "\n")
        }
        bReader.close()
        parseJson(apiUrl, sb.toString())
    }

    private fun parseJson(apiUrl: String, jsonStr: String) {
        val infoJson = Gson().fromJson(jsonStr, JsonObject::class.java)
        // spider
        spider = safeJsonString(infoJson, "spider", "")
        // wallpaper
        wallpaper = safeJsonString(infoJson, "wallpaper", "")
        // 远端站点源
        var firstSite: SourceBean? = null
        for (opt in infoJson["sites"].getAsJsonArray()) {
            val obj = opt as JsonObject
            val sb = SourceBean()
            val siteKey = obj["key"].asString.trim { it <= ' ' }
            sb.key = siteKey
            sb.name = obj["name"].asString.trim { it <= ' ' }
            sb.type = obj["type"].asInt
            sb.api = obj["api"].asString.trim { it <= ' ' }
            sb.setSearchable(safeJsonInt(obj, "searchable", 1))
            sb.setQuickSearch(safeJsonInt(obj, "quickSearch", 1))
            sb.filterable = safeJsonInt(obj, "filterable", 1)
            sb.hide = safeJsonInt(obj, "hide", 0)
            sb.playerUrl = safeJsonString(obj, "playUrl", "")
            if (obj.has("ext") && (obj["ext"].isJsonObject || obj["ext"].isJsonArray)) {
                sb.ext = obj["ext"].toString()
            } else {
                sb.ext = safeJsonString(obj, "ext", "")
            }
            sb.jar = safeJsonString(obj, "jar", "")
            sb.playerType = safeJsonInt(obj, "playerType", -1)
            sb.categories = safeJsonStringList(obj, "categories")
            sb.clickSelector = safeJsonString(obj, "click", "")
            if (firstSite == null && sb.hide == 0) firstSite = sb
            sourceBeanMap!![siteKey] = sb
        }
        if (sourceBeanMap != null && sourceBeanMap.size > 0) {
            val home = Hawk.get(HawkConfig.HOME_API, "")
            val sh = getSource(home)
            if (sh == null || sh.hide == 1) setSourceBean(firstSite) else setSourceBean(sh)
        }
        // 需要使用vip解析的flag
        vipParseFlags = safeJsonStringList(infoJson, "flags")
        // 解析地址
        parseBeanList!!.clear()
        if (infoJson.has("parses")) {
            val parses = infoJson["parses"].getAsJsonArray()
            for (opt in parses) {
                val obj = opt as JsonObject
                val pb = ParseBean()
                pb.name = obj["name"].asString.trim { it <= ' ' }
                pb.url = obj["url"].asString.trim { it <= ' ' }
                val ext = if (obj.has("ext")) obj["ext"].getAsJsonObject().toString() else ""
                pb.ext = ext
                pb.type = safeJsonInt(obj, "type", 0)
                parseBeanList.add(pb)
            }
        }
        // 获取默认解析
        if (parseBeanList != null && parseBeanList.size > 0) {
            val defaultParse = Hawk.get(HawkConfig.DEFAULT_PARSE, "")
            if (!TextUtils.isEmpty(defaultParse)) for (pb in parseBeanList) {
                if (pb.name == defaultParse) setDefaultParse(pb)
            }
            if (this.defaultParse == null) setDefaultParse(parseBeanList[0])
        }

        // takagen99: Check if Live URL is setup in Settings, if no, get from File Config
        liveChannelGroupList.clear() //修复从后台切换重复加载频道列表
        val liveURL = Hawk.get(HawkConfig.LIVE_URL, "")
        val epgURL = Hawk.get(HawkConfig.EPG_URL, "")
        var liveURL_final: String? = null
        try {
            if (infoJson.has("lives") && infoJson["lives"].getAsJsonArray() != null) {
                val livesOBJ = infoJson["lives"].getAsJsonArray()[0].getAsJsonObject()
                val lives = livesOBJ.toString()
                val index = lives.indexOf("proxy://")
                if (index != -1) {
                    val endIndex = lives.lastIndexOf("\"")
                    var url = lives.substring(index, endIndex)
                    url = checkReplaceProxy(url)

                    //clan
                    val extUrl = Uri.parse(url).getQueryParameter("ext")
                    if (extUrl != null && !extUrl.isEmpty()) {
                        var extUrlFix: String
                        extUrlFix = if (extUrl.startsWith("http") || extUrl.startsWith("clan://")) {
                            extUrl
                        } else {
                            String(Base64.decode(extUrl, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP), charset("UTF-8"))
                        }
                        if (extUrlFix.startsWith("clan://")) {
                            extUrlFix = clanContentFix(clanToAddress(apiUrl), extUrlFix)
                        }

                        // takagen99: Capture Live URL into Config
                        println("Live URL :$extUrlFix")
                        putLiveHistory(extUrlFix)
                        // Overwrite with Live URL from Settings
                        if (StringUtils.isBlank(liveURL)) {
                            Hawk.put(HawkConfig.LIVE_URL, extUrlFix)
                        } else {
                            extUrlFix = liveURL
                        }

                        // Final Live URL
                        liveURL_final = extUrlFix

//                    // Encoding the Live URL
//                    extUrlFix = Base64.encodeToString(extUrlFix.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
//                    url = url.replace(extUrl, extUrlFix);
                    }

                    // takagen99 : Getting EPG URL from File Config & put into Settings
                    if (livesOBJ.has("epg")) {
                        val epg = livesOBJ["epg"].asString
                        println("EPG URL :$epg")
                        putEPGHistory(epg)
                        // Overwrite with EPG URL from Settings
                        if (StringUtils.isBlank(epgURL)) {
                            Hawk.put(HawkConfig.EPG_URL, epg)
                        } else {
                            Hawk.put(HawkConfig.EPG_URL, epgURL)
                        }
                    }

//                // Populate Live Channel Listing
//                LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
//                liveChannelGroup.setGroupName(url);
//                liveChannelGroupList.add(liveChannelGroup);
                } else {

                    // if FongMi Live URL Formatting exists
                    if (!lives.contains("type")) {
                        loadLives(infoJson["lives"].getAsJsonArray())
                    } else {
                        val fengMiLives = infoJson["lives"].getAsJsonArray()[0].getAsJsonObject()
                        val type = fengMiLives["type"].asString
                        if (type == "0") {
                            var url = fengMiLives["url"].asString

                            // takagen99 : Getting EPG URL from File Config & put into Settings
                            if (fengMiLives.has("epg")) {
                                val epg = fengMiLives["epg"].asString
                                println("EPG URL :$epg")
                                putEPGHistory(epg)
                                // Overwrite with EPG URL from Settings
                                if (StringUtils.isBlank(epgURL)) {
                                    Hawk.put(HawkConfig.EPG_URL, epg)
                                } else {
                                    Hawk.put(HawkConfig.EPG_URL, epgURL)
                                }
                            }
                            if (url.startsWith("http")) {
                                // takagen99: Capture Live URL into Settings
                                println("Live URL :$url")
                                putLiveHistory(url)
                                // Overwrite with Live URL from Settings
                                if (StringUtils.isBlank(liveURL)) {
                                    Hawk.put(HawkConfig.LIVE_URL, url)
                                } else {
                                    url = liveURL
                                }

                                // Final Live URL
                                liveURL_final = url

//                            url = Base64.encodeToString(url.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                            }
                        }
                    }
                }

                // takagen99: Load Live Channel from settings URL (WIP)
                if (StringUtils.isBlank(liveURL_final)) {
                    liveURL_final = liveURL
                }
                liveURL_final = Base64.encodeToString(liveURL_final!!.toByteArray(charset("UTF-8")), Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP)
                liveURL_final = "http://127.0.0.1:9978/proxy?do=live&type=txt&ext=$liveURL_final"
                val liveChannelGroup = LiveChannelGroup()
                liveChannelGroup.groupName = liveURL_final
                liveChannelGroupList.add(liveChannelGroup)
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }

        // Video parse rule for host
        if (infoJson.has("rules")) {
            VideoParseRuler.clearRule()
            for (oneHostRule in infoJson.getAsJsonArray("rules")) {
                val obj = oneHostRule as JsonObject
                val hosts = ArrayList<String>()
                if (obj["host"] != null) {
                    hosts.add(obj["host"].asString)
                } else if (obj["hosts"] != null) {
                    for (host in obj["hosts"].getAsJsonArray()) {
                        hosts.add(host.asString)
                    }
                } else continue
                if (obj.has("rule")) {
                    val ruleJsonArr = obj.getAsJsonArray("rule")
                    val rule = ArrayList<String>()
                    for (one in ruleJsonArr) {
                        val oneRule = one.asString
                        rule.add(oneRule)
                    }
                    if (rule.size > 0) {
                        for (host in hosts) VideoParseRuler.addHostRule(host, rule)
                    }
                }
                if (obj.has("filter")) {
                    val filterJsonArr = obj.getAsJsonArray("filter")
                    val filter = ArrayList<String>()
                    for (one in filterJsonArr) {
                        val oneFilter = one.asString
                        filter.add(oneFilter)
                    }
                    if (filter.size > 0) {
                        for (host in hosts) VideoParseRuler.addHostFilter(host, filter)
                    }
                }
            }
        }
        val defaultIJKADS = "{\"ijk\":[{\"options\":[{\"name\":\"opensles\",\"category\":4,\"value\":\"0\"},{\"name\":\"overlay-format\",\"category\":4,\"value\":\"842225234\"},{\"name\":\"framedrop\",\"category\":4,\"value\":\"0\"},{\"name\":\"soundtouch\",\"category\":4,\"value\":\"1\"},{\"name\":\"start-on-prepared\",\"category\":4,\"value\":\"1\"},{\"name\":\"http-detect-rangeupport\",\"category\":1,\"value\":\"0\"},{\"name\":\"fflags\",\"category\":1,\"value\":\"fastseek\"},{\"name\":\"skip_loop_filter\",\"category\":2,\"value\":\"48\"},{\"name\":\"reconnect\",\"category\":4,\"value\":\"1\"},{\"name\":\"enable-accurate-seek\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-auto-rotate\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-handle-resolution-change\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-hevc\",\"category\":4,\"value\":\"0\"},{\"name\":\"dns_cache_timeout\",\"category\":1,\"value\":\"600000000\"}],\"group\":\"软解码\"},{\"options\":[{\"name\":\"opensles\",\"category\":4,\"value\":\"0\"},{\"name\":\"overlay-format\",\"category\":4,\"value\":\"842225234\"},{\"name\":\"framedrop\",\"category\":4,\"value\":\"0\"},{\"name\":\"soundtouch\",\"category\":4,\"value\":\"1\"},{\"name\":\"start-on-prepared\",\"category\":4,\"value\":\"1\"},{\"name\":\"http-detect-rangeupport\",\"category\":1,\"value\":\"0\"},{\"name\":\"fflags\",\"category\":1,\"value\":\"fastseek\"},{\"name\":\"skip_loop_filter\",\"category\":2,\"value\":\"48\"},{\"name\":\"reconnect\",\"category\":4,\"value\":\"1\"},{\"name\":\"enable-accurate-seek\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-auto-rotate\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-handle-resolution-change\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-hevc\",\"category\":4,\"value\":\"1\"},{\"name\":\"dns_cache_timeout\",\"category\":1,\"value\":\"600000000\"}],\"group\":\"硬解码\"}],\"ads\":[\"mimg.0c1q0l.cn\",\"www.googletagmanager.com\",\"www.google-analytics.com\",\"mc.usihnbcq.cn\",\"mg.g1mm3d.cn\",\"mscs.svaeuzh.cn\",\"cnzz.hhttm.top\",\"tp.vinuxhome.com\",\"cnzz.mmstat.com\",\"www.baihuillq.com\",\"s23.cnzz.com\",\"z3.cnzz.com\",\"c.cnzz.com\",\"stj.v1vo.top\",\"z12.cnzz.com\",\"img.mosflower.cn\",\"tips.gamevvip.com\",\"ehwe.yhdtns.com\",\"xdn.cqqc3.com\",\"www.jixunkyy.cn\",\"sp.chemacid.cn\",\"hm.baidu.com\",\"s9.cnzz.com\",\"z6.cnzz.com\",\"um.cavuc.com\",\"mav.mavuz.com\",\"wofwk.aoidf3.com\",\"z5.cnzz.com\",\"xc.hubeijieshikj.cn\",\"tj.tianwenhu.com\",\"xg.gars57.cn\",\"k.jinxiuzhilv.com\",\"cdn.bootcss.com\",\"ppl.xunzhuo123.com\",\"xomk.jiangjunmh.top\",\"img.xunzhuo123.com\",\"z1.cnzz.com\",\"s13.cnzz.com\",\"xg.huataisangao.cn\",\"z7.cnzz.com\",\"xg.huataisangao.cn\",\"z2.cnzz.com\",\"s96.cnzz.com\",\"q11.cnzz.com\",\"thy.dacedsfa.cn\",\"xg.whsbpw.cn\",\"s19.cnzz.com\",\"z8.cnzz.com\",\"s4.cnzz.com\",\"f5w.as12df.top\",\"ae01.alicdn.com\",\"www.92424.cn\",\"k.wudejia.com\",\"vivovip.mmszxc.top\",\"qiu.xixiqiu.com\",\"cdnjs.hnfenxun.com\",\"cms.qdwght.com\"]}"
        val defaultJson = Gson().fromJson(defaultIJKADS, JsonObject::class.java)
        // 广告地址
        if (AdBlocker.isEmpty()) {
//            AdBlocker.clear();
            //追加的广告拦截
            if (infoJson.has("ads")) {
                for (host in infoJson.getAsJsonArray("ads")) {
                    AdBlocker.addAdHost(host.asString)
                }
            } else {
                //默认广告拦截
                for (host in defaultJson.getAsJsonArray("ads")) {
                    AdBlocker.addAdHost(host.asString)
                }
            }
        }
        // IJK解码配置
        if (ijkCodes == null) {
            ijkCodes = ArrayList()
            var foundOldSelect = false
            var ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, "")
            val ijkJsonArray = if (infoJson.has("ijk")) infoJson["ijk"].getAsJsonArray() else defaultJson["ijk"].getAsJsonArray()
            for (opt in ijkJsonArray) {
                val obj = opt as JsonObject
                val name = obj["group"].asString
                val baseOpt = LinkedHashMap<String, String>()
                for (cfg in obj["options"].getAsJsonArray()) {
                    val cObj = cfg as JsonObject
                    val key = cObj["category"].asString + "|" + cObj["name"].asString
                    val `val` = cObj["value"].asString
                    baseOpt[key] = `val`
                }
                val codec = IJKCode()
                codec.name = name
                codec.option = baseOpt
                if (name == ijkCodec || TextUtils.isEmpty(ijkCodec)) {
                    codec.selected(true)
                    ijkCodec = name
                    foundOldSelect = true
                } else {
                    codec.selected(false)
                }
                ijkCodes?.add(codec)
            }
            if (!foundOldSelect && ijkCodes?.size?.let { it > 0 } == true) {
                ijkCodes?.get(0)?.selected(true)
            }
        }
    }

    private fun putLiveHistory(url: String) {
        if (!url.isEmpty()) {
            val liveHistory = Hawk.get(HawkConfig.LIVE_HISTORY, ArrayList<String>())
            if (!liveHistory.contains(url)) liveHistory.add(0, url)
            if (liveHistory.size > 20) liveHistory.removeAt(20)
            Hawk.put(HawkConfig.LIVE_HISTORY, liveHistory)
        }
    }

    fun loadLives(livesArray: JsonArray) {
        liveChannelGroupList.clear()
        var groupIndex = 0
        var channelIndex = 0
        var channelNum = 0
        for (groupElement in livesArray) {
            val liveChannelGroup = LiveChannelGroup()
            liveChannelGroup.liveChannels = ArrayList()
            liveChannelGroup.groupIndex = groupIndex++
            val groupName = (groupElement as JsonObject)["group"].asString.trim { it <= ' ' }
            val splitGroupName = groupName.split("_".toRegex(), limit = 2).toTypedArray()
            liveChannelGroup.groupName = splitGroupName[0]
            if (splitGroupName.size > 1) liveChannelGroup.groupPassword = splitGroupName[1] else liveChannelGroup.groupPassword = ""
            channelIndex = 0
            for (channelElement in groupElement["channels"].getAsJsonArray()) {
                val obj = channelElement as JsonObject
                val liveChannelItem = LiveChannelItem()
                liveChannelItem.channelName = obj["name"].asString.trim { it <= ' ' }
                liveChannelItem.channelIndex = channelIndex++
                liveChannelItem.channelNum = ++channelNum
                val urls = safeJsonStringList(obj, "urls")
                val sourceNames = ArrayList<String>()
                val sourceUrls = ArrayList<String>()
                var sourceIndex = 1
                for (url in urls) {
                    val splitText = url.split("\\$".toRegex(), limit = 2).toTypedArray()
                    sourceUrls.add(splitText[0])
                    if (splitText.size > 1) sourceNames.add(splitText[1]) else sourceNames.add("源$sourceIndex")
                    sourceIndex++
                }
                liveChannelItem.channelSourceNames = sourceNames
                liveChannelItem.setChannelUrls(sourceUrls)
                liveChannelGroup.liveChannels.add(liveChannelItem)
            }
            liveChannelGroupList.add(liveChannelGroup)
        }
    }

    fun getCSP(sourceBean: SourceBean): Spider {

        // Getting js api
        return if (sourceBean.api.endsWith(".js") || sourceBean.api.contains(".js?")) {
            jsLoader.getSpider(sourceBean.key, sourceBean.api, sourceBean.ext, sourceBean.jar)
        } else jarLoader.getSpider(sourceBean.key, sourceBean.api, sourceBean.ext, sourceBean.jar)
    }

    fun proxyLocal(param: Map<*, *>?): Array<Any> {
        return jarLoader.proxyInvoke(param)
    }

    fun jsonExt(key: String?, jxs: LinkedHashMap<String?, String?>?, url: String?): JSONObject {
        return jarLoader.jsonExt(key, jxs, url)
    }

    fun jsonExtMix(flag: String?, key: String?, name: String?, jxs: LinkedHashMap<String?, HashMap<String?, String?>?>?, url: String?): JSONObject {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url)
    }

    interface LoadConfigCallback {
        fun success()
        fun retry()
        fun error(msg: String)
    }

    interface FastParseCallback {
        fun success(parse: Boolean, url: String?, header: Map<String?, String?>?)
        fun fail(code: Int, msg: String?)
    }

    fun getSource(key: String): SourceBean? {
        return if (!sourceBeanMap!!.containsKey(key)) null else sourceBeanMap[key]
    }

    fun setSourceBean(sourceBean: SourceBean?) {
        mHomeSource = sourceBean
        Hawk.put(HawkConfig.HOME_API, sourceBean!!.key)
    }

    fun setDefaultParse(parseBean: ParseBean) {
        if (defaultParse != null) defaultParse!!.isDefault = false
        defaultParse = parseBean
        Hawk.put(HawkConfig.DEFAULT_PARSE, parseBean.name)
        parseBean.isDefault = true
    }

    fun getSourceBeanList(): List<SourceBean> {
        return ArrayList(sourceBeanMap!!.values)
    }

    fun getParseBeanList(): List<ParseBean>? {
        return parseBeanList
    }

    val homeSourceBean: SourceBean
        get() = if (mHomeSource == null) emptyHome else mHomeSource!!
    val channelGroupList: List<LiveChannelGroup>
        get() = liveChannelGroupList

    fun getIjkCodes(): List<IJKCode>? {
        return ijkCodes
    }

    val currentIJKCode: IJKCode
        get() {
            val codeName = Hawk.get(HawkConfig.IJK_CODEC, "")
            return getIJKCodec(codeName)
        }

    fun getIJKCodec(name: String): IJKCode {
        for (code in ijkCodes!!) {
            if (code.name == name) return code
        }
        return ijkCodes!![0]
    }

    fun clanToAddress(lanLink: String): String {
        return if (lanLink.startsWith("clan://localhost/")) {
            lanLink.replace("clan://localhost/", ControlManager.get().getAddress(true) + "file/")
        } else {
            val link = lanLink.substring(7)
            val end = link.indexOf('/')
            "http://" + link.substring(0, end) + "/file/" + link.substring(end + 1)
        }
    }

    fun clanContentFix(lanLink: String, content: String): String {
        val fix = lanLink.substring(0, lanLink.indexOf("/file/") + 6)
        return content.replace("clan://", fix)
    }

    fun fixContentPath(url: String, content: String): String {
        var url = url
        var content = content
        if (content.contains("\"./")) {
            if (!url.startsWith("http") && !url.startsWith("clan://")) {
                url = "http://$url"
            }
            if (url.startsWith("clan://")) url = clanToAddress(url)
            content = content.replace("./", url.substring(0, url.lastIndexOf("/") + 1))
        }
        return content
    }

    fun miTV(url: String): String {
        if (url.startsWith("p") || url.startsWith("mitv")) {
        }
        return url
    }

    companion object {
        private var instance: ApiConfig? = null
        @JvmStatic
        fun get(): ApiConfig {
            if (instance == null) {
                synchronized(ApiConfig::class.java) {
                    if (instance == null) {
                        instance = ApiConfig()
                    }
                }
            }
            return instance!!
        }

        fun FindResult(json: String, configKey: String?): String {
            var json = json
            var content = json
            try {
                if (AES.isJson(content)) return content
                val pattern = Pattern.compile("[A-Za-z0]{8}\\*\\*")
                val matcher = pattern.matcher(content)
                if (matcher.find()) {
                    content = content.substring(content.indexOf(matcher.group()) + 10)
                    content = String(Base64.decode(content, Base64.DEFAULT))
                }
                if (content.startsWith("2423")) {
                    val data = content.substring(content.indexOf("2324") + 4, content.length - 26)
                    content = String(AES.toBytes(content)).lowercase(Locale.getDefault())
                    val key = AES.rightPadding(content.substring(content.indexOf("$#") + 2, content.indexOf("#$")), "0", 16)
                    val iv = AES.rightPadding(content.substring(content.length - 13), "0", 16)
                    json = AES.CBC(data, key, iv)
                } else if (configKey != null && !AES.isJson(content)) {
                    json = AES.ECB(content, configKey)
                } else {
                    json = content
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return json
        }

        private fun getImgJar(body: String): ByteArray {
            var body = body
            val pattern = Pattern.compile("[A-Za-z0]{8}\\*\\*")
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                body = body.substring(body.indexOf(matcher.group()) + 10)
                return Base64.decode(body, Base64.DEFAULT)
            }
            return "".toByteArray()
        }

        fun putEPGHistory(url: String) {
            if (!url.isEmpty()) {
                val epgHistory = Hawk.get(HawkConfig.EPG_HISTORY, ArrayList<String>())
                if (!epgHistory.contains(url)) epgHistory.add(0, url)
                if (epgHistory.size > 20) epgHistory.removeAt(20)
                Hawk.put(HawkConfig.EPG_HISTORY, epgHistory)
            }
        }
    }
}