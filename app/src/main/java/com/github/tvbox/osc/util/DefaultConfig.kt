package com.github.tvbox.osc.util

import android.content.Context
import android.content.pm.PackageManager
import android.text.TextUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.MovieSort.SortData
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.ui.activity.HomeActivity.Companion.res
import com.google.gson.JsonObject
import com.hjq.permissions.Permission
import java.util.*
import java.util.regex.Pattern

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
object DefaultConfig {
    fun adjustSort(sourceKey: String?, list: List<SortData>, withMy: Boolean): List<SortData> {
        val data: MutableList<SortData> = ArrayList()
        if (sourceKey != null) {
            val sb = ApiConfig.get()?.getSource(sourceKey)
            val categories = sb?.categories
            if (categories != null) {
                if (categories.isNotEmpty()) {
                    for (cate in categories) {
                        for (sortData in list) {
                            if (sortData.name == cate) {
                                data.add(sortData)
                            }
                        }
                    }
                } else {
                    for (sortData in list) {
                        data.add(sortData)
                    }
                }
            }
        }
        if (withMy) data.add(0, SortData("my0", res!!.getString(R.string.app_home)))
        data.sort()
        return data
    }

    fun getAppVersionCode(mContext: Context): Int {
        //包管理操作管理类
        val pm = mContext.packageManager
        try {
            val packageInfo = pm.getPackageInfo(mContext.packageName, 0)
            return packageInfo.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return -1
    }

    fun getAppVersionName(mContext: Context): String {
        //包管理操作管理类
        val pm = mContext.packageManager
        try {
            val packageInfo = pm.getPackageInfo(mContext.packageName, 0)
            return packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return ""
    }

    /**
     * 后缀
     *
     * @param name
     * @return
     */
    fun getFileSuffix(name: String): String {
        if (TextUtils.isEmpty(name)) {
            return ""
        }
        val endP = name.lastIndexOf(".")
        return if (endP > -1) name.substring(endP) else ""
    }

    /**
     * 获取文件的前缀
     *
     * @param fileName
     * @return
     */
    fun getFilePrefixName(fileName: String): String {
        if (TextUtils.isEmpty(fileName)) {
            return ""
        }
        val start = fileName.lastIndexOf(".")
        return if (start > -1) fileName.substring(0, start) else fileName
    }

    // takagen99 : 增加对flv|avi|mkv|rm|wmv|mpg等几种视频格式的支持
    private val snifferMatch = Pattern.compile(
            "http((?!http).){20,}?\\.(m3u8|mp4|flv|avi|mkv|rm|wmv|mpg)\\?.*|" +
                    "http((?!http).){20,}\\.(m3u8|mp4|flv|avi|mkv|rm|wmv|mpg)|" +
                    "http((?!http).)*?video/tos*|" +
                    "http((?!http).){20,}?/m3u8\\?pt=m3u8.*|" +
                    "http((?!http).)*?default\\.ixigua\\.com/.*|" +
                    "http((?!http).)*?dycdn-tos\\.pstatp[^\\?]*|" +
                    "http.*?/player/m3u8play\\.php\\?url=.*|" +
                    "http.*?/player/.*?[pP]lay\\.php\\?url=.*|" +
                    "http.*?/playlist/m3u8/\\?vid=.*|" +
                    "http.*?\\.php\\?type=m3u8&.*|" +
                    "http.*?/download.aspx\\?.*|" +
                    "http.*?/api/up_api.php\\?.*|" +
                    "https.*?\\.66yk\\.cn.*|" +
                    "http((?!http).)*?netease\\.com/file/.*"
    )

    @JvmStatic
    fun isVideoFormat(url: String): Boolean {
        if (url.contains("=http")) {
            return false
        }
        return if (snifferMatch.matcher(url).find()) {
            !url.contains(".js") && !url.contains(".css") && !url.contains(".jpg") && !url.contains(".png") && !url.contains(".gif") && !url.contains(".ico") && !url.contains("rl=") && !url.contains(".html")
        } else false
    }

    @JvmStatic
    fun safeJsonString(obj: JsonObject, key: String?, defaultVal: String): String {
        try {
            return if (obj.has(key)) obj.getAsJsonPrimitive(key).getAsString().trim { it <= ' ' } else defaultVal
        } catch (th: Throwable) {
            LOG.e(th)
        }
        return defaultVal
    }

    @JvmStatic
    fun safeJsonInt(obj: JsonObject, key: String?, defaultVal: Int): Int {
        try {
            return if (obj.has(key)) obj.getAsJsonPrimitive(key).asInt else defaultVal
        } catch (th: Throwable) {
            LOG.e(th)
        }
        return defaultVal
    }

    @JvmStatic
    fun safeJsonStringList(obj: JsonObject, key: String?): ArrayList<String> {
        val result = ArrayList<String>()
        try {
            if (obj.has(key)) {
                if (obj[key].isJsonObject) {
                    result.add(obj[key].asString)
                } else {
                    for (opt in obj.getAsJsonArray(key)) {
                        result.add(opt.asString)
                    }
                }
            }
        } catch (th: Throwable) {
            LOG.e(th)
        }
        return result
    }

    @JvmStatic
    fun checkReplaceProxy(urlOri: String): String {
        return if (urlOri.startsWith("proxy://")) urlOri.replace("proxy://", ControlManager.get().getAddress(true) + "proxy?") else urlOri
    }

    @JvmStatic
    fun StoragePermissionGroup(): Array<String> {
        return arrayOf(
                Permission.MANAGE_EXTERNAL_STORAGE
        )
    }
}