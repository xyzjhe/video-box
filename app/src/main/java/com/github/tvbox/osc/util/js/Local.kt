package com.github.tvbox.osc.util.js

import androidx.annotation.Keep
import com.orhanobut.hawk.Hawk
import com.whl.quickjs.wrapper.Function

class Local {
    @Keep
    @Function
    fun delete(str: String, str2: String) {
        try {
            Hawk.delete("jsRuntime_" + str + "_" + str2)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Keep
    @Function
    operator fun get(str: String, str2: String): String {
        return try {
            Hawk.get("jsRuntime_" + str + "_" + str2, "")
        } catch (e: Exception) {
            Hawk.delete(str)
            str2
        }
    }

    @Keep
    @Function
    operator fun set(str: String, str2: String, str3: String) {
        try {
            Hawk.put("jsRuntime_" + str + "_" + str2, str3)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}