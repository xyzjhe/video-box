package com.github.tvbox.osc.util.js

import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSObject
import java9.util.concurrent.CompletableFuture

class Async private constructor() {
    private val future: CompletableFuture<Any?> = CompletableFuture()
    private fun call(`object`: JSObject, name: String, args: Array<out Any>): CompletableFuture<Any?> {
        val function = `object`.getJSFunction(name) ?: return empty()
        val result = function.call(*args)
        (result as? JSObject)?.let { then(it) } ?: future.complete(result)
        return future
    }

    private fun empty(): CompletableFuture<Any?> {
        future.complete(null)
        return future
    }

    private fun then(result: Any) {
        val promise = result as JSObject
        val then = promise.getJSFunction("then")
        then?.call(callback)
    }

    private val callback = JSCallFunction { args ->
        future.complete(args[0])
        null
    }

    companion object {
        @JvmStatic
        fun run(`object`: JSObject, name: String, args: Array<out Any>): CompletableFuture<Any?> {
            return Async().call(`object`, name, args)
        }
    }
}