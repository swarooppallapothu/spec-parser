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
    private PathItemDetails get;
    private PathItemDetails put;
    private PathItemDetails post;
    private PathItemDetails delete;
    private PathItemDetails options;
    private PathItemDetails head;
    private PathItemDetails patch;
    private PathItemDetails trace;

    @JsonIgnore
    public boolean hasChange() {
        return get != null || put != null || post != null || delete != null || options != null || head != null || patch != null || trace != null;
    }

}
