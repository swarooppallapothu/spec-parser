package com.swaggerparser.service;

import com.swaggerparser.dto.PathDetails;
import com.swaggerparser.dto.SchemaDetails;
import com.swaggerparser.dto.SpecComparisonResponse;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OpenApiSpecCompareService {

    public SpecComparisonResponse analyzeBreakingChanges(String srcPath, String tgtPath) {
        SpecComparisonResponse specComparisonResponse = new SpecComparisonResponse();
        SwaggerParseResult srcParseResult = new OpenAPIParser().readLocation(srcPath, null, null);
        SwaggerParseResult tgtParseResult = new OpenAPIParser().readLocation(tgtPath, null, null);

        specComparisonResponse.setPaths(breakingChangesForPath(srcParseResult.getOpenAPI().getPaths(), tgtParseResult.getOpenAPI().getPaths()));
        specComparisonResponse.setSchemas(breakingChangesForSchemas(srcParseResult.getOpenAPI().getComponents().getSchemas(), tgtParseResult.getOpenAPI().getComponents().getSchemas()));

        return specComparisonResponse;
    }

    public List<PathDetails> breakingChangesForPath(Paths srcPaths, Paths tgtPaths) {

        List<PathDetails> pathDetails = new ArrayList<>();

        Set<String> srcPathNames = srcPaths.keySet();
        Set<String> tgtPathNames = tgtPaths.keySet();

        tgtPathNames
                .stream()
                .filter(v -> !srcPathNames.contains(v))
                .forEach(v -> {
                    PathDetails newPath = new PathDetails();
                    newPath.setPath(v);
                    newPath.setChanges(Collections.singletonList("Added in target"));
                    pathDetails.add(newPath);
                });

        srcPathNames
                .stream()
                .filter(v -> !tgtPathNames.contains(v))
                .forEach(v -> {
                    PathDetails newPath = new PathDetails();
                    newPath.setPath(v);
                    newPath.setChanges(Collections.singletonList("Removed from target"));
                    pathDetails.add(newPath);
                });

        Set<String> commonPathNames = srcPathNames.stream()
                .distinct()
                .filter(tgtPathNames::contains)
                .collect(Collectors.toSet());

        commonPathNames.forEach(v -> {
            PathItem srcSchema = srcPaths.get(v);
            PathItem tgtSchema = tgtPaths.get(v);
            /*List<String> schemaBreakingChanges = breakingChangesForSchema(srcSchema, tgtSchema);
            if (!schemaBreakingChanges.isEmpty()) {
                SchemaDetails schemaChanges = new SchemaDetails();
                schemaChanges.setSchema(v);
                schemaChanges.setChanges(schemaBreakingChanges);
                schemaDetails.add(schemaChanges);
            }*/
        });

        return pathDetails;
    }

    public List<SchemaDetails> breakingChangesForSchemas(Map<String, Schema> srcSchemas, Map<String, Schema> tgtSchemas) {
        List<SchemaDetails> schemaDetails = new ArrayList<>();

        Set<String> srcSchemaNames = srcSchemas.keySet();
        Set<String> tgtSchemaNames = tgtSchemas.keySet();

        Set<String> commonSchemaNames = srcSchemaNames.stream()
                .distinct()
                .filter(tgtSchemaNames::contains)
                .collect(Collectors.toSet());

        commonSchemaNames.forEach(v -> {
            ObjectSchema srcSchema = (ObjectSchema) srcSchemas.get(v);
            ObjectSchema tgtSchema = (ObjectSchema) tgtSchemas.get(v);
            List<String> schemaBreakingChanges = breakingChangesForSchema(srcSchema, tgtSchema);
            if (!schemaBreakingChanges.isEmpty()) {
                SchemaDetails schemaChanges = new SchemaDetails();
                schemaChanges.setSchema(v);
                schemaChanges.setChanges(schemaBreakingChanges);
                schemaDetails.add(schemaChanges);
            }
        });

        return schemaDetails;

    }

    public List<String> breakingChangesForSchema(ObjectSchema srcSchema, ObjectSchema tgtSchema) {
        List<String> changes = new ArrayList<>();

        Set<String> srcProps = srcSchema.getProperties().keySet();
        Set<String> tgtProps = tgtSchema.getProperties().keySet();

        String newTgtProps = tgtProps
                .stream()
                .filter(v -> !srcProps.contains(v))
                .collect(Collectors.joining(", "));
        if (!newTgtProps.isEmpty()) {
            changes.add("Target has new properties: " + newTgtProps);
        }

        String newSrcProps = srcProps
                .stream()
                .filter(v -> !tgtProps.contains(v))
                .collect(Collectors.joining(", "));

        if (!newSrcProps.isEmpty()) {
            changes.add("Source has new properties: " + newSrcProps);
        }

        String requiredChanges = compareRequiredProps(srcSchema.getRequired(), tgtSchema.getRequired());
        if (requiredChanges != null && !requiredChanges.isEmpty()) {
            changes.add(requiredChanges);
        }

        Set<String> commonProps = srcProps.stream()
                .distinct()
                .filter(tgtProps::contains)
                .collect(Collectors.toSet());

        commonProps.forEach(v -> {
            changes.addAll(compareProperties(v, srcSchema.getProperties().get(v), tgtSchema.getProperties().get(v)));
        });

        return changes;
    }

    public List<String> compareProperties(String propName, Schema srcPropDetails, Schema tgtPropDetails) {
        List<String> changes = new ArrayList<>();
        changes.addAll(comparePropertyType(propName, srcPropDetails, tgtPropDetails));
        if ("array".equals(srcPropDetails.getType())
                && srcPropDetails.getType() != null && tgtPropDetails.getType() != null
                && srcPropDetails.getType().equals(tgtPropDetails.getType())) {
            changes.addAll(comparePropertyType(propName, srcPropDetails.getItems(), tgtPropDetails.getItems()));
        }

        return changes;
    }

    public List<String> comparePropertyType(String propName, Schema srcPropDetails, Schema tgtPropDetails) {
        List<String> changes = new ArrayList<>();
        if (srcPropDetails.getType() == null || tgtPropDetails.getType() == null) {
            if (srcPropDetails.get$ref() != null && tgtPropDetails.get$ref() != null
                    && !srcPropDetails.get$ref().equals(tgtPropDetails.get$ref())) {
                changes.add(propName + " has ref: " + srcPropDetails.get$ref() + " in source and ref: " + tgtPropDetails.get$ref() + " in target");
            }
        } else if (srcPropDetails.getType() == null && tgtPropDetails.getType() != null) {
            changes.add(propName + " has ref: " + srcPropDetails.getType() + " in source and type: " + tgtPropDetails.get$ref() + " in target");
        } else if (srcPropDetails.getType() != null && tgtPropDetails.getType() == null) {
            changes.add(propName + " has type: " + srcPropDetails.getType() + " in source and ref: " + tgtPropDetails.getType() + " in target");
        } else if (!srcPropDetails.getType().equals(tgtPropDetails.getType())) {
            changes.add(propName + " has type: " + srcPropDetails.getType() + " in source and type: " + tgtPropDetails.getType() + " in target");
        }
        if (srcPropDetails.getEnum() != null && tgtPropDetails.getEnum() != null) {
            String enumRes = compareEnum(propName, ((StringSchema) srcPropDetails).getEnum(), ((StringSchema) tgtPropDetails).getEnum());
            if (!enumRes.isEmpty()) {
                changes.add(enumRes);
            }
        }
        return changes;
    }

    public String compareEnum(String name, List<String> src, List<String> tgt) {
        String removedEnumProps = src.stream().filter(v -> !tgt.contains(v)).collect(Collectors.joining(" "));
        String addedEnumProps = tgt.stream().filter(v -> !src.contains(v)).collect(Collectors.joining(" "));

        return removedEnumProps.isEmpty() && addedEnumProps.isEmpty() ? "" : ("Enum: " + name + " has " + (!addedEnumProps.isEmpty() ? ("New values: [" + addedEnumProps + "]") : "") + (!removedEnumProps.isEmpty() ? (" Removed values: [" + removedEnumProps + "]") : ""));
    }

    public String compareRequiredProps(List<String> srcRequired, List<String> tgtRequired) {
        String returnVal = null;
        if (srcRequired != null && tgtRequired != null) {
            String newProps = tgtRequired
                    .stream()
                    .filter(v -> !srcRequired.contains(v))
                    .collect(Collectors.joining(", "));

            String removedProps = srcRequired
                    .stream()
                    .filter(v -> !tgtRequired.contains(v))
                    .collect(Collectors.joining(", "));

            returnVal = !newProps.isEmpty() ? "Required properties added to target: [" + String.join(", ", newProps) + "]. " : "";
            returnVal += !removedProps.isEmpty() ? "Required properties deleted from target: [" + String.join(", ", removedProps) + "]." : "";
        } else if (srcRequired == null && tgtRequired != null) {
            returnVal = "Required properties added to target: [" + String.join(", ", tgtRequired) + "]";
        } else if (srcRequired != null && tgtRequired == null) {
            returnVal = "Required properties added deleted from target: [" + String.join(", ", srcRequired) + "]";
        }

        return returnVal;
    }

}
