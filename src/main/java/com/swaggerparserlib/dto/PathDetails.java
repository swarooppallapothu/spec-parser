package com.swaggerparserlib.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PathDetails {

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
