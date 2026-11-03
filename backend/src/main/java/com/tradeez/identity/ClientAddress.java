package com.tradeez.identity;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The address a request came from, as far as it can be believed (BUG-070): the connection's own, unless proxies we run
 * stand in front and append theirs to {@code X-Forwarded-For}. Shared by the per-address limit and the per-account one
 * (BUG-076), so the two agree on who is asking.
 */
@Component
public class ClientAddress {

	private static final Logger log = LoggerFactory.getLogger(ClientAddress.class);

	/** Said once a process: the warning below is about the configuration, not about any one request. */
	private static final AtomicBoolean WARNED = new AtomicBoolean();

	private final int trustedProxies;

	public ClientAddress(@Value("${tradeez.rate-limit.trusted-proxies:0}") int trustedProxies) {
		this.trustedProxies = trustedProxies;
	}

	/**
	 * Each proxy <i>appends</i> the address it saw, so only entries added by proxies we run can be believed — counted
	 * from the right, {@code tradeez.rate-limit.trusted-proxies} of them. With none (the default, and locally) the
	 * header is ignored: a client can write anything in it.
	 */
	public String of(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		// BUG-131: a proxy in front and none trusted means every visitor arrives from the proxy's address and shares one
		// sign-in quota — one guesser locks the whole site out. Not refused (a client may send the header itself), said.
		if (trustedProxies == 0 && forwarded != null && WARNED.compareAndSet(false, true)) {
			log.warn("Requests carry X-Forwarded-For but tradeez.rate-limit.trusted-proxies is 0: behind a proxy every "
				+ "visitor counts as one address for the sign-in limits. Set TRUSTED_PROXIES to the number of proxies.");
		}
		if (trustedProxies > 0 && forwarded != null && !forwarded.isBlank()) {
			String[] hops = forwarded.split(",");
			if (hops.length >= trustedProxies) {
				return hops[hops.length - trustedProxies].trim();
			}
		}
		return request.getRemoteAddr();
	}
}
