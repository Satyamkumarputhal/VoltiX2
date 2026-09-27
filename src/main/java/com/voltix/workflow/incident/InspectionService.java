package com.voltix.workflow.incident;

import com.voltix.workflow.incident.dto.InspectionCreateRequest;
import com.voltix.workflow.incident.dto.InspectionResponse;
import com.voltix.workflow.incident.dto.InspectionUpdateRequest;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface InspectionService {

    @Transactional
    InspectionResponse createInspectionForFieldJob(Long tenantId, Long fieldJobId);

    @Transactional(readOnly = true)
    List<InspectionResponse> getInspectionsForTenant(Long tenantId);

    @Transactional(readOnly = true)
    InspectionResponse getInspectionById(Long inspectionId, Long tenantId);

    @Transactional(readOnly = true)
    InspectionResponse getInspectionByFieldJobId(Long fieldJobId, Long tenantId);

    @Transactional
    InspectionResponse updateInspection(Long inspectionId, Long tenantId, String username, InspectionUpdateRequest request);
}