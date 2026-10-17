package com.tradeez.marketdata;

import java.time.LocalDate;

/**
 * One CME trading day's high and low, 18:00 to the halt (T19): what a range over many days is read from
 * without reading every minute of them.
 *
 * @param contractId  the contract it traded in, when one
 * @param oneContract false for a day a roll fell in: its high and low mix two contracts
 */
public record TradingDayRange(LocalDate tradingDate, long highTicks, long lowTicks, long contractId,
		boolean oneContract) {
}
