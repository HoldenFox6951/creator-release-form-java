package com.infrai.example.release;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The filled form plus the decision that produced it: locked for signature, or still editable. */
public final class ReleaseForm {

    public final boolean locked;
    public final List<String> missingFields;
    public final Map<String, Object> templateVars;
    public final String idempotencyKey;

    public ReleaseForm(boolean locked,
                       List<String> missingFields,
                       Map<String, Object> templateVars,
                       String idempotencyKey) {
        this.locked = locked;
        this.missingFields = Collections.unmodifiableList(missingFields);
        this.templateVars = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(templateVars));
        this.idempotencyKey = idempotencyKey;
    }

    public String mode() {
        return locked ? "locked" : "editable";
    }
}
