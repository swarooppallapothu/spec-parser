package com.swaggerparser.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class BreakingChange {

    private List<String> changes;

    public BreakingChange() {
        this.changes = new ArrayList<>();
    }

}
