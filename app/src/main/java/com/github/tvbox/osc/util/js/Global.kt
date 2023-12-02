package com.github.tvbox.osc.util.js

import androidx.annotation.Keep
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.util.rsa.RSAEncrypt
import com.whl.quickjs.wrapper.*
import com.whl.quickjs.wrapper.Function
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.ExecutorService

class Global(var executor: ExecutorService) {
    private lateinit var runtime: QuickJSContext
    private val timer: Timer = Timer()

    @Keep
    @Function
    fun getProxy(local: Boolean): String {
        return ControlManager.get().getAddress(local) + "proxy?do=js"
    }

    @Keep
    @Function
    fun js2Proxy(dynamic: Boolean?, siteType: Int, siteKey: String, url: String?, headers: JSObject): String {
        return getProxy(true) + "&from=catvod" + "&siteType=" + siteType + "&siteKey=" + siteKey + "&header=" + URLEncoder.encode(
            headers.toJsonString()
        ) + "&url=" + URLEncoder.encode(url)
    }

    @Keep
    @Function
    fun joinUrl(parent: String?, child: String?): String {
        return HtmlParser.joinUrl(parent, child)
    }

    @Keep
    @Function
    fun pd(html: String?, rule: String?, add_url: String?): String {
        return HtmlParser.parseDomForUrl(html, rule, add_url)
    }

    @Keep
    @Function
    fun pdfh(html: String?, rule: String?): String {
        return HtmlParser.parseDomForUrl(html, rule, "")
    }

    @Keep
    @Function
    fun pdfa(html: String?, rule: String?): JSArray {
        return JSUtils<String>().toArray(runtime, HtmlParser.parseDomForArray(html, rule))
    }

    @Keep
    @Function
    fun pdfla(html: String?, p1: String?, list_text: String?, list_url: String?, add_url: String?): JSArray {
        return JSUtils<String>().toArray(runtime, HtmlParser.parseDomForList(html, p1, list_text, list_url, add_url))
    }

    @Keep
    @Function
    fun s2t(text: String?): String {
        return try {
            Trans.s2t(false, text)
        } catch (e: Exception) {
            ""
        }
    }

    @Keep
    @Function
    fun t2s(text: String?): String {
        return try {
            Trans.t2s(false, text)
        } catch (e: Exception) {
            ""
        }
    }

    @Keep
    @Function
    fun aesX(
        mode: String?,
        encrypt: Boolean,
        input: String?,
        inBase64: Boolean,
        key: String?,
        iv: String?,
        outBase64: Boolean
    ): String {
        //LOG.e("aesX",String.format("mode:%s\nencrypt:%s\ninBase64:%s\noutBase64:%s\nkey:%s\niv:%s\ninput:\n%s\nresult:\n%s", mode, encrypt, inBase64, outBase64, key, iv, input, result));
        return Crypto.aes(mode, encrypt, input, inBase64, key, iv, outBase64)
    }

    @Keep
    @Function
    fun rsaX(
        mode: String?,
        pub: Boolean,
        encrypt: Boolean,
        input: String?,
        inBase64: Boolean,
        key: String?,
        outBase64: Boolean
    ): String {
        //LOG.e("aesX",String.format("mode:%s\npub:%s\nencrypt:%s\ninBase64:%s\noutBase64:%s\nkey:\n%s\ninput:\n%s\nresult:\n%s", mode, pub, encrypt, inBase64, outBase64, key, input, result));
        return Crypto.rsa(pub, encrypt, input, inBase64, key, outBase64)
    }

    @Keep
    @Function
    fun rsaEncrypt(data: String?, key: String?): String {
        return rsaEncrypt(data, key, null)
    }

    /**
     * RSA 加密
     *
     * @param data    要加密的数据
     * @param key     密钥，type 为 1 则公钥，type 为 2 则私钥
     * @param options 加密的选项，包含加密配置和类型：{ config: "RSA/ECB/PKCS1Padding", type: 1, long: 1 }
     * config 加密的配置，默认 RSA/ECB/PKCS1Padding （可选）
     * type 加密类型，1 公钥加密 私钥解密，2 私钥加密 公钥解密（可选，默认 1）
     * long 加密方式，1 普通，2 分段（可选，默认 1）
     * block 分段长度，false 固定117，true 自动（可选，默认 true ）
     * @return 返回加密结果
     */
    @Keep
    @Function
    fun rsaEncrypt(data: String?, key: String?, options: JSObject?): String {
        var mLong = 1
        var mType = 1
        var mBlock = true
        var mConfig: String? = null
        if (options != null) {
            val op = options.toJsonObject()
            if (op.has("config")) {
                try {
                    mConfig = op["config"] as String
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (op.has("type")) {
                try {
                    mType = (op["type"] as Double).toInt()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (op.has("long")) {
                try {
                    mLong = (op["long"] as Double).toInt()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (op.has("block")) {
                try {
                    mBlock = op["block"] as Boolean
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return try {
            when (mType) {
                1 -> if (mConfig != null) {
                    RSAEncrypt.encryptByPublicKey(data, key, mConfig, mLong, mBlock)
                } else {
                    RSAEncrypt.encryptByPublicKey(data, key, mLong, mBlock)
                }

                2 -> if (mConfig != null) {
                    RSAEncrypt.encryptByPrivateKey(data, key, mConfig, mLong, mBlock)
                } else {
                    RSAEncrypt.encryptByPrivateKey(data, key, mLong, mBlock)
                }

                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    @Keep
    @Function
    fun rsaDecrypt(encryptBase64Data: String?, key: String?): String {
        return rsaDecrypt(encryptBase64Data, key, null)
    }

    /**
     * RSA 解密
     *
     * @param encryptBase64Data 加密后的 Base64 字符串
     * @param key               密钥，type 为 1 则私钥，type 为 2 则公钥
     * @param options           解密的选项，包含解密配置和类型：{ config: "RSA/ECB/PKCS1Padding", type: 1, long: 1 }
     * config 解密的配置，默认 RSA/ECB/PKCS1Padding （可选）
     * type 解密类型，1 公钥加密 私钥解密，2 私钥加密 公钥解密（可选，默认 1）
     * long 解密方式，1 普通，2 分段（可选，默认 1）
     * block 分段长度，false 固定128，true 自动（可选，默认 true ）
     * @return 返回解密结果
     */
    @Keep
    @Function
    fun rsaDecrypt(encryptBase64Data: String?, key: String?, options: JSObject?): String {
        var mLong = 1
        var mType = 1
        var mBlock = true
        var mConfig: String? = null
        if (options != null) {
            val op = options.toJsonObject()
            if (op.has("config")) {
                try {
                    mConfig = op["config"] as String
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (op.has("type")) {
                try {
                    mType = (op["type"] as Double).toInt()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (op.has("long")) {
                try {
                    mLong = (op["long"] as Double).toInt()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (op.has("block")) {
                try {
                    mBlock = op["block"] as Boolean
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return try {
            when (mType) {
                1 -> if (mConfig != null) {
                    RSAEncrypt.decryptByPrivateKey(encryptBase64Data, key, mConfig, mLong, mBlock)
                } else {
                    RSAEncrypt.decryptByPrivateKey(encryptBase64Data, key, mLong, mBlock)
                }

                2 -> if (mConfig != null) {
                    RSAEncrypt.decryptByPublicKey(encryptBase64Data, key, mConfig, mLong, mBlock)
                } else {
                    RSAEncrypt.decryptByPublicKey(encryptBase64Data, key, mLong, mBlock)
                }

                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun req(url: String, options: JSObject): JSObject {
        return try {
            val req = Req.objectFrom(options.toJsonObject().toString())
            val res = Connect.to(url, req).execute()
            Connect.success(runtime, req, res)
        } catch (e: Exception) {
            Connect.error(runtime)
        }
    }

    @Keep
    @Function
    fun _http(url: String, options: JSObject): JSObject? {
        val complete = options.getJSFunction("complete") ?: return req(url, options)
        val req = Req.objectFrom(options.toJsonObject().toString())
        Connect.to(url, req).enqueue(getCallback(complete, req))
        return null
    }

    @Keep
    @Function
    fun setTimeout(func: JSFunction, delay: Int) {
        func.hold()
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (!executor.isShutdown) executor.submit { func.call() }
            }
        }, delay.toLong())
    }

    private fun getCallback(complete: JSFunction, req: Req): Callback {
        return object : Callback {
            override fun onResponse(call: Call, res: Response) {
                executor.submit { complete.call(Connect.success(runtime, req, res)) }
            }

            override fun onFailure(call: Call, e: IOException) {
                executor.submit { complete.call(Connect.error(runtime)) }
            }
        }
    }

    @Keep // 声明用于依赖注入的 QuickJSContext
    @ContextSetter
    fun setJSContext(runtime: QuickJSContext) {
        if (!::runtime.isInitialized){
            this.runtime = runtime
        }
    }
}