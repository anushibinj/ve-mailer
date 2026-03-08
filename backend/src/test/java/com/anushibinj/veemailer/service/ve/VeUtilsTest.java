package com.anushibinj.veemailer.service.ve;

import com.hpe.adm.nga.sdk.Octane;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VeUtilsTest {

    private final VeUtils veUtils = new VeUtils();

    @Test
    void testCreateOctaneClient_ReturnsNonNullClient() {
        // Use Mockito's mockConstruction to intercept the Octane.Builder call
        // so we don't need a real Octane server to unit test this.
        Octane mockOctane = mock(Octane.class);

        try (MockedConstruction<Octane.Builder> mockedBuilder =
                     Mockito.mockConstruction(Octane.Builder.class, (builderMock, context) -> {
                         when(builderMock.Server(anyString())).thenReturn(builderMock);
                         when(builderMock.sharedSpace(anyLong())).thenReturn(builderMock);
                         when(builderMock.workSpace(anyLong())).thenReturn(builderMock);
                         when(builderMock.build()).thenReturn(mockOctane);
                     })) {

            Octane result = veUtils.createOctaneClient(
                    "test-client-id",
                    "test-client-secret",
                    "https://fake-server.example.com",
                    1001,
                    2002
            );

            assertNotNull(result, "createOctaneClient should return a non-null Octane instance");
        }
    }

    @Test
    void testCreateOctaneClient_BuilderCalledWithCorrectParams() {
        Octane mockOctane = mock(Octane.class);

        try (MockedConstruction<Octane.Builder> mockedBuilder =
                     Mockito.mockConstruction(Octane.Builder.class, (builderMock, context) -> {
                         when(builderMock.Server("https://my-server")).thenReturn(builderMock);
                         when(builderMock.sharedSpace(42L)).thenReturn(builderMock);
                         when(builderMock.workSpace(99L)).thenReturn(builderMock);
                         when(builderMock.build()).thenReturn(mockOctane);
                     })) {

            Octane result = veUtils.createOctaneClient("cid", "csecret", "https://my-server", 42, 99);

            assertNotNull(result);

            // Verify builder received our parameters
            Octane.Builder usedBuilder = mockedBuilder.constructed().get(0);
            Mockito.verify(usedBuilder).Server("https://my-server");
            Mockito.verify(usedBuilder).sharedSpace(42L);
            Mockito.verify(usedBuilder).workSpace(99L);
            Mockito.verify(usedBuilder).build();
        }
    }
}
