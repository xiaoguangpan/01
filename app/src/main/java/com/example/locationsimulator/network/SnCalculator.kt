package com.example.locationsimulator.network

import java.net.URLEncoder
import java.security.MessageDigest

object SnCalculator {

    fun calculateSn(ak: String, sk: String, query: String, region: String): String {
        // 使用 sortedMapOf 保证参数按key的字母顺序排序
        val params = sortedMapOf<String, String>()
        params["query"] = query
        params["region"] = region
        params["ak"] = ak
        // city_limit is not part of SN calculation according to some docs, but let's be safe
        // It's a boolean, but the API expects a string. Let's add it.
        params["city_limit"] = "true"
        params["output"] = "json"


        // 构造待签名的字符串
        val queryString = toQueryString(params)

        // 拼接sk
        val stringToSign = "/place/v3/suggestion?" + queryString + sk

        // URL编码
        val encodedString = URLEncoder.encode(stringToSign, "UTF-8")

        // MD5加密
        return md5(encodedString)
    }

    private fun toQueryString(params: Map<String, String>): String {
        return params.map { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }.joinToString("&")
    }

    private fun md5(str: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val result = digest.digest(str.toByteArray())
        return result.joinToString("") { "%02x".format(it) }
    }
}
