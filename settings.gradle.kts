rootProject.name = "megalodonte-git"
if (providers.gradleProperty("useLocalSources").orNull != "false") {
    includeBuild("../megalodonte-script-app")
}
