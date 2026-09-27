package com.voltix.workflow.incident.dto;

import com.voltix.workflow.incident.Inspection;
import com.voltix.workflow.incident.InspectionResult;

import java.time.ZonedDateTime;

public class InspectionResponse {

    private Long inspectionId;
    private Long tenantId;
    private Long fieldJobId;
    private Long inspectorId;
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
    private String finding;
    private String conclusion;
    private String evidenceMetadata;
    private String recommendation;
    private InspectionResult result;
    private ZonedDateTime createdAt;

    public InspectionResponse() {}

    public InspectionResponse(Inspection inspection) {
        this.inspectionId = inspection.getInspectionId();
        this.tenantId = inspection.getTenantId();
        this.fieldJobId = inspection.getFieldJobId();
        this.inspectorId = inspection.getInspectorId();
        this.startedAt = inspection.getStartedAt();
        this.completedAt = inspection.getCompletedAt();
        this.finding = inspection.getFinding();
        this.conclusion = inspection.getConclusion();
        this.evidenceMetadata = inspection.getEvidenceMetadata();
        this.recommendation = inspection.getRecommendation();
        this.result = inspection.getResult();
        this.createdAt = inspection.getCreatedAt();
    }

    public Long getInspectionId() {
        return inspectionId;
    }

    public void setInspectionId(Long inspectionId) {
        this.inspectionId = inspectionId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getFieldJobId() {
        return fieldJobId;
    }

    public void setFieldJobId(Long fieldJobId) {
        this.fieldJobId = fieldJobId;
    }

    public Long getInspectorId() {
        return inspectorId;
    }

    public void setInspectorId(Long inspectorId) {
        this.inspectorId = inspectorId;
    }

    public ZonedDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(ZonedDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public ZonedDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(ZonedDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getFinding() {
        return finding;
    }

    public void setFinding(String finding) {
        this.finding = finding;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getEvidenceMetadata() {
        return evidenceMetadata;
    }

    public void setEvidenceMetadata(String evidenceMetadata) {
        this.evidenceMetadata = evidenceMetadata;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public InspectionResult getResult() {
        return result;
    }

    public void setResult(InspectionResult result) {
        this.result = result;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }
}