package com.chsdngm.tilly.metrics

import org.apache.logging.log4j.core.*
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginAttribute
import org.apache.logging.log4j.core.config.plugins.PluginElement
import org.apache.logging.log4j.core.config.plugins.PluginFactory
import org.apache.logging.log4j.core.layout.PatternLayout
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

@Plugin(
    name = "AccumulatingAppender",
    category = Core.CATEGORY_NAME,
    elementType = Appender.ELEMENT_TYPE,
    printObject = true
)
class AccumulatingAppender(name: String) :
    AbstractAppender(name, null, PatternLayout.createDefaultLayout(), false, arrayOf()) {

    override fun append(event: LogEvent) {
        events.add(event)
    }

    companion object {
        private val events = LinkedBlockingQueue<LogEvent>(1024)
        fun drain(out: MutableCollection<LogEvent>) {
            events.drainTo(out)
        }

        @JvmStatic
        @PluginFactory
        fun createAppender(
            @PluginAttribute("name") name: String
        ) = AccumulatingAppender(name)
    }
}
