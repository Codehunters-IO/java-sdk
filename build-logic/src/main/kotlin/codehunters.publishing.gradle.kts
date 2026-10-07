// Publishing shared by the SDK libraries and the sdk-bom platform.
plugins {
    `maven-publish`
}

group = "io.codehunters"

val codehuntersPublishing = extensions.create<CodehuntersPublishingExtension>("codehuntersPublishing")

// Declaring any capability drops the implicit one, so the module's own is declared too. Read after
// evaluation: `version` is assigned in the module's script, after this plugin is applied.
afterEvaluate {
    val replaced = codehuntersPublishing.replacedArtifacts.get()
    if (replaced.isEmpty()) return@afterEvaluate
    listOf("apiElements", "runtimeElements")
        .mapNotNull { configurations.findByName(it) }
        .forEach { variant ->
            variant.outgoing.capability("$group:$name:$version")
            replaced.forEach { variant.outgoing.capability("$group:$it:$version") }
        }
}

// GitHub Packages rejects re-publishing a version (409), so publish only what isn't there yet. A
// file repository (used by the build-logic tests) is checked on disk.
fun isAlreadyPublished(repositoryUrl: java.net.URI, group: String, artifact: String, version: String): Boolean {
    val pom = "${group.replace('.', '/')}/$artifact/$version/$artifact-$version.pom"
    if (repositoryUrl.scheme == "file") {
        return java.io.File(repositoryUrl.resolve("${repositoryUrl.path.trimEnd('/')}/$pom")).exists()
    }
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

// Each upload to GitHub Packages records its git tag (`sdk-<module>/vX.Y.Z`, or `bom/vX.Y.Z`) in
// build/released-tags/, one file per module. Skipped and failed uploads record nothing, so the
// publish workflow tags exactly what this build released, at the commit that built it.
val tagName = if (name == "sdk-bom") "bom" else name
val releasedTagFile = rootProject.layout.buildDirectory.file("released-tags/$tagName.txt")

tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf("version is a release not yet in the target repository") {
        val version = publication.version
        if (version == Project.DEFAULT_VERSION) {
            throw GradleException("${project.name} has no version; set `version` in its build script")
        }
        !version.endsWith("-SNAPSHOT") &&
            !isAlreadyPublished(repository.url, publication.groupId, publication.artifactId, version)
    }
    val tag = tagName
    val tagFile = releasedTagFile
    doLast {
        if (repository.name == "GitHubPackages") {
            tagFile.get().asFile.apply { parentFile.mkdirs() }.writeText("$tag/v${publication.version}\n")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("${rootProject.findProperty("github_packages_url")}")
            // Gradle rejects credentials on a file repository, which the build-logic tests use.
            if (url.scheme != "file") {
                credentials {
                    username = System.getenv("GH_PACKAGES_USERNAME")
                    password = System.getenv("GH_PACKAGES_TOKEN")
                }
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
