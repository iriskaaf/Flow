package com.v2ray.ang

import com.v2ray.ang.util.SubscriptionTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionTitleTest {
    @Test fun providerTitleIsDecodedAndCleaned() {
        assertEquals("Тест", SubscriptionTitle.provider("base64:0KLQtdGB0YI="))
        assertEquals("Provider A", SubscriptionTitle.provider("Provider\nA"))
        assertNull(SubscriptionTitle.provider("base64:!!!"))
    }

    @Test fun oldPlaceholderUsesHostWithoutExposingToken() {
        assertEquals("example.com", SubscriptionTitle.display("import sub", "https://example.com/private-token?q=secret"))
        assertEquals("My title", SubscriptionTitle.display("My title", "https://example.com/link"))
    }
}
