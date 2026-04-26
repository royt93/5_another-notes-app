package com.mckimquyen.notes

import org.junit.Test
import com.roy.sdkadbmob.AdSdkConfig

class AdSdkTest {
    @Test
    fun printAdSdkConfig() {
        val clazz = AdSdkConfig::class.java
        println("--- AdSdkConfig constructors ---")
        clazz.constructors.forEach { constructor ->
            println(constructor)
            constructor.parameters.forEach { param ->
                println("  param: ${param.name} type: ${param.type.name}")
            }
        }
    }
}
