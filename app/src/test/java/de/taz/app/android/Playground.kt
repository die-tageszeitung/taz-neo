package de.taz.app.android

import kotlinx.coroutines.*
import org.junit.Test

class Playground {

    private val longThings: HashMap<String, Deferred<Int>> = HashMap()

    @Test
    fun testCoroutines() = runBlocking {
        longThings["tag"] = doALongThing()
        val job1 = CoroutineScope(Dispatchers.Default).launch {
            delay(400)
            val retval = longThings["tag"]!!.await()
            println("Job1: Nice got my Int: $retval")
        }

        val job2 = CoroutineScope(Dispatchers.Default).launch {
            delay(10000)
            val retval = longThings["tag"]!!.await()
            println("Job2: Nice got my Int: $retval")
        }
        val mainretval = longThings["tag"]!!.await()
        job1.join()
        job2.join()
        println("Main: Nice got my Int: $mainretval")
    }

    private fun doALongThing(): Deferred<Int> = CoroutineScope(Dispatchers.IO).async {
        delay(2000)
        println("The long thing has finished")
        42
    }
}