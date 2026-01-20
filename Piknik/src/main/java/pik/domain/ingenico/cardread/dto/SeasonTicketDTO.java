package pik.domain.ingenico.cardread.dto;

import epis5.duk.bck.core.card.files.SeasonTicket;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * DTO for season ticket data.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
public class SeasonTicketDTO {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd");

    private int ticketId;
    private int zoneId;
    private String validFrom;
    private String validTo;
    private int contractNetwork;
    private int contractProvider;
    private int couponNumber;
    private int couponType;

    public SeasonTicketDTO() {
    }

    public static SeasonTicketDTO fromSeasonTicket(SeasonTicket ticket) {
        if (ticket == null || ticket == SeasonTicket.NULL_INSTANCE) {
            return null;
        }

        SeasonTicketDTO dto = new SeasonTicketDTO();
        dto.contractNetwork = ticket.getContractNetwork();
        dto.contractProvider = ticket.getContractProvider();
        dto.couponNumber = ticket.getCouponNumber();
        dto.couponType = ticket.getCouponType();

        // Set ticketId as contract sale serial number
        dto.ticketId = ticket.getContractSaveSerialNumber();

        // Zone ID - could be from various ticket zone info sources
        dto.zoneId = 0; // Default, would need specific zone extraction logic

        if (ticket.getContractValidity() != null) {
            dto.validFrom = DATE_FORMATTER.print(ticket.getContractValidity().getStart());
            dto.validTo = DATE_FORMATTER.print(ticket.getContractValidity().getEnd());
        }

        return dto;
    }

    // Getters and setters
    public int getTicketId() {
        return ticketId;
    }

    public void setTicketId(int ticketId) {
        this.ticketId = ticketId;
    }

    public int getZoneId() {
        return zoneId;
    }

    public void setZoneId(int zoneId) {
        this.zoneId = zoneId;
    }

    public String getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(String validFrom) {
        this.validFrom = validFrom;
    }

    public String getValidTo() {
        return validTo;
    }

    public void setValidTo(String validTo) {
        this.validTo = validTo;
    }

    public int getContractNetwork() {
        return contractNetwork;
    }

    public void setContractNetwork(int contractNetwork) {
        this.contractNetwork = contractNetwork;
    }

    public int getContractProvider() {
        return contractProvider;
    }

    public void setContractProvider(int contractProvider) {
        this.contractProvider = contractProvider;
    }

    public int getCouponNumber() {
        return couponNumber;
    }

    public void setCouponNumber(int couponNumber) {
        this.couponNumber = couponNumber;
    }

    public int getCouponType() {
        return couponType;
    }

    public void setCouponType(int couponType) {
        this.couponType = couponType;
    }
}