package com.voltix.workflow.incident;

import com.voltix.workflow.incident.dto.FieldJobAdminUpdateRequest;
import com.voltix.workflow.incident.dto.FieldJobCreateRequest;
import com.voltix.workflow.incident.dto.FieldJobResponse;
import com.voltix.workflow.incident.dto.FieldJobStatusUpdateRequest;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FieldJobService {

    @Transactional
    FieldJobResponse createFieldJob(Long tenantId, String username, FieldJobCreateRequest request);

    @Transactional(readOnly = true)
    List<FieldJobResponse> getFieldJobsForIncident(Long incidentId, Long tenantId);

    @Transactional(readOnly = true)
    FieldJobResponse getFieldJobById(Long fieldJobId, Long tenantId);

    @Transactional
    FieldJobResponse updateFieldJobStatus(Long fieldJobId, Long tenantId, String username, FieldJobStatusUpdateRequest request);

    @Transactional
    FieldJobResponse updateFieldJobAdmin(Long fieldJobId, Long tenantId, FieldJobAdminUpdateRequest request);
}