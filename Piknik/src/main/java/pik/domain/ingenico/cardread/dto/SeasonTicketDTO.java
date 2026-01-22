package pik.domain.ingenico.cardread.dto;

import epis5.duk.bck.core.card.files.ContractJourneyType;
import epis5.duk.bck.core.card.files.SeasonTicket;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.List;

/**
 * DTO for season ticket data.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
public class SeasonTicketDTO {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd");

    private int ticketId;
    private ContractJourneyType zoneType;
    private int zoneId;
    private String validFrom;
    private String validTo;
    private int contractNetwork;
    private int contractProvider;
    private int couponNumber;
    private int couponType;

    // Zone-specific fields
    private Integer contractNetId;      // For NETWORK_TICKET
    private Integer startZoneId;        // For RELATION
    private Integer endZoneId;          // For RELATION
    private String zoneList;            // For ZONE_ENUMERATION (comma-separated)

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

        // Zone information
        if (ticket.hasNetworkInfo()) {
            dto.zoneType = ContractJourneyType.NETWORK_TICKET;
            dto.contractNetId = ticket.getNetworkInfo().getContractNetworkId();

        } else if (ticket.hasRelationInfo()) {
            dto.zoneType = ContractJourneyType.RELATION;
            dto.startZoneId = ticket.getRelationInfo().getStartZoneId();
            dto.endZoneId = ticket.getRelationInfo().getEndZoneId();

        } else if (ticket.hasZonesInfo()) {
            dto.zoneType = ContractJourneyType.ZONE_ENUMERATION;
            List<Integer> contractJourneyZones = ticket.getZonesInfo().getContractJourneyZones();
            // Convert list of integers to comma-separated string
            if (contractJourneyZones != null && !contractJourneyZones.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < contractJourneyZones.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(contractJourneyZones.get(i));
                }
                dto.zoneList = sb.toString();
            }
        }

        dto.zoneId = 0;

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

    public ContractJourneyType getZoneType() {
        return zoneType;
    }

    public void setZoneType(ContractJourneyType zoneType) {
        this.zoneType = zoneType;
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

    public Integer getContractNetId() {
        return contractNetId;
    }

    public void setContractNetId(Integer contractNetId) {
        this.contractNetId = contractNetId;
    }

    public Integer getStartZoneId() {
        return startZoneId;
    }

    public void setStartZoneId(Integer startZoneId) {
        this.startZoneId = startZoneId;
    }

    public Integer getEndZoneId() {
        return endZoneId;
    }

    public void setEndZoneId(Integer endZoneId) {
        this.endZoneId = endZoneId;
    }

    public String getZoneList() {
        return zoneList;
    }

    public void setZoneList(String zoneList) {
        this.zoneList = zoneList;
    }
}