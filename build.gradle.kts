// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 trae Kotlin 2.2; Firebase BoM 35 necesita uno más nuevo. Solo fija la versión (no se aplica).
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.google.services) apply false
}