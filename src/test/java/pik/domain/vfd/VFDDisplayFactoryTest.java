package pik.domain.vfd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pik.common.EDisplayType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
class VFDDisplayFactoryTest {

    @Test
    @DisplayName("Should create FV-2030B display")
    void shouldCreateFV2030BDisplay() {
        // When
        IVFDDisplay display = VFDDisplayFactory.createDisplay(EDisplayType.FV_2030B);

        // Then
        assertThat(display).isInstanceOf(FV2030BDisplay.class);
        assertThat(display.getDisplayModel()).isEqualTo("FV-2030B");
    }

    @Test
    @DisplayName("Should create dummy display for NONE type")
    void shouldCreateDummyDisplay() {
        // When
        IVFDDisplay display = VFDDisplayFactory.createDisplay(EDisplayType.NONE);

        // Then
        assertThat(display).isInstanceOf(DummyDisplay.class);
        assertThat(display.isDummy()).isTrue();
    }

    @Test
    @DisplayName("Should throw exception for null type")
    void shouldThrowExceptionForNullType() {
        // When & Then
        assertThatThrownBy(() -> VFDDisplayFactory.createDisplay(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should return all available display types")
    void shouldReturnAllDisplayTypes() {
        // When
        EDisplayType[] types = VFDDisplayFactory.getAvailableDisplayTypes();

        // Then
        assertThat(types).contains(EDisplayType.NONE, EDisplayType.FV_2030B);
    }

    @Test
    @DisplayName("Should provide display dimensions through interface")
    void shouldProvideDisplayDimensions() {
        // Given
        IVFDDisplay fvDisplay = VFDDisplayFactory.createDisplay(EDisplayType.FV_2030B);
        IVFDDisplay dummyDisplay = VFDDisplayFactory.createDisplay(EDisplayType.NONE);

        // When & Then
        assertThat(fvDisplay.getMaxRows()).isEqualTo(2);
        assertThat(fvDisplay.getMaxColumns()).isEqualTo(30);

        assertThat(dummyDisplay.getMaxRows()).isEqualTo(2);
        assertThat(dummyDisplay.getMaxColumns()).isEqualTo(30);
    }
}
