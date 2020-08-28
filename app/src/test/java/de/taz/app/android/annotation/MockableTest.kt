package de.taz.app.android.annotation

import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class MockableTest {
    @Test
    fun `@Mockable class is opened on test builds`() {
        val fooMock = mock(Foo::class.java)
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