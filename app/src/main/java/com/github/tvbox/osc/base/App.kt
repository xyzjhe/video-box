package com.github.tvbox.osc.base

import androidx.multidex.MultiDexApplication
import com.github.catvod.crawler.JsLoader
import com.github.tvbox.osc.callback.EmptyCallback
import com.github.tvbox.osc.callback.LoadingCallback
import com.github.tvbox.osc.data.AppDataManager
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.util.*
import com.kingja.loadsir.core.LoadSir
import com.orhanobut.hawk.Hawk
import com.p2p.P2PClass
import com.whl.quickjs.android.QuickJSLoader
import me.jessyan.autosize.AutoSizeConfig
import me.jessyan.autosize.unit.Subunits

/**
 * @author pj567
 * @date :2020/12/17
 * @description:
 */
class App : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        initParams()
        // takagen99 : Initialize Locale
        initLocale()
        // OKGo
        OkGoHelper.init()
        // Get EPG Info
        EpgUtil.init()
        // 初始化Web服务器
        ControlManager.init(this)
        //初始化数据库
        AppDataManager.init()
        LoadSir.beginBuilder()
            .addCallback(EmptyCallback())
            .addCallback(LoadingCallback())
            .commit()
        AutoSizeConfig.getInstance().setCustomFragment(true).unitsManager
            .setSupportDP(false)
            .setSupportSP(false)
            .setSupportSubunits(Subunits.MM)
        PlayerHelper.init()

        // Delete Cache
        /*File dir = getCacheDir();
        FileUtils.recursiveDelete(dir);
        dir = getExternalCacheDir();
        FileUtils.recursiveDelete(dir);*/
        FileUtils.cleanPlayerCache()

        // Add JS support
        QuickJSLoader.init()
    }

    private fun initParams() {
        // Hawk
        Hawk.init(this).build()
        Hawk.put(HawkConfig.DEBUG_OPEN, false)

        // 首页选项
        putDefault(HawkConfig.HOME_SHOW_SOURCE, true) //数据源显示: true=开启, false=关闭
        putDefault(HawkConfig.HOME_SEARCH_POSITION, false) //按钮位置-搜索: true=上方, false=下方
        putDefault(HawkConfig.HOME_MENU_POSITION, true) //按钮位置-设置: true=上方, false=下方
        putDefault(HawkConfig.HOME_REC, 2) //推荐: 0=豆瓣热播, 1=站点推荐, 2=观看历史
        putDefault(HawkConfig.HOME_NUM, 4) //历史条数: 0=20条, 1=40条, 2=60条, 3=80条, 4=100条
        // 播放器选项
        putDefault(HawkConfig.SHOW_PREVIEW, true) //窗口预览: true=开启, false=关闭
        putDefault(HawkConfig.PLAY_SCALE, 0) //画面缩放: 0=默认, 1=16:9, 2=4:3, 3=填充, 4=原始, 5=裁剪
        putDefault(HawkConfig.PIC_IN_PIC, true) //画中画: true=开启, false=关闭
        putDefault(HawkConfig.PLAY_TYPE, 1) //播放器: 0=系统, 1=IJK, 2=Exo, 3=MX, 4=Reex, 5=Kodi
        putDefault(HawkConfig.IJK_CODEC, "硬解码") //IJK解码: 软解码, 硬解码
        // 系统选项
        putDefault(HawkConfig.HOME_LOCALE, 0) //语言: 0=中文, 1=英文
        putDefault(HawkConfig.THEME_SELECT, 0) //主题: 0=奈飞, 1=哆啦, 2=百事, 3=鸣人, 4=小黄, 5=八神, 6=樱花
        putDefault(HawkConfig.SEARCH_VIEW, 1) //搜索展示: 0=文字列表, 1=缩略图
        putDefault(HawkConfig.PARSE_WEBVIEW, true) //嗅探Webview: true=系统自带, false=XWalkView
        putDefault(HawkConfig.DOH_URL, 0) //安全DNS: 0=关闭, 1=腾讯, 2=阿里, 3=360, 4=Google, 5=AdGuard, 6=Quad9
    }

    private fun initLocale() {
        if (Hawk.get(HawkConfig.HOME_LOCALE, 0) == 0) {
            LocaleHelper.setLocale(this@App, "zh")
        } else {
            LocaleHelper.setLocale(this@App, "")
        }
    }

    private fun putDefault(key: String, value: Any) {
        if (!Hawk.contains(key)) {
            Hawk.put(key, value)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        JsLoader.load()
    }

    companion object {
        @JvmStatic
        private lateinit var instance: App
        private var p: P2PClass? = null
        @JvmField
        var burl: String? = null
        @JvmField
        var dashData: String? = null
        @JvmStatic
        fun getp2p(): P2PClass? {
            return try {
                if (p == null) {
                    p = P2PClass(
                        instance.externalCacheDir!!.absolutePath
                    )
                }
                p
            } catch (e: Exception) {
                LOG.e(e.toString())
                null
            }
        }
        @JvmStatic
        fun getInstance(): App {
            return instance
        }
    }
}