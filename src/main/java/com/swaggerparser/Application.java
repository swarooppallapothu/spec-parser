package com.swaggerparser;

public class Application {

    public static void main(String[] args) {

        String srcPath = "D:/projects/vani/spec-parser/src/main/resources/openapi_src.yaml";
        String tgtPath = "D:/projects/vani/spec-parser/src/main/resources/openapi_tgt.yaml";

        OpenApiSpecComparator openApiSpecComparator = new OpenApiSpecComparator();
        openApiSpecComparator.analyzeBreakingChanges(srcPath, tgtPath);

    }

}
