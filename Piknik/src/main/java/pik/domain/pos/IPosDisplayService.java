package pik.domain.pos;

/**
 * Display service interface for POS operations.
 * In Piknik (REST API), this is a no-op stub since there's no physical display.
 * EVK uses this for SWING UI, but Piknik uses REST API + web client.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 2026-01-15
 */
public interface IPosDisplayService {
    /**
     * Show card processing message (e.g., "Place card")
     * No-op in Piknik REST API
     */
    void showCardProcessing(Object msg);

    /**
     * Show result display (e.g., "SAM UNLOCKED")
     * No-op in Piknik REST API
     */
    void showResult(Object display);

    /**
     * Show default screen
     * No-op in Piknik REST API
     */
    void showDefault();

    /**
     * Show error message
     * No-op in Piknik REST API
     */
    void showErrorMessage(String message);
}