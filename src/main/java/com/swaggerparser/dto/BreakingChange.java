package com.swaggerparser.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class BreakingChange {

    private List<String> majorChanges;
    private List<String> minorChanges;

    public BreakingChange() {
        this.minorChanges = new ArrayList<>();
        this.majorChanges = new ArrayList<>();
    }

    @JsonIgnore
    public boolean hasChanges() {
        return !this.minorChanges.isEmpty() || !this.majorChanges.isEmpty();
    }

}
