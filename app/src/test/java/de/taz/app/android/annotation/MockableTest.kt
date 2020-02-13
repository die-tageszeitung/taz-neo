package de.taz.app.android.annotation

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.Test

class MockableTest {
    @Test
    fun `@Mockable class is opened on test builds`() {
        val fooMock = mock<Foo>()
        fooMock.hello()
        fooMock.world()
        verify(fooMock).hello()
        verify(fooMock).world()
    }
}

@Mockable
class Foo {
    internal fun hello() = Unit
    fun world() = Unit
}