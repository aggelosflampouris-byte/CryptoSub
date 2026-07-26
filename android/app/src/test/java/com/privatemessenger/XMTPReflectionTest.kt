package com.privatemessenger

import org.junit.Test
import org.xmtp.android.library.Group
import org.xmtp.android.library.Member

class XMTPReflectionTest {
    @Test
    fun printGroupMethods() {
        println("Group methods:")
        Group::class.java.methods.forEach { println(it.name + " returns " + it.returnType.name) }
        println("Member methods:")
        Member::class.java.methods.forEach { println(it.name + " returns " + it.returnType.name) }
    }
}
