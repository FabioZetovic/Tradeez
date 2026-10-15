package com.tradeez.drills;

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

import com.tradeez.drills.DrillDtos.AnswerRequest;
import com.tradeez.drills.DrillDtos.CreateDrillRequest;
import com.tradeez.drills.DrillDtos.DrillResponse;
import com.tradeez.drills.DrillDtos.KindView;
import com.tradeez.identity.JwtAuthenticationFilter.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Drills (DEC-023): short, single-concept exercises, one per question a rule answers (T13, DEC-054).
 *
 * <p>Owner-scoped like everything user-owned (DEC-022): another user's drill answers exactly like a
 * drill that does not exist.
 */
@RestController
@RequestMapping("/api/drills")
@Tag(name = "Drills", description = "Short, single-concept exercises — graded on real data, fixtures on simulated")
public class DrillController {

	private final DrillService drillService;

	public DrillController(DrillService drillService) {
		this.drillService = drillService;
	}

	@GetMapping("/kinds")
	@Operation(summary = "Every drill there is", description = "Each with its mode and the rule it is graded by.")
	public List<KindView> kinds() {
		return drillService.kinds();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Start a drill",
		description = "Returns the window's candles and the rule. The answer key is withheld.")
	public DrillResponse create(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestBody(required = false) CreateDrillRequest body) {
		CreateDrillRequest request = body == null ? new CreateDrillRequest(null, null, null) : body;
		return drillService.create(principal.id(), request.kind(), request.instrument(),
			request.timeframe());
	}

	@GetMapping("/{id}")
	@Operation(summary = "A drill, with its result once answered")
	public DrillResponse get(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID id) {
		return drillService.get(principal.id(), id);
	}

	@PostMapping("/{id}/answer")
	@Operation(summary = "Answer a drill, once",
		description = "Identification: marks, the epoch seconds of candles in the window. Classification: a "
			+ "choice, one of the drill's options. The result explains every hit from the rule's own evidence.")
	public DrillResponse answer(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID id,
			@Valid @RequestBody AnswerRequest body) {
		return drillService.answer(principal.id(), id, body.marks(), body.choice());
	}
}
