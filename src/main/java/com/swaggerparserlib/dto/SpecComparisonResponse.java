package com.swaggerparserlib.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class SpecComparisonResponse {

    private List<PathDetails> paths;
    private List<SchemaDetails> schemas;


}
