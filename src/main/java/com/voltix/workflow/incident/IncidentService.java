package com.voltix.workflow.incident;

import com.voltix.workflow.incident.dto.IncidentCreateRequest;
import com.voltix.workflow.incident.dto.IncidentResponse;
import com.voltix.workflow.incident.dto.IncidentUpdateRequest;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface IncidentService {

    @Transactional
    IncidentResponse createIncident(Long tenantId, String username, IncidentCreateRequest request);

    @Transactional(readOnly = true)
    List<IncidentResponse> getIncidentsForTenant(Long tenantId);

    @Transactional(readOnly = true)
    IncidentResponse getIncidentById(Long incidentId, Long tenantId);

    @Transactional
    IncidentResponse updateIncident(Long incidentId, Long tenantId, IncidentUpdateRequest request);
}