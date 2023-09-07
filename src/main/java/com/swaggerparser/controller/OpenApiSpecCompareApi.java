package com.swaggerparser.controller;

import com.swaggerparser.dto.BreakingChange;
import com.swaggerparser.dto.SpecCompareRequest;
import com.swaggerparser.service.OpenApiSpecCompareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/open-api-spec")
@RestController
public class OpenApiSpecCompareApi {

    @Autowired
    private OpenApiSpecCompareService openApiSpecCompareService;

    @PostMapping("/compare")
    public ResponseEntity<BreakingChange> compare(@RequestBody SpecCompareRequest request) {
        return ResponseEntity.ok(openApiSpecCompareService.analyzeBreakingChanges(request.getSourcePath(), request.getTargetPath()));

    }

}
