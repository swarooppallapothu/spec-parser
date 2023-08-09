package com.swaggerparser.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Getter
@Setter
public class PathItemDetails extends BreakingChange {

    private Map<String, BreakingChange> requestBodyChanges;
    private Map<String, Map<String, BreakingChange>> responseContentChanges;

    @JsonIgnore
    public boolean hasChange() {
        return (getChanges() != null && !getChanges().isEmpty())
                || (requestBodyChanges != null && !requestBodyChanges.isEmpty())
                || (responseContentChanges != null && !responseContentChanges.isEmpty());
    }

}
