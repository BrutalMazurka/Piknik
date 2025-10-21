package pik.domain.vfd;

import pik.common.EDisplayType;

/**
 * VFD Factory - Creates appropriate VFD display instances
 * @author Martin Sustik <sustik@herman.cz>
 * @since 27/08/2025
 */
public class VFDDisplayFactory {

    public static IVFDDisplay createDisplay(EDisplayType type) {
        switch (type) {
            case NONE:
                return new DummyDisplay();
            case FV_2030B:
                return new FV2030BDisplay();
            default:
                throw new IllegalArgumentException("Unsupported display type: " + type);
        }
    }

    public static EDisplayType[] getAvailableDisplayTypes() {
        return EDisplayType.values();
    }
}
