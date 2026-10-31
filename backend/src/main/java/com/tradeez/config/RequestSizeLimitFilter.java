package com.tradeez.config;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * BUG-123 (T57). A request body is refused past {@link #MAX_BODY} bytes before anything reads it. The field limits
 * ({@code @Size}) are checked only once the whole body has been turned into strings, so without this a few large
 * requests to sign-in — no account needed — could fill a small server's memory.
 *
 * <p>The largest body the app sends is a drawing or a plan, well under 4 KB. A declared length over the limit is
 * answered 413 at once; a body sent without one (chunked) is cut off when it passes the limit, and the JSON reader then
 * fails on it as on any broken body.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

	static final int MAX_BODY = 64 * 1024;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request.getContentLengthLong() > MAX_BODY) {
			response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setHeader("Connection", "close");
			response.getWriter().write("{\"status\":413,\"message\":\"That request is too large.\"}");
			return;
		}
		chain.doFilter(new Capped(request), response);
	}

	/** The body as a stream that ends after {@link #MAX_BODY} bytes, whatever the client keeps sending. */
	private static final class Capped extends HttpServletRequestWrapper {

		private ServletInputStream stream;

		Capped(HttpServletRequest request) {
			super(request);
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			if (stream == null) {
				ServletInputStream in = super.getInputStream();
				stream = new ServletInputStream() {
					private long read;

					@Override
					public int read() throws IOException {
						if (read >= MAX_BODY) {
							return atLimit();
						}
						int b = in.read();
						if (b >= 0) {
							read++;
						}
						return b;
					}

					@Override
					public int read(byte[] buffer, int offset, int length) throws IOException {
						if (read >= MAX_BODY) {
							return atLimit();
						}
						int n = in.read(buffer, offset, (int) Math.min(length, MAX_BODY - read));
						if (n > 0) {
							read += n;
						}
						return n;
					}

					/** At the limit: the end of a body exactly that long, or a body too long. */
					private int atLimit() throws IOException {
						if (in.read() < 0) {
							return -1;
						}
						throw new IOException("Request body over " + MAX_BODY + " bytes");
					}

					@Override
					public boolean isFinished() {
						return in.isFinished();
					}

					@Override
					public boolean isReady() {
						return in.isReady();
					}

					@Override
					public void setReadListener(ReadListener listener) {
						in.setReadListener(listener);
					}
				};
			}
			return stream;
		}
	}
}
