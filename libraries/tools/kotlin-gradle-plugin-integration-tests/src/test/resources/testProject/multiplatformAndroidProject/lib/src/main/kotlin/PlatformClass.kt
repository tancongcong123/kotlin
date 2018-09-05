package foo

import kotlin.js.JsName
import kotlin.jvm.JvmName

expect class PlatformClass {
    val value: String
}

class CommonClass {
    @JsName("jsFun")
    @JvmName("jvmFun")
    fun commonFun() { }
}