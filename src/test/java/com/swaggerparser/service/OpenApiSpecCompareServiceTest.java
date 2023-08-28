package com.swaggerparser.service;

import com.swaggerparser.dto.SpecComparisonResponse;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@SpringBootTest
public class OpenApiSpecCompareServiceTest {

    @Autowired
    OpenApiSpecCompareService openApiSpecCompareService;

    SwaggerParseResult srcParseResult;
    SwaggerParseResult tgtParseResult;

    @BeforeEach
    void beforeEach() {
        Path openApiSpecFile = Paths.get("src", "test", "resources", "open-api-spec.yaml");
        srcParseResult = new OpenAPIParser().readLocation(openApiSpecFile.toFile().getAbsolutePath(), null, null);
        tgtParseResult = new OpenAPIParser().readLocation(openApiSpecFile.toFile().getAbsolutePath(), null, null);
    }

    @Test
    void checkRequiredRequestParam() {
        tgtParseResult.getOpenAPI().getPaths().get("/utilities/tenant-authorization").getGet().getParameters().get(0).setRequired(false);
        SpecComparisonResponse response = openApiSpecCompareService.analyzeBreakingChanges(srcParseResult, tgtParseResult);
        assertEquals(response.getPaths().get(0).getGet().getMajorChanges().size(), 1);
    }

    @Test
    void checkRequiredPathParam() {
        tgtParseResult.getOpenAPI().getPaths().get("/utilities/deposit/v1/{productType}/rates").getGet().getParameters().get(0).setRequired(false);
        SpecComparisonResponse response = openApiSpecCompareService.analyzeBreakingChanges(srcParseResult, tgtParseResult);
        assertEquals(response.getPaths().get(0).getGet().getMajorChanges().size(), 1);
    }

    @Test
    void checkRequiredHeaderParam() {
        tgtParseResult.getOpenAPI().getComponents().getParameters().get("Authorization").setRequired(false);
        SpecComparisonResponse response = openApiSpecCompareService.analyzeBreakingChanges(srcParseResult, tgtParseResult);
        assertEquals(response.getPaths().get(0).getGet().getMajorChanges().size(), 1);
    }

    @Test
    void checkPathChanges_addNewPath() {
        Operation operation = new Operation();
        QueryParameter parameter = new QueryParameter();
        parameter.setRequired(true);
        parameter.setName("testParam");
        parameter.setSchema(new StringSchema());
        operation.addParametersItem(parameter);
        ApiResponses apiResponses = new ApiResponses();
        ApiResponse apiResponse = new ApiResponse();
        Content content = new Content();
        MediaType mediaType = new MediaType();
        mediaType.setSchema(new StringSchema());
        content.addMediaType("*/*", mediaType);
        apiResponse.setContent(content);
        apiResponses.put("200", apiResponse);
        operation.setResponses(apiResponses);
        tgtParseResult.getOpenAPI().getPaths().get("/utilities/validateAddress").get(operation);
        SpecComparisonResponse response = openApiSpecCompareService.analyzeBreakingChanges(srcParseResult, tgtParseResult);
        assertEquals(response.getPaths().get(0).getGet().getMajorChanges().size(), 1);
    }

    @Test
    void checkPathChanges_deleteExistingPath() {
        tgtParseResult.getOpenAPI().getPaths().get("/utilities/validateAddress").post(null);
        SpecComparisonResponse response = openApiSpecCompareService.analyzeBreakingChanges(srcParseResult, tgtParseResult);
        assertEquals(response.getPaths().get(0).getPost().getMajorChanges().size(), 1);
    }

    @Test
    void checkEnumValueChanges() {
        tgtParseResult.getOpenAPI().getPaths().get("/utilities/tenant-authorization").getGet().getParameters().get(3).getSchema().getEnum().remove(2);
        SpecComparisonResponse response = openApiSpecCompareService.analyzeBreakingChanges(srcParseResult, tgtParseResult);
        assertEquals(response.getPaths().get(0).getGet().getMajorChanges().size(), 1);
    }

    @Test
    void checkRequestParamType() {
        tgtParseResult.getOpenAPI().getPaths().get("/relateduserdetails").getPost().getParameters().get(0).setSchema(new IntegerSchema());
        SpecComparisonResponse response = openApiSpecCompareService.analyzeBreakingChanges(srcParseResult, tgtParseResult);
        assertEquals(response.getPaths().get(0).getPost().getMajorChanges().size(), 1);
    }

    @Test
    void checkRequestBodyFieldType() {
        ((ObjectSchema) tgtParseResult.getOpenAPI().getComponents().getSchemas().get("UpdateAllianceOffersRequest")).getProperties().put("offerId", new IntegerSchema());
        SpecComparisonResponse response = openApiSpecCompareService.analyzeBreakingChanges(srcParseResult, tgtParseResult);
        assertEquals(response.getPaths().get(0).getPost().getRequestBodyChanges().size(), 1);
    }

    @Test
    void removeRequestBodyField() {
        ((ObjectSchema) tgtParseResult.getOpenAPI().getComponents().getSchemas().get("UpdateAllianceOffersRequest")).getProperties().remove("offerId");
        SpecComparisonResponse response = openApiSpecCompareService.analyzeBreakingChanges(srcParseResult, tgtParseResult);
        assertEquals(response.getPaths().get(0).getPost().getRequestBodyChanges().size(), 1);
    }

    @Test
    void checkResponseBodyFieldType() {
        ((ObjectSchema) tgtParseResult.getOpenAPI().getComponents().getSchemas().get("VerifyAccountResponse")).getProperties().put("routingNumber", new IntegerSchema());
        SpecComparisonResponse response = openApiSpecCompareService.analyzeBreakingChanges(srcParseResult, tgtParseResult);
        assertEquals(response.getPaths().get(0).getPost().getResponseContentChanges().get("200").get("*/*").getMajorChanges().size(), 1);
    }

    @Test
    void checkRequestParamNameCase() {
        tgtParseResult.getOpenAPI().getPaths().get("/myvest/getGoalDetails").getPost().getParameters().get(0).setName("uid");
        SpecComparisonResponse response = openApiSpecCompareService.analyzeBreakingChanges(srcParseResult, tgtParseResult);
        assertEquals(response.getPaths().get(0).getPost().getMajorChanges().size(), 3);
    }

    @Test
    void checkResponseFormats() {
        Content content = tgtParseResult.getOpenAPI().getPaths().get("/utilities/enrollment/verifyaccount").getPost().getResponses().get("200").getContent();
        content.addMediaType("application/xml", content.get("application/json"));
        content.remove("application/json");
        SpecComparisonResponse response = openApiSpecCompareService.analyzeBreakingChanges(srcParseResult, tgtParseResult);
        assertEquals(response.getPaths().get(0).getPost().getResponseContentChanges().get("200").size(), 2);
    }

    @Test
    void checkResponseCodes() {
        ApiResponses responses = tgtParseResult.getOpenAPI().getPaths().get("/utilities/enrollment/verifyaccount").getPost().getResponses();
        responses.put("201", responses.get("200"));
        responses.remove("200");
        SpecComparisonResponse response = openApiSpecCompareService.analyzeBreakingChanges(srcParseResult, tgtParseResult);
        assertEquals(response.getPaths().get(0).getPost().getResponseContentChanges().get("200").size(), 2);
        assertEquals(response.getPaths().get(0).getPost().getResponseContentChanges().get("201").size(), 2);
    }
}
