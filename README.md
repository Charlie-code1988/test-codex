# FinanceAppMvp

Minimal single-module Android MVP scaffold.

## Generate wrapper jar locally

This repository keeps only text files. If `gradle/wrapper/gradle-wrapper.jar` is missing, regenerate it locally:

```bash
gradle wrapper --gradle-version 8.7 --no-validate-url
```

Then Android Studio and `./gradlew` will work as expected.
