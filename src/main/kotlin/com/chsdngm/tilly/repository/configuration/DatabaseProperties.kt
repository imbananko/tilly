package com.chsdngm.tilly.repository.configuration

import org.springframework.beans.factory.BeanClassLoaderAware
import org.springframework.beans.factory.BeanCreationException
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.boot.jdbc.DatabaseDriver
import org.springframework.util.Assert
import org.springframework.util.ClassUtils
import org.springframework.util.StringUtils
import javax.sql.DataSource

//todo rename to tilly.datasource
/**
 * See *DataSourceProperties
 */
@ConfigurationProperties(prefix = "spring.datasource")
class DatabaseProperties : BeanClassLoaderAware {
    var classLoader: ClassLoader? = null
    var type: Class<out DataSource?>? = null
    var url: String? = null

    var driverClassName: String? = null
    var username: String? = null
    var password: String? = null

    override fun setBeanClassLoader(classLoader: ClassLoader) {
        this.classLoader = classLoader
    }

    fun initializeDataSourceBuilder(): DataSourceBuilder<*> =
        DataSourceBuilder
            .create(classLoader)
            .type(type)
            .driverClassName(determineDriverClassName())
            .url(determineUrl())
            .username(determineUsername())
            .password(determinePassword())

    private fun determineDriverClassName(): String? {
        if (StringUtils.hasText(driverClassName)) {
            Assert.state(driverClassIsLoadable()) {
                "Cannot load driver class: $driverClassName"
            }

            return driverClassName
        }
        return if (StringUtils.hasText(url)) {
            DatabaseDriver.fromJdbcUrl(url).driverClassName
        } else null
    }

    private fun driverClassIsLoadable(): Boolean {
        return try {
            driverClassName?.let { ClassUtils.forName(it, null) }
            true
        } catch (ex: UnsupportedClassVersionError) {
            throw ex
        } catch (ex: Throwable) {
            false
        }
    }

    private fun determineUrl(): String? {
        if (StringUtils.hasText(url)) {
            return url
        }

        throw DataSourceBeanCreationException("Failed to determine suitable jdbc url", this)
    }

    private fun determineUsername(): String? {
        if (StringUtils.hasText(username)) {
            return username
        }

        return null
    }

    private fun determinePassword(): String? {
        if (StringUtils.hasText(password)) {
            return password
        }

        return ""
    }

    internal class DataSourceBeanCreationException(
        message: String,
        val properties: DatabaseProperties
    ) : BeanCreationException(message)
}