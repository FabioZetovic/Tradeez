package com.tradeez.practice;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.identity.JwtAuthenticationFilter.AuthenticatedUser;
import com.tradeez.practice.PracticeDtos.CalibrationView;
import com.tradeez.practice.PracticeDtos.SettingsRequest;
import com.tradeez.practice.PracticeDtos.SettingsView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * What belongs to the learner rather than to one session (T23, DEC-067): how their confidence compares with
 * their results, and the rules they practise under. Always the authenticated principal's own (DEC-022).
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

	@GetMapping("/settings")
	@Operation(summary = "The learner's practice rules")
	public SettingsView settings(@AuthenticationPrincipal AuthenticatedUser principal) {
		return practice.settings(principal.id());
	}

	@PutMapping("/settings")
	@Operation(summary = "Set the learner's practice rules",
		description = "The risk limit is copied onto each plan as it is placed, so a later change never rewrites "
			+ "whether an earlier trade kept it. A missing limit clears it.")
	public SettingsView saveSettings(@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody SettingsRequest body) {
		return practice.saveSettings(principal.id(), body.riskLimitCents());
	}
}
