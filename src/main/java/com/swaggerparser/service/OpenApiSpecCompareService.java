package com.swaggerparser.service;

import com.swaggerparser.dto.BreakingChange;
import com.swaggerparser.dto.PathDetails;
import com.swaggerparser.dto.SchemaDetails;
import com.swaggerparser.dto.SpecComparisonResponse;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static io.swagger.v3.oas.models.PathItem.HttpMethod;

@Service
public class OpenApiSpecCompareService {

    public SpecComparisonResponse analyzeBreakingChanges(String srcPath, String tgtPath) {
        SpecComparisonResponse specComparisonResponse = new SpecComparisonResponse();
        SwaggerParseResult srcParseResult = new OpenAPIParser().readLocation(srcPath, null, null);
        SwaggerParseResult tgtParseResult = new OpenAPIParser().readLocation(tgtPath, null, null);

        specComparisonResponse.setPaths(breakingChangesForPath(srcParseResult.getOpenAPI(), tgtParseResult.getOpenAPI()));
        specComparisonResponse.setSchemas(breakingChangesForSchemas(srcParseResult.getOpenAPI().getComponents().getSchemas(), tgtParseResult.getOpenAPI().getComponents().getSchemas()));

        return specComparisonResponse;
    }

    public List<PathDetails> breakingChangesForPath(OpenAPI srcOpenApi, OpenAPI tgtOpenApi) {

        List<PathDetails> pathDetailsList = new ArrayList<>();

        Set<String> srcPathNames = srcOpenApi.getPaths().keySet();
        Set<String> tgtPathNames = tgtOpenApi.getPaths().keySet();

        tgtPathNames
                .stream()
                .filter(v -> !srcPathNames.contains(v))
                .forEach(v -> {
                    PathDetails newPath = new PathDetails();
                    newPath.setPath(v);
                    newPath.setChanges(Collections.singletonList("Added in target"));
                    pathDetailsList.add(newPath);
                });

        srcPathNames
                .stream()
                .filter(v -> !tgtPathNames.contains(v))
                .forEach(v -> {
                    PathDetails newPath = new PathDetails();
                    newPath.setPath(v);
                    newPath.setChanges(Collections.singletonList("Removed from target"));
                    pathDetailsList.add(newPath);
                });

        Set<String> commonPathNames = srcPathNames.stream()
                .distinct()
                .filter(tgtPathNames::contains)
                .collect(Collectors.toSet());

        commonPathNames.forEach(v -> {
            PathDetails pathDetails = breakingChangesForPath(v, srcOpenApi, tgtOpenApi);
            if (pathDetails.hasChange()) {
                pathDetails.setPath(v);
                pathDetailsList.add(pathDetails);
            }
        });

        return pathDetailsList;
    }

    public PathDetails breakingChangesForPath(String path, OpenAPI srcOpenApi, OpenAPI tgtOpenApi) {
        PathDetails pathDetails = new PathDetails();

        PathItem srcPathItem = srcOpenApi.getPaths().get(path);
        PathItem tgtPathItem = tgtOpenApi.getPaths().get(path);

        pathDetails.setGet(breakingChangesForPath(HttpMethod.GET, srcPathItem.readOperationsMap().get(HttpMethod.GET), tgtPathItem.readOperationsMap().get(HttpMethod.GET), srcOpenApi, tgtOpenApi));
        pathDetails.setPost(breakingChangesForPath(HttpMethod.POST, srcPathItem.readOperationsMap().get(HttpMethod.POST), tgtPathItem.readOperationsMap().get(HttpMethod.POST), srcOpenApi, tgtOpenApi));
        pathDetails.setPut(breakingChangesForPath(HttpMethod.PUT, srcPathItem.readOperationsMap().get(HttpMethod.PUT), tgtPathItem.readOperationsMap().get(HttpMethod.PUT), srcOpenApi, tgtOpenApi));
        pathDetails.setDelete(breakingChangesForPath(HttpMethod.DELETE, srcPathItem.readOperationsMap().get(HttpMethod.DELETE), tgtPathItem.readOperationsMap().get(HttpMethod.DELETE), srcOpenApi, tgtOpenApi));
        pathDetails.setOptions(breakingChangesForPath(HttpMethod.OPTIONS, srcPathItem.readOperationsMap().get(HttpMethod.OPTIONS), tgtPathItem.readOperationsMap().get(HttpMethod.OPTIONS), srcOpenApi, tgtOpenApi));
        pathDetails.setHead(breakingChangesForPath(HttpMethod.HEAD, srcPathItem.readOperationsMap().get(HttpMethod.HEAD), tgtPathItem.readOperationsMap().get(HttpMethod.HEAD), srcOpenApi, tgtOpenApi));
        pathDetails.setPatch(breakingChangesForPath(HttpMethod.PATCH, srcPathItem.readOperationsMap().get(HttpMethod.PATCH), tgtPathItem.readOperationsMap().get(HttpMethod.PATCH), srcOpenApi, tgtOpenApi));
        pathDetails.setTrace(breakingChangesForPath(HttpMethod.TRACE, srcPathItem.readOperationsMap().get(HttpMethod.TRACE), tgtPathItem.readOperationsMap().get(HttpMethod.TRACE), srcOpenApi, tgtOpenApi));

        return pathDetails;
    }

    public BreakingChange breakingChangesForPath(HttpMethod method, Operation srcOperation, Operation tgtOperation, OpenAPI srcOpenApi, OpenAPI tgtOpenApi) {
        BreakingChange change = new BreakingChange();

        if (srcOperation == null && tgtOperation == null) {
            return null;
        } else if (srcOperation == null) {
            change.getChanges().add("Added " + method + " Operation");
        } else if (tgtOperation == null) {
            change.getChanges().add("Removed " + method + " Operation");
        } else {
            if (srcOperation.getParameters() == null) {
                srcOperation.setParameters(new ArrayList<>());
            }
            if (tgtOperation.getParameters() == null) {
                tgtOperation.setParameters(new ArrayList<>());
            }

            for (int i = 0; i < srcOperation.getParameters().size(); i++) {
                Parameter parameter = srcOperation.getParameters().get(i);
                if (parameter.getName() == null && parameter.get$ref() != null) {
                    String parameterName = parameter.get$ref().substring(parameter.get$ref().lastIndexOf("/") + 1);
                    srcOperation.getParameters().set(i, srcOpenApi.getComponents().getParameters().get(parameterName));
                }
            }

            for (int i = 0; i < tgtOperation.getParameters().size(); i++) {
                Parameter parameter = tgtOperation.getParameters().get(i);
                if (parameter.getName() == null && parameter.get$ref() != null) {
                    String parameterName = parameter.get$ref().substring(parameter.get$ref().lastIndexOf("/") + 1);
                    tgtOperation.getParameters().set(i, tgtOpenApi.getComponents().getParameters().get(parameterName));
                }
            }

            List<String> srcParameterNames = srcOperation.getParameters()
                    .stream()
                    .map(Parameter::getName)
                    .collect(Collectors.toList());

            List<String> tgtParameterNames = tgtOperation.getParameters()
                    .stream()
                    .map(Parameter::getName)
                    .collect(Collectors.toList());

            String newParameters = tgtParameterNames.stream()
                    .filter(v -> !srcParameterNames.contains(v))
                    .collect(Collectors.joining(", "));

            if (!newParameters.isEmpty()) {
                change.getChanges().add("Parameters added to Target: " + newParameters);
            }

            String removedParameters = srcParameterNames.stream()
                    .filter(v -> !tgtParameterNames.contains(v))
                    .collect(Collectors.joining(", "));
            if (!removedParameters.isEmpty()) {
                change.getChanges().add("Parameters removed from Target: " + removedParameters);
            }

            List<String> srcRequiredParameterNames = srcOperation.getParameters()
                    .stream()
                    .filter(p -> p.getRequired() != null && p.getRequired())
                    .map(Parameter::getName)
                    .collect(Collectors.toList());

            List<String> tgtRequiredParameterNames = tgtOperation.getParameters()
                    .stream()
                    .filter(p -> p.getRequired() != null && p.getRequired())
                    .map(Parameter::getName)
                    .collect(Collectors.toList());

            String requiredChanges = compareRequiredProps(srcRequiredParameterNames, tgtRequiredParameterNames, "Parameters");
            if (requiredChanges != null && !requiredChanges.isEmpty()) {
                change.getChanges().add(requiredChanges);
            }

            List<String> commonParameters = srcParameterNames.stream()
                    .filter(tgtParameterNames::contains)
                    .collect(Collectors.toList());

            commonParameters.forEach(v -> {
                Parameter srcParameter = getParameter(v, srcOperation.getParameters());
                Parameter tgtParameter = getParameter(v, tgtOperation.getParameters());
                List<String> changes = compareProperties(v, srcParameter.getSchema(), tgtParameter.getSchema());
                if (!changes.isEmpty()) {
                    change.getChanges().addAll(changes);
                }
                if (!srcParameter.getIn().equals(tgtParameter.getIn())) {
                    change.getChanges().add("Parameter is " + srcParameter.getIn() + " in source and " + tgtParameter.getIn() + " in target");
                }
            });

            if (srcOperation.getRequestBody() != null && tgtOperation.getRequestBody() != null) {

            } else if (srcOperation.getRequestBody() == null && tgtOperation.getRequestBody() != null) {
                change.getChanges().add("Request body added on target");
            } else if (srcOperation.getRequestBody() != null && tgtOperation.getRequestBody() == null) {
                change.getChanges().add("Request body removed from target");
            }
        }

        return change.getChanges().isEmpty() ? null : change;
    }

    public Parameter getParameter(String parameterName, List<Parameter> parameters) {
        return parameters.stream().filter(p -> p.getName().equals(parameterName)).findAny().orElse(null);
    }


    public List<SchemaDetails> breakingChangesForSchemas(Map<String, Schema> srcSchemas, Map<String, Schema> tgtSchemas) {
        List<SchemaDetails> schemaDetails = new ArrayList<>();

        Set<String> srcSchemaNames = srcSchemas.keySet();
        Set<String> tgtSchemaNames = tgtSchemas.keySet();

        tgtSchemaNames
                .stream()
                .filter(v -> !srcSchemaNames.contains(v))
                .forEach(v -> {
                    SchemaDetails newSchema = new SchemaDetails();
                    newSchema.setSchema(v);
                    newSchema.setChanges(Collections.singletonList("Added in target"));
                    schemaDetails.add(newSchema);
                });

        srcSchemaNames
                .stream()
                .filter(v -> !tgtSchemaNames.contains(v))
                .forEach(v -> {
                    SchemaDetails removedSchema = new SchemaDetails();
                    removedSchema.setSchema(v);
                    removedSchema.setChanges(Collections.singletonList("Removed from target"));
                    schemaDetails.add(removedSchema);
                });

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
            changes.add("Properties added to Target: " + newTgtProps);
        }

        String newSrcProps = srcProps
                .stream()
                .filter(v -> !tgtProps.contains(v))
                .collect(Collectors.joining(", "));

        if (!newSrcProps.isEmpty()) {
            changes.add("Properties deleted from Target: " + newSrcProps);
        }

        String requiredChanges = compareRequiredProps(srcSchema.getRequired(), tgtSchema.getRequired(), "Properties");
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

    public String compareRequiredProps(List<String> srcRequired, List<String> tgtRequired, String propPlaceholder) {
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

            returnVal = !newProps.isEmpty() ? propPlaceholder + " marked as required in target: [" + String.join(", ", newProps) + "]. " : "";
            returnVal += !removedProps.isEmpty() ? propPlaceholder + " marked as not required in target: [" + String.join(", ", removedProps) + "]." : "";
        } else if (srcRequired == null && tgtRequired != null) {
            returnVal = propPlaceholder + " marked as required in target: [" + String.join(", ", tgtRequired) + "]";
        } else if (srcRequired != null && tgtRequired == null) {
            returnVal = propPlaceholder + " marked as not required in target: [" + String.join(", ", srcRequired) + "]";
        }

        return returnVal;
    }

}
