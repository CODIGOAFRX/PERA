package com.peraerp.operations.claims;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Anotación de seguimiento de una reclamación. Es inmutable. */
@Entity
@Table(name = "claim_comments")
public class ClaimComment extends CompanyScopedEntity {

    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;
    @Column(name = "author_user_id", nullable = false, updatable = false)
    private UUID authorUserId;
    @Column(name = "author_name", nullable = false, length = 160, updatable = false)
    private String authorName;
    @Column(name = "comment_text", nullable = false, length = 2000, updatable = false)
    private String text;

    protected ClaimComment() {
    }

    public ClaimComment(UUID companyId, UUID claimId, UUID authorUserId, String authorName, String text) {
        super(companyId);
        this.claimId = claimId;
        this.authorUserId = authorUserId;
        this.authorName = authorName;
        this.text = text;
    }

    public UUID getClaimId() { return claimId; }
    public UUID getAuthorUserId() { return authorUserId; }
    public String getAuthorName() { return authorName; }
    public String getText() { return text; }
}
