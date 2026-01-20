package pik.domain.ingenico.cardread.dto;

/**
 * Request DTO for card reading.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
public class CardReadRequest {
    private String readSchema;
    private Integer timeout;

    public CardReadRequest() {
    }

    public CardReadRequest(String readSchema, Integer timeout) {
        this.readSchema = readSchema;
        this.timeout = timeout;
    }

    public String getReadSchema() {
        return readSchema;
    }

    public void setReadSchema(String readSchema) {
        this.readSchema = readSchema;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    /**
     * Get timeout with default value of 30 seconds
     */
    public int getTimeoutOrDefault() {
        return timeout != null ? timeout : 30000;
    }

    /**
     * Get read schema with default value of FULL
     */
    public String getReadSchemaOrDefault() {
        return readSchema != null ? readSchema : "FULL";
    }
}