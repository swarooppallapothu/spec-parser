package com.swaggerparser.service;

import com.swaggerparser.dto.BreakingChange;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static io.swagger.v3.oas.models.PathItem.HttpMethod;

@Service
public class OpenApiSpecCompareService {

    public BreakingChange analyzeBreakingChanges(String srcPath, String tgtPath) {

        SwaggerParseResult srcParseResult = new OpenAPIParser().readLocation(srcPath, null, null);
        SwaggerParseResult tgtParseResult = new OpenAPIParser().readLocation(tgtPath, null, null);

        return analyzeBreakingChanges(srcParseResult, tgtParseResult);
    }

    public BreakingChange analyzeBreakingChanges(SwaggerParseResult source, SwaggerParseResult target) {
        BreakingChange breakingChange = new BreakingChange();
        BreakingChange pathChanges = breakingChangesForPath(source.getOpenAPI(), target.getOpenAPI());
        if (pathChanges.hasChanges()) {
            breakingChange.getMajorChanges().addAll(pathChanges.getMajorChanges());
            breakingChange.getMinorChanges().addAll(pathChanges.getMinorChanges());
        }

        BreakingChange schemaChanges = breakingChangesForSchemas(source.getOpenAPI().getComponents().getSchemas(), target.getOpenAPI().getComponents().getSchemas());
        if (schemaChanges.hasChanges()) {
            breakingChange.getMajorChanges().addAll(schemaChanges.getMajorChanges());
            breakingChange.getMinorChanges().addAll(schemaChanges.getMinorChanges());
        }

        return breakingChange;
    }

    public BreakingChange breakingChangesForPath(OpenAPI srcOpenApi, OpenAPI tgtOpenApi) {

        BreakingChange changes = new BreakingChange();

        Set<String> srcPathNames = srcOpenApi.getPaths().keySet();
        Set<String> tgtPathNames = tgtOpenApi.getPaths().keySet();

        tgtPathNames
                .stream()
                .filter(v -> !srcPathNames.contains(v))
                .forEach(v -> {
                    changes.getMajorChanges().add(v + ": Added in target");
                });

        srcPathNames
                .stream()
                .filter(v -> !tgtPathNames.contains(v))
                .forEach(v -> {
                    changes.getMajorChanges().add(v + ": Removed from target");
                });

        Set<String> commonPathNames = srcPathNames.stream()
                .distinct()
                .filter(tgtPathNames::contains)
                .collect(Collectors.toSet());

        commonPathNames.forEach(v -> {
            BreakingChange pathChanges = breakingChangesForPath(v, srcOpenApi, tgtOpenApi);
            if (pathChanges.hasChanges()) {
                changes.getMajorChanges().addAll(pathChanges.getMajorChanges());
                changes.getMinorChanges().addAll(pathChanges.getMinorChanges());
            }
        });

        return changes;
    }

    public BreakingChange breakingChangesForPath(String path, OpenAPI srcOpenApi, OpenAPI tgtOpenApi) {
        BreakingChange pathChanges = new BreakingChange();

        PathItem srcPathItem = srcOpenApi.getPaths().get(path);
        PathItem tgtPathItem = tgtOpenApi.getPaths().get(path);

        for (HttpMethod method : HttpMethod.values()) {
            BreakingChange methodChanges = breakingChangesForPath(path, method, srcPathItem.readOperationsMap().get(method), tgtPathItem.readOperationsMap().get(method), srcOpenApi, tgtOpenApi);
            pathChanges.getMajorChanges().addAll(methodChanges.getMajorChanges());
            pathChanges.getMinorChanges().addAll(methodChanges.getMinorChanges());
        }
        return pathChanges;
    }

    public BreakingChange breakingChangesForPath(String path, HttpMethod method, Operation srcOperation, Operation tgtOperation, OpenAPI srcOpenApi, OpenAPI tgtOpenApi) {

        BreakingChange changes = new BreakingChange();

        if (srcOperation == null && tgtOperation == null) {
            return changes;
        } else if (srcOperation == null) {
            changes.getMajorChanges().add(path + ": Added " + method + " Operation");
        } else if (tgtOperation == null) {
            changes.getMajorChanges().add(path + ": Removed " + method + " Operation");
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
                changes.getMajorChanges().add(path + " -> " + method.name() + ": Parameters added to Target: " + newParameters);
            }

            String removedParameters = srcParameterNames.stream()
                    .filter(v -> !tgtParameterNames.contains(v))
                    .collect(Collectors.joining(", "));
            if (!removedParameters.isEmpty()) {
                changes.getMajorChanges().add(path + " -> " + method.name() + ": Parameters removed from Target: " + removedParameters);
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
                changes.getMajorChanges().add(path + " -> " + method.name() + ": " + requiredChanges);
            }

            List<String> commonParameters = srcParameterNames.stream()
                    .filter(tgtParameterNames::contains)
                    .collect(Collectors.toList());

            commonParameters.forEach(v -> {
                Parameter srcParameter = getParameter(v, srcOperation.getParameters());
                Parameter tgtParameter = getParameter(v, tgtOperation.getParameters());
                List<String> paramChanges = compareProperties(v, srcParameter.getSchema(), tgtParameter.getSchema());
                for (String paramChange : paramChanges) {
                    changes.getMajorChanges().add(path + " -> " + method.name() + ": " + paramChange);
                }
                if (!srcParameter.getIn().equals(tgtParameter.getIn())) {
                    changes.getMajorChanges().add(path + " -> " + method.name() + ": Parameter is " + srcParameter.getIn() + " in source and " + tgtParameter.getIn() + " in target");
                }
            });

            if (srcOperation.getRequestBody() != null && tgtOperation.getRequestBody() != null) {
                BreakingChange requestBodyChanges = compareRequestBodyChanges(srcOperation.getRequestBody(), tgtOperation.getRequestBody(), srcOpenApi, tgtOpenApi);
                if (requestBodyChanges.hasChanges()) {
                    for (String reqMajorChange : requestBodyChanges.getMajorChanges()) {
                        changes.getMajorChanges().add(path + " -> " + method.name() + ": " + reqMajorChange);
                    }
                    for (String reqMinorChange : requestBodyChanges.getMinorChanges()) {
                        changes.getMinorChanges().add(path + " -> " + method.name() + ": " + reqMinorChange);
                    }
                }
            } else if (srcOperation.getRequestBody() == null && tgtOperation.getRequestBody() != null) {
                changes.getMajorChanges().add(path + " -> " + method.name() + ": Request body added on target");
            } else if (srcOperation.getRequestBody() != null && tgtOperation.getRequestBody() == null) {
                changes.getMajorChanges().add(path + " -> " + method.name() + ": Request body removed from target");
            }

            if (hasValidResponse(srcOperation.getResponses()) && hasValidResponse(tgtOperation.getResponses())) {
                BreakingChange breakingChange = compareApiResponsesChanges(srcOperation.getResponses(), tgtOperation.getResponses(), srcOpenApi, tgtOpenApi);
                if (breakingChange.hasChanges()) {
                    changes.getMinorChanges().addAll(breakingChange.getMinorChanges()
                            .stream()
                            .map(c -> path + " -> " + method.name() + " -> " + c)
                            .collect(Collectors.toList()));
                    changes.getMajorChanges().addAll(breakingChange.getMajorChanges()
                            .stream()
                            .map(c -> path + " -> " + method.name() + " -> " + c)
                            .collect(Collectors.toList()));
                }
            } else if (!hasValidResponse(srcOperation.getResponses()) && hasValidResponse(tgtOperation.getResponses())) {
                tgtOperation.getResponses().forEach((k, v) -> {
                    v.getContent().forEach((k1, v1) -> {
                        changes.getMajorChanges().add(path + " -> " + method.name() + " -> " + k + " -> " + k1 + ": Response added to target");
                    });
                });

            } else if (hasValidResponse(srcOperation.getResponses()) && !hasValidResponse(tgtOperation.getResponses())) {
                srcOperation.getResponses().forEach((k, v) -> {
                    v.getContent().forEach((k1, v1) -> {
                        changes.getMajorChanges().add(path + " -> " + method.name() + " -> " + k + " -> " + k1 + ": Response removed from target");
                    });
                });
            }
        }

        return changes;
    }

    public BreakingChange compareRequestBodyChanges(RequestBody srcRequestBody, RequestBody tgtRequestBody, OpenAPI srcOpenApi, OpenAPI tgtOpenApi) {
        BreakingChange requestBodyChanges = new BreakingChange();
        Set<String> srcContentNames = srcRequestBody.getContent().keySet();
        Set<String> tgtContentNames = tgtRequestBody.getContent().keySet();

        tgtContentNames
                .stream()
                .filter(v -> !srcContentNames.contains(v))
                .forEach(v -> {
                    requestBodyChanges.getMajorChanges().add("Added in target");
                });

        srcContentNames
                .stream()
                .filter(v -> !tgtContentNames.contains(v))
                .forEach(v -> {
                    requestBodyChanges.getMajorChanges().add("Removed from target");
                });

        Set<String> commonSchemaNames = srcContentNames.stream()
                .distinct()
                .filter(tgtContentNames::contains)
                .collect(Collectors.toSet());

        commonSchemaNames.forEach(v -> {
            if (srcRequestBody.getContent().get(v).getSchema().get$ref() != null &&
                    srcRequestBody.getContent().get(v).getSchema().get$ref().equals(tgtRequestBody.getContent().get(v).getSchema().get$ref())) {
                String schemaName = srcRequestBody.getContent().get(v).getSchema().get$ref().substring(srcRequestBody.getContent().get(v).getSchema().get$ref().lastIndexOf("/") + 1);
                ObjectSchema srcSchema = (ObjectSchema) srcOpenApi.getComponents().getSchemas().get(schemaName);
                ObjectSchema tgtSchema = (ObjectSchema) tgtOpenApi.getComponents().getSchemas().get(schemaName);
                BreakingChange breakingChange = breakingChangesForSchema(srcSchema, tgtSchema);
                if (breakingChange.hasChanges()) {
                    requestBodyChanges.getMajorChanges().addAll(breakingChange.getMajorChanges());
                    requestBodyChanges.getMinorChanges().addAll(breakingChange.getMinorChanges());
                }
            } else {
                BreakingChange breakingChange = new BreakingChange();
                breakingChange.setMajorChanges(Collections.singletonList("Request body changed"));
            }

        });
        return requestBodyChanges;
    }

    public BreakingChange compareApiResponsesChanges(ApiResponses srcResponses, ApiResponses tgtResponses, OpenAPI srcOpenApi, OpenAPI tgtOpenApi) {
        BreakingChange responseBodyChanges = new BreakingChange();
        Set<String> srcResponseNames = srcResponses.keySet();
        Set<String> tgtResponseNames = tgtResponses.keySet();

        tgtResponseNames
                .stream()
                .filter(v -> !srcResponseNames.contains(v))
                .forEach(v -> {
                    tgtResponses.get(v).getContent().forEach((k, s) -> {
                        responseBodyChanges.getMajorChanges().add(v + " -> " + k + ": Added in target");
                    });
                });

        srcResponseNames
                .stream()
                .filter(v -> !tgtResponseNames.contains(v))
                .forEach(v -> {
                    srcResponses.get(v).getContent().forEach((k, s) -> {
                        responseBodyChanges.getMajorChanges().add(v + " -> " + k + ": Removed from target");
                    });
                });

        Set<String> commonSchemaNames = srcResponseNames.stream()
                .distinct()
                .filter(tgtResponseNames::contains)
                .collect(Collectors.toSet());

        commonSchemaNames.forEach(v -> {
            BreakingChange changeMap = compareResponseContentChanges(srcResponses.get(v).getContent(), tgtResponses.get(v).getContent(), srcOpenApi, tgtOpenApi);
            if (changeMap.hasChanges()) {
                responseBodyChanges.getMinorChanges().addAll(changeMap.getMinorChanges()
                        .stream()
                        .map(c -> v + " -> " + c)
                        .collect(Collectors.toList()));
                responseBodyChanges.getMajorChanges().addAll(changeMap.getMajorChanges()
                        .stream()
                        .map(c -> v + " -> " + c)
                        .collect(Collectors.toList()));
            }
        });
        return responseBodyChanges;
    }

    public BreakingChange compareResponseContentChanges(Content srcContentIn, Content tgtContentIn, OpenAPI srcOpenApi, OpenAPI tgtOpenApi) {
        BreakingChange responseBodyChanges = new BreakingChange();

        Content srcContent = srcContentIn == null ? new Content() : srcContentIn;
        Content tgtContent = tgtContentIn == null ? new Content() : tgtContentIn;

        Set<String> srcContentNames = srcContent.keySet();
        Set<String> tgtContentNames = tgtContent.keySet();

        tgtContentNames
                .stream()
                .filter(v -> !srcContentNames.contains(v))
                .forEach(v -> {
                    responseBodyChanges.getMajorChanges().add(v + ": Added in target");
                });

        srcContentNames
                .stream()
                .filter(v -> !tgtContentNames.contains(v))
                .forEach(v -> {
                    responseBodyChanges.getMajorChanges().add(v + ": Removed from target");
                });

        Set<String> commonContentNames = srcContentNames.stream()
                .distinct()
                .filter(tgtContentNames::contains)
                .collect(Collectors.toSet());

        commonContentNames.forEach(v -> {
            if (srcContent.get(v).getSchema() != null && tgtContent.get(v).getSchema() != null
                    && srcContent.get(v).getSchema().get$ref() != null &&
                    srcContent.get(v).getSchema().get$ref().equals(tgtContent.get(v).getSchema().get$ref())) {
                String schemaName = srcContent.get(v).getSchema().get$ref().substring(srcContent.get(v).getSchema().get$ref().lastIndexOf("/") + 1);
                ObjectSchema srcSchema = (ObjectSchema) srcOpenApi.getComponents().getSchemas().get(schemaName);
                ObjectSchema tgtSchema = (ObjectSchema) tgtOpenApi.getComponents().getSchemas().get(schemaName);
                BreakingChange breakingChange = breakingChangesForSchema(srcSchema, tgtSchema);
                if (breakingChange.hasChanges()) {
                    responseBodyChanges.getMinorChanges().addAll(breakingChange.getMinorChanges()
                            .stream()
                            .map(c -> v + ": " + c)
                            .collect(Collectors.toList()));
                    responseBodyChanges.getMajorChanges().addAll(breakingChange.getMajorChanges()
                            .stream()
                            .map(c -> v + ": " + c)
                            .collect(Collectors.toList()));
                }
            } else if ((srcContent.get(v).getSchema() == null && tgtContent.get(v).getSchema() != null) || (srcContent.get(v).getSchema() != null && tgtContent.get(v).getSchema() == null)) {
                responseBodyChanges.getMajorChanges().add(v + ": Response content changed");
            }
        });
        return responseBodyChanges;
    }

    public Parameter getParameter(String parameterName, List<Parameter> parameters) {
        return parameters.stream().filter(p -> p.getName().equals(parameterName)).findAny().orElse(null);
    }


    public BreakingChange breakingChangesForSchemas(Map<String, Schema> srcSchemas, Map<String, Schema> tgtSchemas) {
        BreakingChange schemaChanges = new BreakingChange();

        Set<String> srcSchemaNames = srcSchemas.keySet();
        Set<String> tgtSchemaNames = tgtSchemas.keySet();

        tgtSchemaNames
                .stream()
                .filter(v -> !srcSchemaNames.contains(v))
                .forEach(v -> {
                    schemaChanges.getMajorChanges().add(v + ": Added in target");
                });

        srcSchemaNames
                .stream()
                .filter(v -> !tgtSchemaNames.contains(v))
                .forEach(v -> {
                    schemaChanges.getMajorChanges().add(v + ": Removed from target");
                });

        Set<String> commonSchemaNames = srcSchemaNames.stream()
                .distinct()
                .filter(tgtSchemaNames::contains)
                .collect(Collectors.toSet());

        commonSchemaNames.forEach(v -> {
            ObjectSchema srcSchema = (ObjectSchema) srcSchemas.get(v);
            ObjectSchema tgtSchema = (ObjectSchema) tgtSchemas.get(v);
            BreakingChange schemaBreakingChanges = breakingChangesForSchema(srcSchema, tgtSchema);
            if (schemaBreakingChanges.hasChanges()) {
                schemaChanges.getMinorChanges().addAll(schemaBreakingChanges.getMinorChanges()
                        .stream()
                        .map(c -> v + ": " + c)
                        .collect(Collectors.toList()));
                schemaChanges.getMajorChanges().addAll(schemaBreakingChanges.getMajorChanges()
                        .stream()
                        .map(c -> v + ": " + c)
                        .collect(Collectors.toList()));

            }
        });

        return schemaChanges;

    }

    public BreakingChange breakingChangesForSchema(ObjectSchema srcSchema, ObjectSchema tgtSchema) {
        BreakingChange breakingChange = new BreakingChange();

        if (srcSchema == null && tgtSchema == null) {
            return breakingChange;
        } else if (srcSchema == null || srcSchema.getProperties() == null) {
            breakingChange.getMajorChanges().add("Schema is missing on source");
            return breakingChange;
        } else if (tgtSchema == null || tgtSchema.getProperties() == null) {
            breakingChange.getMajorChanges().add("Schema is missing on target");
            return breakingChange;
        }

        Set<String> srcProps = srcSchema.getProperties().keySet();
        Set<String> tgtProps = tgtSchema.getProperties().keySet();
        Set<String> srcPropsUpper = srcSchema.getProperties().keySet().stream().map(String::toUpperCase).collect(Collectors.toSet());
        Set<String> tgtPropsUpper = tgtSchema.getProperties().keySet().stream().map(String::toUpperCase).collect(Collectors.toSet());

        String newTgtProps = tgtProps
                .stream()
                .filter(v -> !srcPropsUpper.contains(v.toUpperCase()))
                .collect(Collectors.joining(", "));
        if (!newTgtProps.isEmpty()) {
            breakingChange.getMinorChanges().add("Properties added to Target: " + newTgtProps);
        }

        String newSrcProps = srcProps
                .stream()
                .filter(v -> !tgtPropsUpper.contains(v.toUpperCase()))
                .collect(Collectors.joining(", "));

        if (!newSrcProps.isEmpty()) {
            breakingChange.getMinorChanges().add("Properties deleted from Target: " + newSrcProps);
        }

        tgtProps
                .forEach(t -> {
                    srcProps.stream()
                            .filter(s -> !s.equals(t) && s.equalsIgnoreCase(t))
                            .findAny()
                            .ifPresent(s -> breakingChange.getMajorChanges().add("Property " + s + " renamed in Target: " + t));
                });

        String requiredChanges = compareRequiredProps(srcSchema.getRequired(), tgtSchema.getRequired(), "Properties");
        if (requiredChanges != null && !requiredChanges.isEmpty()) {
            breakingChange.getMajorChanges().add(requiredChanges);
        }

        Set<String> commonProps = srcProps.stream()
                .distinct()
                .filter(tgtProps::contains)
                .collect(Collectors.toSet());

        commonProps.forEach(v -> {
            breakingChange.getMajorChanges().addAll(compareProperties(v, srcSchema.getProperties().get(v), tgtSchema.getProperties().get(v)));
        });

        return breakingChange;
    }

    public List<String> compareProperties(String propName, Schema srcPropDetails, Schema tgtPropDetails) {
        List<String> changes = new ArrayList<>(comparePropertyType(propName, srcPropDetails, tgtPropDetails));
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

    public boolean hasValidResponse(ApiResponses apiResponses) {
        return apiResponses != null && !apiResponses.isEmpty();
    }

}
