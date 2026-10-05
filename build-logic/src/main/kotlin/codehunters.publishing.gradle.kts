// Publishing shared by the SDK libraries and the sdk-bom platform.
plugins {
    `maven-publish`
}

group = "io.codehunters"

// GitHub Packages rejects re-publishing a version (409), so publish only what isn't there yet.
fun isAlreadyPublished(repositoryUrl: java.net.URI, group: String, artifact: String, version: String): Boolean {
    val pom = "${group.replace('.', '/')}/$artifact/$version/$artifact-$version.pom"
    val token = System.getenv("GH_PACKAGES_TOKEN")
        ?: throw GradleException("GH_PACKAGES_TOKEN is required to check whether $artifact $version exists")
    val credentials = java.util.Base64.getEncoder()
        .encodeToString("${System.getenv("GH_PACKAGES_USERNAME") ?: "token"}:$token".toByteArray())
    val request = java.net.http.HttpRequest.newBuilder(repositoryUrl.resolve("${repositoryUrl.path.trimEnd('/')}/$pom"))
        .method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody())
        .header("Authorization", "Basic $credentials")
        .timeout(java.time.Duration.ofSeconds(30))
        .build()
    val status = java.net.http.HttpClient.newHttpClient()
        .send(request, java.net.http.HttpResponse.BodyHandlers.discarding())
        .statusCode()
    return when (status) {
        200 -> true
        404 -> false
        else -> throw GradleException("Cannot check $pom in $repositoryUrl: HTTP $status")
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf("version is a release not yet in the target repository") {
        val version = publication.version
        if (version == Project.DEFAULT_VERSION) {
            throw GradleException("${project.name} has no version; set `version` in its build script")
        }
        !version.endsWith("-SNAPSHOT") &&
            !isAlreadyPublished(repository.url, publication.groupId, publication.artifactId, version)
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("${rootProject.findProperty("github_packages_url")}")
            credentials {
                username = System.getenv("GH_PACKAGES_USERNAME")
                password = System.getenv("GH_PACKAGES_TOKEN")
            }
        }
    }

    publications.withType<MavenPublication>().configureEach {
        pom {
            val repositoryUrl = rootProject.property("repository.url").toString()
            name.set(project.name)
            description.set(provider { project.description })
            url.set(repositoryUrl)
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            scm {
                url.set(repositoryUrl)
                connection.set("scm:git:$repositoryUrl.git")
            }
        }
    }
}
