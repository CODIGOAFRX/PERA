package com.peraerp.activity.audit;

import com.peraerp.platform.domain.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class AuditMetadataPolicy {
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(password|passwd|secret|token|authorization|cookie|api[-_]?key|private[-_]?key).*"
    );
    private static final int MAX_DEPTH = 8;

    public void validate(Map<String, Object> metadata) {
        inspect(metadata, 0);
    }

    private void inspect(Object value, int depth) {
        if (depth > MAX_DEPTH) {
            throw new BusinessRuleException("Los metadatos del evento exceden la profundidad permitida.");
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (SENSITIVE_KEY.matcher(key).matches()) {
                    throw new BusinessRuleException("Los metadatos de auditoría no pueden contener secretos.");
                }
                inspect(entry.getValue(), depth + 1);
            }
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(item -> inspect(item, depth + 1));
        } else if (value != null && value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                inspect(Array.get(value, index), depth + 1);
            }
        }
    }
}
