rootProject.name = "megalodonte-git"
if (providers.gradleProperty("useLocalSources").orNull != "false") {
    includeBuild("../../framework/megalodonte-declarative/megalodonte-script-app")
}
