package com.swaggerparserlib;

public class Application {

    public static void main(String[] args) {
        /*
        SwaggerParseResult result = new OpenAPIParser().readLocation("https://petstore3.swagger.io/api/v3/openapi.json", null, null);

        // or from a file
        //   SwaggerParseResult result = new OpenAPIParser().readLocation("./path/to/openapi.yaml", null, null);

        // the parsed POJO
        OpenAPI openAPI = result.getOpenAPI();

        if (result.getMessages() != null)
            result.getMessages().forEach(System.err::println); // validation errors and warnings

        if (openAPI != null) {
            System.out.println(openAPI.getComponents().getLinks());
        }*/

        String srcPath = "D:/projects/vani/swagger-parser-lib/src/main/resources/openapi_src.yaml";
        String tgtPath = "D:/projects/vani/swagger-parser-lib/src/main/resources/openapi_tgt.yaml";

        OpenApiSpecComparator openApiSpecComparator = new OpenApiSpecComparator();
        openApiSpecComparator.analyzeBreakingChanges(srcPath, tgtPath);

    }

}
