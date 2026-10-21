package com.tradeez.drills;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.drills.DrillDtos.CreateRiskDrillRequest;
import com.tradeez.drills.DrillDtos.RiskAnswerRequest;
import com.tradeez.drills.DrillDtos.RiskDrillView;
import com.tradeez.identity.JwtAuthenticationFilter.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Risk drills, mode F (T26, DEC-070): position size, a losing streak, recovery — against the clock. */
@RestController
@RequestMapping("/api/drills/risk")
@Tag(name = "Drills", description = "Short single-concept exercises, graded against versioned rules")
public class RiskDrillController {

	private final RiskDrillService risk;

	public RiskDrillController(RiskDrillService risk) {
		this.risk = risk;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Ask a risk question", description = "Its answer and working come only with the result.")
	public RiskDrillView create(@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestBody(required = false) CreateRiskDrillRequest body) {
		return risk.create(principal.id(), body == null ? null : body.kind());
	}

	@GetMapping("/{id}")
	@Operation(summary = "A risk question, with its result once answered")
	public RiskDrillView get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
		return risk.get(principal.id(), id);
	}

	@PostMapping("/{id}/answer")
	@Operation(summary = "Answer a risk question, once", description = "Graded within the question's tolerance; the time "
		+ "taken is the server's clock from the question to the answer.")
	public RiskDrillView answer(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id,
			@Valid @RequestBody RiskAnswerRequest body) {
		return risk.answer(principal.id(), id, body.value());
	}
}
