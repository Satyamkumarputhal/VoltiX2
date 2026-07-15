package com.voltix.workflow.complaints;

import com.voltix.platform.config.VoltixProperties;
import com.voltix.workflow.complaints.dto.ComplaintRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/complaints")
public class PublicComplaintController {
    private static final Logger log = LoggerFactory.getLogger(PublicComplaintController.class);

    private final PublicComplaintRepository complaintRepository;
    private final VoltixProperties properties;
    private final Map<String, io.github.bucket4j.Bucket> rateLimitCache = new ConcurrentHashMap<>();

    public PublicComplaintController(PublicComplaintRepository complaintRepository, VoltixProperties properties) {
        this.complaintRepository = complaintRepository;
        this.properties = properties;
    }

    @PostMapping("/anonymous")
    public ResponseEntity<Map<String, String>> submitAnonymousComplaint(
            @Valid @RequestBody ComplaintRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String clientIp = httpServletRequest.getRemoteAddr();
        io.github.bucket4j.Bucket bucket = resolveBucket(clientIp);

        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for client IP: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "status", "RATE_LIMITED",
                            "message", "Rate limit exceeded. Please try again later."
                    ));
        }

        String addressHash = computeSha256(request.getIncidentAddress());
        long windowMinutes = properties.getComplaints().getDedupWindowMinutes();
        ZonedDateTime since = ZonedDateTime.now().minusMinutes(windowMinutes);

        // Deduplication Check
        Optional<PublicComplaint> existing = complaintRepository
                .findFirstByAddressHashAndStatusAndSubmittedAtAfter(addressHash, ComplaintStatus.PENDING_VERIFICATION, since);

        if (existing.isPresent()) {
            log.info("Deduplicated complaint for address hash: {}", addressHash);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "status", "ACCEPTED",
                            "message", "A complaint for this address is already under verification."
                    ));
        }

        // Save new anonymous complaint
        PublicComplaint complaint = new PublicComplaint();
        long tenantId = request.getTenantId() != null ? request.getTenantId() : properties.getTenant().getDefaultId();
        complaint.setTenantId(tenantId);
        complaint.setZoneId(request.getZoneId());
        complaint.setIncidentAddress(request.getIncidentAddress());
        complaint.setAddressHash(addressHash);
        complaint.setDescription(request.getDescription());
        complaint.setSubmitterIpHash(computeSha256(clientIp));
        complaint.setStatus(ComplaintStatus.PENDING_VERIFICATION);
        complaint.setSubmittedAt(ZonedDateTime.now());

        PublicComplaint saved = complaintRepository.save(complaint);
        log.info("Anonymous complaint created with ID: {}", saved.getComplaintId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "status", "CREATED",
                        "complaintId", saved.getComplaintId().toString(),
                        "message", "Complaint submitted successfully and is pending verification."
                ));
    }

    /**
     * Operator endpoint: list complaints filtered by status.
     * Used by the dashboard triage panel. Requires authentication.
     *
     * @param status filter by complaint status (defaults to PENDING_VERIFICATION)
     * @return list of matching complaints, ordered by submission time descending
     */
    @GetMapping
    public ResponseEntity<List<PublicComplaint>> listComplaints(
            @RequestParam(defaultValue = "PENDING_VERIFICATION") ComplaintStatus status
    ) {
        List<PublicComplaint> complaints = complaintRepository.findByStatusOrderBySubmittedAtDesc(status);
        return ResponseEntity.ok(complaints);
    }

    @PatchMapping("/{complaintId}/triage")
    public ResponseEntity<Map<String, String>> triageComplaint(
            @PathVariable Long complaintId,
            @RequestParam ComplaintStatus status
    ) {
        Optional<PublicComplaint> optional = complaintRepository.findById(complaintId);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PublicComplaint complaint = optional.get();
        complaint.setStatus(status);
        complaint.setTriagedAt(ZonedDateTime.now());
        complaintRepository.save(complaint);

        log.info("Public complaint ID={} triaged to state={}", complaintId, status);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Complaint state successfully updated to " + status
        ));
    }

    private io.github.bucket4j.Bucket resolveBucket(String ip) {
        return rateLimitCache.computeIfAbsent(ip, key -> io.github.bucket4j.Bucket.builder()
                .addLimit(io.github.bucket4j.Bandwidth.classic(
                        properties.getComplaints().getRateLimitCapacity(),
                        io.github.bucket4j.Refill.intervally(
                                properties.getComplaints().getRateLimitCapacity(),
                                Duration.ofMinutes(properties.getComplaints().getRateLimitRefillMinutes())
                        )
                ))
                .build());
    }

    private String computeSha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String normalized = value.trim().toLowerCase().replaceAll("\\s+", " ");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate SHA-256 hash", e);
        }
    }
}
