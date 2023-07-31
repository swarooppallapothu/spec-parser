package com.swaggerparser.dto;

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

}
