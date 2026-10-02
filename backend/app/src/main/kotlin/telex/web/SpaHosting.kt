package telex.web

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * Serves the React SPA from `classpath:/static/`. Client-side routes (paths without a file extension, outside
 * `/api`) fall back to `index.html`; missing assets and API paths stay 404.
 */
@Configuration(proxyBeanMethods = false)
class SpaHosting : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(SpaFallbackResolver())
    }

    private class SpaFallbackResolver : PathResourceResolver() {
        override fun getResource(
            resourcePath: String,
            location: Resource,
        ): Resource? {
            val requested = super.getResource(resourcePath, location)
            if (requested != null) return requested
            val reserved =
                resourcePath.startsWith("api/") || resourcePath.startsWith("webauthn/") ||
                    resourcePath == "login/webauthn"
            val isClientRoute = !reserved && '.' !in resourcePath.substringAfterLast('/')
            return if (isClientRoute) super.getResource("index.html", location) else null
        }
    }
}
