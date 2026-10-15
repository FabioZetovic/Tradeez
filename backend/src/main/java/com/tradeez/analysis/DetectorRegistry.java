package com.tradeez.analysis;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

/** Every detector the application has, by id (T9). A new detector is a new {@link Detector} bean. */
@Component
public class DetectorRegistry {

	private final Map<String, Detector> byId = new TreeMap<>();

	public DetectorRegistry(List<Detector> detectors) {
		for (Detector detector : detectors) {
			if (byId.put(detector.id(), detector) != null) {
				throw new IllegalStateException("Two detectors share the id " + detector.id());
			}
		}
	}

	public Detector require(String id) {
		Detector detector = byId.get(id);
		if (detector == null) {
			throw new IllegalArgumentException(
				"Unknown detector '" + id + "'. Available: " + byId.keySet());
		}
		return detector;
	}

	public Collection<Detector> all() {
		return byId.values();
	}
}
