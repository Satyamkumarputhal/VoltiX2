package com.voltix.workflow.incident.dto;

import com.voltix.workflow.incident.InspectionResult;
import jakarta.validation.constraints.Size;

public class InspectionUpdateRequest {

    @Size(max = 5000)
    private String finding;

    @Size(max = 5000)
    private String conclusion;

    @Size(max = 5000)
    private String evidenceMetadata;

    @Size(max = 5000)
    private String recommendation;

    private InspectionResult result;

    public InspectionUpdateRequest() {}

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
}