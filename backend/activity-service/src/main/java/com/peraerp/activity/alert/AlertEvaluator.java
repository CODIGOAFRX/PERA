package com.peraerp.activity.alert;

import com.peraerp.activity.audit.AuditEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.IntPredicate;

@Component
public class AlertEvaluator {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_.-]+)}}");

    private final AlertRuleRepository ruleRepository;
    private final AlertInstanceRepository alertRepository;

    public AlertEvaluator(AlertRuleRepository ruleRepository, AlertInstanceRepository alertRepository) {
        this.ruleRepository = ruleRepository;
        this.alertRepository = alertRepository;
    }

    public void evaluate(AuditEvent event, Map<String, Object> metadata) {
        for (AlertRule rule : ruleRepository.findAllByCompanyIdAndActiveTrue(event.getCompanyId())) {
            if (!matches(rule, event, metadata)) continue;

            String dedupeKey = event.getResourceType() + ":" + Objects.toString(event.getResourceId(), "-");
            if (rule.getCooldownMinutes() > 0) {
                Instant threshold = Instant.now().minus(rule.getCooldownMinutes(), ChronoUnit.MINUTES);
                if (alertRepository.existsByCompanyIdAndRuleIdAndDedupeKeyAndCreatedAtGreaterThanEqual(
                        event.getCompanyId(), rule.getId(), dedupeKey, threshold)) {
                    continue;
                }
            }
            String title = abbreviate(render(rule.getTitleTemplate(), event, metadata), 200);
            String message = abbreviate(render(rule.getMessageTemplate(), event, metadata), 500);
            alertRepository.save(new AlertInstance(event.getCompanyId(), rule, event, dedupeKey,
                    rule.getSeverity(), title, message));
        }
    }

    boolean matches(AlertRule rule, AuditEvent event, Map<String, Object> metadata) {
        if (!("*".equals(rule.getEventType()) || rule.getEventType().equalsIgnoreCase(event.getEventType()))) {
            return false;
        }
        if (rule.getAction() != null && !rule.getAction().equalsIgnoreCase(event.getAction())) return false;
        if (rule.getResourceType() != null
                && !rule.getResourceType().equalsIgnoreCase(event.getResourceType())) return false;
        if (rule.getConditionField() == null) return true;

        Lookup lookup = lookup(metadata, rule.getConditionField());
        return compare(lookup, rule.getConditionOperator(), rule.getConditionValue());
    }

    private boolean compare(Lookup lookup, AlertConditionOperator operator, String expected) {
        if (operator == AlertConditionOperator.EXISTS) return lookup.found();
        if (operator == AlertConditionOperator.NOT_EXISTS) return !lookup.found();
        if (!lookup.found() || lookup.value() == null) return false;

        String actual = String.valueOf(lookup.value());
        return switch (operator) {
            case EQUALS -> actual.equalsIgnoreCase(expected);
            case NOT_EQUALS -> !actual.equalsIgnoreCase(expected);
            case CONTAINS -> actual.toLowerCase(java.util.Locale.ROOT)
                    .contains(expected.toLowerCase(java.util.Locale.ROOT));
            case GREATER_THAN -> compareNumbers(actual, expected, comparison -> comparison > 0);
            case GREATER_THAN_OR_EQUAL -> compareNumbers(actual, expected, comparison -> comparison >= 0);
            case LESS_THAN -> compareNumbers(actual, expected, comparison -> comparison < 0);
            case LESS_THAN_OR_EQUAL -> compareNumbers(actual, expected, comparison -> comparison <= 0);
            default -> false;
        };
    }

    private boolean compareNumbers(String actual, String expected, IntPredicate predicate) {
        try {
            return predicate.test(new BigDecimal(actual).compareTo(new BigDecimal(expected)));
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String render(String template, AuditEvent event, Map<String, Object> metadata) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = switch (key) {
                case "eventType" -> event.getEventType();
                case "action" -> event.getAction();
                case "resourceType" -> event.getResourceType();
                case "resourceId" -> event.getResourceId();
                case "actorName" -> event.getActorName();
                case "sourceService" -> event.getSourceService();
                case "outcome" -> event.getOutcome().name();
                default -> key.startsWith("metadata.")
                        ? lookup(metadata, key.substring("metadata.".length())).value()
                        : null;
            };
            matcher.appendReplacement(output, Matcher.quoteReplacement(Objects.toString(value, "")));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private Lookup lookup(Map<String, Object> metadata, String path) {
        Object current = metadata;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) {
                return new Lookup(false, null);
            }
            current = map.get(segment);
        }
        return new Lookup(true, current);
    }

    private String abbreviate(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 1) + "…";
    }

    private record Lookup(boolean found, Object value) {
    }
}
