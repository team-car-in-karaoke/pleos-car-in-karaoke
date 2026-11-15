package io.github.teamcarinkaraoke.pleosstage.mic

class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return sayHello(platform.name)
    }
}