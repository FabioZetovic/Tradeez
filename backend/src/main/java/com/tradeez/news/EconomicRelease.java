package com.tradeez.news;

import java.time.LocalTime;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The US releases NQ traders plan around (T40, DEC-079), with Tradeez's own impact rating — the red and orange folders
 * of a trader's calendar (R-8). A release keeps its publication time for years; when an agency changes one, it is
 * changed here.
 *
 * <p>High: the releases that set the day's direction for the index — inflation, jobs, growth, spending and the Fed.
 * Medium: the ones that move it less often.
 */
public enum EconomicRelease {

	CPI(10, "CPI — Consumer Price Index", "BLS", Impact.HIGH, LocalTime.of(8, 30), "https://www.bls.gov/cpi/"),
	NFP(50, "Non-Farm Payrolls — the Employment Situation", "BLS", Impact.HIGH, LocalTime.of(8, 30),
		"https://www.bls.gov/ces/"),
	FOMC(null, "FOMC rate decision", "Federal Reserve", Impact.HIGH, LocalTime.of(14, 0),
		"https://www.federalreserve.gov/monetarypolicy/fomccalendars.htm"),
	GDP(53, "GDP — Gross Domestic Product", "BEA", Impact.HIGH, LocalTime.of(8, 30),
		"https://www.bea.gov/data/gdp/gross-domestic-product"),
	PCE(54, "PCE — Personal Income and Outlays", "BEA", Impact.HIGH, LocalTime.of(8, 30),
		"https://www.bea.gov/data/income-saving/personal-income"),
	RETAIL_SALES(9, "Retail Sales", "Census Bureau", Impact.HIGH, LocalTime.of(8, 30), "https://www.census.gov/retail/"),
	PPI(46, "PPI — Producer Price Index", "BLS", Impact.MEDIUM, LocalTime.of(8, 30), "https://www.bls.gov/ppi/"),
	JOBLESS_CLAIMS(180, "Initial Jobless Claims", "Department of Labor", Impact.MEDIUM, LocalTime.of(8, 30),
		"https://www.dol.gov/ui/data.pdf"),
	JOLTS(192, "JOLTS — Job Openings", "BLS", Impact.MEDIUM, LocalTime.of(10, 0), "https://www.bls.gov/jlt/");

	public enum Impact { HIGH, MEDIUM }

	private final Integer fredReleaseId;
	private final String title;
	private final String agency;
	private final Impact impact;
	private final LocalTime newYorkTime;
	private final String link;

	EconomicRelease(Integer fredReleaseId, String title, String agency, Impact impact, LocalTime newYorkTime,
			String link) {
		this.fredReleaseId = fredReleaseId;
		this.title = title;
		this.agency = agency;
		this.impact = impact;
		this.newYorkTime = newYorkTime;
		this.link = link;
	}

	/** Its id in FRED's release calendar; none for the FOMC, whose dates come from the Fed's calendar. */
	public OptionalInt fredReleaseId() {
		return fredReleaseId == null ? OptionalInt.empty() : OptionalInt.of(fredReleaseId);
	}

	public String title() {
		return title;
	}

	public String agency() {
		return agency;
	}

	public Impact impact() {
		return impact;
	}

	/** When it is published, New York time. */
	public LocalTime newYorkTime() {
		return newYorkTime;
	}

	public String link() {
		return link;
	}

	static Optional<EconomicRelease> byKey(String key) {
		for (EconomicRelease release : values()) {
			if (release.name().equals(key)) {
				return Optional.of(release);
			}
		}
		return Optional.empty();
	}
}
