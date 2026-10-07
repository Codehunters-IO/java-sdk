plugins {
    id("codehunters.java-library")
}

version = "1.0.0"
description = "Framework-free helpers: masking, text normalization, passwords, relative time, file names."

dependencies {
    testImplementation(libs.archunit.junit5)
}
