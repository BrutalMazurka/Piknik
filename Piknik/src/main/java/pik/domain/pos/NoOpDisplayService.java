package pik.domain.pos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

/**
 * No-op implementation of display service for Piknik REST API.
 * Logs display actions instead of showing them on screen.
 * Piknik uses REST API + web client, so there's no physical display.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 2026-01-15
 */
@Singleton
public class NoOpDisplayService implements IPosDisplayService {
    private static final Logger logger = LoggerFactory.getLogger(NoOpDisplayService.class);

    @Override
    public void showCardProcessing(Object msg) {
        logger.debug("Display: showCardProcessing ({})", msg);
    }

    @Override
    public void showResult(Object display) {
        logger.debug("Display: showResult ({})", display);
    }

    @Override
    public void showDefault() {
        logger.debug("Display: showDefault");
    }

    @Override
    public void showErrorMessage(String message) {
        logger.debug("Display: showErrorMessage ({})", message);
    }
}