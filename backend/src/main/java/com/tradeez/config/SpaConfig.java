package com.tradeez.config;

import java.io.IOException;
import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * T31 (DEC-084) — deployed, this server also serves the built page from {@code classpath:/static/}, so the page and the
 * API are one site: the session and CSRF cookies are first-party and no CORS is needed (BUG-073). The Docker build
 * puts {@code frontend/dist} there; locally there is nothing there and Vite serves the page as before.
 *
 * <p>The page routes in the browser, so a link such as {@code /learn/ICT-20} names no file: any path that is not a
 * file and not the API's is answered with {@code index.html}, and the page draws the screen.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

	private static final String STATIC = "classpath:/static/";

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// Vite names every built asset by its content's hash: a changed file is a new name, so it can be kept a year.
		registry.addResourceHandler("/assets/**")
			.addResourceLocations(STATIC + "assets/")
			.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
		// index.html and the rest are asked again each time, so a new deploy is seen at once.
		registry.addResourceHandler("/**")
			.addResourceLocations(STATIC)
			.setCacheControl(CacheControl.noCache())
			.resourceChain(true)
			.addResolver(new PageResolver());
	}

	/** A file when there is one; otherwise the page, except under the API's own paths, which stay a 404. */
	static final class PageResolver extends PathResourceResolver {

		@Override
		protected Resource getResource(String path, Resource location) throws IOException {
			Resource file = super.getResource(path, location);
			if (file != null) {
				return file;
			}
			if (path.startsWith("api/") || path.startsWith("actuator/") || path.startsWith("assets/")) {
				return null;
			}
			return super.getResource("index.html", location);
		}
	}
}
