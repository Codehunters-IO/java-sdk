plugins {
    id("codehunters.java-library")
}

version = "1.0.0"
description = "The error contract shared by the SDKs: ClientInputException, input the client got wrong."

codehuntersPackages {
    root = "io.codehunters.errors"
}

// The contract depends on the JDK only, so any library can throw it without pulling a framework.
