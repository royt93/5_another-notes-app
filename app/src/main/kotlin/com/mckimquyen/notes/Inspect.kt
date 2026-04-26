package com.mckimquyen.notes

import com.roy.sdkadbmob.AdSdkConfig

fun printAdSdkConfigFields() {
    val clazz = AdSdkConfig::class.java
    println("--- AdSdkConfig constructors ---")
    clazz.constructors.forEach { constructor ->
        println(constructor)
        constructor.parameters.forEach { param ->
            println("  param: ${param.name} type: ${param.type.name}")
        }
    }
}
