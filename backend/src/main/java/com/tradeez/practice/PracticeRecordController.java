package com.tradeez.practice;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.identity.JwtAuthenticationFilter.AuthenticatedUser;
import com.tradeez.practice.PracticeDtos.CalibrationView;
import com.tradeez.practice.PracticeDtos.JournalTradeView;
import com.tradeez.practice.PracticeDtos.SettingsRequest;
import com.tradeez.practice.PracticeDtos.SettingsView;
import com.tradeez.practice.PracticeDtos.StatisticsView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * What belongs to the learner rather than to one session: how their confidence compares with their results
 * (T23, DEC-067), their trades added up (T25, DEC-069), and the rules they practise under. Always the authenticated principal's own (DEC-022).
 */
@RestController
@RequestMapping("/api/practice")
@Tag(name = "Practice", description = "Hidden-moment replay with committed bracket trades")
public class PracticeRecordController {

	private final PracticeService practice;

	public PracticeRecordController(PracticeService practice) {
		this.practice = practice;
	}

	@GetMapping("/calibration")
	@Operation(summary = "Stated confidence against how often trades reached their target first",
		description = "Counts finished trades whose plan said how sure the learner was. Trades closed by hand, ended by "
			+ "the session's close, or with both levels inside one candle are counted apart (calibration-1.0.0).")
	public CalibrationView calibration(@AuthenticationPrincipal AuthenticatedUser principal) {
		return practice.calibration(principal.id());
	}

	@GetMapping("/statistics")
	@Operation(summary = "Finished trades added up",
		description = "Overall and by model, killzone, weekday, side, model checks and own rules: trades, win rate, average "
			+ "win and loss, expectancy with its 95% range, profit factor, total R — the count behind every figure "
			+ "(journal-stats-1.0.0). Only trades whose exit the replay has shown.")
	public StatisticsView statistics(@AuthenticationPrincipal AuthenticatedUser principal) {
		return practice.statistics(principal.id());
	}

	@GetMapping("/trades")
	@Operation(summary = "The learner's finished trades, newest first",
		description = "One row per trade across every session (T41): when it was placed — the time the session shows while "
			+ "it is open, the real moment once it is complete (DEC-025) — the side, the entry, how it ended, R and money "
			+ "net of costs, the model. Only trades whose exit the replay has shown. limit: 1 to 200, 50 by default.")
	public List<JournalTradeView> trades(@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(defaultValue = "50") int limit) {
		if (limit < 1 || limit > 200) {
			throw new IllegalArgumentException("limit is 1 to 200");
		}
		return practice.journal(principal.id(), limit);
	}

	@GetMapping("/settings")
	@Operation(summary = "The learner's risk limit, and the costs every trade pays")
	public SettingsView settings(@AuthenticationPrincipal AuthenticatedUser principal) {
		return practice.settings(principal.id());
	}

	@PutMapping("/settings")
	@Operation(summary = "Set the learner's risk limit",
		description = "Copied onto each plan as it is placed, so a later change never rewrites an earlier trade; missing, "
			+ "it is unset. Costs are fixed at the standard (T41, DEC-080): sent, they are refused with 400.")
	public SettingsView saveSettings(@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody SettingsRequest body) {
		return practice.saveSettings(principal.id(), body);
	}
}
