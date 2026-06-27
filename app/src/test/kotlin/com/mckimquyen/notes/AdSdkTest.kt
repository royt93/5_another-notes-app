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
        }
    }

    @Test
    fun printAdSafetyLimits() {
        val clazz = com.roy.sdkadbmob.AdSafetyLimits::class.java
        println("--- AdSafetyLimits constructors ---")
        clazz.constructors.forEach { constructor ->
            println(constructor)
            constructor.parameters.forEach { param ->
                println("  param: ${param.name} type: ${param.type.name}")
            }
        }
        println("--- AdSafetyLimits declared fields ---")
        clazz.declaredFields.forEach { field ->
            println("  field: ${field.name} type: ${field.type.name}")
        }
    }
}
