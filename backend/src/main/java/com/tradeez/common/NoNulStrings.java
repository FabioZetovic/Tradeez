package com.tradeez.common;

import org.springframework.stereotype.Component;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.KeyDeserializer;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.module.SimpleModule;

/**
 * BUG-079: PostgreSQL stores no U+0000 in {@code text} or {@code jsonb}, and a string carrying one reached the
 * database and failed there — a 500, for a character no one types. Refused while a request body is read instead, for
 * every string and every map key at once: a 400 that says why ({@link RequestErrorHandler}).
 *
 * <p>A bean: Spring Boot registers every Jackson module bean with the application's mapper.
 */
@Component
public class NoNulStrings extends SimpleModule {

	static final String REFUSED = "Text cannot contain the NUL character (U+0000).";

	public NoNulStrings() {
		super("no-nul-strings");
		addDeserializer(String.class, new Strings());
		addKeyDeserializer(String.class, new Keys());
	}

	private static boolean hasNul(String text) {
		return text != null && text.indexOf('\u0000') >= 0;
	}

	/** Every JSON string, read as Jackson reads it, then refused if it holds U+0000. */
	static final class Strings extends StringDeserializer {

		@Override
		public String deserialize(JsonParser p, DeserializationContext ctxt) {
			String value = super.deserialize(p, ctxt);
			if (hasNul(value)) {
				throw InvalidFormatException.from(p, REFUSED, value, String.class);
			}
			return value;
		}
	}

	/** A map's keys: a drawing's style is a map, and its keys are stored too. */
	static final class Keys extends KeyDeserializer {

		@Override
		public Object deserializeKey(String key, DeserializationContext ctxt) {
			if (hasNul(key)) {
				throw ctxt.weirdKeyException(String.class, key, REFUSED);
			}
			return key;
		}
	}
}
