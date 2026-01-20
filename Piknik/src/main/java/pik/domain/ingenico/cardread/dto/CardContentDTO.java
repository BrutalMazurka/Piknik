package pik.domain.ingenico.cardread.dto;

import epis5.duk.bck.core.card.CardDuk;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for card content data returned to client.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
public class CardContentDTO {
    private String cardUid;
    private String cardType;
    private CardInfoDTO cardInfo;
    private CardHolderInfoDTO cardHolderInfo;
    private ValueEPDTO valueEP;
    private List<SeasonTicketDTO> tickets;

    public CardContentDTO() {
    }

    /**
     * Create DTO from CardDuk domain object
     */
    public static CardContentDTO fromCardDuk(CardDuk cardDuk) {
        if (cardDuk == null) {
            return null;
        }

        CardContentDTO dto = new CardContentDTO();
        dto.cardUid = cardDuk.getCardUidAsHexString();
        dto.cardType = "ISO_CARD"; // DESfire cards are ISO

        // Card Info
        if (cardDuk.hasCardInfo()) {
            dto.cardInfo = CardInfoDTO.fromCardInfo(cardDuk.getCardInfo());
        }

        // Card Holder Info
        if (cardDuk.hasCardHolderInfo()) {
            dto.cardHolderInfo = CardHolderInfoDTO.fromCardHolderInfo(cardDuk.getCardHolderInfo());
        }

        // Value EP
        if (cardDuk.hasValueEP()) {
            dto.valueEP = ValueEPDTO.fromValueEP(cardDuk.getValueEP());
        }

        // Tickets
        dto.tickets = new ArrayList<>();
        if (cardDuk.hasTickets()) {
            cardDuk.getTickets().forEach(ticket ->
                dto.tickets.add(SeasonTicketDTO.fromSeasonTicket(ticket))
            );
        }

        return dto;
    }

    // Getters and setters
    public String getCardUid() {
        return cardUid;
    }

    public void setCardUid(String cardUid) {
        this.cardUid = cardUid;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public CardInfoDTO getCardInfo() {
        return cardInfo;
    }

    public void setCardInfo(CardInfoDTO cardInfo) {
        this.cardInfo = cardInfo;
    }

    public CardHolderInfoDTO getCardHolderInfo() {
        return cardHolderInfo;
    }

    public void setCardHolderInfo(CardHolderInfoDTO cardHolderInfo) {
        this.cardHolderInfo = cardHolderInfo;
    }

    public ValueEPDTO getValueEP() {
        return valueEP;
    }

    public void setValueEP(ValueEPDTO valueEP) {
        this.valueEP = valueEP;
    }

    public List<SeasonTicketDTO> getTickets() {
        return tickets;
    }

    public void setTickets(List<SeasonTicketDTO> tickets) {
        this.tickets = tickets;
    }
}