plugins {
    alias(libs.plugins.android.application) apply false
    // не застосовується — лише фіксує версію Kotlin; AGP 9 сам тягнув би 2.2.10
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
