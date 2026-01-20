package pik.domain.ingenico.cardread.dto;

import epis5.duk.bck.core.card.files.CardHolderInfo;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * DTO for card holder info data.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
public class CardHolderInfoDTO {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd");

    private String firstName;
    private String lastName;
    private String birthDate;
    private int holderProfile1;
    private String profileValidity1From;
    private String profileValidity1To;
    private Integer holderProfile2;
    private String profileValidity2From;
    private String profileValidity2To;

    public CardHolderInfoDTO() {
    }

    public static CardHolderInfoDTO fromCardHolderInfo(CardHolderInfo info) {
        if (info == null || info == CardHolderInfo.NULL_INSTANCE) {
            return null;
        }

        CardHolderInfoDTO dto = new CardHolderInfoDTO();

        // Note: CardHolderInfo in the core library doesn't have firstName/lastName/birthDate as simple fields
        // They are embedded in bitfields. For now, we'll set them as empty.
        // These would need to be extracted from the bitfield data if needed.
        dto.firstName = "";
        dto.lastName = "";
        dto.birthDate = "";

        dto.holderProfile1 = info.getHolderProfile1();
        if (info.getProfileValidity1() != null) {
            dto.profileValidity1From = DATE_FORMATTER.print(info.getProfileValidity1().getStart());
            dto.profileValidity1To = DATE_FORMATTER.print(info.getProfileValidity1().getEnd());
        }

        if (info.hasProfile2()) {
            dto.holderProfile2 = info.getHolderProfile2();
            if (info.getProfileValidity2() != null) {
                dto.profileValidity2From = DATE_FORMATTER.print(info.getProfileValidity2().getStart());
                dto.profileValidity2To = DATE_FORMATTER.print(info.getProfileValidity2().getEnd());
            }
        }

        return dto;
    }

    // Getters and setters
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public int getHolderProfile1() {
        return holderProfile1;
    }

    public void setHolderProfile1(int holderProfile1) {
        this.holderProfile1 = holderProfile1;
    }

    public String getProfileValidity1From() {
        return profileValidity1From;
    }

    public void setProfileValidity1From(String profileValidity1From) {
        this.profileValidity1From = profileValidity1From;
    }

    public String getProfileValidity1To() {
        return profileValidity1To;
    }

    public void setProfileValidity1To(String profileValidity1To) {
        this.profileValidity1To = profileValidity1To;
    }

    public Integer getHolderProfile2() {
        return holderProfile2;
    }

    public void setHolderProfile2(Integer holderProfile2) {
        this.holderProfile2 = holderProfile2;
    }

    public String getProfileValidity2From() {
        return profileValidity2From;
    }

    public void setProfileValidity2From(String profileValidity2From) {
        this.profileValidity2From = profileValidity2From;
    }

    public String getProfileValidity2To() {
        return profileValidity2To;
    }

    public void setProfileValidity2To(String profileValidity2To) {
        this.profileValidity2To = profileValidity2To;
    }
}