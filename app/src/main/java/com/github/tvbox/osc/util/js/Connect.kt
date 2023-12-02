package com.github.tvbox.osc.util.js

import android.util.Base64
import com.github.catvod.net.OkHttp
import com.github.tvbox.osc.util.LOG
import com.google.common.net.HttpHeaders
import com.lzy.okgo.OkGo
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.JSUtils
import com.whl.quickjs.wrapper.QuickJSContext
import okhttp3.*
import java.util.*

object Connect {
    var client: OkHttpClient? = null
    fun to(url: String, req: Req): Call {
        val client = OkHttp.client(req.isRedirect, req.timeout)
        return client.newCall(getRequest(url, req, Headers.of(req.header)))
    }

    fun success(ctx: QuickJSContext, req: Req, res: Response): JSObject {
        return try {
            val jsObject = ctx.createJSObject()
            val jsHeader = ctx.createJSObject()
            setHeader(ctx, res, jsHeader)
            jsObject["headers"] = jsHeader
            if (req.buffer == 0) jsObject["content"] = String(res.body()!!.bytes(), charset(req.getCharset()))
            if (req.buffer == 1) {
                val array = ctx.createJSArray()
                for (aByte in res.body()!!.bytes()) array.push(aByte.toInt())
                jsObject["content"] = array
            }
            if (req.buffer == 2) jsObject["content"] = Base64.encodeToString(res.body()!!.bytes(), Base64.DEFAULT)
            jsObject
        } catch (e: Exception) {
            error(ctx)
        }
    }

    fun error(ctx: QuickJSContext): JSObject {
        val jsObject = ctx.createJSObject()
        val jsHeader = ctx.createJSObject()
        jsObject["headers"] = jsHeader
        jsObject["content"] = ""
        return jsObject
    }

    private fun getRequest(url: String, req: Req, headers: Headers): Request {
        return if (req.method.equals("post", ignoreCase = true)) {
            Request.Builder().url(url).tag("js_okhttp_tag").headers(headers)
                .post(getPostBody(req, headers[HttpHeaders.CONTENT_TYPE])).build()
        } else if (req.method.equals("header", ignoreCase = true)) {
            Request.Builder().url(url).tag("js_okhttp_tag").headers(headers).head().build()
        } else {
            Request.Builder().url(url).tag("js_okhttp_tag").headers(headers).get().build()
        }
    }

    private fun getPostBody(req: Req, contentType: String?): RequestBody {
        if (req.data != null && req.postType == "json") return getJsonBody(req)
        if (req.data != null && req.postType == "form") return getFormBody(req)
        if (req.data != null && req.postType == "form-data") return getFormDataBody(req)
        return if (req.body != null && contentType != null) RequestBody.create(
            MediaType.get(contentType),
            req.body
        ) else RequestBody.create(
            null,
            ""
        )
    }

    private fun getJsonBody(req: Req): RequestBody {
        return RequestBody.create(MediaType.get("application/json"), req.data.toString())
    }

    private fun getFormBody(req: Req): RequestBody {
        val formBody = FormBody.Builder()
        val params = Json.toMap(req.data)
        for (key in params.keys) formBody.add(key, params[key])
        return formBody.build()
    }

    private fun getFormDataBody(req: Req): RequestBody {
        val boundary = "--dio-boundary-" + Random().nextInt(42949) + "" + Random().nextInt(67296)
        val builder = MultipartBody.Builder(boundary).setType(MultipartBody.FORM)
        val params = Json.toMap(req.data)
        for (key in params.keys) builder.addFormDataPart(key, params[key])
        return builder.build()
    }

    private fun setHeader(ctx: QuickJSContext, res: Response, `object`: JSObject) {
        for ((key, value) in res.headers().toMultimap()) {
            if (value.size == 1) `object`[key] = value[0]
            if (value.size >= 2) `object`[key] = JSUtils<String>().toArray(ctx, value)
        }
    }

    fun cancelByTag(tag: Any) {
        try {
            if (client != null) {
                for (call in client!!.dispatcher().queuedCalls()) {
                    if (tag == call.request().tag()) {
                        call.cancel()
                    }
                }
                for (call in client!!.dispatcher().runningCalls()) {
                    if (tag == call.request().tag()) {
                        call.cancel()
                    }
                }
            }
            OkGo.getInstance().cancelTag(tag)
        } catch (e: Exception) {
            LOG.e(e)
        }
    }
}