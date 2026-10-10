package com.signify.translation;

/** One rule that changed a sentence, with the gloss order before and after it ran. */
public record RuleApplication(String ruleId, String name, int sentence, String detail, String before, String after) {
}
