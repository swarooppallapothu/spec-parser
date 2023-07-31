package com.swaggerparser.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SpecCompareRequest {

    private String sourcePath;
    private String targetPath;

}
