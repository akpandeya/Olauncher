package app.olauncher.data

import java.net.URLEncoder

sealed interface SearchTarget {
    data class PhoneDefault(val query: String) : SearchTarget
    data class Url(val url: String) : SearchTarget
}

object SearchEngine {
    const val PHONE_DEFAULT = "PHONE_DEFAULT"
    const val GOOGLE = "GOOGLE"
    const val DUCKDUCKGO = "DUCKDUCKGO"
    const val BING = "BING"
    const val CUSTOM = "CUSTOM"

    const val TEMPLATE_GOOGLE = "https://www.google.com/search?q=%s"
    const val TEMPLATE_DUCKDUCKGO = "https://duckduckgo.com/?q=%s"
    const val TEMPLATE_BING = "https://www.bing.com/search?q=%s"

    const val URL_GOOGLE = TEMPLATE_GOOGLE
    const val URL_DUCKDUCKGO = TEMPLATE_DUCKDUCKGO
    const val URL_BING = TEMPLATE_BING

    fun defaultTemplate(engineId: String): String? = when (engineId) {
        GOOGLE -> TEMPLATE_GOOGLE
        DUCKDUCKGO -> TEMPLATE_DUCKDUCKGO
        BING -> TEMPLATE_BING
        else -> null
    }

    fun isValidTemplate(template: String): Boolean {
        val trimmed = template.trim()
        val hasValidScheme = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        return hasValidScheme && trimmed.contains("%s")
    }

    fun buildUrl(template: String, query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        return template.replace("%s", encoded)
    }

    fun resolve(engineId: String, customTemplate: String, rawQuery: String): SearchTarget {
        val query = rawQuery.trim()
        val effectiveEngine = when (engineId) {
            GOOGLE -> GOOGLE
            DUCKDUCKGO -> DUCKDUCKGO
            BING -> BING
            CUSTOM -> CUSTOM
            else -> PHONE_DEFAULT
        }

        val isCustomValid = effectiveEngine == CUSTOM && isValidTemplate(customTemplate)

        return if (query.startsWith("!")) {
            if (effectiveEngine == DUCKDUCKGO || effectiveEngine == PHONE_DEFAULT || (effectiveEngine == CUSTOM && !isCustomValid)) {
                SearchTarget.Url(buildUrl(TEMPLATE_DUCKDUCKGO, query))
            } else {
                val strippedQuery = query.removePrefix("!").trim()
                val template = when (effectiveEngine) {
                    GOOGLE -> TEMPLATE_GOOGLE
                    BING -> TEMPLATE_BING
                    CUSTOM -> customTemplate.trim()
                    else -> TEMPLATE_DUCKDUCKGO
                }
                SearchTarget.Url(buildUrl(template, strippedQuery))
            }
        } else {
            when {
                effectiveEngine == PHONE_DEFAULT -> SearchTarget.PhoneDefault(query)
                effectiveEngine == CUSTOM && !isCustomValid -> SearchTarget.PhoneDefault(query)
                effectiveEngine == CUSTOM -> SearchTarget.Url(buildUrl(customTemplate.trim(), query))
                effectiveEngine == GOOGLE -> SearchTarget.Url(buildUrl(TEMPLATE_GOOGLE, query))
                effectiveEngine == DUCKDUCKGO -> SearchTarget.Url(buildUrl(TEMPLATE_DUCKDUCKGO, query))
                effectiveEngine == BING -> SearchTarget.Url(buildUrl(TEMPLATE_BING, query))
                else -> SearchTarget.PhoneDefault(query)
            }
        }
    }
}
