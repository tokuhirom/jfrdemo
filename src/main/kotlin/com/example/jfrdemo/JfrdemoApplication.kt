package com.example.jfrdemo

import jdk.jfr.Configuration
import jdk.jfr.consumer.RecordingStream
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

val samples = mutableMapOf<String, MutableList<String>>()

@SpringBootApplication
class JfrdemoApplication

@Controller
class JfrController {
    @GetMapping("/")
    fun index(model: Model): String {
        val sampledKeys = samples.keys.toSet()
        val keys: List<Pair<String, Boolean>> = Configuration.getConfiguration("default").settings.keys.map {
            it.replace("#.*".toRegex(), "")
        }.toSet().sortedBy { it }.map {
            it to sampledKeys.contains(it)
        }
        model.addAttribute("keys", keys)
        return "index"
    }

    @GetMapping("/sample")
    fun sample(@RequestParam("key") key: String, model: Model): String {
        model.addAttribute("key", key)
        model.addAttribute("values", samples[key])
        return "sample"
    }
}

data class Leaker(val s: String)

@Controller
class HeavyOperationController {
    var leaker: MutableList<Leaker> = mutableListOf()

    @GetMapping("/http_get")
    @ResponseBody
    fun httpGet(@RequestParam("url") url: String): String {
        val client = HttpClient.newBuilder().build()
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .build();
        val response = client.send(req, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }

    @GetMapping("/gc")
    @ResponseBody
    fun gc(): String {
        System.gc()
        return "Called System.gc()"
    }

    @GetMapping("/leak")
    @ResponseBody
    fun leak(@RequestParam("n") n: Int): String {
        this.leaker.add(Leaker((0..n).map { it.toString() }.joinToString("")))
        return "Called Leaker"
    }
}

fun main(args: Array<String>) {
    val rs = RecordingStream()
    enableAllConfiguration(rs)

    // JavaMonitorBlocked
    rs.onEvent { event ->
        // println("Event::\n${event}")
        val name: String = event.eventType.name
        if (!samples.containsKey(name)) {
            samples[name] = mutableListOf()
        }
        val list = samples[name]!!
        list.add(event.toString())
        if (list.size > 5) {
            list.removeAt(0)
        }
    }
    rs.startAsync()

    runApplication<JfrdemoApplication>(*args)
}

fun enableAllConfiguration(rs: RecordingStream) {
    val config: Configuration = Configuration.getConfiguration("default")
    config.settings.map { (key, _) ->
        key.replace("#.*".toRegex(), "")
    }.toSet().forEach {
        rs.enable(it).withPeriod(Duration.ofSeconds(1))
    }
}
