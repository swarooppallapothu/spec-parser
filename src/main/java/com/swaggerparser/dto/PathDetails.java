package com.swaggerparser.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Getter
@Setter
public class PathDetails extends BreakingChange {

    private String path;
    private BreakingChange get;
    private BreakingChange put;
    private BreakingChange post;
    private BreakingChange delete;
    private BreakingChange options;
    private BreakingChange head;
    private BreakingChange patch;
    private BreakingChange trace;

    @JsonIgnore
    public boolean hasChange() {
        return get != null || put != null || post != null || delete != null || options != null || head != null || patch != null || trace != null;
    }

}
