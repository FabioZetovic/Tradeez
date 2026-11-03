package com.tradeez.config;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
			.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
			.setUseLastModified(false);
		// index.html and the rest are asked again each time, so a new deploy is seen at once. BUG-122: the jar dates every
		// file 1 February 1980, so Last-Modified answered 304 "not modified" across deploys and returning visitors kept an
		// index.html naming scripts the new build no longer has — a blank page. The answer is an ETag of the content.
		registry.addResourceHandler("/**")
			.addResourceLocations(STATIC)
			.setCacheControl(CacheControl.noCache())
			.setUseLastModified(false)
			.setEtagGenerator(SpaConfig::etag)
			.resourceChain(true)
			.addResolver(new PageResolver());
	}

	/** A file's content, hashed once: the jar never changes while this process runs. */
	private static final Map<String, String> ETAGS = new ConcurrentHashMap<>();

	static String etag(Resource resource) {
		return ETAGS.computeIfAbsent(resource.getDescription(), key -> {
			try (InputStream in = resource.getInputStream()) {
				return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()), 0, 16) + "\"";
			} catch (IOException | NoSuchAlgorithmException e) {
				return null;
			}
		});
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
