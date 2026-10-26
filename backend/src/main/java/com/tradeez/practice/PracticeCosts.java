package com.tradeez.practice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tradeez.practice.FillEngine.FillModel;

/**
 * What every practice trade costs (T41, DEC-080): fees and slippage are no longer the learner's to set — Fabio: "needs to
 * go, get removed and changed forever". Every new trade is resolved with {@link FillModel#STANDARD} (DEC-072); a trade
 * placed before keeps the model it was resolved with, stored on its result.
 *
 * <p>{@code tradeez.practice.costs} names the model by its version ({@code mkt-1t/slip-1t/thru-1t/comm-450c}): the
 * standard unless the app is configured otherwise. The worked-example tests set DEC-008's simpler one, so their
 * arithmetic stays readable; no learner can.
 */
@Component
public class PracticeCosts {

	private final FillModel model;

	public PracticeCosts(@Value("${tradeez.practice.costs}") String version) {
		this.model = FillModel.parse(version);
	}

	public FillModel model() {
		return model;
	}
}
