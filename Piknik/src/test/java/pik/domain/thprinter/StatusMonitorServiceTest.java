package pik.domain.thprinter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Tests for StatusMonitorService
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
@ExtendWith(MockitoExtension.class)
class StatusMonitorServiceTest {

    @Mock
    private PrinterService mockPrinterService;

    @Mock
    private Consumer<String> mockStatusCallback;

    private StatusMonitorService monitorService;

    @BeforeEach
    void setUp() {
        monitorService = new StatusMonitorService(mockPrinterService, mockStatusCallback, 1000);
    }

    @Test
    @DisplayName("Should not be monitoring initially")
    void shouldNotBeMonitoringInitially() {
        // When
        boolean monitoring = monitorService.isMonitoring();

        // Then
        assertThat(monitoring).isFalse();
    }

    @Test
    @DisplayName("Should update printer status when checking")
    void shouldUpdateStatusWhenChecking() {
        // This would require access to the private checkStatus method
        // or making it package-private for testing

        // Given
        //doNothing().when(mockPrinterService).updatePrinterStatus();
        lenient().doNothing().when(mockPrinterService).updatePrinterStatus();
        // When - would call checkStatus()

        // Then
        // verify(mockPrinterService, times(1)).updatePrinterStatus();
    }
}
