package pik.domain.ingenico.cardread.dto;

import epis5.duk.bck.core.card.files.ValueEP;

/**
 * DTO for electronic purse value data.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
public class ValueEPDTO {
    private int balance; // Balance in minor currency units (cents/halers)
    private String lastTransactionDate;

    public ValueEPDTO() {
    }

    public static ValueEPDTO fromValueEP(ValueEP valueEP) {
        if (valueEP == null || valueEP == ValueEP.NULL_INSTANCE) {
            return null;
        }

        ValueEPDTO dto = new ValueEPDTO();
        dto.balance = valueEP.getValue().getAmountMinorInt();
        // Note: ValueEP doesn't store last transaction date in the core library
        // This would need to be tracked separately if needed
        dto.lastTransactionDate = null;

        return dto;
    }

    // Getters and setters
    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public String getLastTransactionDate() {
        return lastTransactionDate;
    }

    public void setLastTransactionDate(String lastTransactionDate) {
        this.lastTransactionDate = lastTransactionDate;
    }
}