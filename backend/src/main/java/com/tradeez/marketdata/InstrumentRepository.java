package com.tradeez.marketdata;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<Instrument, UUID> {

	Optional<Instrument> findBySymbolIgnoreCase(String symbol);

	List<Instrument> findAllByOrderBySymbolAsc();

	/** What the app offers (DEC-040: withdrawn simulated markets still resolve by symbol). */
	List<Instrument> findAllByListedTrueOrderBySymbolAsc();
}
