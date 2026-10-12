plugins {
    id("codehunters.java-library")
}

version = "3.0.0"
description = "Barcode and QR code rendering (PNG, SVG) and decoding on ZXing, with size limits."

codehuntersPackages {
    root = "io.codehunters.barcode"
}

dependencies {
    // BarcodeRejectedException is a ClientInputException: part of the API.
    // BarcodeRejectedException extends ClientInputException, so the contract is part of the API.
    api(project(":sdk-errors"))
    implementation(libs.zxing.core)

    testImplementation(libs.archunit.junit5)
}

// Rendering and decoding use java.awt images only; the build proves they need no display.
tasks.named<Test>("test") {
    systemProperty("java.awt.headless", "true")
}
