package com.cyclingcoach

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.test.context.ActiveProfiles

@Tag("unit")
class ProfileEnforcementTest {
    @Test
    fun `every SpringBootTest class activates the test profile`() {
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AnnotationTypeFilter(SpringBootTest::class.java))
        val violators = scanner.findCandidateComponents("com.cyclingcoach")
            .map { Class.forName(it.beanClassName) }
            .filter { clazz ->
                val profiles = AnnotatedElementUtils
                    .findMergedAnnotation(clazz, ActiveProfiles::class.java)
                profiles == null || Profiles.TEST !in profiles.value
            }
            .map { it.simpleName }
        assertThat(violators)
            .withFailMessage(
                "These @SpringBootTest classes are missing @ActiveProfiles(Profiles.TEST): $violators"
            )
            .isEmpty()
    }
}
