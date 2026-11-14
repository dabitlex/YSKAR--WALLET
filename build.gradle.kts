// Plugin-Versionen zentral deklariert, in den Modulen nur noch angewandt.
//
// Hinweis: Ab AGP 9.0 bringt das Android-Plugin Kotlin-Unterstuetzung eingebaut mit
// ("built-in Kotlin", android.builtInKotlin=true ist Standard). Das frueher noetige
// Plugin org.jetbrains.kotlin.android darf NICHT mehr angewandt werden - der Build
// bricht sonst ab. Der Compose-Compiler bleibt ein eigenes Plugin.
plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
