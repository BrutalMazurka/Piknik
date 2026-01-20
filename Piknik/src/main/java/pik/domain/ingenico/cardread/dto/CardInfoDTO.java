package pik.domain.ingenico.cardread.dto;

import epis5.duk.bck.core.card.files.CardInfo;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * DTO for card info data.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
public class CardInfoDTO {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd");

    private String cardNumber;
    private int profileId;
    private String validFrom;
    private String validTo;
    private int publisherProviderId;
    private int publisherNetworkId;

    public CardInfoDTO() {
    }

    public static CardInfoDTO fromCardInfo(CardInfo cardInfo) {
        if (cardInfo == null || cardInfo == CardInfo.NULL_INSTANCE) {
            return null;
        }

        CardInfoDTO dto = new CardInfoDTO();
        dto.cardNumber = cardInfo.getLogicalCardNumberAsHexString();
        dto.publisherProviderId = cardInfo.getPublisherProviderId();
        dto.publisherNetworkId = cardInfo.getPublisherNetworkId();

        if (cardInfo.getValidityInterval() != null) {
            dto.validFrom = DATE_FORMATTER.print(cardInfo.getValidityInterval().getStart());
            dto.validTo = DATE_FORMATTER.print(cardInfo.getValidityInterval().getEnd());
        }

        // Note: profileId is not directly in CardInfo, it's in CardHolderInfo
        // This is kept for API compatibility as per plan
        dto.profileId = 0;

        return dto;
    }

    // Getters and setters
    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public int getProfileId() {
        return profileId;
    }

    public void setProfileId(int profileId) {
        this.profileId = profileId;
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

    public int getPublisherProviderId() {
        return publisherProviderId;
    }

    public void setPublisherProviderId(int publisherProviderId) {
        this.publisherProviderId = publisherProviderId;
    }

    public int getPublisherNetworkId() {
        return publisherNetworkId;
    }

    public void setPublisherNetworkId(int publisherNetworkId) {
        this.publisherNetworkId = publisherNetworkId;
    }
}