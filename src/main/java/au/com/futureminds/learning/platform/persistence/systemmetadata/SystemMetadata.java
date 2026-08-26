package au.com.futureminds.learning.platform.persistence.systemmetadata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_metadata")
public class SystemMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metadata_key", nullable = false, unique = true)
    private String metadataKey;

    @Column(name = "metadata_value", nullable = false)
    private String metadataValue;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SystemMetadata() {
    }

    public SystemMetadata(String metadataKey, String metadataValue) {
        this.metadataKey = metadataKey;
        this.metadataValue = metadataValue;
    }

    public Long getId() {
        return id;
    }

    public String getMetadataKey() {
        return metadataKey;
    }

    public String getMetadataValue() {
        return metadataValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
