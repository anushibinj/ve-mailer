package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.service.ve.VeUtils;
import com.hpe.adm.nga.sdk.Octane;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OctaneCacheServiceTest {

    @Mock
    private VeUtils veUtils;

    private OctaneCacheService octaneCacheService;

    @BeforeEach
    void setUp() {
        // Construct with an empty cache so each test starts fresh
        octaneCacheService = new OctaneCacheService(veUtils, new HashMap<>());
    }

    @Test
    void testGetOctaneClient_CacheMiss_CreatesNewClient() {
        Octane mockOctane = mock(Octane.class);
        when(veUtils.createOctaneClient("client1", "secret1", "https://server", 1, 2))
                .thenReturn(mockOctane);

        Octane result = octaneCacheService.getOctaneClient("https://server", "client1", "secret1", 1, 2);

        assertNotNull(result);
        assertEquals(mockOctane, result);
        verify(veUtils, times(1)).createOctaneClient("client1", "secret1", "https://server", 1, 2);
    }

    @Test
    void testGetOctaneClient_CacheHit_ReturnsCachedClient() {
        Octane mockOctane = mock(Octane.class);
        when(veUtils.createOctaneClient("client1", "secret1", "https://server", 1, 2))
                .thenReturn(mockOctane);

        // First call — cache miss
        Octane first = octaneCacheService.getOctaneClient("https://server", "client1", "secret1", 1, 2);
        // Second call — cache hit
        Octane second = octaneCacheService.getOctaneClient("https://server", "client1", "secret1", 1, 2);

        assertEquals(first, second);
        // VeUtils should only be called once (second call hits cache)
        verify(veUtils, times(1)).createOctaneClient("client1", "secret1", "https://server", 1, 2);
    }

    @Test
    void testGetOctaneClient_DifferentKeys_CreatesSeparateClients() {
        Octane mockOctane1 = mock(Octane.class);
        Octane mockOctane2 = mock(Octane.class);

        when(veUtils.createOctaneClient("client1", "secret1", "https://server", 1, 2))
                .thenReturn(mockOctane1);
        when(veUtils.createOctaneClient("client2", "secret2", "https://server", 1, 3))
                .thenReturn(mockOctane2);

        Octane result1 = octaneCacheService.getOctaneClient("https://server", "client1", "secret1", 1, 2);
        Octane result2 = octaneCacheService.getOctaneClient("https://server", "client2", "secret2", 1, 3);

        assertEquals(mockOctane1, result1);
        assertEquals(mockOctane2, result2);
        verify(veUtils, times(1)).createOctaneClient("client1", "secret1", "https://server", 1, 2);
        verify(veUtils, times(1)).createOctaneClient("client2", "secret2", "https://server", 1, 3);
    }

    @Test
    void testGetOctaneClient_SameClientDifferentWorkspace_CreatesSeparateClients() {
        Octane mockOctane1 = mock(Octane.class);
        Octane mockOctane2 = mock(Octane.class);

        when(veUtils.createOctaneClient("client1", "secret1", "https://server", 1, 10))
                .thenReturn(mockOctane1);
        when(veUtils.createOctaneClient("client1", "secret1", "https://server", 1, 20))
                .thenReturn(mockOctane2);

        Octane result1 = octaneCacheService.getOctaneClient("https://server", "client1", "secret1", 1, 10);
        Octane result2 = octaneCacheService.getOctaneClient("https://server", "client1", "secret1", 1, 20);

        assertEquals(mockOctane1, result1);
        assertEquals(mockOctane2, result2);
        verify(veUtils, times(2)).createOctaneClient(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }
}
