/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.user.pre.update.profile.action.internal.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.wso2.carbon.identity.action.management.api.exception.ActionDTOModelResolverClientException;
import org.wso2.carbon.identity.action.management.api.exception.ActionDTOModelResolverException;
import org.wso2.carbon.identity.action.management.api.exception.ActionDTOModelResolverServerException;
import org.wso2.carbon.identity.action.management.api.model.Action;
import org.wso2.carbon.identity.action.management.api.model.ActionDTO;
import org.wso2.carbon.identity.action.management.api.model.ActionPropertyForDAO;
import org.wso2.carbon.identity.action.management.api.model.BinaryObject;
import org.wso2.carbon.identity.action.management.api.service.ActionDTOModelResolver;
import org.wso2.carbon.identity.claim.metadata.mgt.ClaimMetadataManagementService;
import org.wso2.carbon.identity.claim.metadata.mgt.exception.ClaimMetadataException;
import org.wso2.carbon.identity.claim.metadata.mgt.model.LocalClaim;
import org.wso2.carbon.identity.user.pre.update.profile.action.internal.component.PreUpdateProfileActionServiceComponentHolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.wso2.carbon.identity.user.pre.update.profile.action.internal.constant.PreUpdateProfileActionConstants.ATTRIBUTES;
import static org.wso2.carbon.identity.user.pre.update.profile.action.internal.constant.PreUpdateProfileActionConstants.MAX_ATTRIBUTES;
import static org.wso2.carbon.identity.user.pre.update.profile.action.internal.constant.PreUpdateProfileActionConstants.ROLE_CLAIM_URI;

/**
 * This class implements the methods required to resolve ActionDTO objects in Pre Update Profile extension.
 */
public class PreUpdateProfileActionDTOModelResolver implements ActionDTOModelResolver {

    @Override
    public Action.ActionTypes getSupportedActionType() {

        return Action.ActionTypes.PRE_UPDATE_PROFILE;
    }

    @Override
    public ActionDTO resolveForAddOperation(ActionDTO actionDTO, String tenantDomain)
            throws ActionDTOModelResolverException {

        Map<String, ActionPropertyForDAO> properties = new HashMap<>();
        Object attributes = actionDTO.getProperty(ATTRIBUTES);
        // Attributes is an optional field.
        if (attributes != null) {
            List<String> validatedAttributes = validateAttributes(attributes);
            ActionPropertyForDAO attributesObject = createActionProperty(validatedAttributes);
            properties.put(ATTRIBUTES, attributesObject);
        }

        return new ActionDTO.BuilderForData(actionDTO)
                .properties(properties)
                .build();
    }

    @Override
    public ActionDTO resolveForGetOperation(ActionDTO actionDTO, String tenantDomain)
            throws ActionDTOModelResolverException {

        Map<String, Object> properties = new HashMap<>();
        // Attributes is an optional field.
        if (actionDTO.getProperty(ATTRIBUTES) != null) {
            if (!(actionDTO.getProperty(ATTRIBUTES) instanceof ActionPropertyForDAO)) {
                throw new ActionDTOModelResolverServerException("Unable to retrieve attributes.",
                        "Invalid action property provided to retrieve attributes.");
            }
            properties.put(ATTRIBUTES, getAttributes(((BinaryObject) ((ActionPropertyForDAO) actionDTO
                    .getProperty(ATTRIBUTES)).getValue()).getJSONString()));
        }

        return new ActionDTO.Builder(actionDTO)
                .properties(properties)
                .build();
    }

    @Override
    public List<ActionDTO> resolveForGetOperation(List<ActionDTO> actionDTOList, String tenantDomain)
            throws ActionDTOModelResolverException {

        List<ActionDTO> actionDTOS = new ArrayList<>();
        for (ActionDTO actionDTO : actionDTOList) {
            actionDTOS.add(resolveForGetOperation(actionDTO, tenantDomain));
        }

        return actionDTOS;
    }

    /**
     * Resolves the actionDTO for the update operation.
     * When properties are updated, the existing properties are replaced with the new properties.
     * When properties are not updated, the existing properties should be sent to the upstream component.
     *
     * @param updatingActionDTO ActionDTO that needs to be updated.
     * @param existingActionDTO Existing ActionDTO.
     * @param tenantDomain      Tenant domain.
     * @return Resolved ActionDTO.
     * @throws ActionDTOModelResolverException ActionDTOModelResolverException.
     */
    @Override
    public ActionDTO resolveForUpdateOperation(ActionDTO updatingActionDTO, ActionDTO existingActionDTO,
                                               String tenantDomain) throws ActionDTOModelResolverException {

        Map<String, ActionPropertyForDAO> properties = new HashMap<>();
        // Action Properties updating operation is treated as a PUT in DAO layer. Therefore if no properties are updated
        // the existing properties should be sent to the DAO layer.
        List<String> attributes = getResolvedUpdatingOrExistingAttributes(updatingActionDTO, existingActionDTO,
                tenantDomain);
        if (!attributes.isEmpty()) {
            properties.put(ATTRIBUTES, createActionProperty(attributes));
        }
        return new ActionDTO.BuilderForData(updatingActionDTO)
                .properties(properties)
                .build();
    }

    @Override
    public void resolveForDeleteOperation(ActionDTO deletingActionDTO, String tenantDomain)
            throws ActionDTOModelResolverException {

    }

    private List<String> getResolvedUpdatingOrExistingAttributes(ActionDTO updatingActionDTO,
                                                                 ActionDTO existingActionDTO, String tenantDomain)
            throws ActionDTOModelResolverClientException {

        if (updatingActionDTO.getProperty(ATTRIBUTES) != null) {
            // return updating attributes after validation
            return validateAttributes(updatingActionDTO.getProperty(ATTRIBUTES), tenantDomain);
        } else if (existingActionDTO.getProperty(ATTRIBUTES) != null) {
            // return existing attributes
            return (List<String>) existingActionDTO.getProperty(ATTRIBUTES);
        }
        // if attributes are not getting updated or not configured earlier, return empty list.
        return emptyList();
    }

    private ActionPropertyForDAO createActionProperty(List<String> attributes) throws ActionDTOModelResolverException {

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            // Convert the attributes to a JSON string.
            BinaryObject attributesBinaryObject = BinaryObject.fromJsonString(objectMapper
                    .writeValueAsString(attributes));
            return new ActionPropertyForDAO(attributesBinaryObject);
        } catch (JsonProcessingException e) {
            throw new ActionDTOModelResolverException("Failed to convert object values to JSON string.", e);
        }
    }

    private List<String> getAttributes(String value) throws ActionDTOModelResolverException {

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(value, new TypeReference<List<String>>() { });
        } catch (IOException e) {
            throw new ActionDTOModelResolverException("Error while reading the attribute values from storage.", e);
        }
    }

    private List<String> validateAttributes(Object attributes, String tenantDomain)
            throws ActionDTOModelResolverClientException {

        List<String> validatedAttributes = validateAttributesType(attributes);
        validateAttributesCount(validatedAttributes);
        validatedAttributes = validateIfAttributeIsKnownBySystem(validatedAttributes, tenantDomain);
        return validatedAttributes;
    }

    private List<String> validateAttributesType(Object attributes) throws ActionDTOModelResolverClientException {

        if (!(attributes instanceof List<?>)) {
            throw new ActionDTOModelResolverClientException("Invalid attributes format.",
                    "Attributes should be provided as a list of Strings.");
        }

        List<?> attributesList = (List<?>) attributes;
        for (Object item : attributesList) {
            if (!(item instanceof String)) {
                throw new ActionDTOModelResolverClientException("Invalid attributes format.",
                        "Attributes must contain only String values.");
            }
        }

        return (List<String>) attributes;
    }

    private void validateAttributesCount(List<String> attributes) throws ActionDTOModelResolverClientException {

        if (attributes.size() > MAX_ATTRIBUTES) {
            throw new ActionDTOModelResolverClientException("Maximum number of allowed attributes to configure " +
                    "exceeded.", String.format("Maximum allowed: %d Provider: %d", MAX_ATTRIBUTES, attributes.size()));
        }
    }

    private List<String> validateIfAttributeIsKnownBySystem(List<String> attributes, String tenantDomain)
            throws ActionDTOModelResolverClientException {

        try {
            ClaimMetadataManagementService claimMetadataManagementService =
                    PreUpdateProfileActionServiceComponentHolder.getInstance().getClaimManagementService();
            List<LocalClaim> localClaims = claimMetadataManagementService.getLocalClaims(tenantDomain);
            Set<String> localClaimURIs = localClaims.stream()
                    .map(LocalClaim::getClaimURI)
                    .collect(Collectors.toSet());
            Set<String> uniqueAttributes = new HashSet<>();
            for (String attribute : attributes) {
                if (!localClaimURIs.contains(attribute)) {
                    throw new ActionDTOModelResolverClientException("Invalid attribute provided.",
                            "The provided attribute is not available in the system."
                    );
                }
                if (attribute.equals(ROLE_CLAIM_URI)) {
                    throw new ActionDTOModelResolverClientException("Not supported.",
                            "'roles' attribute is not supported to be shared with extension."
                    );
                }
                uniqueAttributes.add(attribute);
            }
            return Collections.unmodifiableList(new ArrayList<>(uniqueAttributes));
        } catch (ClaimMetadataException e) {
            throw new ActionDTOModelResolverClientException("Error while retrieving local claims from claim meta " +
                    "data service.", e);
        }
    }
}
