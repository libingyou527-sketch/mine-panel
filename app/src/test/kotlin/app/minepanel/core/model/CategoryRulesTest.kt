package app.minepanel.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryRulesTest {

    @Test
    fun blankPackageIsUnmapped() {
        assertEquals(EventCategory.UNMAPPED, CategoryRules.categorize(null))
        assertEquals(EventCategory.UNMAPPED, CategoryRules.categorize(""))
    }

    @Test
    fun knownPackagesMapToExpectedCategories() {
        assertEquals(EventCategory.SOCIAL, CategoryRules.categorize("com.tencent.mm"))
        assertEquals(EventCategory.SOCIAL, CategoryRules.categorize("com.tencent.mobileqq"))
        assertEquals(EventCategory.MEDIA, CategoryRules.categorize("tv.danmaku.bili"))
        assertEquals(EventCategory.MEDIA, CategoryRules.categorize("com.ss.android.ugc.aweme"))
        assertEquals(EventCategory.WORK, CategoryRules.categorize("com.alibaba.android.rimet"))
        assertEquals(EventCategory.WORK, CategoryRules.categorize("cn.wps.moffice_eng"))
    }

    @Test
    fun unknownPackageIsUnmappedUntilRuleAdded() {
        assertEquals(EventCategory.UNMAPPED, CategoryRules.categorize("com.example.random"))
    }
}
