package com.tradeez.practice;

import java.util.List;
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

import com.tradeez.identity.JwtAuthenticationFilter.AuthenticatedUser;
import com.tradeez.practice.PracticeDtos.AdvanceRequest;
import com.tradeez.practice.PracticeDtos.AdvanceResponse;
import com.tradeez.practice.PracticeDtos.CreateSessionRequest;
import com.tradeez.practice.PracticeDtos.PlanRequest;
import com.tradeez.practice.PracticeDtos.PracticeState;
import com.tradeez.practice.PracticeDtos.SessionResponse;
import com.tradeez.practice.PracticeDtos.SessionSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * The Practice tab.
 *
 * <p>DEC-022 — the owner is always the authenticated principal. Every session lookup is scoped to
 * it, and a session belonging to someone else answers exactly like one that does not exist.
 */
@RestController
@RequestMapping("/api/practice/sessions")
@Tag(name = "Practice", description = "Hidden-moment replay with a committed bracket trade")
public class PracticeController {

	private final PracticeService practice;

	public PracticeController(PracticeService practice) {
		this.practice = practice;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Start a session at a random hidden moment",
		description = "Returns the history up to the moment, and nothing after it.")
	public SessionResponse create(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestBody(required = false) CreateSessionRequest body) {
		CreateSessionRequest request = body == null ? new CreateSessionRequest(null, null) : body;
		return practice.create(principal.id(), request.instrument(), request.timeframe());
	}

	@GetMapping
	@Operation(summary = "Journal: this user's most recent sessions",
		description = "Outcome fields stay null until a session is COMPLETE.")
	public List<SessionSummary> journal(@AuthenticationPrincipal AuthenticatedUser principal) {
		return practice.journal(principal.id());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Resume a session: its state and every bar revealed so far")
	public SessionResponse get(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID id) {
		return practice.get(principal.id(), id);
	}

	@PostMapping("/{id}/plan")
	@Operation(summary = "Lock a bracket trade",
		description = "Immutable once accepted (DEC-013). The outcome is decided now and revealed "
			+ "as the replay reaches it.")
	public PracticeState plan(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID id,
			@Valid @RequestBody PlanRequest body) {
		return practice.submitPlan(principal.id(), id, body);
	}

	@PostMapping("/{id}/advance")
	@Operation(summary = "Reveal the next bars")
	public AdvanceResponse advance(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID id,
			@Valid @RequestBody AdvanceRequest body) {
		return practice.advance(principal.id(), id, body.bars());
	}

	@PostMapping("/{id}/skip")
	@Operation(summary = "Reveal everything up to the bar in which the trade ended")
	public AdvanceResponse skip(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID id) {
		return practice.skipToResult(principal.id(), id);
	}
}
