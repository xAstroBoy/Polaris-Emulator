package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AvatarEffectSupportTest {
    @Test
    void acceptsOnlyEffectsBackedByTheDeployedEffectMapAndBundleSet() {
        // The registry currently lists 490 effects. The bar is only here to catch it loading as an
        // empty stub, so it sits below that rather than tracking the exact bundle set.
        assertTrue(AvatarEffectSupport.supportedCount() > 450);
        assertTrue(AvatarEffectSupport.isSupported(1));
        assertTrue(AvatarEffectSupport.isSupported(4000));
        assertFalse(AvatarEffectSupport.isSupported(-3));
        assertFalse(AvatarEffectSupport.isSupported(3999));
        assertEquals(0, AvatarEffectSupport.normalize(-3));
        assertEquals(0, AvatarEffectSupport.normalize(999999999));
    }
}
