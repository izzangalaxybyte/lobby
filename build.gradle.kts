plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// versionCode is the commit count, so every commit is a publishable, higher version for apphound.
fun git(vararg args: String): String = providers.exec { commandLine("git", *args); isIgnoreExitValue = true }.standardOutput.asText.get().trim()
val buildNumber = git("rev-list", "--count", "HEAD").toIntOrNull() ?: 1
val gitSha = git("rev-parse", "--short", "HEAD").ifEmpty { "dev" }
extra["hhVersionCode"] = buildNumber
extra["hhVersionName"] = "0.1.$buildNumber-$gitSha"
